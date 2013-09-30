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
package com.gitblit.fanout;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for Fanout service implementations.
 *
 * Subclass implementations can be used as a Sparkleshare PubSub notification
 * server.  This allows Sparkleshare to be used in conjunction with Gitblit
 * behind a corporate firewall that restricts or prohibits client internet access
 * to the default Sparkleshare PubSub server: notifications.sparkleshare.org
 *
 * @author James Moger
 *
 */
public abstract class FanoutService implements Runnable {

	private final static Logger logger = LoggerFactory.getLogger(FanoutService.class);

	public final static int DEFAULT_PORT = 17000;

	protected final static int serviceTimeout = 5000;

	protected final String host;
	protected final int port;
	protected final String name;

	private Thread serviceThread;

	private final Map<String, FanoutServiceConnection> connections;
	private final Map<String, Set<FanoutServiceConnection>> subscriptions;

	protected final AtomicBoolean isRunning;
	private final AtomicBoolean strictRequestTermination;
	private final AtomicBoolean allowAllChannelAnnouncements;
	private final AtomicInteger concurrentConnectionLimit;

	private final Date bootDate;
	private final AtomicLong rejectedConnectionCount;
	private final AtomicInteger peakConnectionCount;
	private final AtomicLong totalConnections;
	private final AtomicLong totalAnnouncements;
	private final AtomicLong totalMessages;
	private final AtomicLong totalSubscribes;
	private final AtomicLong totalUnsubscribes;
	private final AtomicLong totalPings;

	protected FanoutService(String host, int port, String name) {
		this.host = host;
		this.port = port;
		this.name = name;

		connections = new ConcurrentHashMap<String, FanoutServiceConnection>();
		subscriptions = new ConcurrentHashMap<String, Set<FanoutServiceConnection>>();
		subscriptions.put(FanoutConstants.CH_ALL, new ConcurrentSkipListSet<FanoutServiceConnection>());

		isRunning = new AtomicBoolean(false);
		strictRequestTermination = new AtomicBoolean(false);
		allowAllChannelAnnouncements = new AtomicBoolean(false);
		concurrentConnectionLimit = new AtomicInteger(0);

		bootDate = new Date();
		rejectedConnectionCount = new AtomicLong(0);
		peakConnectionCount = new AtomicInteger(0);
		totalConnections = new AtomicLong(0);
		totalAnnouncements = new AtomicLong(0);
		totalMessages = new AtomicLong(0);
		totalSubscribes = new AtomicLong(0);
		totalUnsubscribes = new AtomicLong(0);
		totalPings = new AtomicLong(0);
	}

	/*
	 * Abstract methods
	 */

	protected abstract boolean isConnected();

	protected abstract boolean connect();

	protected abstract void listen() throws IOException;

	protected abstract void disconnect();

	/**
	 * Returns true if the service requires \n request termination.
	 *
	 * @return true if request requires \n termination
	 */
	public boolean isStrictRequestTermination() {
		return strictRequestTermination.get();
	}

	/**
	 * Control the termination of fanout requests. If true, fanout requests must
	 * be terminated with \n. If false, fanout requests may be terminated with
	 * \n, \r, \r\n, or \n\r. This is useful for debugging with a telnet client.
	 *
	 * @param isStrictTermination
	 */
	public void setStrictRequestTermination(boolean isStrictTermination) {
		strictRequestTermination.set(isStrictTermination);
	}

	/**
	 * Returns the maximum allowable concurrent fanout connections.
	 *
	 * @return the maximum allowable concurrent connection count
	 */
	public int getConcurrentConnectionLimit() {
		return concurrentConnectionLimit.get();
	}

	/**
	 * Sets the maximum allowable concurrent fanout connection count.
	 *
	 * @param value
	 */
	public void setConcurrentConnectionLimit(int value) {
		concurrentConnectionLimit.set(value);
	}

	/**
	 * Returns true if connections are allowed to announce on the all channel.
	 *
	 * @return true if connections are allowed to announce on the all channel
	 */
	public boolean allowAllChannelAnnouncements() {
		return allowAllChannelAnnouncements.get();
	}

	/**
	 * Allows/prohibits connections from announcing on the ALL channel.
	 *
	 * @param value
	 */
	public void setAllowAllChannelAnnouncements(boolean value) {
		allowAllChannelAnnouncements.set(value);
	}

	/**
	 * Returns the current connections
	 *
	 * @param channel
	 * @return map of current connections keyed by their id
	 */
	public Map<String, FanoutServiceConnection> getCurrentConnections() {
		return connections;
	}

	/**
	 * Returns all subscriptions
	 *
	 * @return map of current subscriptions keyed by channel name
	 */
	public Map<String, Set<FanoutServiceConnection>> getCurrentSubscriptions() {
		return subscriptions;
	}

	/**
	 * Returns the subscriptions for the specified channel
	 *
	 * @param channel
	 * @return set of subscribed connections for the specified channel
	 */
	public Set<FanoutServiceConnection> getCurrentSubscriptions(String channel) {
		return subscriptions.get(channel);
	}

	/**
	 * Returns the runtime statistics object for this service.
	 *
	 * @return stats
	 */
	public FanoutStats getStatistics() {
		FanoutStats stats = new FanoutStats();

		// settings
		stats.allowAllChannelAnnouncements = allowAllChannelAnnouncements();
		stats.concurrentConnectionLimit = getConcurrentConnectionLimit();
		stats.strictRequestTermination = isStrictRequestTermination();

		// runtime stats
		stats.bootDate = bootDate;
		stats.rejectedConnectionCount = rejectedConnectionCount.get();
		stats.peakConnectionCount = peakConnectionCount.get();
		stats.totalConnections = totalConnections.get();
		stats.totalAnnouncements = totalAnnouncements.get();
		stats.totalMessages = totalMessages.get();
		stats.totalSubscribes = totalSubscribes.get();
		stats.totalUnsubscribes = totalUnsubscribes.get();
		stats.totalPings = totalPings.get();
		stats.currentConnections = connections.size();
		stats.currentChannels = subscriptions.size();
		stats.currentSubscriptions = subscriptions.size() * connections.size();
		return stats;
	}

	/**
	 * Returns true if the service is ready.
	 *
	 * @return true, if the service is ready
	 */
	public boolean isReady() {
		if (isRunning.get()) {
			return isConnected();
		}
		return false;
	}

	/**
	 * Start the Fanout service thread and immediatel return.
	 *
	 */
	public void start() {
		if (isRunning.get()) {
			logger.warn(MessageFormat.format("{0} is already running", name));
			return;
		}
		serviceThread = new Thread(this);
		serviceThread.setName(MessageFormat.format("{0} {1}:{2,number,0}", name, host == null ? "all" : host, port));
		serviceThread.start();
	}

	/**
	 * Start the Fanout service thread and wait until it is accepting connections.
	 *
	 */
	public void startSynchronously() {
		start();
		while (!isReady()) {
			try {
				Thread.sleep(100);
			} catch (Exception e) {
			}
		}
	}

	/**
	 * Stop the Fanout service.  This method returns when the service has been
	 * completely shutdown.
	 */
	public void stop() {
		if (!isRunning.get()) {
			logger.warn(MessageFormat.format("{0} is not running", name));
			return;
		}
		logger.info(MessageFormat.format("stopping {0}...", name));
		isRunning.set(false);
		try {
			if (serviceThread != null) {
				serviceThread.join();
				serviceThread = null;
			}
		} catch (InterruptedException e1) {
			logger.error("", e1);
		}
		logger.info(MessageFormat.format("stopped {0}", name));
	}

	/**
	 * Main execution method of the service
	 */
	@Override
	public final void run() {
		disconnect();
		resetState();
		isRunning.set(true);
		while (isRunning.get()) {
			if (connect()) {
				try {
					listen();
				} catch (IOException e) {
					logger.error(MessageFormat.format("error processing {0}", name), e);
					isRunning.set(false);
				}
			} else {
				try {
					Thread.sleep(serviceTimeout);
				} catch (InterruptedException x) {
				}
			}
		}
		disconnect();
		resetState();
	}

	protected void resetState() {
		// reset state data
		connections.clear();
		subscriptions.clear();
		rejectedConnectionCount.set(0);
		peakConnectionCount.set(0);
		totalConnections.set(0);
		totalAnnouncements.set(0);
		totalMessages.set(0);
		totalSubscribes.set(0);
		totalUnsubscribes.set(0);
		totalPings.set(0);
	}

	/**
	 * Configure the client connection socket.
	 *
	 * @param socket
	 * @throws SocketException
	 */
	protected void configureClientSocket(Socket socket) throws SocketException {
		socket.setKeepAlive(true);
		socket.setSoLinger(true, 0); // immediately discard any remaining data
	}

	/**
	 * Add the connection to the connections map.
	 *
	 * @param connection
	 * @return false if the connection was rejected due to too many concurrent
	 *         connections
	 */
	protected boolean addConnection(FanoutServiceConnection connection) {
		int limit = getConcurrentConnectionLimit();
		if (limit > 0 && connections.size() > limit) {
			logger.info(MessageFormat.format("hit {0,number,0} connection limit, rejecting fanout connection", concurrentConnectionLimit));
			increment(rejectedConnectionCount);
			connection.busy();
			return false;
		}

		// add the connection to our map
		connections.put(connection.id, connection);

		// track peak number of concurrent connections
		if (connections.size() > peakConnectionCount.get()) {
			peakConnectionCount.set(connections.size());
		}

		logger.info("fanout new connection " + connection.id);
		connection.connected();
		return true;
	}

	/**
	 * Remove the connection from the connections list and from subscriptions.
	 *
	 * @param connection
	 */
	protected void removeConnection(FanoutServiceConnection connection) {
		connections.remove(connection.id);
		Iterator<Map.Entry<String, Set<FanoutServiceConnection>>> itr = subscriptions.entrySet().iterator();
		while (itr.hasNext()) {
			Map.Entry<String, Set<FanoutServiceConnection>> entry = itr.next();
			Set<FanoutServiceConnection> subscriptions = entry.getValue();
			subscriptions.remove(connection);
			if (!FanoutConstants.CH_ALL.equals(entry.getKey())) {
				if (subscriptions.size() == 0) {
					itr.remove();
					logger.info(MessageFormat.format("fanout remove channel {0}, no subscribers", entry.getKey()));
				}
			}
		}
		logger.info(MessageFormat.format("fanout connection {0} removed", connection.id));
	}

	/**
	 * Tests to see if the connection is being monitored by the service.
	 *
	 * @param connection
	 * @return true if the service is monitoring the connection
	 */
	protected boolean hasConnection(FanoutServiceConnection connection) {
		return connections.containsKey(connection.id);
	}

	/**
	 * Reply to a connection on the specified channel.
	 *
	 * @param connection
	 * @param channel
	 * @param message
	 * @return the reply
	 */
	protected String reply(FanoutServiceConnection connection, String channel, String message) {
		if (channel != null && channel.length() > 0) {
			increment(totalMessages);
		}
		return connection.reply(channel, message);
	}

	/**
	 * Service method to broadcast a message to all connections.
	 *
	 * @param message
	 */
	public void broadcastAll(String message) {
		broadcast(connections.values(), FanoutConstants.CH_ALL, message);
		increment(totalAnnouncements);
	}

	/**
	 * Service method to broadcast a message to connections subscribed to the
	 * channel.
	 *
	 * @param message
	 */
	public void broadcast(String channel, String message) {
		List<FanoutServiceConnection> connections = new ArrayList<FanoutServiceConnection>(subscriptions.get(channel));
		broadcast(connections, channel, message);
		increment(totalAnnouncements);
	}

	/**
	 * Broadcast a message to connections subscribed to the specified channel.
	 *
	 * @param connections
	 * @param channel
	 * @param message
	 */
	protected void broadcast(Collection<FanoutServiceConnection> connections, String channel, String message) {
		for (FanoutServiceConnection connection : connections) {
			reply(connection, channel, message);
		}
	}

	/**
	 * Process an incoming Fanout request.
	 *
	 * @param connection
	 * @param req
	 * @return the reply to the request, may be null
	 */
	protected String processRequest(FanoutServiceConnection connection, String req) {
		logger.info(MessageFormat.format("fanout request from {0}: {1}", connection.id, req));
		String[] fields = req.split(" ", 3);
		String action = fields[0];
		String channel = fields.length >= 2 ? fields[1] : null;
		String message = fields.length >= 3 ? fields[2] : null;
		try {
			return processRequest(connection, action, channel, message);
		} catch (IllegalArgumentException e) {
			// invalid action
			logger.error(MessageFormat.format("fanout connection {0} requested invalid action {1}", connection.id, action));
			logger.error(asHexArray(req));
		}
		return null;
	}

	/**
	 * Process the Fanout request.
	 *
	 * @param connection
	 * @param action
	 * @param channel
	 * @param message
	 * @return the reply to the request, may be null
	 * @throws IllegalArgumentException
	 */
	protected String processRequest(FanoutServiceConnection connection, String action, String channel, String message) throws IllegalArgumentException {
		if ("ping".equals(action)) {
			// ping
			increment(totalPings);
			return reply(connection, null, "" + System.currentTimeMillis());
		} else if ("info".equals(action)) {
			// info
			String info = getStatistics().info();
			return reply(connection, null, info);
		} else if ("announce".equals(action)) {
			// announcement
			if (!allowAllChannelAnnouncements.get() && FanoutConstants.CH_ALL.equals(channel)) {
				// prohibiting connection-sourced all announcements
				logger.warn(MessageFormat.format("fanout connection {0} attempted to announce {1} on ALL channel", connection.id, message));
			} else if ("debug".equals(channel)) {
				// prohibiting connection-sourced debug announcements
				logger.warn(MessageFormat.format("fanout connection {0} attempted to announce {1} on DEBUG channel", connection.id, message));
			} else {
				// acceptable announcement
				List<FanoutServiceConnection> connections = new ArrayList<FanoutServiceConnection>(subscriptions.get(channel));
				connections.remove(connection); // remove announcer
				broadcast(connections, channel, message);
				increment(totalAnnouncements);
			}
		} else if ("subscribe".equals(action)) {
			// subscribe
			if (!subscriptions.containsKey(channel)) {
				logger.info(MessageFormat.format("fanout new channel {0}", channel));
				subscriptions.put(channel, new ConcurrentSkipListSet<FanoutServiceConnection>());
			}
			subscriptions.get(channel).add(connection);
			logger.debug(MessageFormat.format("fanout connection {0} subscribed to channel {1}", connection.id, channel));
			increment(totalSubscribes);
		} else if ("unsubscribe".equals(action)) {
			// unsubscribe
			if (subscriptions.containsKey(channel)) {
				subscriptions.get(channel).remove(connection);
				if (subscriptions.get(channel).size() == 0) {
					subscriptions.remove(channel);
				}
				increment(totalUnsubscribes);
			}
		} else {
			// invalid action
			throw new IllegalArgumentException(action);
		}
		return null;
	}

	private String asHexArray(String req) {
		StringBuilder sb = new StringBuilder();
		for (char c : req.toCharArray()) {
			sb.append(Integer.toHexString(c)).append(' ');
		}
		return "[ " + sb.toString().trim() + " ]";
	}

	/**
	 * Increment a long and prevent negative rollover.
	 *
	 * @param counter
	 */
	private void increment(AtomicLong counter) {
		long v = counter.incrementAndGet();
		if (v < 0) {
			counter.set(0);
		}
	}

	@Override
	public String toString() {
		return name;
	}
}