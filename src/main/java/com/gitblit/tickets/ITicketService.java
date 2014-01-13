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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.IStoredSettings;
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

		boolean accept(TicketModel issue);
	}

	protected final Logger log;

	protected final IStoredSettings settings;

	protected final IRuntimeManager runtimeManager;

	protected final INotificationManager notificationManager;

	protected final IUserManager userManager;

	protected final IRepositoryManager repositoryManager;

	protected final TicketIndexer indexer;

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
		return !indexer.queryFor(Lucene.repository.matches(repository), 1, 0, null, true).isEmpty();
	}

	/**
	 * Closes any open resources used by this service.
	 */
	protected abstract void close();

	/**
	 * Ensures the change-id is properly formed.
	 *
	 * @param idStr
	 * @return true if the change-id conforms the the spec
	 */
	public boolean isValidChangeId(String idStr) {
		return idStr.matches("^I[0-9a-fA-F]{40}$") && !idStr.matches("^I00*$");
	}

	/**
	 * Generates and sets a changeId
	 *
	 * @param change
	 * @return the change id
	 */
	public String generateChangeId(Change change) {
		String changeId = "I"
				+ StringUtils.getSHA1(change.createdAt.toString()
				+ change.createdBy
				+ change.getString(Field.repository)
				+ change.getString(Field.title)
				+ change.getField(Field.body));
		change.setField(Field.changeId, changeId);
		return changeId;
	}

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
				return tl;
			}
		}
		return new TicketLabel(label);
	}

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
				String q = QueryBuilder.q(Lucene.repository.matches(repository)).and(Lucene.milestone.matches(milestone)).build();
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
	 * Assigns a new long id for the change-id.
	 *
	 * @param repository
	 * @param changeId
	 * @return a new long id for the change-id
	 */
	public abstract long assignTicketId(String repository, String changeId);

	/**
	 * Ensures that we have a ticket for this ticket id.
	 *
	 * @param repository
	 * @param ticketId
	 * @return true if the ticket exists
	 */
	public abstract boolean hasTicket(String repository, long ticketId);

	/**
	 * Ensures that this change-id maps to an existing ticket.
	 *
	 * @param repository
	 * @param changeId
	 * @return true if the ticket exists
	 */
	public abstract boolean hasTicket(String repository, String changeId);

	/**
	 * Returns the ticketId for the changeId
	 *
	 * @param repository
	 * @param changeId
	 * @return ticket id for the changeId, or 0 if it does not exist
	 */
	public abstract long getTicketId(String repository, String changeId);

	/**
	 * Returns the changeId for the ticketId
	 *
	 * @param repository
	 * @param ticketId
	 * @return changeId for the ticketId, or null if it does not exist
	 */
	public abstract String getChangeId(String repository, long ticketId);

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
	 * Returns all open tickets.  This is not a Lucene search!
	 *
	 * @param repository
	 * @return all open tickets
	 */
	public List<TicketModel> getOpenTickets(String repository) {
		return getTickets(repository, new TicketFilter() {
			@Override
			public boolean accept(TicketModel ticket) {
				return ticket.isOpen();
			}
		});
	}

	/**
	 * Returns all closed tickets.  This is not a Lucene search!
	 *
	 * @param repository
	 * @return all closed tickets
	 */
	public List<TicketModel> getClosedTickets(String repository) {
		return getTickets(repository, new TicketFilter() {
			@Override
			public boolean accept(TicketModel ticket) {
				return ticket.isClosed();
			}
		});
	}

	/**
	 * Returns all tickets that satisfy the filter. Querying tickets from the
	 * repository requires deserializing all tickets. This is an  expensive
	 * process and not recommended. Instead, the TicketIndexer should be used
	 * via the queryFor method.
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
	public abstract TicketModel getTicket(String repository, long ticketId);

	/**
	 * Retrieves the ticket.
	 *
	 * @param repository
	 * @param changeId
	 * @return a ticket, if it exists, otherwise null
	 */
	public abstract TicketModel getTicket(String repository, String changeId);

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
	 * @param changeId
	 * @param filename
	 * @return an attachment, if found, null otherwise
	 */
	public abstract Attachment getAttachment(String repository, String changeId, String filename);

	/**
	 * Creates a ticket.  Your change must include a repository, author & title,
	 * at a minimum. If your change does not have those minimum requirements a
	 * RuntimeException will be thrown.
	 *
	 * @param change
	 * @return true if successful
	 */
	public TicketModel createTicket(Change change) {

		if (StringUtils.isEmpty(change.createdBy)) {
			throw new RuntimeException("Must specify a change author!");
		}
		if (!change.hasField(Field.repository)) {
			throw new RuntimeException("Must specify a repository!");
		}
		if (!change.hasField(Field.title)) {
			throw new RuntimeException("Must specify a title!");
		}

		change.setField(Field.createdBy, change.createdBy);

		String changeId = (String) change.getField(Field.changeId);
		if (StringUtils.isEmpty(changeId)) {
			changeId = generateChangeId(change);
		}

		String repository = change.getString(Field.repository);
		if (!change.hasField(Field.number)) {
			long number = assignTicketId(repository, changeId);
			change.setField(Field.number, number);
		}

		change.setField(Field.status, Status.New);

		boolean success = commitChange(repository, changeId, change);
		if (success) {
			TicketModel ticket = getTicket(repository, changeId);
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
	public TicketModel updateTicket(String repository, long ticketId, Change change) {
		String changeId = getChangeId(repository, ticketId);
		return updateTicket(repository, changeId, change);
	}

	/**
	 * Updates a ticket.
	 *
	 * @param repository
	 * @param changeId
	 * @param change
	 * @return the ticket model if successful
	 */
	public TicketModel updateTicket(String repository, String changeId, Change change) {

		if (change == null) {
			throw new RuntimeException("change can not be null!");
		}

		if (StringUtils.isEmpty(change.createdBy)) {
			throw new RuntimeException("must specify a change author!");
		}

		boolean success = commitChange(repository, changeId, change);
		if (success) {
			TicketModel ticket = getTicket(repository, changeId);
			if (indexer != null) {
				indexer.index(ticket);
			}
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
	 * @param changeId
	 * @param deletedBy
	 * @return true if successful
	 */
	public boolean deleteTicket(String repository, String changeId, String deletedBy) {
		TicketModel ticket = getTicket(repository, changeId);
		boolean success = deleteTicket(ticket, deletedBy);
		if (success) {
			indexer.delete(ticket);
			return true;
		}
		return false;
	}

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
	public TicketModel updateComment(TicketModel ticket, String commentId,
			String updatedBy, String comment) {
		Change revision = new Change(updatedBy);
		revision.comment(comment);
		revision.comment.id = commentId;
		TicketModel revisedTicket = updateTicket(ticket.repository, ticket.changeId, revision);
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
	public TicketModel deleteComment(TicketModel ticket, String commentId, String deletedBy) {
		Change deletion = new Change(deletedBy);
		deletion.comment("");
		deletion.comment.id = commentId;
		deletion.comment.deleted = true;
		TicketModel revisedTicket = updateTicket(ticket.repository, ticket.changeId, deletion);
		return revisedTicket;
	}

	/**
	 * Commit a ticket change to the repository.
	 *
	 * @param repository
	 * @param changeId
	 * @param change
	 * @return true, if the change was committed
	 */
	protected abstract boolean commitChange(String repository, String changeId, Change change);


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
		return indexer.searchFor(text, page, pageSize);
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
