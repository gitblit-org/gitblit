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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

	private static class TicketKey {
		final String repository;
		final long ticketId;

		TicketKey(String repository, long ticketId) {
			this.repository = repository;
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

	public boolean isReady(String repository) {
		RepositoryModel model = repositoryManager.getRepositoryModel(repository);
		return isReady() && model.acceptPatches;
	}

	/**
	 * Returns true if the repository has any tickets
	 * @param repository
	 * @return true if the repository has tickets
	 */
	public boolean hasTickets(String repository) {
		return indexer.hasTickets(repository);
	}

	/**
	 * Closes any open resources used by this service.
	 */
	protected abstract void close();

	/**
	 * Reset any caches in the service.
	 */
	public abstract void resetCaches();

	/**
	 * Returns the list of labels for the repository.
	 *
	 * @param repository
	 * @return the list of labels
	 */
	public abstract List<TicketLabel> getLabels(String repository);

	/**
	 * Returns a TicketLabel object for a given label.  If the label is not
	 * found, a ticket label object is created.
	 *
	 * @param repository
	 * @param label
	 * @return a TicketLabel
	 */
	public TicketLabel getLabel(String repository, String label) {
		for (TicketLabel tl : getLabels(repository)) {
			if (tl.name.equalsIgnoreCase(label)) {
				String q = QueryBuilder.q(Lucene.rid.matches(StringUtils.getSHA1(repository))).and(Lucene.labels.matches(label)).build();
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
	 * @param label
	 * @param createdBy
	 * @return the label
	 */
	public abstract TicketLabel createLabel(String repository, String label, String createdBy);

	/**
	 * Updates a label.
	 *
	 * @param repository
	 * @param label
	 * @param createdBy
	 * @return true if successful
	 */
	public abstract boolean updateLabel(String repository, TicketLabel label, String createdBy);

	/**
	 * Renames a label.
	 *
	 * @param repository
	 * @param oldName
	 * @param newName
	 * @param createdBy
	 * @return true if successful
	 */
	public abstract boolean renameLabel(String repository, String oldName, String newName, String createdBy);

	/**
	 * Deletes a label.
	 *
	 * @param repository
	 * @param label
	 * @param createdBy
	 * @return true if successful
	 */
	public abstract boolean deleteLabel(String repository, String label, String createdBy);

	/**
	 * Returns the list of milestones for the repository.
	 *
	 * @param repository
	 * @return the list of milestones
	 */
	public abstract List<TicketMilestone> getMilestones(String repository);

	/**
	 * Returns the list of milestones for the repository that match the status.
	 *
	 * @param repository
	 * @param status
	 * @return the list of milestones
	 */
	public List<TicketMilestone> getMilestones(String repository, Status status) {
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
	public TicketMilestone getMilestone(String repository, String milestone) {
		for (TicketMilestone ms : getMilestones(repository)) {
			if (ms.name.equalsIgnoreCase(milestone)) {
				String q = QueryBuilder.q(Lucene.rid.matches(StringUtils.getSHA1(repository))).and(Lucene.milestone.matches(milestone)).build();
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
	public abstract TicketMilestone createMilestone(String repository, String milestone, String createdBy);

	/**
	 * Updates a milestone.
	 *
	 * @param repository
	 * @param milestone
	 * @param createdBy
	 * @return true if successful
	 */
	public abstract boolean updateMilestone(String repository, TicketMilestone milestone, String createdBy);

	/**
	 * Renames a milestone.
	 *
	 * @param repository
	 * @param oldName
	 * @param newName
	 * @param createdBy
	 * @return true if successful
	 */
	public abstract boolean renameMilestone(String repository, String oldName, String newName, String createdBy);

	/**
	 * Deletes a milestone.
	 *
	 * @param repository
	 * @param milestone
	 * @param createdBy
	 * @return true if successful
	 */
	public abstract boolean deleteMilestone(String repository, String milestone, String createdBy);

	/**
	 * Assigns a new ticket id.
	 *
	 * @param repository
	 * @return a new ticket id
	 */
	public abstract long assignNewId(String repository);

	/**
	 * Ensures that we have a ticket for this ticket id.
	 *
	 * @param repository
	 * @param ticketId
	 * @return true if the ticket exists
	 */
	public abstract boolean hasTicket(String repository, long ticketId);

	/**
	 * Returns all tickets.  This is not a Lucene search!
	 *
	 * @param repository
	 * @return all tickets
	 */
	public List<TicketModel> getTickets(String repository) {
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
	public abstract List<TicketModel> getTickets(String repository, TicketFilter filter);

	/**
	 * Retrieves the ticket.
	 *
	 * @param repository
	 * @param ticketId
	 * @return a ticket, if it exists, otherwise null
	 */
	public final TicketModel getTicket(String repository, long ticketId) {
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
	protected abstract TicketModel getTicketImpl(String repository, long ticketId);

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
	public abstract Attachment getAttachment(String repository, long ticketId, String filename);

	/**
	 * Creates a ticket.  Your change must include a repository, author & title,
	 * at a minimum. If your change does not have those minimum requirements a
	 * RuntimeException will be thrown.
	 *
	 * @param repository
	 * @param change
	 * @return true if successful
	 */
	public TicketModel createTicket(String repository, Change change) {

		if (StringUtils.isEmpty(repository)) {
			throw new RuntimeException("Must specify a repository!");
		}
		if (StringUtils.isEmpty(change.createdBy)) {
			throw new RuntimeException("Must specify a change author!");
		}
		if (!change.hasField(Field.title)) {
			throw new RuntimeException("Must specify a title!");
		}

		change.watch(change.createdBy);

		long ticketId = 0L;
		if (change.hasField(Field.number)) {
			ticketId = Long.parseLong(change.getString(Field.number));
			change.remove(Field.number);
		} else {
			ticketId = assignNewId(repository);
		}

		change.setField(Field.status, Status.New);

		boolean success = commitChange(repository, ticketId, change);
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
	public final TicketModel updateTicket(String repository, long ticketId, Change change) {
		if (change == null) {
			throw new RuntimeException("change can not be null!");
		}

		if (StringUtils.isEmpty(change.createdBy)) {
			throw new RuntimeException("must specify a change author!");
		}

		TicketKey key = new TicketKey(repository, ticketId);
		ticketsCache.invalidate(key);

		boolean success = commitChange(repository, ticketId, change);
		if (success) {
			TicketModel ticket = getTicket(repository, ticketId);
			ticketsCache.put(key, ticket);
			indexer.index(ticket);
			return ticket;
		}
		return null;
	}

	/**
	 * Deletes all tickets.
	 *
	 * @return true if successful
	 */
	public abstract boolean deleteAll();


	/**
	 * Deletes a ticket.
	 *
	 * @param repository
	 * @param ticketId
	 * @param deletedBy
	 * @return true if successful
	 */
	public boolean deleteTicket(String repository, long ticketId, String deletedBy) {
		TicketModel ticket = getTicket(repository, ticketId);
		boolean success = deleteTicket(ticket, deletedBy);
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
	 * @param ticket
	 * @param deletedBy
	 * @return true if successful
	 */
	protected abstract boolean deleteTicket(TicketModel ticket, String deletedBy);


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
		TicketModel revisedTicket = updateTicket(ticket.repository, ticket.number, revision);
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
		TicketModel revisedTicket = updateTicket(ticket.repository, ticket.number, deletion);
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
	protected abstract boolean commitChange(String repository, long ticketId, Change change);


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
	public List<QueryResult> searchFor(String repository, String text, int page, int pageSize) {
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
		indexer.clear();
		for (String repository : repositoryManager.getRepositoryList()) {
			List<TicketModel> tickets = getTickets(repository);
			if (!tickets.isEmpty()) {
				log.info("reindexing {} tickets from {} ...", tickets.size(), repository);
				indexer.index(tickets);
				System.gc();
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
	public void reindex(String repository) {
		long start = System.nanoTime();
		List<TicketModel> tickets = getTickets(repository);
		indexer.index(tickets);
		log.info("reindexing {} tickets from {} ...", tickets.size(), repository);
		long end = System.nanoTime();
		long secs = TimeUnit.NANOSECONDS.toMillis(end - start);
		log.info("reindexing completed in {} msecs.", secs);
	}
}
