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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

	private final Map<String, Map<String, Long>> cid2number;

	private final Map<String, Map<Long, String>> number2cid;

	private enum KeyType {
		index, journal, object, milestone, label
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

		this.cid2number = new ConcurrentHashMap<String, Map<String, Long>>();
		this.number2cid = new ConcurrentHashMap<String, Map<Long, String>>();
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

	private void cache(String repository, long ticketId, String changeId) {
		if (ticketId > 0 && !StringUtils.isEmpty(changeId)) {
			if (!number2cid.containsKey(repository)) {
				number2cid.put(repository, new ConcurrentHashMap<Long, String>());
			}
			if (!cid2number.containsKey(repository)) {
				cid2number.put(repository, new ConcurrentHashMap<String, Long>());
			}
			number2cid.get(repository).put(ticketId, changeId);
			cid2number.get(repository).put(changeId, ticketId);
		}
	}

	/**
	 * Reset any caches in the service.
	 */
	@Override
	public synchronized void resetCaches() {
		cid2number.clear();
		number2cid.clear();
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
				updateTicket(repository, qr.changeId, change);
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
				TicketModel ticket = updateTicket(repository, qr.changeId, change);
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
	 * Assigns a new long id for the change-id.
	 *
	 * @param repository
	 * @param changeId
	 * @return a new long id for the change-id
	 */
	@Override
	public synchronized long assignTicketId(String repository, String changeId) {
		Jedis jedis = pool.getResource();
		try {
			String key = key(KeyType.object, repository, "counter");
			String val = jedis.get(key);
			if (isNull(val)) {
				jedis.set(key, "0");
			}
			long ticketNumber = jedis.incr(key);
			cache(repository, ticketNumber, changeId);
			return ticketNumber;
		} catch (JedisException e) {
			log.error("failed to assign ticket id to changeid in Redis @ " + getUrl(), e);
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
	 * Returns the ticketId for the changeId
	 *
	 * @param repository
	 * @param changeId
	 * @return ticket id for the changeId, or 0 if it does not exist
	 */
	@Override
	public long getTicketId(String repository, String changeId) {
		if (cid2number.containsKey(repository)) {
			Long ticketId = cid2number.get(repository).get(changeId);
			if (ticketId != null) {
				return ticketId;
			}
		}
		Jedis jedis = pool.getResource();
		try {
			String val = jedis.get(key(KeyType.index, repository, changeId));
			if (isNull(val)) {
				return 0;
			}
			long ticketId = Long.parseLong(val);
			cache(repository, ticketId, changeId);
			return ticketId;
		} catch (JedisException e) {
			log.error("failed to retrieve ticket id for changeid from Redis @ " + getUrl(), e);
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
	 * Returns the changeId for the ticketId
	 *
	 * @param repository
	 * @param ticketId
	 * @return changeId for the ticketId, or null if it does not exist
	 */
	@Override
	public String getChangeId(String repository, long ticketId) {
		if (number2cid.containsKey(repository)) {
			String changeId = number2cid.get(repository).get(ticketId);
			if (!StringUtils.isEmpty(changeId)) {
				return changeId;
			}
		}
		Jedis jedis = pool.getResource();
		try {
			String changeId = jedis.get(key(KeyType.index, repository, "" + ticketId));
			cache(repository, ticketId, changeId);
			return changeId;
		} catch (JedisException e) {
			log.error("failed to retrieve changeid from Redis @ " + getUrl(), e);
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
			Set<String> keys = jedis.keys(key(KeyType.object, repository, "I*"));
			for (String key : keys) {
				String json = jedis.get(key);
				TicketModel ticket = TicketSerializer.deserializeTicket(json);
				cache(repository, ticket.number, ticket.changeId);

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

		Jedis jedis = pool.getResource();
		if (jedis == null) {
			return null;
		}

		try {
			String object = jedis.get(key(KeyType.object, repository, changeId));
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
	 * @param changeId
	 * @return a list of changes
	 */
	private List<Change> getJournal(String repository, String changeId) {
		if (StringUtils.isEmpty(changeId)) {
			return new ArrayList<Change>();
		}
		Jedis jedis = pool.getResource();
		if (jedis == null) {
			return new ArrayList<Change>();
		}
		try {
			List<String> entries = jedis.lrange(key(KeyType.journal, repository, changeId), 0, -1);
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

		Jedis jedis = pool.getResource();
		if (jedis == null) {
			return false;
		}

		try {
			// atomically remove ticket
			Transaction t = jedis.multi();
			t.del(key(KeyType.index, ticket.repository, ticket.changeId));
			t.del(key(KeyType.index, ticket.repository, "" + ticket.number));
			t.del(key(KeyType.object, ticket.repository, ticket.changeId));
			t.del(key(KeyType.journal, ticket.repository, ticket.changeId));
			t.exec();

			success = true;
			log.debug("deleted ticket {} from Redis @ {}", "" + ticket.changeId, getUrl());
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

		Jedis jedis = pool.getResource();
		if (jedis == null) {
			return false;
		}
		try {
			String object = TicketSerializer.serialize(ticket);
			String journal = TicketSerializer.serialize(change);

			// atomically store ticket
			Transaction t = jedis.multi();
			t.set(key(KeyType.index, ticket.repository, ticket.changeId), "" + ticket.number);
			t.set(key(KeyType.index, ticket.repository, "" + ticket.number), ticket.changeId);
			t.set(key(KeyType.object, ticket.repository, ticket.changeId), object);
			if (journal != null) {
				t.rpush(key(KeyType.journal, ticket.repository, ticket.changeId), journal);
			}
			t.exec();

			log.debug("updated ticket {} in Redis @ {}", "" + ticket.changeId, getUrl());
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
