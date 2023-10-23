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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jgit.lib.Repository;

import com.gitblit.Constants;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IPluginManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Attachment;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.FileUtils;
import com.gitblit.utils.StringUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Implementation of a ticket service based on a directory within the repository.
 * All tickets are serialized as a list of JSON changes and persisted in a hashed
 * directory structure, similar to the standard git loose object structure.
 *
 * @author James Moger
 *
 */
@Singleton
public class FileTicketService extends ITicketService {

	private static final String JOURNAL = "journal.json";

	private static final String TICKETS_PATH = "tickets/";

	private final Map<String, AtomicLong> lastAssignedId;

	@Inject
	public FileTicketService(
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
	}

	@Override
	public void onStart() {
		log.info("{} started", getClass().getSimpleName());
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
	 * Returns the ticket path. This follows the same scheme as Git's object
	 * store path where the first two characters of the hash id are the root
	 * folder with the remaining characters as a subfolder within that folder.
	 *
	 * @param ticketId
	 * @return the root path of the ticket content in the ticket directory
	 */
	private String toTicketPath(long ticketId) {
		StringBuilder sb = new StringBuilder();
		sb.append(TICKETS_PATH);
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
			String journalPath = toTicketPath(ticketId) + "/" + JOURNAL;
			hasTicket = new File(db.getDirectory(), journalPath).exists();
		} finally {
			db.close();
		}
		return hasTicket;
	}

	@Override
	public synchronized Set<Long> getIds(RepositoryModel repository) {
		Set<Long> ids = new TreeSet<Long>();
		Repository db = repositoryManager.getRepository(repository.name);
		try {
			// identify current highest ticket id by scanning the paths in the tip tree
			File dir = new File(db.getDirectory(), TICKETS_PATH);
			dir.mkdirs();
			List<File> journals = findAll(dir, JOURNAL);
			for (File journal : journals) {
				// Reconstruct ticketId from the path
				// id/26/326/journal.json
				String path = FileUtils.getRelativePath(dir, journal);
				String tid = path.split("/")[1];
				long ticketId = Long.parseLong(tid);
				ids.add(ticketId);
			}
		} finally {
			if (db != null) {
				db.close();
			}
		}
		return ids;
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
			File journal = new File(db.getDirectory(), journalPath);
			journal.getParentFile().mkdirs();
			journal.createNewFile();
		} catch (IOException e) {
			log.error("failed to assign ticket id", e);
			return 0L;
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
			// Collect the set of all json files
			File dir = new File(db.getDirectory(), TICKETS_PATH);
			List<File> journals = findAll(dir, JOURNAL);

			// Deserialize each ticket and optionally filter out unwanted tickets
			for (File journal : journals) {
				String json = null;
				try {
					json = new String(FileUtils.readContent(journal), Constants.ENCODING);
				} catch (Exception e) {
					log.error(null, e);
				}
				if (StringUtils.isEmpty(json)) {
					// journal was touched but no changes were written
					continue;
				}
				try {
					// Reconstruct ticketId from the path
					// id/26/326/journal.json
					String path = FileUtils.getRelativePath(dir, journal);
					String tid = path.split("/")[1];
					long ticketId = Long.parseLong(tid);
					List<Change> changes = TicketSerializer.deserializeJournal(json);
					if (ArrayUtils.isEmpty(changes)) {
						log.warn("Empty journal for {}:{}", repository, journal);
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
					log.error("failed to deserialize {}/{}\n{}", repository, journal, e.getMessage());
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

	private List<File> findAll(File dir, String filename) {
		List<File> list = new ArrayList<File>();
		File [] files = dir.listFiles();
		if (files == null) {
			return list;
		}
		for (File file : files) {
			if (file.isDirectory()) {
				list.addAll(findAll(file, filename));
			} else if (file.isFile()) {
				if (file.getName().equalsIgnoreCase(filename)) {
					list.add(file);
				}
			}
		}
		return list;
	}

	/**
	 * Retrieves the ticket from the repository.
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
		if (ticketId <= 0L) {
			return new ArrayList<Change>();
		}

		String journalPath = toTicketPath(ticketId) + "/" + JOURNAL;
		File journal = new File(db.getDirectory(), journalPath);
		if (!journal.exists()) {
			return new ArrayList<Change>();
		}

		String json = null;
		try {
			json = new String(FileUtils.readContent(journal), Constants.ENCODING);
		} catch (Exception e) {
			log.error(null, e);
		}
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
			File file = new File(db.getDirectory(), attachmentPath);
			if (file.exists()) {
				attachment.content = FileUtils.readContent(file);
				attachment.size = attachment.content.length;
			}
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
			String ticketPath = toTicketPath(ticket.number);
			File dir = new File(db.getDirectory(), ticketPath);
			if (dir.exists()) {
				success = FileUtils.delete(dir);
			}
			success = true;
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
			List<Change> changes = getJournal(db, ticketId);
			changes.add(change);
			String journal = TicketSerializer.serializeJournal(changes).trim();

			String journalPath = toTicketPath(ticketId) + "/" + JOURNAL;
			File file = new File(db.getDirectory(), journalPath);
			file.getParentFile().mkdirs();
			FileUtils.writeContent(file, journal);
			success = true;
		} catch (Throwable t) {
			log.error("Failed to commit ticket {} to {}", ticketId, db.getDirectory(), t);
		} finally {
			db.close();
		}
		return success;
	}

	@Override
	protected boolean deleteAllImpl(RepositoryModel repository) {
		Repository db = repositoryManager.getRepository(repository.name);
		if (db == null) {
			// the tickets no longer exist because the db no longer exists
			return true;
		}
		try {
			File dir = new File(db.getDirectory(), TICKETS_PATH);
			return FileUtils.delete(dir);
		} catch (Exception e) {
			log.error(null, e);
		} finally {
			db.close();
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
