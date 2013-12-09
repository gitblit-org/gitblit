/*
 * Copyright 2013 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.tickets;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;

import com.gitblit.Constants;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.models.PathModel;
import com.gitblit.models.RefModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Attachment;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.models.TicketModel.Field;
import com.gitblit.models.TicketModel.Status;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;

/**
 * Implementation of a ticket service based on an orphan branch.  All tickets
 * are serialized as a list of JSON changes and persisted in a hashed directory
 * structure, similar to the standard git structure.  Tickets may optionally be
 * cached in a Redis server for performance or data sharing.
 *
 * @author James Moger
 *
 */
public class RepositoryTicketService extends ITicketService {

	private static final String GITBLIT_TICKETS = "refs/gitblit/tickets";

	private static final String INDEX = "index";

	private static final String SETTINGS = "settings";

	private static final String JOURNAL = "journal.json";

	private static final String LABEL = "label";

	private static final String MILESTONE = "milestone";

	private static final String DUE_DATE_PATTERN = "yyyy-MM-dd";

	@Deprecated
	private static final String CHANGELOG = "changelog.json";

	private final Map<String, Map<String, Long>> cid2number;

	private final Map<String, Map<Long, String>> number2cid;

	private final Map<String, List<TicketLabel>> labelsCache;

	private final Map<String, List<TicketMilestone>> milestonesCache;

	private RedisTicketService rts;

	@Inject
	public RepositoryTicketService(
			IRuntimeManager runtimeManager,
			INotificationManager notificationManager,
			IUserManager userManager,
			IRepositoryManager repositoryManager) {

		super(runtimeManager,
				notificationManager,
				userManager,
				repositoryManager);

		this.cid2number = new ConcurrentHashMap<String, Map<String, Long>>();
		this.number2cid = new ConcurrentHashMap<String, Map<Long, String>>();
		this.labelsCache = new ConcurrentHashMap<String, List<TicketLabel>>();
		this.milestonesCache = new ConcurrentHashMap<String, List<TicketMilestone>>();
	}

	@Override
	public RepositoryTicketService start() {
		// create a nested Redis ticket service which may be used for caching
		// or for real-time data-sharing with other services/tools
		rts = new RedisTicketService(
				runtimeManager,
				notificationManager,
				userManager,
				repositoryManager).start();

		if (rts.isReady()) {
			log.info("Redis is ready to cache tickets.");
		} else {
			log.warn("Redis ticket caching is disabled.");
		}
		return this;
	}

	@Override
	protected void close() {
		rts.stop();
	}

	/**
	 * Returns a RefModel for the refs/gitblit/tickets branch in the repository.
	 * If the branch can not be found, null is returned.
	 *
	 * @return a refmodel for the gitblit tickets branch or null
	 */
	private RefModel getTicketsBranch(Repository db) {
		List<RefModel> refs = JGitUtils.getRefs(db, Constants.R_GITBLIT);
		for (RefModel ref : refs) {
			if (ref.reference.getName().equals(GITBLIT_TICKETS)) {
				return ref;
			}
		}
		return null;
	}

	/**
	 * Creates the refs/gitblit/tickets branch.
	 * @param db
	 */
	private void createTicketsBranch(Repository db) {
		if (JGitUtils.createOrphanBranch(db, GITBLIT_TICKETS, null)) {
			insertResource(db, SETTINGS);
			insertResource(db, INDEX);
		}
	}

	/**
	 * Inserts the classpath resource into the root of the tickets branch.
	 * This is used for initializing a tickets branch with default settings.
	 *
	 * @param db
	 * @param resource
	 */
	private void insertResource(Repository db, String resource) {
		String content = readResource(resource);
		writeTicketsFile(db, resource, content, "gitblit", "added \"" + resource + "\"");
	}

	/**
	 * Returns the ticket path. This follows the same scheme as Git's object
	 * store path where the first two characters of the hash id are the root
	 * folder with the remaining characters as a subfolder within that folder.
	 *
	 * @param changeId
	 * @return the root path of the ticket content on the refs/gitblit/tickets branch
	 */
	private String toTicketPath(String changeId) {
		return "cid/" + changeId.substring(1, 3) + "/" + changeId.substring(3);
	}

	/**
	 * Returns the path to the attachment for the specified ticket.
	 *
	 * @param changeId
	 * @param filename
	 * @return the path to the specified attachment
	 */
	private String toAttachmentPath(String changeId, String filename) {
		return toTicketPath(changeId) + "/attachments/" + filename;
	}

	/**
	 * Reset any caches in the service.
	 */
	@Override
	public synchronized void resetCaches() {
		rts.resetCaches();
		cid2number.clear();
		number2cid.clear();
		labelsCache.clear();
		milestonesCache.clear();
	}

	/**
	 * Returns the list of labels for a repository.
	 *
	 * @param repository
	 * @return the list of labels
	 */
	@Override
	public List<TicketLabel> getLabels(String repository) {
		String key = repository.toLowerCase();
		if (labelsCache.containsKey(key)) {
			return labelsCache.get(key);
		}
		List<TicketLabel> list = new ArrayList<TicketLabel>();
		Repository db = repositoryManager.getRepository(repository);
		try {
			String content = readTicketsFile(db, SETTINGS);
			if (!StringUtils.isEmpty(content)) {
				Config config = new Config();
				config.fromText(content);
				Set<String> names = config.getSubsections(LABEL);
				for (String name : names) {
					TicketLabel label = new TicketLabel(name);
					label.color = config.getString(LABEL, name, "color");
					list.add(label);
				}
				labelsCache.put(key,  Collections.unmodifiableList(list));
			}
		} catch (ConfigInvalidException e) {
			log.error("invalid tickets settings for " + repository, e);
		} finally {
			db.close();
		}
		return list;
	}

	/**
	 * Returns the list of milestones for a repository.
	 *
	 * @param repository
	 * @return the list of milestones
	 */
	@Override
	public List<TicketMilestone> getMilestones(String repository) {
		String key = repository.toLowerCase();
		if (milestonesCache.containsKey(key)) {
			return milestonesCache.get(key);
		}
		List<TicketMilestone> list = new ArrayList<TicketMilestone>();
		Repository db = repositoryManager.getRepository(repository);
		try {
			String content = readTicketsFile(db, SETTINGS);
			if (!StringUtils.isEmpty(content)) {
				Config config = new Config();
				config.fromText(content);
				Set<String> names = config.getSubsections(MILESTONE);
				for (String name : names) {
					TicketMilestone milestone = new TicketMilestone(name);
					Status state = Status.fromObject(config.getString(MILESTONE, name, "state"));
					if (state != null) {
						milestone.state = state;
					}
					milestone.color = config.getString(MILESTONE, name, "color");
					String due = config.getString(MILESTONE, name, "due");
					if (!StringUtils.isEmpty(due)) {
						try {
							milestone.due = new SimpleDateFormat(DUE_DATE_PATTERN).parse(due);
						} catch (ParseException e) {
							log.error("failed to parse {} milestone {} due date \"{}\"",
									new Object [] { repository, name, due });
						}
					}
					list.add(milestone);
				}
				milestonesCache.put(key, Collections.unmodifiableList(list));
			}
		} catch (ConfigInvalidException e) {
			log.error("invalid tickets settings for " + repository, e);
		} finally {
			db.close();
		}
		return list;
	}

	/**
	 * Creates a milestone.
	 *
	 * @param repository
	 * @param milestone
	 * @param createdBy
	 * @return the milestone
	 */
	@Override
	public synchronized TicketMilestone createMilestone(String repository, String milestone, String createdBy) {
		TicketMilestone ms = new TicketMilestone(milestone);
		Repository db = null;
		try {
			db = repositoryManager.getRepository(repository);
			String content = readTicketsFile(db, SETTINGS);
			Config config = new Config();
			config.fromText(content);
			config.setString(MILESTONE, milestone, "state", ms.state.name());
			config.setString(MILESTONE, milestone, "color", ms.color);
			content = config.toText();
			writeTicketsFile(db, SETTINGS, content, createdBy, "created milestone " + milestone);
		} catch (ConfigInvalidException e) {
			log.error("failed to create milestone " + milestone + " in " + repository, e);
		} finally {
			db.close();
		}
		return ms;
	}

	/**
	 * Updates a milestone.
	 *
	 * @param repository
	 * @param milestone
	 * @param createdBy
	 * @return true if the update was successful
	 */
	@Override
	public synchronized boolean updateMilestone(String repository, TicketMilestone milestone, String createdBy) {
		Repository db = null;
		try {
			db = repositoryManager.getRepository(repository);
			String content = readTicketsFile(db, SETTINGS);
			Config config = new Config();
			config.fromText(content);
			config.setString(MILESTONE, milestone.name, "state", milestone.state.name());
			config.setString(MILESTONE, milestone.name, "color", milestone.color);
			if (milestone.due != null) {
				config.setString(MILESTONE, milestone.name, "due",
						new SimpleDateFormat(DUE_DATE_PATTERN).format(milestone.due));
			}
			content = config.toText();
			writeTicketsFile(db, SETTINGS, content, createdBy, "updated milestone " + milestone.name);
			return true;
		} catch (ConfigInvalidException e) {
			log.error("failed to create milestone " + milestone + " in " + repository, e);
		} finally {
			db.close();
		}
		return false;
	}

	/**
	 * Renames a milestone.
	 *
	 * @param repository
	 * @param oldName
	 * @param newName
	 * @param createdBy
	 * @return true if the rename was successful
	 */
	@Override
	public synchronized boolean renameMilestone(String repository, String oldName, String newName, String createdBy) {
		if (StringUtils.isEmpty(newName)) {
			throw new IllegalArgumentException("new milestone can not be empty!");
		}
		Repository db = null;
		try {
			db = repositoryManager.getRepository(repository);
			TicketMilestone milestone = getMilestone(repository, oldName);
			String content = readTicketsFile(db, SETTINGS);
			Config config = new Config();
			config.fromText(content);
			config.unsetSection(MILESTONE, oldName);
			config.setString(MILESTONE, newName, "state", milestone.state.name());
			config.setString(MILESTONE, newName, "color", milestone.color);
			if (milestone.due != null) {
				config.setString(MILESTONE, milestone.name, "due",
						new SimpleDateFormat(DUE_DATE_PATTERN).format(milestone.due));
			}
			content = config.toText();
			writeTicketsFile(db, SETTINGS, content, createdBy, "renamed milestone " + oldName + " => " + newName);

			TicketNotifier notifier = createNotifier();
			for (QueryResult qr : milestone.tickets) {
				Change change = new Change(createdBy);
				change.setField(Field.milestone, newName);
				TicketModel ticket = updateTicket(repository, qr.changeId, change);
				notifier.queueMailing(ticket);
			}
			notifier.sendAll();

			return true;
		} catch (ConfigInvalidException e) {
			log.error("failed to rename milestone " + oldName + " in " + repository, e);
		} finally {
			db.close();
		}
		return false;
	}

	/**
	 * Reads a file from the tickets branch.
	 *
	 * @param db
	 * @param file
	 * @return the file content or null
	 */
	private String readTicketsFile(Repository db, String file) {
		RevWalk rw = null;
		try {
			if (getTicketsBranch(db) == null) {
				createTicketsBranch(db);
			}

			ObjectId treeId = db.resolve(GITBLIT_TICKETS + "^{tree}");
			if (treeId == null) {
				return null;
			}
			rw = new RevWalk(db);
			RevTree tree = rw.lookupTree(treeId);
			if (tree != null) {
				return JGitUtils.getStringContent(db, tree, file, Constants.ENCODING);
			}
		} catch (IOException e) {
			log.error("failed to read " + file, e);
		} finally {
			if (rw != null) {
				rw.release();
			}
		}
		return null;
	}

	/**
	 * Writes a file to the tickets branch.
	 *
	 * @param db
	 * @param file
	 * @param content
	 * @param createdBy
	 * @param msg
	 */
	private void writeTicketsFile(Repository db, String file, String content, String createdBy, String msg) {
		if (getTicketsBranch(db) == null) {
			createTicketsBranch(db);
		}

		DirCache newIndex = DirCache.newInCore();
		DirCacheBuilder builder = newIndex.builder();
		ObjectInserter inserter = db.newObjectInserter();

		try {
			// create an index entry for the revised index
			final DirCacheEntry idIndexEntry = new DirCacheEntry(file);
			idIndexEntry.setLength(content.length());
			idIndexEntry.setLastModified(System.currentTimeMillis());
			idIndexEntry.setFileMode(FileMode.REGULAR_FILE);

			// insert new ticket index
			idIndexEntry.setObjectId(inserter.insert(org.eclipse.jgit.lib.Constants.OBJ_BLOB,
					content.getBytes(Constants.ENCODING)));

			// add to temporary in-core index
			builder.add(idIndexEntry);

			Set<String> ignorePaths = new HashSet<String>();
			ignorePaths.add(file);

			for (DirCacheEntry entry : getTreeEntries(db, ignorePaths)) {
				builder.add(entry);
			}

			// finish temporary in-core index used for this commit
			builder.finish();

			// commit the change
			commitIndex(db, newIndex, createdBy, msg);

		} catch (ConcurrentRefUpdateException e) {
			log.error("", e);
		} catch (IOException e) {
			log.error("", e);
		} finally {
			inserter.release();
		}
	}

	/**
	 * Reads and caches the ticket index for the repository.
	 *
	 * @param repository
	 */
	private void readTicketIndex(Repository db) {
		String key = db.getDirectory().getAbsolutePath();
		if (!cid2number.containsKey(key) || !number2cid.containsKey(key)) {
			RefModel ticketsBranch = getTicketsBranch(db);
			if (ticketsBranch == null) {
				return;
			}

			String idx = readTicketsFile(db, INDEX);
			if (StringUtils.isEmpty(idx)) {
				return;
			}

			Map<String, Long> numberLookup = new ConcurrentHashMap<String, Long>();
			Map<Long, String> changeidLookup = new ConcurrentHashMap<Long, String>();
			for (String line : idx.split("\n")) {
				String [] fields = line.split(" ");
				long tid = Long.parseLong(fields[1]);
				numberLookup.put(fields[0], tid);
				changeidLookup.put(tid, fields[0]);
			}

			cid2number.put(key, numberLookup);
			number2cid.put(key, changeidLookup);
		}
	}

	/**
	 * Assigns a new long id for the change-id.
	 *
	 * @param repository
	 * @param changeId
	 * @return a new long id for the change-id
	 */
	@Override
	public synchronized long assignTicketId(String repository, String changeId) {
		Repository db = repositoryManager.getRepository(repository);
		String idx = readTicketsFile(db, INDEX);
		if (StringUtils.isEmpty(idx)) {
			idx = "";
		}

		// determine current highest assigned id
		// this is almost certainly linear
		long id = 0;
		for (String line : idx.split("\n")) {
			String [] fields = line.split(" ");
			if (fields != null && fields.length > 1) {
				String ns = fields[1];
				long i = Long.parseLong(ns);
				if (i > id) {
					id = i;
				}
			}
		}

		id++;

		// append the new ticket int id to the index
		idx += changeId + " " + id + "\n";

		writeTicketsFile(db, INDEX, idx, "gitblit", id + " = " + changeId);
		db.close();

		// reset the ticket index cache
		String key = db.getDirectory().getAbsolutePath();
		cid2number.remove(key);
		number2cid.remove(key);

		return id;
	}

	/**
	 * Returns the ticketId for the changeId
	 *
	 * @param repository
	 * @param changeId
	 * @return ticket id for the changeId, or 0 if it does not exist
	 */
	@Override
	public long getTicketId(String repository, String changeId) {
		if (StringUtils.isEmpty(changeId)) {
			return 0;
		}
		Repository db = repositoryManager.getRepository(repository);
		try {
			readTicketIndex(db);
		} finally {
			db.close();
		}
		String key = db.getDirectory().getAbsolutePath();
		if (cid2number.containsKey(key)) {
			Long value = cid2number.get(key).get(changeId);
			if (value != null) {
				return value;
			}
		}
		return 0;
	}

	/**
	 * Returns the changeId for the ticketId
	 *
	 * @param repository
	 * @param ticketId
	 * @return changeId for the ticketId, or null if it does not exist
	 */
	@Override
	public String getChangeId(String repository, long ticketId) {
		if (ticketId <= 0) {
			return null;
		}

		Repository db = repositoryManager.getRepository(repository);
		try {
			return getChangeId(db, ticketId);
		} finally {
			db.close();
		}
	}

	/**
	 * Returns the changeId for the ticketId
	 *
	 * @param db
	 * @param ticketId
	 * @return changeId for the ticketId, or null if it does not exist
	 */
	private String getChangeId(Repository db, long ticketId) {
		RefModel ticketsBranch = getTicketsBranch(db);
		if (ticketsBranch == null) {
			return null;
		}
		readTicketIndex(db);
		String key = db.getDirectory().getAbsolutePath();
		if (number2cid.containsKey(key)) {
			return number2cid.get(key).get(ticketId);
		}
		return null;
	}

	/**
	 * Ensures that we have a ticket for this id.
	 *
	 * @param repository
	 * @param ticketId
	 * @return true if the ticket exists
	 */
	@Override
	public boolean hasTicket(String repository, long ticketId) {
		return !StringUtils.isEmpty(getChangeId(repository, ticketId));
	}

	/**
	 * Ensures that this change-id maps to an existing ticket.
	 *
	 * @param repository
	 * @param changeId
	 * @return true if the ticket exists
	 */
	@Override
	public boolean hasTicket(String repository, String changeId) {
		if (StringUtils.isEmpty(changeId)) {
			return false;
		}

		Repository db = repositoryManager.getRepository(repository);
		try {
			if (getTicketsBranch(db) == null) {
				return false;
			}
			String ticketPath = toTicketPath(changeId);
			List<RevCommit> commits = JGitUtils.getRevLog(db, GITBLIT_TICKETS, ticketPath, 0, 1);
			return !commits.isEmpty();
		} finally {
			db.close();
		}
	}

	/**
	 * Returns all the tickets in the repository. Querying tickets from the
	 * repository requires deserializing all tickets. This is an  expensive
	 * process and not recommended. Tickets should be indexed by Lucene and
	 * queries should be executed against that index.
	 *
	 * @param repository
	 * @param filter
	 *            optional filter to only return matching results
	 * @return a list of tickets
	 */
	@Override
	public List<TicketModel> getTickets(String repository, TicketFilter filter) {
		List<TicketModel> list = new ArrayList<TicketModel>();

		Repository db = repositoryManager.getRepository(repository);
		try {
			RefModel ticketsBranch = getTicketsBranch(db);
			if (ticketsBranch == null) {
				return list;
			}

			// Collect the set of all json files
			List<PathModel> paths = JGitUtils.getDocuments(db, Arrays.asList("json"), GITBLIT_TICKETS);

			// Deserialize each ticket and optionally filter out unwanted tickets
			for (PathModel path : paths) {
				String name = path.name.substring(path.name.lastIndexOf('/') + 1);
				if (!JOURNAL.equals(name) && !CHANGELOG.equals(name)) {
					continue;
				}
				String json = readTicketsFile(db, path.path);
				List<Change> changes = TicketSerializer.deserializeJournal(json);
				TicketModel ticket = TicketModel.buildTicket(changes);
				ticket.repository = repository;

				// add the ticket, conditionally, to the list
				if (filter == null) {
					list.add(ticket);
				} else {
					if (filter.accept(ticket)) {
						list.add(ticket);
					}
				}
			}

			// sort the tickets by creation
			Collections.sort(list);
			return list;
		} finally {
			db.close();
		}
	}

	/**
	 * Retrieves the ticket from the repository by first looking-up the changeId
	 * associated with the ticketId.
	 *
	 * @param repository
	 * @param ticketId
	 * @return a ticket, if it exists, otherwise null
	 */
	@Override
	public TicketModel getTicket(String repository, long ticketId) {
		Repository db = repositoryManager.getRepository(repository);
		try {
			String changeId = getChangeId(db, ticketId);
			if (StringUtils.isEmpty(changeId)) {
				return null;
			}

			TicketModel ticket = rts.getTicket(repository, changeId);
			if (ticket != null) {
				ticket.repository = repository;
				return ticket;
			}

			ticket = getTicket(db, changeId);
			if (ticket != null) {
				ticket.repository = repository;
				rts.store(ticket, null);
			}
			return ticket;
		} finally {
			db.close();
		}
	}

	/**
	 * Retrieves the ticket from the repository by deserializing the journal
	 * and building an effective ticket.  If Redis caching is enabled, we try to
	 * deserialize a ticket object.
	 *
	 * @param repository
	 * @param changeId
	 * @return a ticket, if it exists, otherwise null
	 */
	@Override
	public TicketModel getTicket(String repository, String changeId) {
		// illegal changeid
		if (StringUtils.isEmpty(changeId)) {
			return null;
		}

		TicketModel ticket = rts.getTicket(repository, changeId);
		if (ticket != null) {
			ticket.repository = repository;
			return ticket;
		}

		Repository db = repositoryManager.getRepository(repository);
		try {
			ticket = getTicket(db, changeId);
			if (ticket != null) {
				ticket.repository = repository;
				rts.store(ticket, null);
			}
		} finally {
			db.close();
		}
		return ticket;
	}

	/**
	 * Retrieves the ticket from the repository by deserializing the journal
	 * and building an effective ticket.
	 *
	 * @param db
	 * @param changeId
	 * @return a ticket, if it exists, otherwise null
	 */
	private TicketModel getTicket(Repository db, String changeId) {
		List<Change> changes = getJournal(db, changeId);
		if (ArrayUtils.isEmpty(changes)) {
			return null;
		}
		TicketModel ticket = TicketModel.buildTicket(changes);
		return ticket;
	}

	/**
	 * Returns the journal for the specified ticket.
	 *
	 * @param db
	 * @param changeId
	 * @return a list of changes
	 */
	private List<Change> getJournal(Repository db, String changeId) {
		RefModel ticketsBranch = getTicketsBranch(db);
		if (ticketsBranch == null) {
			return new ArrayList<Change>();
		}

		if (StringUtils.isEmpty(changeId)) {
			return new ArrayList<Change>();
		}

		String journalPath = toTicketPath(changeId) + "/" + JOURNAL;
		String json = readTicketsFile(db, journalPath);
		if (json == null) {
			// try older changelog path
			String changelogPath = toTicketPath(changeId) + "/" + CHANGELOG;
			json = readTicketsFile(db, changelogPath);
			if (json == null) {
				return new ArrayList<Change>();
			}
		}
		List<Change> list = TicketSerializer.deserializeJournal(json);
		return list;
	}

	@Override
	public boolean supportsAttachments() {
		return true;
	}

	/**
	 * Retrieves the specified attachment from a ticket.
	 *
	 * @param repository
	 * @param changeId
	 * @param filename
	 * @return an attachment, if found, null otherwise
	 */
	@Override
	public Attachment getAttachment(String repository, String changeId, String filename) {
		if (StringUtils.isEmpty(changeId)) {
			return null;
		}

		// deserialize the ticket model so that we have the attachment metadata
		TicketModel ticket = getTicket(repository, changeId);
		Attachment attachment = ticket.getAttachment(filename);

		// attachment not found
		if (attachment == null) {
			return null;
		}

		// retrieve the attachment content
		Repository db = repositoryManager.getRepository(repository);
		try {
			String attachmentPath = toAttachmentPath(changeId, attachment.name);
			RevTree tree = JGitUtils.getCommit(db, GITBLIT_TICKETS).getTree();
			byte[] content = JGitUtils.getByteContent(db, tree, attachmentPath, false);
			attachment.content = content;
			attachment.size = content.length;
			return attachment;
		} finally {
			db.close();
		}
	}

	/**
	 * Deletes a ticket from the repository.
	 *
	 * @param ticket
	 * @return true if successful
	 */
	@Override
	protected synchronized boolean deleteTicket(TicketModel ticket, String deletedBy) {
		if (ticket == null) {
			throw new RuntimeException("must specify a ticket!");
		}

		boolean success = false;
		Repository db = repositoryManager.getRepository(ticket.repository);
		try {
			RefModel ticketsBranch = getTicketsBranch(db);

			if (ticketsBranch == null) {
				throw new RuntimeException(GITBLIT_TICKETS + " does not exist!");
			}

			String ticketPath = toTicketPath(ticket.changeId);

			TreeWalk treeWalk = null;
			try {
				ObjectId treeId = db.resolve(GITBLIT_TICKETS + "^{tree}");

				// Create the in-memory index of the new/updated ticket
				DirCache index = DirCache.newInCore();
				DirCacheBuilder builder = index.builder();

				// Traverse HEAD to add all other paths
				treeWalk = new TreeWalk(db);
				int hIdx = -1;
				if (treeId != null) {
					hIdx = treeWalk.addTree(treeId);
				}
				treeWalk.setRecursive(true);
				while (treeWalk.next()) {
					String path = treeWalk.getPathString();
					CanonicalTreeParser hTree = null;
					if (hIdx != -1) {
						hTree = treeWalk.getTree(hIdx, CanonicalTreeParser.class);
					}
					if (!path.startsWith(ticketPath)) {
						// add entries from HEAD for all other paths
						if (hTree != null) {
							final DirCacheEntry entry = new DirCacheEntry(path);
							entry.setObjectId(hTree.getEntryObjectId());
							entry.setFileMode(hTree.getEntryFileMode());

							// add to temporary in-core index
							builder.add(entry);
						}
					}
				}

				// finish temporary in-core index used for this commit
				builder.finish();

				success = commitIndex(db, index, deletedBy, "- " + ticket.changeId);

				rts.deleteTicket(ticket, deletedBy);
			} catch (Throwable t) {
				log.error(MessageFormat.format("Failed to delete ticket {0,number,0} from {1}",
						ticket.number, db.getDirectory()), t);
			} finally {
				// release the treewalk
				treeWalk.release();
			}
		} finally {
			db.close();
		}
		return success;
	}

	/**
	 * Commit a ticket change to the repository.
	 *
	 * @param repository
	 * @param changeId
	 * @param change
	 * @return true, if the change was committed
	 */
	@Override
	protected synchronized boolean commitChange(String repository, String changeId, Change change) {
		boolean success = false;

		Repository db = repositoryManager.getRepository(repository);
		try {
			// strip the repository field because it is implied
			change.remove(Field.repository);

			long number = getTicketId(repository, changeId);
			DirCache index = createIndex(db, changeId, change);
			success = commitIndex(db, index, change.createdBy, number + " : " + changeId);

			// optionally cache the ticket in Redis
			if (rts.isReady()) {
				// build a new effective ticket from the changes
				List<Change> changes = getJournal(db, changeId);
				changes.add(change);
				TicketModel ticket = TicketModel.buildTicket(changes);
				ticket.repository = repository;
				rts.store(ticket, null);
			}
		} catch (Throwable t) {
			log.error(MessageFormat.format("Failed to commit ticket {0} to {1}",
					changeId, db.getDirectory()), t);
		} finally {
			db.close();
		}
		return success;
	}

	/**
	 * Creates an in-memory index of the ticket change.
	 *
	 * @param changeId
	 * @param change
	 * @return an in-memory index
	 * @throws IOException
	 */
	private DirCache createIndex(Repository db, String changeId, Change change)
			throws IOException, ClassNotFoundException, NoSuchFieldException {

		String ticketPath = toTicketPath(changeId);
		DirCache newIndex = DirCache.newInCore();
		DirCacheBuilder builder = newIndex.builder();
		ObjectInserter inserter = db.newObjectInserter();

		Set<String> ignorePaths = new TreeSet<String>();
		try {
			// create/update the journal
			// exclude the attachment content
			String journalPath = ticketPath + "/" + JOURNAL;
			String changelogPath = ticketPath + "/" + CHANGELOG;

			ObjectId treeId = db.resolve(GITBLIT_TICKETS + "^{tree}");
			RevTree headTree = new RevWalk(db).parseTree(treeId);

			String journal = JGitUtils.getStringContent(db, headTree, journalPath, Constants.ENCODING);
			if (StringUtils.isEmpty(journal)) {
				// try older changelog path
				journal = JGitUtils.getStringContent(db, headTree, changelogPath, Constants.ENCODING);
			}
			String json = TicketSerializer.serialize(change);
			if (StringUtils.isEmpty(journal)) {
				// journal is an array of changes
				journal = "[\n" + json + "\n]";
			} else {
				StringBuilder sb = new StringBuilder(journal);
				// trim out the end-array bracket
				sb.setLength(journal.length() - 1);
				// append the new change and close the array
				sb.append(",\n").append(json).append("\n]");
				journal = sb.toString();
			}

			byte [] journalBytes = journal.getBytes(Constants.ENCODING);
			final DirCacheEntry journalEntry = new DirCacheEntry(journalPath);
			journalEntry.setLength(journalBytes.length);
			journalEntry.setLastModified(change.createdAt.getTime());
			journalEntry.setFileMode(FileMode.REGULAR_FILE);
			journalEntry.setObjectId(inserter.insert(org.eclipse.jgit.lib.Constants.OBJ_BLOB, journalBytes));

			// add journal to index
			builder.add(journalEntry);
			ignorePaths.add(journalEntry.getPathString());

			// ignore (delete) legacy paths
			ignorePaths.add(changelogPath);
			ignorePaths.add(ticketPath + "/ticket.json");

			// Add any attachments to the index
			if (change.hasAttachments()) {
				for (Attachment attachment : change.attachments) {
					// build a path name for the attachment and mark as ignored
					String path = toAttachmentPath(changeId, attachment.name);
					ignorePaths.add(path);

					// create an index entry for this attachment
					final DirCacheEntry entry = new DirCacheEntry(path);
					entry.setLength(attachment.content.length);
					entry.setLastModified(change.createdAt.getTime());
					entry.setFileMode(FileMode.REGULAR_FILE);

					// insert object
					entry.setObjectId(inserter.insert(org.eclipse.jgit.lib.Constants.OBJ_BLOB, attachment.content));

					// add to temporary in-core index
					builder.add(entry);
				}
			}

			for (DirCacheEntry entry : getTreeEntries(db, ignorePaths)) {
				builder.add(entry);
			}

			// finish the index
			builder.finish();
		} finally {
			inserter.release();
		}
		return newIndex;
	}

	/**
	 * Returns all tree entries that do not match the ignore paths.
	 *
	 * @param db
	 * @param ignorePaths
	 * @param dcBuilder
	 * @throws IOException
	 */
	private List<DirCacheEntry> getTreeEntries(Repository db, Collection<String> ignorePaths) throws IOException {
		List<DirCacheEntry> list = new ArrayList<DirCacheEntry>();
		TreeWalk tw = null;
		try {
			tw = new TreeWalk(db);
			ObjectId treeId = db.resolve(GITBLIT_TICKETS + "^{tree}");
			int hIdx = tw.addTree(treeId);
			tw.setRecursive(true);

			while (tw.next()) {
				String path = tw.getPathString();
				CanonicalTreeParser hTree = null;
				if (hIdx != -1) {
					hTree = tw.getTree(hIdx, CanonicalTreeParser.class);
				}
				if (!ignorePaths.contains(path)) {
					// add all other tree entries
					if (hTree != null) {
						final DirCacheEntry entry = new DirCacheEntry(path);
						entry.setObjectId(hTree.getEntryObjectId());
						entry.setFileMode(hTree.getEntryFileMode());
						list.add(entry);
					}
				}
			}
		} finally {
			if (tw != null) {
				tw.release();
			}
		}
		return list;
	}

	private boolean commitIndex(Repository db, DirCache index, String author, String message) throws IOException, ConcurrentRefUpdateException {
		boolean success = false;

		ObjectId headId = db.resolve(GITBLIT_TICKETS + "^{commit}");
		if (headId == null) {
			// create the branch
			createTicketsBranch(db);
			headId = db.resolve(GITBLIT_TICKETS + "^{commit}");
		}
		ObjectInserter odi = db.newObjectInserter();
		try {
			// Create the in-memory index of the new/updated ticket
			ObjectId indexTreeId = index.writeTree(odi);

			// Create a commit object
			PersonIdent ident = new PersonIdent(author, "gitblit@localhost");
			CommitBuilder commit = new CommitBuilder();
			commit.setAuthor(ident);
			commit.setCommitter(ident);
			commit.setEncoding(Constants.ENCODING);
			commit.setMessage(message);
			commit.setParentId(headId);
			commit.setTreeId(indexTreeId);

			// Insert the commit into the repository
			ObjectId commitId = odi.insert(commit);
			odi.flush();

			RevWalk revWalk = new RevWalk(db);
			try {
				RevCommit revCommit = revWalk.parseCommit(commitId);
				RefUpdate ru = db.updateRef(GITBLIT_TICKETS);
				ru.setNewObjectId(commitId);
				ru.setExpectedOldObjectId(headId);
				ru.setRefLogMessage("commit: " + revCommit.getShortMessage(), false);
				Result rc = ru.forceUpdate();
				switch (rc) {
				case NEW:
				case FORCED:
				case FAST_FORWARD:
					success = true;
					break;
				case REJECTED:
				case LOCK_FAILURE:
					throw new ConcurrentRefUpdateException(JGitText.get().couldNotLockHEAD,
							ru.getRef(), rc);
				default:
					throw new JGitInternalException(MessageFormat.format(
							JGitText.get().updatingRefFailed, GITBLIT_TICKETS, commitId.toString(),
							rc));
				}
			} finally {
				revWalk.release();
			}
		} finally {
			odi.release();
		}
		return success;
	}

	@Override
	public boolean deleteAll() {
		try {
			// TODO finish me
			indexer.clear();
			return true;
		} catch (Exception e) {
			log.error(null, e);
		}
		return false;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}

	protected String readResource(String resource) {
		StringBuilder sb = new StringBuilder();
		InputStream is = null;
		try {
			is = getClass().getResourceAsStream(resource);
			List<String> lines = IOUtils.readLines(is);
			for (String line : lines) {
				sb.append(line).append('\n');
			}
		} catch (IOException e) {

		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
				}
			}
		}
		return sb.toString();
	}
}
