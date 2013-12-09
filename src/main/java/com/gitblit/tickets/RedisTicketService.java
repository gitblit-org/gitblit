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

import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import redis.clients.jedis.Client;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.exceptions.JedisException;

import com.gitblit.Keys;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Attachment;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.StringUtils;

/**
 * Implementation of a ticket service based on a Redis key-value store.  All
 * tickets are persisted in the Redis store so it must be configured for
 * durability otherwise tickets are lost on a flush or restart.  Tickets are
 * indexed with Lucene and all queries are executed against the Lucene index.
 *
 * @author James Moger
 *
 */
public class RedisTicketService extends ITicketService {

	private final String namespace = "gb:";

	private final Jedis redis;

	private enum KeyType {
		index, journal, object
	}

	@Inject
	public RedisTicketService(
			IRuntimeManager runtimeManager,
			INotificationManager notificationManager,
			IUserManager userManager,
			IRepositoryManager repositoryManager) {

		super(runtimeManager,
				notificationManager,
				userManager,
				repositoryManager);

		String redisUrl = settings.getString(Keys.tickets.redisUrl, "");
		this.redis = createClient(redisUrl);
	}

	@Override
	public RedisTicketService start() {
		return this;
	}

	@Override
	protected void close() {
	}

	@Override
	public boolean isReady() {
		return redis != null;
	}

	/**
	 * Constructs a key for use with a key-value data store.
	 *
	 * @param key
	 * @param repository
	 * @param id
	 * @return a key
	 */
	private String key(KeyType key, String repository, String id) {
		StringBuilder sb = new StringBuilder();
		sb.append(namespace);
		sb.append(StringUtils.stripDotGit(repository)).append(':');
		sb.append("ticket:");
		sb.append(key.name());
		if (!StringUtils.isEmpty(id)) {
			sb.append(':');
			sb.append(id);
		}
		return sb.toString();
	}

	private boolean isNull(String value) {
		return value == null || "nil".equals(value);
	}

	public String getUrl() {
		if (redis != null) {
			Client client = redis.getClient();
			return client.getHost() + ":" + client.getPort() + "/" + client.getDB();
		}
		return null;
	}

	/**
	 * Reset any caches in the service.
	 */
	@Override
	public synchronized void resetCaches() {
	}

	/**
	 * Returns the list of labels for a repository.
	 *
	 * @param repository
	 * @return the list of labels
	 */
	@Override
	public List<TicketLabel> getLabels(String repository) {
		List<TicketLabel> list = new ArrayList<TicketLabel>();
		if (redis == null) {
			// TODO implement me
			return list;
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
		List<TicketMilestone> list = new ArrayList<TicketMilestone>();
		if (redis == null) {
			// TODO implement me
			return list;
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
	public TicketMilestone createMilestone(String repository, String milestone, String createdBy) {
		TicketMilestone ms = new TicketMilestone(milestone);
		// TODO implement me
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
	@Override
	public boolean updateMilestone(String repository, TicketMilestone milestone, String createdBy) {
		// TODO implement me
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
	@Override
	public boolean renameMilestone(String repository, String oldName, String newName, String createdBy) {
		// TODO implement me
		return false;
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
		String key = key(KeyType.object, repository, "counter");
		String val = redis.get(key);
		if (isNull(val)) {
			redis.set(key, "0");
		}
		long ticketNumber = redis.incr(key);
		return ticketNumber;
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
		String val = redis.get(key(KeyType.index, repository, changeId));
		if (isNull(val)) {
			return 0;
		}
		return Long.parseLong(val);
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
		return redis.get(key(KeyType.index, repository, "" + ticketId));
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
		return redis.exists(key(KeyType.index, repository, changeId));
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
		if (redis == null) {
			return list;
		}

		// Deserialize each ticket and optionally filter out unwanted tickets
		Set<String> keys = redis.keys(key(KeyType.object, repository, "I*"));
		for (String key : keys) {
			String json = redis.get(key);
			TicketModel ticket = TicketSerializer.deserializeTicket(json);

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
		String changeId = getChangeId(repository, ticketId);
		if (!StringUtils.isEmpty(changeId)) {
			TicketModel ticket = getTicket(repository, changeId);
			return ticket;
		}

		return null;
	}

	/**
	 * Retrieves the ticket from Redis store.
	 *
	 * @param repository
	 * @param changeId
	 * @return a ticket, if it exists, otherwise null
	 */
	@Override
	public TicketModel getTicket(String repository, String changeId) {
		if (StringUtils.isEmpty(changeId)) {
			return null;
		}

		if (redis == null) {
			return null;
		}

		try {
			String object = redis.get(key(KeyType.object, repository, changeId));
			if (isNull(object)) {
				List<Change> changes = getJournal(repository, changeId);
				if (ArrayUtils.isEmpty(changes)) {
					return null;
				}
				TicketModel ticket = TicketModel.buildTicket(changes);
				log.debug("rebuilt ticket {} from Redis @ {}", changeId, getUrl());
				return ticket;
			}

			// build from json object
			TicketModel ticket = TicketSerializer.deserializeTicket(object);
			log.debug("retrieved ticket {} from Redis @ {}", changeId, getUrl());
			return ticket;
		} catch (JedisConnectionException e) {
			log.error("failed to connect to Redis @ {}", getUrl());
		} catch (JedisDataException e) {
			log.error("failed to retrieve ticket from Redis @ " + getUrl(), e);
		}
		return null;
	}

	/**
	 * Returns the journal for the specified ticket.
	 *
	 * @param repository
	 * @param changeId
	 * @return a list of changes
	 */
	private List<Change> getJournal(String repository, String changeId) {
		if (!StringUtils.isEmpty(changeId)) {
			List<String> entries = redis.lrange(key(KeyType.journal, repository, changeId), 0, -1);
			if (entries.size() > 0) {
				// build a json array from the individual entries
				StringBuilder sb = new StringBuilder();
				sb.append("[");
				for (String entry : entries) {
					sb.append(entry).append(',');
				}
				sb.setLength(sb.length() - 1);
				sb.append(']');
				String journal = sb.toString();

				return TicketSerializer.deserializeJournal(journal);
			}
		}
		return new ArrayList<Change>();
	}

	@Override
	public boolean supportsAttachments() {
		return false;
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
		return null;
	}

	/**
	 * Deletes a ticket.
	 *
	 * @param ticket
	 * @return true if successful
	 */
	@Override
	protected boolean deleteTicket(TicketModel ticket, String deletedBy) {
		boolean success = false;
		if (ticket == null) {
			throw new RuntimeException("must specify a ticket!");
		}

		if (redis == null) {
			return false;
		}

		try {
			// atomically remove ticket
			Transaction t = redis.multi();
			t.del(key(KeyType.index, ticket.repository, ticket.changeId));
			t.del(key(KeyType.index, ticket.repository, "" + ticket.number));
			t.del(key(KeyType.object, ticket.repository, ticket.changeId));
			t.del(key(KeyType.journal, ticket.repository, ticket.changeId));
			t.exec();

			success = true;
			log.debug("deleted ticket {} from Redis @ {}", "" + ticket.changeId, getUrl());
		} catch (JedisConnectionException e) {
			log.error("failed to connect to Redis @ {}", getUrl());
		} catch (JedisDataException e) {
			log.error("failed to delete ticket from Redis @ " + getUrl(), e);
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
	protected boolean commitChange(String repository, String changeId, Change change) {
		boolean success = false;

		try {
			List<Change> changes = getJournal(repository, changeId);
			changes.add(change);

			// build a new effective ticket from the changes
			TicketModel ticket = TicketModel.buildTicket(changes);
			success = store(ticket, change);

		} catch (Throwable t) {
			log.error(MessageFormat.format("Failed to store ticket {0} in Redis @ {1}",
					changeId, getUrl()), t);
		}
		return success;
	}

	/**
	 * Store the ticket and the change in Redis.
	 *
	 * @param ticket
	 * @param change
	 * @return true, if successful
	 */
	public boolean store(TicketModel ticket, Change change) {
		if (ticket == null) {
			return false;
		}
		if (redis == null) {
			return false;
		}
		try {
			String object = TicketSerializer.serialize(ticket);
			String journal = TicketSerializer.serialize(change);

			// atomically store ticket
			Transaction t = redis.multi();
			t.set(key(KeyType.index, ticket.repository, ticket.changeId), "" + ticket.number);
			t.set(key(KeyType.index, ticket.repository, "" + ticket.number), ticket.changeId);
			t.set(key(KeyType.object, ticket.repository, ticket.changeId), object);
			if (journal != null) {
				t.rpush(key(KeyType.journal, ticket.repository, ticket.changeId), journal);
			}
			t.exec();

			log.debug("updated ticket {} in Redis @ {}", "" + ticket.changeId, getUrl());
			return true;
		} catch (JedisConnectionException e) {
			log.error("failed to connect to Redis @ {}", getUrl());
		} catch (JedisDataException e) {
			log.error("failed to update ticket cache in Redis @ " + getUrl(), e);
		}
		return false;
	}

	/**
	 *  Deletes all Tickets from the Redis key-value store.
	 *
	 */
	@Override
	public boolean deleteAll() {
		if (redis == null) {
			return false;
		}

		boolean success = false;
		try {
			Set<String> keys = redis.keys(namespace + "*:ticket:*");
			if (keys.size() > 0) {
				Transaction t = redis.multi();
				t.del(keys.toArray(new String[keys.size()]));
				t.exec();
			}
			indexer.clear();
			success = true;
		} finally {
		}
		return success;
	}

	private Jedis createClient(String url) {
		Jedis client = null;
		if (!StringUtils.isEmpty(url)) {
			try {
				URI uri = URI.create(url);
				if (uri.getScheme() != null && uri.getScheme().equalsIgnoreCase("redis")) {
					client = new Jedis(uri.getHost(), uri.getPort());
					if (uri.getUserInfo() != null) {
						client.auth(uri.getUserInfo().split(":", 2)[1]);
					}
					if (uri.getPath().indexOf('/') > -1) {
						client.select(Integer.parseInt(uri.getPath().split("/", 2)[1]));
					}
				} else {
					client = new Jedis(url);
				}
			} catch (JedisException e) {
				log.error("failed to create a Redis client!", e);
			}
		}
		return client;
	}

	@Override
	public String toString() {
		String url = getUrl();
		return getClass().getSimpleName() + " (" + (url == null ? "DISABLED" : url) + ")";
	}
}
