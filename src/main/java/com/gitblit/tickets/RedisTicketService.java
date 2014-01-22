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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.apache.commons.pool.impl.GenericObjectPool.Config;

import redis.clients.jedis.Client;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.exceptions.JedisException;

import com.gitblit.Keys;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Attachment;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.models.TicketModel.Field;
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

	private final JedisPool pool;

	private enum KeyType {
		journal, object, milestone, label, counter
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
		this.pool = createPool(redisUrl);
	}

	@Override
	public RedisTicketService start() {
		return this;
	}

	@Override
	protected void close() {
		pool.destroy();
	}

	@Override
	public boolean isReady() {
		return pool != null;
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
		sb.append(StringUtils.stripDotGit(repository)).append(':');
		sb.append("ticket:");
		sb.append(key.name());
		if (!StringUtils.isEmpty(id)) {
			sb.append(':');
			sb.append(id);
		}
		return sb.toString();
	}

	/**
	 * Constructs a key for use with a key-value data store.
	 *
	 * @param key
	 * @param repository
	 * @param id
	 * @return a key
	 */
	private String key(KeyType key, String repository, long id) {
		return key(key, repository, "" + id);
	}

	private boolean isNull(String value) {
		return value == null || "nil".equals(value);
	}

	public String getUrl() {
		Jedis jedis = pool.getResource();
		try {
			if (jedis != null) {
				Client client = jedis.getClient();
				return client.getHost() + ":" + client.getPort() + "/" + client.getDB();
			}
		} catch (JedisException e) {
			pool.returnBrokenResource(jedis);
			jedis = null;
		} finally {
			if (jedis != null) {
				pool.returnResource(jedis);
			}
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
		Jedis jedis = pool.getResource();
		if (jedis == null) {
			return list;
		}
		try {
			Set<String> keys = jedis.keys(key(KeyType.label, repository, "*"));
			for (String key : keys) {
				String json = jedis.get(key);
				if (!isNull(json)) {
					TicketLabel label = TicketSerializer.deserializeLabel(json);
					list.add(label);
				}
			}

			log.debug("retrieved {} labels from in Redis @ {}", "" + list.size(), getUrl());
		} catch (JedisException e) {
			log.error("failed to retrieve labels from Redis @ " + getUrl(), e);
			pool.returnBrokenResource(jedis);
			jedis = null;
		} finally {
			if (jedis != null) {
				pool.returnResource(jedis);
			}
		}
		return list;
	}

	/**
	 * Creates a label.
	 *
	 * @param repository
	 * @param label
	 * @param createdBy
	 * @return the label
	 */
	@Override
	public TicketLabel createLabel(String repository, String label, String createdBy) {
		Jedis jedis = pool.getResource();
		if (jedis == null) {
			return null;
		}
		TicketLabel lb = new TicketLabel(label);
		try {
			String object = TicketSerializer.serialize(lb);
			jedis.set(key(KeyType.label, repository, label), object);

			log.debug("created label {} in Redis @ {}", "" + label, getUrl());
			return lb;
		} catch (JedisException e) {
			log.error("failed to create milestone in Redis @ " + getUrl(), e);
			pool.returnBrokenResource(jedis);
			jedis = null;
		} finally {
			if (jedis != null) {
				pool.returnResource(jedis);
			}
		}
		return lb;
	}

	/**
	 * Updates a label.
	 *
	 * @param repository
	 * @param label
	 * @param createdBy
	 * @return true if successful
	 */
	@Override
	public boolean updateLabel(String repository, TicketLabel label, String createdBy) {
		Jedis jedis = pool.getResource();
		if (jedis == null) {
			return false;
		}
		try {
			String object = TicketSerializer.serialize(label);
			jedis.set(key(KeyType.label, repository, label.name), object);

			log.debug("updated label {} in Redis @ {}", "" + label, getUrl());
			return true;
		} catch (JedisException e) {
			log.error("failed to update label in Redis @ " + getUrl(), e);
			pool.returnBrokenResource(jedis);
			jedis = null;
		} finally {
			if (jedis != null) {
				pool.returnResource(jedis);
			}
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
	 * @return true if successful
	 */
	@Override
	public boolean renameLabel(String repository, String oldName, String newName, String createdBy) {
		Jedis jedis = pool.getResource();
		if (jedis == null) {
			return false;
		}
		try {
			TicketLabel label = getLabel(repository, oldName);
			jedis.rename(key(KeyType.label, repository, oldName), key(KeyType.label, repository, newName));

			log.debug("renamed label from {} to {} in Redis @ {}", new Object [] { oldName, newName, getUrl() });

			for (QueryResult qr : label.tickets) {
				Change change = new Change(createdBy);
				change.unlabel(oldName);
				change.label(newName);
				updateTicket(repository, qr.number, change);
			}

			return true;
		} catch (JedisException e) {
			log.error("failed to rename milestone in Redis @ " + getUrl(), e);
			pool.returnBrokenResource(jedis);
			jedis = null;
		} finally {
			if (jedis != null) {
				pool.returnResource(jedis);
			}
		}
		return false;
	}

	/**
	 * Deletes a label.
	 *
	 * @param repository
	 * @param label
	 * @param createdBy
	 * @return true if successful
	 */
	@Override
	public boolean deleteLabel(String repository, String label, String createdBy) {
		Jedis jedis = pool.getResource();
		if (jedis == null) {
			return false;
		}
		try {
			long count = jedis.del(key(KeyType.label, repository, label));

			log.debug("deleted label {} from Redis @ {}", "" + label, getUrl());
			return count == 1;
		} catch (JedisException e) {
			log.error("failed to delete label from Redis @ " + getUrl(), e);
			pool.returnBrokenResource(jedis);
			jedis = null;
		} finally {
			if (jedis != null) {
				pool.returnResource(jedis);
			}
		}
		return false;
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
		Jedis jedis = pool.getResource();
		if (jedis == null) {
			return list;
		}
		try {
			Set<String> keys = jedis.keys(key(KeyType.milestone, repository, "*"));
			for (String key : keys) {
				String json = jedis.get(key);
				if (!isNull(json)) {
					TicketMilestone milestone = TicketSerializer.deserializeMilestone(json);
					list.add(milestone);
				}
			}

			log.debug("retrieved {} milestones from in Redis @ {}", "" + list.size(), getUrl());
		} catch (JedisException e) {
			log.error("failed to retrieve milestones from Redis @ " + getUrl(), e);
			pool.returnBrokenResource(jedis);
			jedis = null;
		} finally {
			if (jedis != null) {
				pool.returnResource(jedis);
			}
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
		Jedis jedis = pool.getResource();
		if (jedis == null) {
			return null;
		}
		TicketMilestone ms = new TicketMilestone(milestone);
		try {
			String object = TicketSerializer.serialize(ms);
			jedis.set(key(KeyType.milestone, repository, milestone), object);

			log.debug("created milestone {} in Redis @ {}", "" + milestone, getUrl());
			return ms;
		} catch (JedisException e) {
			log.error("failed to create milestone in Redis @ " + getUrl(), e);
			pool.returnBrokenResource(jedis);
			jedis = null;
		} finally {
			if (jedis != null) {
				pool.returnResource(jedis);
			}
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
	@Override
	public boolean updateMilestone(String repository, TicketMilestone milestone, String createdBy) {
		Jedis jedis = pool.getResource();
		if (jedis == null) {
			return false;
		}
		try {
			String object = TicketSerializer.serialize(milestone);
			jedis.set(key(KeyType.milestone, repository, milestone.name), object);

			log.debug("updated milestone {} in Redis @ {}", "" + milestone, getUrl());
			return true;
		} catch (JedisException e) {
			log.error("failed to update milestone in Redis @ " + getUrl(), e);
			pool.returnBrokenResource(jedis);
			jedis = null;
		} finally {
			if (jedis != null) {
				pool.returnResource(jedis);
			}
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
	@Override
	public boolean renameMilestone(String repository, String oldName, String newName, String createdBy) {
		Jedis jedis = pool.getResource();
		if (jedis == null) {
			return false;
		}
		try {
			TicketMilestone milestone = getMilestone(repository, oldName);
			jedis.rename(key(KeyType.milestone, repository, oldName), key(KeyType.milestone, repository, newName));

			log.debug("renamed milestone from {} to {} in Redis @ {}", new Object [] { oldName, newName, getUrl() });

			TicketNotifier notifier = createNotifier();
			for (QueryResult qr : milestone.tickets) {
				Change change = new Change(createdBy);
				change.setField(Field.milestone, newName);
				TicketModel ticket = updateTicket(repository, qr.number, change);
				notifier.queueMailing(ticket);
			}
			notifier.sendAll();

			return true;
		} catch (JedisException e) {
			log.error("failed to rename milestone in Redis @ " + getUrl(), e);
			pool.returnBrokenResource(jedis);
			jedis = null;
		} finally {
			if (jedis != null) {
				pool.returnResource(jedis);
			}
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
	@Override
	public boolean deleteMilestone(String repository, String milestone, String createdBy) {
		Jedis jedis = pool.getResource();
		if (jedis == null) {
			return false;
		}
		try {
			long count = jedis.del(key(KeyType.milestone, repository, milestone));

			log.debug("deleted milestone {} from Redis @ {}", "" + milestone, getUrl());
			return count == 1;
		} catch (JedisException e) {
			log.error("failed to delete milestone from Redis @ " + getUrl(), e);
			pool.returnBrokenResource(jedis);
			jedis = null;
		} finally {
			if (jedis != null) {
				pool.returnResource(jedis);
			}
		}
		return false;
	}

	/**
	 * Ensures that we have a ticket for this ticket id.
	 *
	 * @param repository
	 * @param ticketId
	 * @return true if the ticket exists
	 */
	@Override
	public boolean hasTicket(String repository, long ticketId) {
		if (ticketId <= 0L) {
			return false;
		}
		Jedis jedis = pool.getResource();
		if (jedis == null) {
			return false;
		}
		try {
			Boolean exists = jedis.exists(key(KeyType.journal, repository, ticketId));
			return exists != null && !exists;
		} catch (JedisException e) {
			log.error("failed to check hasTicket from Redis @ " + getUrl(), e);
			pool.returnBrokenResource(jedis);
			jedis = null;
		} finally {
			if (jedis != null) {
				pool.returnResource(jedis);
			}
		}
		return false;
	}

	/**
	 * Assigns a new ticket id.
	 *
	 * @param repository
	 * @return a new long ticket id
	 */
	@Override
	public synchronized long assignNewId(String repository) {
		Jedis jedis = pool.getResource();
		try {
			String key = key(KeyType.counter, repository, null);
			String val = jedis.get(key);
			if (isNull(val)) {
				jedis.set(key, "0");
			}
			long ticketNumber = jedis.incr(key);
			return ticketNumber;
		} catch (JedisException e) {
			log.error("failed to assign new ticket id in Redis @ " + getUrl(), e);
			pool.returnBrokenResource(jedis);
			jedis = null;
		} finally {
			if (jedis != null) {
				pool.returnResource(jedis);
			}
		}
		return 0L;
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
		Jedis jedis = pool.getResource();
		List<TicketModel> list = new ArrayList<TicketModel>();
		if (jedis == null) {
			return list;
		}
		try {
			// Deserialize each ticket and optionally filter out unwanted tickets
			Set<String> keys = jedis.keys(key(KeyType.object, repository, "*"));
			for (String key : keys) {
				String json = jedis.get(key);
				TicketModel ticket = TicketSerializer.deserializeTicket(json);
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
		} catch (JedisException e) {
			log.error("failed to retrieve tickets from Redis @ " + getUrl(), e);
			pool.returnBrokenResource(jedis);
			jedis = null;
		} finally {
			if (jedis != null) {
				pool.returnResource(jedis);
			}
		}
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
	protected TicketModel getTicketImpl(String repository, long ticketId) {
		Jedis jedis = pool.getResource();
		if (jedis == null) {
			return null;
		}

		try {
			String object = jedis.get(key(KeyType.object, repository, ticketId));
			if (isNull(object)) {
				List<Change> changes = getJournal(repository, ticketId);
				if (ArrayUtils.isEmpty(changes)) {
					return null;
				}
				TicketModel ticket = TicketModel.buildTicket(changes);
				ticket.repository = repository;
				ticket.number = ticketId;
				log.debug("rebuilt ticket {} from Redis @ {}", ticketId, getUrl());
				return ticket;
			}

			// build from json object
			TicketModel ticket = TicketSerializer.deserializeTicket(object);
			ticket.repository = repository;
			ticket.number = ticketId;
			log.debug("retrieved ticket {} from Redis @ {}", ticketId, getUrl());
			return ticket;
		} catch (JedisException e) {
			log.error("failed to retrieve ticket from Redis @ " + getUrl(), e);
			pool.returnBrokenResource(jedis);
			jedis = null;
		} finally {
			if (jedis != null) {
				pool.returnResource(jedis);
			}
		}
		return null;
	}

	/**
	 * Returns the journal for the specified ticket.
	 *
	 * @param repository
	 * @param ticketId
	 * @return a list of changes
	 */
	private List<Change> getJournal(String repository, long ticketId) {
		if (ticketId <= 0L) {
			return new ArrayList<Change>();
		}
		Jedis jedis = pool.getResource();
		if (jedis == null) {
			return new ArrayList<Change>();
		}
		try {
			List<String> entries = jedis.lrange(key(KeyType.journal, repository, ticketId), 0, -1);
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
		} catch (JedisException e) {
			log.error("failed to retrieve journal from Redis @ " + getUrl(), e);
			pool.returnBrokenResource(jedis);
			jedis = null;
		} finally {
			if (jedis != null) {
				pool.returnResource(jedis);
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
	 * @param ticketId
	 * @param filename
	 * @return an attachment, if found, null otherwise
	 */
	@Override
	public Attachment getAttachment(String repository, long ticketId, String filename) {
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

		Jedis jedis = pool.getResource();
		if (jedis == null) {
			return false;
		}

		try {
			// atomically remove ticket
			Transaction t = jedis.multi();
			t.del(key(KeyType.object, ticket.repository, ticket.number));
			t.del(key(KeyType.journal, ticket.repository, ticket.number));
			t.exec();

			success = true;
			log.debug("deleted ticket {} from Redis @ {}", "" + ticket.number, getUrl());
		} catch (JedisException e) {
			log.error("failed to delete ticket from Redis @ " + getUrl(), e);
			pool.returnBrokenResource(jedis);
			jedis = null;
		} finally {
			if (jedis != null) {
				pool.returnResource(jedis);
			}
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
	protected boolean commitChange(String repository, long ticketId, Change change) {
		Jedis jedis = pool.getResource();
		if (jedis == null) {
			return false;
		}
		try {
			List<Change> changes = getJournal(repository, ticketId);
			changes.add(change);
			// build a new effective ticket from the changes
			TicketModel ticket = TicketModel.buildTicket(changes);

			String object = TicketSerializer.serialize(ticket);
			String journal = TicketSerializer.serialize(change);

			// atomically store ticket
			Transaction t = jedis.multi();
			t.set(key(KeyType.object, repository, ticketId), object);
			t.rpush(key(KeyType.journal, repository, ticketId), journal);
			t.exec();

			log.debug("updated ticket {} in Redis @ {}", "" + ticketId, getUrl());
			return true;
		} catch (JedisException e) {
			log.error("failed to update ticket cache in Redis @ " + getUrl(), e);
			pool.returnBrokenResource(jedis);
			jedis = null;
		} finally {
			if (jedis != null) {
				pool.returnResource(jedis);
			}
		}
		return false;
	}

	/**
	 *  Deletes all Tickets from the Redis key-value store.
	 *
	 */
	@Override
	public boolean deleteAll() {
		Jedis jedis = pool.getResource();
		if (jedis == null) {
			return false;
		}

		boolean success = false;
		try {
			Set<String> keys = jedis.keys("*:ticket:*");
			if (keys.size() > 0) {
				Transaction t = jedis.multi();
				t.del(keys.toArray(new String[keys.size()]));
				t.exec();
			}
			indexer.clear();
			resetCaches();
			success = true;
		} catch (JedisException e) {
			log.error("failed to delete all tickets in Redis @ " + getUrl(), e);
			pool.returnBrokenResource(jedis);
			jedis = null;
		} finally {
			if (jedis != null) {
				pool.returnResource(jedis);
			}
		}
		return success;
	}

	private JedisPool createPool(String url) {
		JedisPool pool = null;
		if (!StringUtils.isEmpty(url)) {
			try {
				URI uri = URI.create(url);
				if (uri.getScheme() != null && uri.getScheme().equalsIgnoreCase("redis")) {
					int database = Protocol.DEFAULT_DATABASE;
					String password = null;
					if (uri.getUserInfo() != null) {
						password = uri.getUserInfo().split(":", 2)[1];
					}
					if (uri.getPath().indexOf('/') > -1) {
						database = Integer.parseInt(uri.getPath().split("/", 2)[1]);
					}
					pool = new JedisPool(new Config(), uri.getHost(), uri.getPort(), Protocol.DEFAULT_TIMEOUT, password, database);
				} else {
					pool = new JedisPool(url);
				}
			} catch (JedisException e) {
				log.error("failed to create a Redis pool!", e);
			}
		}
		return pool;
	}

	@Override
	public String toString() {
		String url = getUrl();
		return getClass().getSimpleName() + " (" + (url == null ? "DISABLED" : url) + ")";
	}
}
