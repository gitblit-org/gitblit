/*
 * Copyright 2014 gitblit.com.
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
import java.text.MessageFormat;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.events.RefsChangedEvent;
import org.eclipse.jgit.events.RefsChangedListener;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;

import com.gitblit.Constants;
import com.gitblit.git.ReceiveCommandEvent;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IPluginManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.models.PathModel;
import com.gitblit.models.PathModel.PathChangeModel;
import com.gitblit.models.RefModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Attachment;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;

/**
 * Implementation of a ticket service based on an orphan branch.  All tickets
 * are serialized as a list of JSON changes and persisted in a hashed directory
 * structure, similar to the standard git loose object structure.
 *
 * @author James Moger
 *
 */
public class BranchTicketService extends ITicketService implements RefsChangedListener {

	public static final String BRANCH = "refs/meta/gitblit/tickets";

	private static final String JOURNAL = "journal.json";

	private static final String ID_PATH = "id/";

	private final Map<String, AtomicLong> lastAssignedId;

	public BranchTicketService(
			IRuntimeManager runtimeManager,
			IPluginManager pluginManager,
			INotificationManager notificationManager,
			IUserManager userManager,
			IRepositoryManager repositoryManager) {

		super(runtimeManager,
				pluginManager,
				notificationManager,
				userManager,
				repositoryManager);

		lastAssignedId = new ConcurrentHashMap<String, AtomicLong>();

		// register the branch ticket service for repository ref changes
		Repository.getGlobalListenerList().addRefsChangedListener(this);
	}

	@Override
	public BranchTicketService start() {
		return this;
	}

	@Override
	protected void resetCachesImpl() {
		lastAssignedId.clear();
	}

	@Override
	protected void resetCachesImpl(RepositoryModel repository) {
		if (lastAssignedId.containsKey(repository.name)) {
			lastAssignedId.get(repository.name).set(0);
		}
	}

	@Override
	protected void close() {
	}

	/**
	 * Listen for tickets branch changes and (re)index tickets, as appropriate
	 */
	@Override
	public synchronized void onRefsChanged(RefsChangedEvent event) {
		if (!(event instanceof ReceiveCommandEvent)) {
			return;
		}

		ReceiveCommandEvent branchUpdate = (ReceiveCommandEvent) event;
		RepositoryModel repository = branchUpdate.model;
		ReceiveCommand cmd = branchUpdate.cmd;
		try {
			switch (cmd.getType()) {
			case CREATE:
			case UPDATE_NONFASTFORWARD:
				// reindex everything
				reindex(repository);
				break;
			case UPDATE:
				// incrementally index ticket updates
				resetCaches(repository);
				long start = System.nanoTime();
				log.info("incrementally indexing {} ticket branch due to received ref update", repository.name);
				Repository db = repositoryManager.getRepository(repository.name);
				try {
					Set<Long> ids = new HashSet<Long>();
					List<PathChangeModel> paths = JGitUtils.getFilesInRange(db,
							cmd.getOldId().getName(), cmd.getNewId().getName());
					for (PathChangeModel path : paths) {
						String name = path.name.substring(path.name.lastIndexOf('/') + 1);
						if (!JOURNAL.equals(name)) {
							continue;
						}
						String tid = path.path.split("/")[2];
						long ticketId = Long.parseLong(tid);
						if (!ids.contains(ticketId)) {
							ids.add(ticketId);
							TicketModel ticket = getTicket(repository, ticketId);
							log.info(MessageFormat.format("indexing ticket #{0,number,0}: {1}",
									ticketId, ticket.title));
							indexer.index(ticket);
						}
					}
					long end = System.nanoTime();
					log.info("incremental indexing of {0} ticket(s) completed in {1} msecs",
							ids.size(), TimeUnit.NANOSECONDS.toMillis(end - start));
				} finally {
					db.close();
				}
				break;
			default:
				log.warn("Unexpected receive type {} in BranchTicketService.onRefsChanged" + cmd.getType());
				break;
			}
		} catch (Exception e) {
			log.error("failed to reindex " + repository.name, e);
		}
	}

	/**
	 * Returns a RefModel for the refs/meta/gitblit/tickets branch in the repository.
	 * If the branch can not be found, null is returned.
	 *
	 * @return a refmodel for the gitblit tickets branch or null
	 */
	private RefModel getTicketsBranch(Repository db) {
		List<RefModel> refs = JGitUtils.getRefs(db, "refs/");
		Ref oldRef = null;
		for (RefModel ref : refs) {
			if (ref.reference.getName().equals(BRANCH)) {
				return ref;
			} else if (ref.reference.getName().equals("refs/gitblit/tickets")) {
				oldRef = ref.reference;
			}
		}
		if (oldRef != null) {
			// rename old ref to refs/meta/gitblit/tickets
			RefRename cmd;
			try {
				cmd = db.renameRef(oldRef.getName(), BRANCH);
				cmd.setRefLogIdent(new PersonIdent("Gitblit", "gitblit@localhost"));
				cmd.setRefLogMessage("renamed " + oldRef.getName() + " => " + BRANCH);
				Result res = cmd.rename();
				switch (res) {
				case RENAMED:
					log.info(db.getDirectory() + " " + cmd.getRefLogMessage());
					return getTicketsBranch(db);
				default:
					log.error("failed to rename " + oldRef.getName() + " => " + BRANCH + " (" + res.name() + ")");
				}
			} catch (IOException e) {
				log.error("failed to rename tickets branch", e);
			}
		}
		return null;
	}

	/**
	 * Creates the refs/meta/gitblit/tickets branch.
	 * @param db
	 */
	private void createTicketsBranch(Repository db) {
		JGitUtils.createOrphanBranch(db, BRANCH, null);
	}

	/**
	 * Returns the ticket path. This follows the same scheme as Git's object
	 * store path where the first two characters of the hash id are the root
	 * folder with the remaining characters as a subfolder within that folder.
	 *
	 * @param ticketId
	 * @return the root path of the ticket content on the refs/meta/gitblit/tickets branch
	 */
	private String toTicketPath(long ticketId) {
		StringBuilder sb = new StringBuilder();
		sb.append(ID_PATH);
		long m = ticketId % 100L;
		if (m < 10) {
			sb.append('0');
		}
		sb.append(m);
		sb.append('/');
		sb.append(ticketId);
		return sb.toString();
	}

	/**
	 * Returns the path to the attachment for the specified ticket.
	 *
	 * @param ticketId
	 * @param filename
	 * @return the path to the specified attachment
	 */
	private String toAttachmentPath(long ticketId, String filename) {
		return toTicketPath(ticketId) + "/attachments/" + filename;
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
			ObjectId treeId = db.resolve(BRANCH + "^{tree}");
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
	 * Ensures that we have a ticket for this ticket id.
	 *
	 * @param repository
	 * @param ticketId
	 * @return true if the ticket exists
	 */
	@Override
	public boolean hasTicket(RepositoryModel repository, long ticketId) {
		boolean hasTicket = false;
		Repository db = repositoryManager.getRepository(repository.name);
		try {
			RefModel ticketsBranch = getTicketsBranch(db);
			if (ticketsBranch == null) {
				return false;
			}
			String ticketPath = toTicketPath(ticketId);
			RevCommit tip = JGitUtils.getCommit(db, BRANCH);
			hasTicket = !JGitUtils.getFilesInPath(db, ticketPath, tip).isEmpty();
		} finally {
			db.close();
		}
		return hasTicket;
	}

	/**
	 * Returns the assigned ticket ids.
	 *
	 * @return the assigned ticket ids
	 */
	@Override
	public synchronized Set<Long> getIds(RepositoryModel repository) {
		Repository db = repositoryManager.getRepository(repository.name);
		try {
			if (getTicketsBranch(db) == null) {
				return Collections.emptySet();
			}
			Set<Long> ids = new TreeSet<Long>();
			List<PathModel> paths = JGitUtils.getDocuments(db, Arrays.asList("json"), BRANCH);
			for (PathModel path : paths) {
				String name = path.name.substring(path.name.lastIndexOf('/') + 1);
				if (!JOURNAL.equals(name)) {
					continue;
				}
				String tid = path.path.split("/")[2];
				long ticketId = Long.parseLong(tid);
				ids.add(ticketId);
			}
			return ids;
		} finally {
			if (db != null) {
				db.close();
			}
		}
	}

	/**
	 * Assigns a new ticket id.
	 *
	 * @param repository
	 * @return a new long id
	 */
	@Override
	public synchronized long assignNewId(RepositoryModel repository) {
		long newId = 0L;
		Repository db = repositoryManager.getRepository(repository.name);
		try {
			if (getTicketsBranch(db) == null) {
				createTicketsBranch(db);
			}

			// identify current highest ticket id by scanning the paths in the tip tree
			if (!lastAssignedId.containsKey(repository.name)) {
				lastAssignedId.put(repository.name, new AtomicLong(0));
			}
			AtomicLong lastId = lastAssignedId.get(repository.name);
			if (lastId.get() <= 0) {
				Set<Long> ids = getIds(repository);
				for (long id : ids) {
					if (id > lastId.get()) {
						lastId.set(id);
					}
				}
			}

			// assign the id and touch an empty journal to hold it's place
			newId = lastId.incrementAndGet();
			String journalPath = toTicketPath(newId) + "/" + JOURNAL;
			writeTicketsFile(db, journalPath, "", "gitblit", "assigned id #" + newId);
		} finally {
			db.close();
		}
		return newId;
	}

	/**
	 * Returns all the tickets in the repository. Querying tickets from the
	 * repository requires deserializing all tickets. This is an  expensive
	 * process and not recommended. Tickets are indexed by Lucene and queries
	 * should be executed against that index.
	 *
	 * @param repository
	 * @param filter
	 *            optional filter to only return matching results
	 * @return a list of tickets
	 */
	@Override
	public List<TicketModel> getTickets(RepositoryModel repository, TicketFilter filter) {
		List<TicketModel> list = new ArrayList<TicketModel>();

		Repository db = repositoryManager.getRepository(repository.name);
		try {
			RefModel ticketsBranch = getTicketsBranch(db);
			if (ticketsBranch == null) {
				return list;
			}

			// Collect the set of all json files
			List<PathModel> paths = JGitUtils.getDocuments(db, Arrays.asList("json"), BRANCH);

			// Deserialize each ticket and optionally filter out unwanted tickets
			for (PathModel path : paths) {
				String name = path.name.substring(path.name.lastIndexOf('/') + 1);
				if (!JOURNAL.equals(name)) {
					continue;
				}
				String json = readTicketsFile(db, path.path);
				if (StringUtils.isEmpty(json)) {
					// journal was touched but no changes were written
					continue;
				}
				try {
					// Reconstruct ticketId from the path
					// id/26/326/journal.json
					String tid = path.path.split("/")[2];
					long ticketId = Long.parseLong(tid);
					List<Change> changes = TicketSerializer.deserializeJournal(json);
					if (ArrayUtils.isEmpty(changes)) {
						log.warn("Empty journal for {}:{}", repository, path.path);
						continue;
					}
					TicketModel ticket = TicketModel.buildTicket(changes);
					ticket.project = repository.projectPath;
					ticket.repository = repository.name;
					ticket.number = ticketId;

					// add the ticket, conditionally, to the list
					if (filter == null) {
						list.add(ticket);
					} else {
						if (filter.accept(ticket)) {
							list.add(ticket);
						}
					}
				} catch (Exception e) {
					log.error("failed to deserialize {}/{}\n{}",
							new Object [] { repository, path.path, e.getMessage()});
					log.error(null, e);
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
	protected TicketModel getTicketImpl(RepositoryModel repository, long ticketId) {
		Repository db = repositoryManager.getRepository(repository.name);
		try {
			List<Change> changes = getJournal(db, ticketId);
			if (ArrayUtils.isEmpty(changes)) {
				log.warn("Empty journal for {}:{}", repository, ticketId);
				return null;
			}
			TicketModel ticket = TicketModel.buildTicket(changes);
			if (ticket != null) {
				ticket.project = repository.projectPath;
				ticket.repository = repository.name;
				ticket.number = ticketId;
			}
			return ticket;
		} finally {
			db.close();
		}
	}

	/**
	 * Retrieves the journal for the ticket.
	 *
	 * @param repository
	 * @param ticketId
	 * @return a journal, if it exists, otherwise null
	 */
	@Override
	protected List<Change> getJournalImpl(RepositoryModel repository, long ticketId) {
		Repository db = repositoryManager.getRepository(repository.name);
		try {
			List<Change> changes = getJournal(db, ticketId);
			if (ArrayUtils.isEmpty(changes)) {
				log.warn("Empty journal for {}:{}", repository, ticketId);
				return null;
			}
			return changes;
		} finally {
			db.close();
		}
	}

	/**
	 * Returns the journal for the specified ticket.
	 *
	 * @param db
	 * @param ticketId
	 * @return a list of changes
	 */
	private List<Change> getJournal(Repository db, long ticketId) {
		RefModel ticketsBranch = getTicketsBranch(db);
		if (ticketsBranch == null) {
			return new ArrayList<Change>();
		}

		if (ticketId <= 0L) {
			return new ArrayList<Change>();
		}

		String journalPath = toTicketPath(ticketId) + "/" + JOURNAL;
		String json = readTicketsFile(db, journalPath);
		if (StringUtils.isEmpty(json)) {
			return new ArrayList<Change>();
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
	 * @param ticketId
	 * @param filename
	 * @return an attachment, if found, null otherwise
	 */
	@Override
	public Attachment getAttachment(RepositoryModel repository, long ticketId, String filename) {
		if (ticketId <= 0L) {
			return null;
		}

		// deserialize the ticket model so that we have the attachment metadata
		TicketModel ticket = getTicket(repository, ticketId);
		Attachment attachment = ticket.getAttachment(filename);

		// attachment not found
		if (attachment == null) {
			return null;
		}

		// retrieve the attachment content
		Repository db = repositoryManager.getRepository(repository.name);
		try {
			String attachmentPath = toAttachmentPath(ticketId, attachment.name);
			RevTree tree = JGitUtils.getCommit(db, BRANCH).getTree();
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
	protected synchronized boolean deleteTicketImpl(RepositoryModel repository, TicketModel ticket, String deletedBy) {
		if (ticket == null) {
			throw new RuntimeException("must specify a ticket!");
		}

		boolean success = false;
		Repository db = repositoryManager.getRepository(ticket.repository);
		try {
			RefModel ticketsBranch = getTicketsBranch(db);

			if (ticketsBranch == null) {
				throw new RuntimeException(BRANCH + " does not exist!");
			}
			String ticketPath = toTicketPath(ticket.number);

			TreeWalk treeWalk = null;
			try {
				ObjectId treeId = db.resolve(BRANCH + "^{tree}");

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

				success = commitIndex(db, index, deletedBy, "- " + ticket.number);

			} catch (Throwable t) {
				log.error(MessageFormat.format("Failed to delete ticket {0,number,0} from {1}",
						ticket.number, db.getDirectory()), t);
			} finally {
				// release the treewalk
				if (treeWalk != null) {
					treeWalk.release();
				}
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
	 * @param ticketId
	 * @param change
	 * @return true, if the change was committed
	 */
	@Override
	protected synchronized boolean commitChangeImpl(RepositoryModel repository, long ticketId, Change change) {
		boolean success = false;

		Repository db = repositoryManager.getRepository(repository.name);
		try {
			DirCache index = createIndex(db, ticketId, change);
			success = commitIndex(db, index, change.author, "#" + ticketId);

		} catch (Throwable t) {
			log.error(MessageFormat.format("Failed to commit ticket {0,number,0} to {1}",
					ticketId, db.getDirectory()), t);
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
	private DirCache createIndex(Repository db, long ticketId, Change change)
			throws IOException, ClassNotFoundException, NoSuchFieldException {

		String ticketPath = toTicketPath(ticketId);
		DirCache newIndex = DirCache.newInCore();
		DirCacheBuilder builder = newIndex.builder();
		ObjectInserter inserter = db.newObjectInserter();

		Set<String> ignorePaths = new TreeSet<String>();
		try {
			// create/update the journal
			// exclude the attachment content
			List<Change> changes = getJournal(db, ticketId);
			changes.add(change);
			String journal = TicketSerializer.serializeJournal(changes).trim();

			byte [] journalBytes = journal.getBytes(Constants.ENCODING);
			String journalPath = ticketPath + "/" + JOURNAL;
			final DirCacheEntry journalEntry = new DirCacheEntry(journalPath);
			journalEntry.setLength(journalBytes.length);
			journalEntry.setLastModified(change.date.getTime());
			journalEntry.setFileMode(FileMode.REGULAR_FILE);
			journalEntry.setObjectId(inserter.insert(org.eclipse.jgit.lib.Constants.OBJ_BLOB, journalBytes));

			// add journal to index
			builder.add(journalEntry);
			ignorePaths.add(journalEntry.getPathString());

			// Add any attachments to the index
			if (change.hasAttachments()) {
				for (Attachment attachment : change.attachments) {
					// build a path name for the attachment and mark as ignored
					String path = toAttachmentPath(ticketId, attachment.name);
					ignorePaths.add(path);

					// create an index entry for this attachment
					final DirCacheEntry entry = new DirCacheEntry(path);
					entry.setLength(attachment.content.length);
					entry.setLastModified(change.date.getTime());
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
			ObjectId treeId = db.resolve(BRANCH + "^{tree}");
			if (treeId == null) {
				// branch does not exist yet, could be migrating tickets
				return list;
			}
			tw = new TreeWalk(db);
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

		ObjectId headId = db.resolve(BRANCH + "^{commit}");
		if (headId == null) {
			// create the branch
			createTicketsBranch(db);
			headId = db.resolve(BRANCH + "^{commit}");
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
				RefUpdate ru = db.updateRef(BRANCH);
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
							JGitText.get().updatingRefFailed, BRANCH, commitId.toString(),
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
	protected boolean deleteAllImpl(RepositoryModel repository) {
		Repository db = repositoryManager.getRepository(repository.name);
		try {
			RefModel branch = getTicketsBranch(db);
			if (branch != null) {
				return JGitUtils.deleteBranchRef(db, BRANCH);
			}
			return true;
		} catch (Exception e) {
			log.error(null, e);
		} finally {
			if (db != null) {
				db.close();
			}
		}
		return false;
	}

	@Override
	protected boolean renameImpl(RepositoryModel oldRepository, RepositoryModel newRepository) {
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}
