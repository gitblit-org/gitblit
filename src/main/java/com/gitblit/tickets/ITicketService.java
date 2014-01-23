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
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Attachment;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.models.TicketModel.Field;
import com.gitblit.models.TicketModel.Status;
import com.gitblit.tickets.TicketIndexer.Lucene;
import com.gitblit.utils.StringUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * Abstract parent class of a ticket service that stubs out required methods
 * and transparently handles Lucene indexing.
 *
 * @author James Moger
 *
 */
public abstract class ITicketService {

	private static final String LABEL = "label";

	private static final String MILESTONE = "milestone";

	private static final String DUE_DATE_PATTERN = "yyyy-MM-dd";

	/**
	 * Object filter interface to querying against all available ticket models.
	 */
	public interface TicketFilter {

		boolean accept(TicketModel ticket);
	}

	protected final Logger log;

	protected final IStoredSettings settings;

	protected final IRuntimeManager runtimeManager;

	protected final INotificationManager notificationManager;

	protected final IUserManager userManager;

	protected final IRepositoryManager repositoryManager;

	protected final TicketIndexer indexer;

	private final Cache<TicketKey, TicketModel> ticketsCache;

	private final Map<String, List<TicketLabel>> labelsCache;

	private final Map<String, List<TicketMilestone>> milestonesCache;

	private static class TicketKey {
		final String repository;
		final long ticketId;

		TicketKey(RepositoryModel repository, long ticketId) {
			this.repository = repository.name;
			this.ticketId = ticketId;
		}

		@Override
		public int hashCode() {
			return (repository + ticketId).hashCode();
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof TicketKey) {
				return o.hashCode() == hashCode();
			}
			return false;
		}

		@Override
		public String toString() {
			return repository + ":" + ticketId;
		}
	}


	/**
	 * Creates a ticket service.
	 */
	public ITicketService(
			IRuntimeManager runtimeManager,
			INotificationManager notificationManager,
			IUserManager userManager,
			IRepositoryManager repositoryManager) {

		this.log = LoggerFactory.getLogger(getClass());
		this.settings = runtimeManager.getSettings();
		this.runtimeManager = runtimeManager;
		this.notificationManager = notificationManager;
		this.userManager = userManager;
		this.repositoryManager = repositoryManager;

		this.indexer = new TicketIndexer(runtimeManager);

		CacheBuilder<Object, Object> cb = CacheBuilder.newBuilder();
		this.ticketsCache = cb
				.maximumSize(1000)
				.expireAfterAccess(10, TimeUnit.MINUTES)
				.build();

		this.labelsCache = new ConcurrentHashMap<String, List<TicketLabel>>();
		this.milestonesCache = new ConcurrentHashMap<String, List<TicketMilestone>>();
	}

	/**
	 * Start the service.
	 *
	 */
	public abstract ITicketService start();

	/**
	 * Stop the service.
	 *
	 */
	public final ITicketService stop() {
		indexer.close();
		ticketsCache.invalidateAll();
		close();
		return this;
	}

	/**
	 * Creates a ticket notifier.  The ticket notifier is not thread-safe!
	 *
	 */
	public TicketNotifier createNotifier() {
		return new TicketNotifier(
				runtimeManager,
				notificationManager,
				userManager,
				repositoryManager,
				this);
	}

	/**
	 * Returns the ready status of the ticket service.
	 *
	 * @return true if the ticket service is ready
	 */
	public boolean isReady() {
		return true;
	}

	public boolean isReady(RepositoryModel repository) {
		return isReady() && repository.acceptPatches;
	}

	/**
	 * Returns true if the repository has any tickets
	 * @param repository
	 * @return true if the repository has tickets
	 */
	public boolean hasTickets(RepositoryModel repository) {
		return indexer.hasTickets(repository);
	}

	/**
	 * Closes any open resources used by this service.
	 */
	protected abstract void close();

	/**
	 * Reset all caches in the service.
	 */
	public final synchronized void resetCaches() {
		ticketsCache.invalidateAll();
		labelsCache.clear();
		milestonesCache.clear();
		resetCachesImpl();
	}

	protected abstract void resetCachesImpl();

	/**
	 * Reset any caches for the repository in the service.
	 */
	public final synchronized void resetCaches(RepositoryModel repository) {
		List<TicketKey> repoKeys = new ArrayList<TicketKey>();
		for (TicketKey key : ticketsCache.asMap().keySet()) {
			if (key.repository.equals(repository.name)) {
				repoKeys.add(key);
			}
		}
		ticketsCache.invalidateAll(repoKeys);
		labelsCache.remove(repository.name);
		milestonesCache.remove(repository.name);
		resetCachesImpl(repository);
	}

	protected abstract void resetCachesImpl(RepositoryModel repository);


	/**
	 * Returns the list of labels for the repository.
	 *
	 * @param repository
	 * @return the list of labels
	 */
	public List<TicketLabel> getLabels(RepositoryModel repository) {
		String key = repository.name;
		if (labelsCache.containsKey(key)) {
			return labelsCache.get(key);
		}
		List<TicketLabel> list = new ArrayList<TicketLabel>();
		Repository db = repositoryManager.getRepository(repository.name);
		try {
			StoredConfig config = db.getConfig();
			Set<String> names = config.getSubsections(LABEL);
			for (String name : names) {
				TicketLabel label = new TicketLabel(name);
				label.color = config.getString(LABEL, name, "color");
				list.add(label);
			}
			labelsCache.put(key,  Collections.unmodifiableList(list));
		} catch (Exception e) {
			log.error("invalid tickets settings for " + repository, e);
		} finally {
			db.close();
		}
		return list;
	}

	/**
	 * Returns a TicketLabel object for a given label.  If the label is not
	 * found, a ticket label object is created.
	 *
	 * @param repository
	 * @param label
	 * @return a TicketLabel
	 */
	public TicketLabel getLabel(RepositoryModel repository, String label) {
		for (TicketLabel tl : getLabels(repository)) {
			if (tl.name.equalsIgnoreCase(label)) {
				String q = QueryBuilder.q(Lucene.rid.matches(repository.getRID())).and(Lucene.labels.matches(label)).build();
				tl.tickets = indexer.queryFor(q, 1, 0, Lucene.number.name(), true);
				return tl;
			}
		}
		return new TicketLabel(label);
	}

	/**
	 * Creates a label.
	 *
	 * @param repository
	 * @param milestone
	 * @param createdBy
	 * @return the label
	 */
	public synchronized TicketLabel createLabel(RepositoryModel repository, String label, String createdBy) {
		TicketLabel lb = new TicketMilestone(label);
		Repository db = null;
		try {
			db = repositoryManager.getRepository(repository.name);
			StoredConfig config = db.getConfig();
			config.setString(LABEL, label, "color", lb.color);
			config.save();
		} catch (IOException e) {
			log.error("failed to create label " + label + " in " + repository, e);
		} finally {
			db.close();
		}
		return lb;
	}

	/**
	 * Updates a label.
	 *
	 * @param repository
	 * @param label
	 * @param createdBy
	 * @return true if the update was successful
	 */
	public synchronized boolean updateLabel(RepositoryModel repository, TicketLabel label, String createdBy) {
		Repository db = null;
		try {
			db = repositoryManager.getRepository(repository.name);
			StoredConfig config = db.getConfig();
			config.setString(LABEL, label.name, "color", label.color);
			config.save();

			return true;
		} catch (IOException e) {
			log.error("failed to update label " + label + " in " + repository, e);
		} finally {
			db.close();
		}
		return false;
	}

	/**
	 * Renames a label.
	 *
	 * @param repository
	 * @param oldName
	 * @param newName
	 * @param createdBy
	 * @return true if the rename was successful
	 */
	public synchronized boolean renameLabel(RepositoryModel repository, String oldName, String newName, String createdBy) {
		if (StringUtils.isEmpty(newName)) {
			throw new IllegalArgumentException("new label can not be empty!");
		}
		Repository db = null;
		try {
			db = repositoryManager.getRepository(repository.name);
			TicketLabel label = getLabel(repository, oldName);
			StoredConfig config = db.getConfig();
			config.unsetSection(LABEL, oldName);
			config.setString(LABEL, newName, "color", label.color);
			config.save();

			for (QueryResult qr : label.tickets) {
				Change change = new Change(createdBy);
				change.unlabel(oldName);
				change.label(newName);
				updateTicket(repository, qr.number, change);
			}

			return true;
		} catch (IOException e) {
			log.error("failed to rename label " + oldName + " in " + repository, e);
		} finally {
			db.close();
		}
		return false;
	}

	/**
	 * Deletes a label.
	 *
	 * @param repository
	 * @param label
	 * @param createdBy
	 * @return true if the delete was successful
	 */
	public synchronized boolean deleteLabel(RepositoryModel repository, String label, String createdBy) {
		if (StringUtils.isEmpty(label)) {
			throw new IllegalArgumentException("label can not be empty!");
		}
		Repository db = null;
		try {
			db = repositoryManager.getRepository(repository.name);
			StoredConfig config = db.getConfig();
			config.unsetSection(LABEL, label);
			config.save();

			return true;
		} catch (IOException e) {
			log.error("failed to delete label " + label + " in " + repository, e);
		} finally {
			db.close();
		}
		return false;
	}

	/**
	 * Returns the list of milestones for the repository.
	 *
	 * @param repository
	 * @return the list of milestones
	 */
	public List<TicketMilestone> getMilestones(RepositoryModel repository) {
		String key = repository.name;
		if (milestonesCache.containsKey(key)) {
			return milestonesCache.get(key);
		}
		List<TicketMilestone> list = new ArrayList<TicketMilestone>();
		Repository db = repositoryManager.getRepository(repository.name);
		try {
			StoredConfig config = db.getConfig();
			Set<String> names = config.getSubsections(MILESTONE);
			for (String name : names) {
				TicketMilestone milestone = new TicketMilestone(name);
				Status status = Status.fromObject(config.getString(MILESTONE, name, "status"));
				if (status != null) {
					milestone.status = status;
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
		} catch (Exception e) {
			log.error("invalid tickets settings for " + repository, e);
		} finally {
			db.close();
		}
		return list;
	}

	/**
	 * Returns the list of milestones for the repository that match the status.
	 *
	 * @param repository
	 * @param status
	 * @return the list of milestones
	 */
	public List<TicketMilestone> getMilestones(RepositoryModel repository, Status status) {
		List<TicketMilestone> matches = new ArrayList<TicketMilestone>();
		for (TicketMilestone milestone : getMilestones(repository)) {
			if (status == milestone.status) {
				matches.add(milestone);
			}
		}
		return matches;
	}

	/**
	 * Returns the specified milestone or null if the milestone does not exist.
	 *
	 * @param repository
	 * @param milestone
	 * @return the milestone or null if it does not exist
	 */
	public TicketMilestone getMilestone(RepositoryModel repository, String milestone) {
		for (TicketMilestone ms : getMilestones(repository)) {
			if (ms.name.equalsIgnoreCase(milestone)) {
				String q = QueryBuilder.q(Lucene.rid.matches(repository.getRID())).and(Lucene.milestone.matches(milestone)).build();
				ms.tickets = indexer.queryFor(q, 1, 0, Lucene.number.name(), true);
				return ms;
			}
		}
		return null;
	}

	/**
	 * Creates a milestone.
	 *
	 * @param repository
	 * @param milestone
	 * @param createdBy
	 * @return the milestone
	 */
	public synchronized TicketMilestone createMilestone(RepositoryModel repository, String milestone, String createdBy) {
		TicketMilestone ms = new TicketMilestone(milestone);
		Repository db = null;
		try {
			db = repositoryManager.getRepository(repository.name);
			StoredConfig config = db.getConfig();
			config.setString(MILESTONE, milestone, "state", ms.status.name());
			config.setString(MILESTONE, milestone, "color", ms.color);
			config.save();

			milestonesCache.remove(repository.name);
		} catch (IOException e) {
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
	 * @return true if successful
	 */
	public synchronized boolean updateMilestone(RepositoryModel repository, TicketMilestone milestone, String createdBy) {
		Repository db = null;
		try {
			db = repositoryManager.getRepository(repository.name);
			StoredConfig config = db.getConfig();
			config.setString(MILESTONE, milestone.name, "state", milestone.status.name());
			config.setString(MILESTONE, milestone.name, "color", milestone.color);
			if (milestone.due != null) {
				config.setString(MILESTONE, milestone.name, "due",
						new SimpleDateFormat(DUE_DATE_PATTERN).format(milestone.due));
			}
			config.save();

			milestonesCache.remove(repository.name);
			return true;
		} catch (IOException e) {
			log.error("failed to update milestone " + milestone + " in " + repository, e);
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
	 * @return true if successful
	 */
	public synchronized boolean renameMilestone(RepositoryModel repository, String oldName, String newName, String createdBy) {
		if (StringUtils.isEmpty(newName)) {
			throw new IllegalArgumentException("new milestone can not be empty!");
		}
		Repository db = null;
		try {
			db = repositoryManager.getRepository(repository.name);
			TicketMilestone milestone = getMilestone(repository, oldName);
			StoredConfig config = db.getConfig();
			config.unsetSection(MILESTONE, oldName);
			config.setString(MILESTONE, newName, "state", milestone.status.name());
			config.setString(MILESTONE, newName, "color", milestone.color);
			if (milestone.due != null) {
				config.setString(MILESTONE, milestone.name, "due",
						new SimpleDateFormat(DUE_DATE_PATTERN).format(milestone.due));
			}
			config.save();

			milestonesCache.remove(repository.name);

			TicketNotifier notifier = createNotifier();
			for (QueryResult qr : milestone.tickets) {
				Change change = new Change(createdBy);
				change.setField(Field.milestone, newName);
				TicketModel ticket = updateTicket(repository, qr.number, change);
				notifier.queueMailing(ticket);
			}
			notifier.sendAll();

			return true;
		} catch (IOException e) {
			log.error("failed to rename milestone " + oldName + " in " + repository, e);
		} finally {
			db.close();
		}
		return false;
	}
	/**
	 * Deletes a milestone.
	 *
	 * @param repository
	 * @param milestone
	 * @param createdBy
	 * @return true if successful
	 */
	public synchronized boolean deleteMilestone(RepositoryModel repository, String milestone, String createdBy) {
		if (StringUtils.isEmpty(milestone)) {
			throw new IllegalArgumentException("milestone can not be empty!");
		}
		Repository db = null;
		try {
			db = repositoryManager.getRepository(repository.name);
			StoredConfig config = db.getConfig();
			config.unsetSection(MILESTONE, milestone);
			config.save();

			milestonesCache.remove(repository.name);

			return true;
		} catch (IOException e) {
			log.error("failed to delete milestone " + milestone + " in " + repository, e);
		} finally {
			db.close();
		}
		return false;
	}

	/**
	 * Assigns a new ticket id.
	 *
	 * @param repository
	 * @return a new ticket id
	 */
	public abstract long assignNewId(RepositoryModel repository);

	/**
	 * Ensures that we have a ticket for this ticket id.
	 *
	 * @param repository
	 * @param ticketId
	 * @return true if the ticket exists
	 */
	public abstract boolean hasTicket(RepositoryModel repository, long ticketId);

	/**
	 * Returns all tickets.  This is not a Lucene search!
	 *
	 * @param repository
	 * @return all tickets
	 */
	public List<TicketModel> getTickets(RepositoryModel repository) {
		return getTickets(repository, null);
	}

	/**
	 * Returns all tickets that satisfy the filter. Retrieving tickets from the
	 * service requires deserializing all journals and building ticket models.
	 * This is an  expensive process and not recommended. Instead, the queryFor
	 * method should be used which executes against the Lucene index.
	 *
	 * @param repository
	 * @param filter
	 *            optional issue filter to only return matching results
	 * @return a list of tickets
	 */
	public abstract List<TicketModel> getTickets(RepositoryModel repository, TicketFilter filter);

	/**
	 * Retrieves the ticket.
	 *
	 * @param repository
	 * @param ticketId
	 * @return a ticket, if it exists, otherwise null
	 */
	public final TicketModel getTicket(RepositoryModel repository, long ticketId) {
		TicketKey key = new TicketKey(repository, ticketId);
		TicketModel ticket = ticketsCache.getIfPresent(key);

		if (ticket == null) {
			// load & cache ticket
			ticket = getTicketImpl(repository, ticketId);
			if (ticket != null) {
				ticketsCache.put(key, ticket);
			}
		}
		return ticket;
	}

	/**
	 * Retrieves the ticket.
	 *
	 * @param repository
	 * @param ticketId
	 * @return a ticket, if it exists, otherwise null
	 */
	protected abstract TicketModel getTicketImpl(RepositoryModel repository, long ticketId);

	/**
	 * Get the ticket url
	 *
	 * @param ticket
	 * @return the ticket url
	 */
	public String getTicketUrl(TicketModel ticket) {
		final String canonicalUrl = settings.getString(Keys.web.canonicalUrl, "https://localhost:8443");
		final String hrefPattern = "{0}/tickets?r={1}&h={2,number,0}";
		return MessageFormat.format(hrefPattern, canonicalUrl, ticket.repository, ticket.number);
	}

	/**
	 * Returns true if attachments are supported.
	 *
	 * @return true if attachments are supported
	 */
	public abstract boolean supportsAttachments();

	/**
	 * Retrieves the specified attachment from a ticket.
	 *
	 * @param repository
	 * @param ticketId
	 * @param filename
	 * @return an attachment, if found, null otherwise
	 */
	public abstract Attachment getAttachment(RepositoryModel repository, long ticketId, String filename);

	/**
	 * Creates a ticket.  Your change must include a repository, author & title,
	 * at a minimum. If your change does not have those minimum requirements a
	 * RuntimeException will be thrown.
	 *
	 * @param repository
	 * @param change
	 * @return true if successful
	 */
	public TicketModel createTicket(RepositoryModel repository, Change change) {
		return createTicket(repository, 0L, change);
	}

	/**
	 * Creates a ticket.  Your change must include a repository, author & title,
	 * at a minimum. If your change does not have those minimum requirements a
	 * RuntimeException will be thrown.
	 *
	 * @param repository
	 * @param ticketId (if <=0 the ticket id will be assigned)
	 * @param change
	 * @return true if successful
	 */
	public TicketModel createTicket(RepositoryModel repository, long ticketId, Change change) {

		if (repository == null) {
			throw new RuntimeException("Must specify a repository!");
		}
		if (StringUtils.isEmpty(change.author)) {
			throw new RuntimeException("Must specify a change author!");
		}
		if (!change.hasField(Field.title)) {
			throw new RuntimeException("Must specify a title!");
		}

		change.watch(change.author);

		if (ticketId <= 0L) {
			ticketId = assignNewId(repository);
		}

		change.setField(Field.status, Status.New);

		boolean success = commitChangeImpl(repository, ticketId, change);
		if (success) {
			TicketModel ticket = getTicket(repository, ticketId);
			indexer.index(ticket);
			return ticket;
		}
		return null;
	}

	/**
	 * Updates a ticket.
	 *
	 * @param repository
	 * @param ticketId
	 * @param change
	 * @return the ticket model if successful
	 */
	public final TicketModel updateTicket(RepositoryModel repository, long ticketId, Change change) {
		if (change == null) {
			throw new RuntimeException("change can not be null!");
		}

		if (StringUtils.isEmpty(change.author)) {
			throw new RuntimeException("must specify a change author!");
		}

		TicketKey key = new TicketKey(repository, ticketId);
		ticketsCache.invalidate(key);

		boolean success = commitChangeImpl(repository, ticketId, change);
		if (success) {
			TicketModel ticket = getTicket(repository, ticketId);
			ticketsCache.put(key, ticket);
			indexer.index(ticket);
			return ticket;
		}
		return null;
	}

	/**
	 * Deletes all tickets in every repository.
	 *
	 * @return true if successful
	 */
	public boolean deleteAll() {
		List<String> repositories = repositoryManager.getRepositoryList();
		BitSet bitset = new BitSet(repositories.size());
		for (int i = 0; i < repositories.size(); i++) {
			String name = repositories.get(i);
			RepositoryModel repository = repositoryManager.getRepositoryModel(name);
			boolean success = deleteAll(repository);
			bitset.set(i, success);
		}
		boolean success = bitset.cardinality() == repositories.size();
		if (success) {
			indexer.deleteAll();
			resetCaches();
		}
		return success;
	}

	/**
	 * Deletes all tickets in the specified repository.
	 * @param repository
	 * @return true if succesful
	 */
	public boolean deleteAll(RepositoryModel repository) {
		boolean success = deleteAllImpl(repository);
		if (success) {
			resetCaches(repository);
			indexer.deleteAll(repository);
		}
		return success;
	}

	protected abstract boolean deleteAllImpl(RepositoryModel repository);

	/**
	 * Handles repository renames.
	 *
	 * @param oldRepositoryName
	 * @param newRepositoryName
	 * @return true if successful
	 */
	public boolean rename(RepositoryModel oldRepository, RepositoryModel newRepository) {
		if (renameImpl(oldRepository, newRepository)) {
			resetCaches(oldRepository);
			indexer.deleteAll(oldRepository);
			reindex(newRepository);
			return true;
		}
		return false;
	}

	protected abstract boolean renameImpl(RepositoryModel oldRepository, RepositoryModel newRepository);

	/**
	 * Deletes a ticket.
	 *
	 * @param repository
	 * @param ticketId
	 * @param deletedBy
	 * @return true if successful
	 */
	public boolean deleteTicket(RepositoryModel repository, long ticketId, String deletedBy) {
		TicketModel ticket = getTicket(repository, ticketId);
		boolean success = deleteTicketImpl(repository, ticket, deletedBy);
		if (success) {
			ticketsCache.invalidate(new TicketKey(repository, ticketId));
			indexer.delete(ticket);
			return true;
		}
		return false;
	}

	/**
	 * Deletes a ticket.
	 *
	 * @param repository
	 * @param ticket
	 * @param deletedBy
	 * @return true if successful
	 */
	protected abstract boolean deleteTicketImpl(RepositoryModel repository, TicketModel ticket, String deletedBy);


	/**
	 * Updates the text of an ticket comment.
	 *
	 * @param ticket
	 * @param commentId
	 *            the id of the comment to revise
	 * @param updatedBy
	 *            the author of the updated comment
	 * @param comment
	 *            the revised comment
	 * @return the revised ticket if the change was successful
	 */
	public final TicketModel updateComment(TicketModel ticket, String commentId,
			String updatedBy, String comment) {
		Change revision = new Change(updatedBy);
		revision.comment(comment);
		revision.comment.id = commentId;
		RepositoryModel repository = repositoryManager.getRepositoryModel(ticket.repository);
		TicketModel revisedTicket = updateTicket(repository, ticket.number, revision);
		return revisedTicket;
	}

	/**
	 * Deletes a comment from a ticket.
	 *
	 * @param ticket
	 * @param commentId
	 *            the id of the comment to delete
	 * @param deletedBy
	 * 			the user deleting the comment
	 * @return the revised ticket if the deletion was successful
	 */
	public final TicketModel deleteComment(TicketModel ticket, String commentId, String deletedBy) {
		Change deletion = new Change(deletedBy);
		deletion.comment("");
		deletion.comment.id = commentId;
		deletion.comment.deleted = true;
		RepositoryModel repository = repositoryManager.getRepositoryModel(ticket.repository);
		TicketModel revisedTicket = updateTicket(repository, ticket.number, deletion);
		return revisedTicket;
	}

	/**
	 * Commit a ticket change to the repository.
	 *
	 * @param repository
	 * @param ticketId
	 * @param change
	 * @return true, if the change was committed
	 */
	protected abstract boolean commitChangeImpl(RepositoryModel repository, long ticketId, Change change);


	/**
	 * Searches for the specified text.  This will use the indexer, if available,
	 * or will fall back to brute-force retrieval of all tickets and string
	 * matching.
	 *
	 * @param repository
	 * @param text
	 * @param page
	 * @param pageSize
	 * @return a list of matching tickets
	 */
	public List<QueryResult> searchFor(RepositoryModel repository, String text, int page, int pageSize) {
		return indexer.searchFor(repository, text, page, pageSize);
	}

	/**
	 * Queries the index for the matching tickets.
	 *
	 * @param query
	 * @param page
	 * @param pageSize
	 * @param sortBy
	 * @param descending
	 * @return a list of matching tickets or an empty list
	 */
	public List<QueryResult> queryFor(String query, int page, int pageSize, String sortBy, boolean descending) {
		return indexer.queryFor(query, page, pageSize, sortBy, descending);
	}

	/**
	 * Destroys an existing index and reindexes all tickets.
	 * This operation may be expensive and time-consuming.
	 */
	public void reindex() {
		long start = System.nanoTime();
		indexer.deleteAll();
		for (String name : repositoryManager.getRepositoryList()) {
			RepositoryModel repository = repositoryManager.getRepositoryModel(name);
			try {
			List<TicketModel> tickets = getTickets(repository);
			if (!tickets.isEmpty()) {
				log.info("reindexing {} tickets from {} ...", tickets.size(), repository);
				indexer.index(tickets);
				System.gc();
			}
			} catch (Exception e) {
				log.error("failed to reindex {}", repository.name);
				log.error(null, e);
			}
		}
		long end = System.nanoTime();
		long secs = TimeUnit.NANOSECONDS.toMillis(end - start);
		log.info("reindexing completed in {} msecs.", secs);
	}

	/**
	 * Destroys any existing index and reindexes all tickets.
	 * This operation may be expensive and time-consuming.
	 */
	public void reindex(RepositoryModel repository) {
		long start = System.nanoTime();
		List<TicketModel> tickets = getTickets(repository);
		indexer.index(tickets);
		log.info("reindexing {} tickets from {} ...", tickets.size(), repository);
		long end = System.nanoTime();
		long secs = TimeUnit.NANOSECONDS.toMillis(end - start);
		log.info("reindexing completed in {} msecs.", secs);
	}
}
