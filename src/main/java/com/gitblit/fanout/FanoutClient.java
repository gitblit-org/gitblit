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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fanout client class.
 *
 * @author James Moger
 *
 */
public class FanoutClient implements Runnable {

	private final static Logger logger = LoggerFactory.getLogger(FanoutClient.class);

	private final int clientTimeout = 500;
	private final int reconnectTimeout = 2000;
	private final String host;
	private final int port;
	private final List<FanoutListener> listeners;

	private String id;
	private volatile Selector selector;
	private volatile SocketChannel socketCh;
	private Thread clientThread;

	private final AtomicBoolean isConnected;
	private final AtomicBoolean isRunning;
	private final AtomicBoolean isAutomaticReconnect;
	private final ByteBuffer writeBuffer;
	private final ByteBuffer readBuffer;
	private final CharsetDecoder decoder;

	private final Set<String> subscriptions;
	private boolean resubscribe;

	public interface FanoutListener {
		public void pong(Date timestamp);
		public void announcement(String channel, String message);
	}

	public static class FanoutAdapter implements FanoutListener {
		@Override
		public void pong(Date timestamp) { }
		@Override
		public void announcement(String channel, String message) { }
	}

	public static void main(String args[]) throws Exception {
		FanoutClient client = new FanoutClient("localhost", 2000);
		client.addListener(new FanoutAdapter() {

			@Override
			public void pong(Date timestamp) {
				System.out.println("Pong. " + timestamp);
			}

			@Override
			public void announcement(String channel, String message) {
				System.out.println(MessageFormat.format("Here ye, Here ye. {0} says {1}", channel, message));
			}
		});
		client.start();

		Thread.sleep(5000);
		client.ping();
		client.subscribe("james");
		client.announce("james", "12345");
		client.subscribe("c52f99d16eb5627877ae957df7ce1be102783bd5");

		while (true) {
			Thread.sleep(10000);
			client.ping();
		}
	}

	public FanoutClient(String host, int port) {
		this.host = host;
		this.port = port;
		readBuffer = ByteBuffer.allocateDirect(FanoutConstants.BUFFER_LENGTH);
		writeBuffer = ByteBuffer.allocateDirect(FanoutConstants.BUFFER_LENGTH);
		decoder = Charset.forName(FanoutConstants.CHARSET).newDecoder();
		listeners = Collections.synchronizedList(new ArrayList<FanoutListener>());
		subscriptions = new LinkedHashSet<String>();
		isRunning = new AtomicBoolean(false);
		isConnected = new AtomicBoolean(false);
		isAutomaticReconnect = new AtomicBoolean(true);
	}

	public void addListener(FanoutListener listener) {
		listeners.add(listener);
	}

	public void removeListener(FanoutListener listener) {
		listeners.remove(listener);
	}

	public boolean isAutomaticReconnect() {
		return isAutomaticReconnect.get();
	}

	public void setAutomaticReconnect(boolean value) {
		isAutomaticReconnect.set(value);
	}

	public void ping() {
		confirmConnection();
		write("ping");
	}

	public void status() {
		confirmConnection();
		write("status");
	}

	public void subscribe(String channel) {
		confirmConnection();
		if (subscriptions.add(channel)) {
			write("subscribe " + channel);
		}
	}

	public void unsubscribe(String channel) {
		confirmConnection();
		if (subscriptions.remove(channel)) {
			write("unsubscribe " + channel);
		}
	}

	public void announce(String channel, String message) {
		confirmConnection();
		write("announce " + channel + " " + message);
	}

	private void confirmConnection() {
		if (!isConnected()) {
			throw new RuntimeException("Fanout client is disconnected!");
		}
	}

	public boolean isConnected() {
		return isRunning.get() && socketCh != null && isConnected.get();
	}

	/**
	 * Start client connection and return immediately.
	 */
	public void start() {
		if (isRunning.get()) {
			logger.warn("Fanout client is already running");
			return;
		}
		clientThread = new Thread(this, "Fanout client");
		clientThread.start();
	}

	/**
	 * Start client connection and wait until it has connected.
	 */
	public void startSynchronously() {
		start();
		while (!isConnected()) {
			try {
				Thread.sleep(100);
			} catch (Exception e) {
			}
		}
	}

	/**
	 * Stops client connection.  This method returns when the connection has
	 * been completely shutdown.
	 */
	public void stop() {
		if (!isRunning.get()) {
			logger.warn("Fanout client is not running");
			return;
		}
		isRunning.set(false);
		try {
			if (clientThread != null) {
				clientThread.join();
				clientThread = null;
			}
		} catch (InterruptedException e1) {
		}
	}

	@Override
	public void run() {
		resetState();

		isRunning.set(true);
		while (isRunning.get()) {
			// (re)connect
			if (socketCh == null) {
				try {
					InetAddress addr = InetAddress.getByName(host);
					socketCh = SocketChannel.open(new InetSocketAddress(addr, port));
					socketCh.configureBlocking(false);
					selector = Selector.open();
					id = FanoutConstants.getLocalSocketId(socketCh.socket());
					socketCh.register(selector, SelectionKey.OP_READ);
				} catch (Exception e) {
					logger.error(MessageFormat.format("failed to open client connection to {0}:{1,number,0}", host, port), e);
					try {
						Thread.sleep(reconnectTimeout);
					} catch (InterruptedException x) {
					}
					continue;
				}
			}

			// read/write
			try {
				selector.select(clientTimeout);

				Iterator<SelectionKey> i = selector.selectedKeys().iterator();
				while (i.hasNext()) {
					SelectionKey key = i.next();
					i.remove();

					if (key.isReadable()) {
						// read message
						String content = read();
						String[] lines = content.split("\n");
						for (String reply : lines) {
							logger.trace(MessageFormat.format("fanout client {0} received: {1}", id, reply));
							if (!processReply(reply)) {
								logger.error(MessageFormat.format("fanout client {0} received unknown message", id));
							}
						}
					} else if (key.isWritable()) {
						// resubscribe
						if (resubscribe) {
							resubscribe = false;
							logger.info(MessageFormat.format("fanout client {0} re-subscribing to {1} channels", id, subscriptions.size()));
							for (String subscription : subscriptions) {
								write("subscribe " + subscription);
							}
						}
						socketCh.register(selector, SelectionKey.OP_READ);
					}
				}
			} catch (IOException e) {
				logger.error(MessageFormat.format("fanout client {0} error: {1}", id, e.getMessage()));
				closeChannel();
				if (!isAutomaticReconnect.get()) {
					isRunning.set(false);
					continue;
				}
			}
		}

		closeChannel();
		resetState();
	}

	protected void resetState() {
		readBuffer.clear();
		writeBuffer.clear();
		isRunning.set(false);
		isConnected.set(false);
	}

	private void closeChannel() {
		try {
			if (socketCh != null) {
				socketCh.close();
				socketCh = null;
				selector.close();
				selector = null;
				isConnected.set(false);
			}
		} catch (IOException x) {
		}
	}

	protected boolean processReply(String reply) {
		String[] fields = reply.split("!", 2);
		if (fields.length == 1) {
			try {
				long time = Long.parseLong(fields[0]);
				Date date = new Date(time);
				firePong(date);
			} catch (Exception e) {
			}
			return true;
		} else if (fields.length == 2) {
			String channel = fields[0];
			String message = fields[1];
			if (FanoutConstants.CH_DEBUG.equals(channel)) {
				// debug messages are for internal use
				if (FanoutConstants.MSG_CONNECTED.equals(message)) {
					isConnected.set(true);
					resubscribe = subscriptions.size() > 0;
					if (resubscribe) {
						try {
							// register for async resubscribe
							socketCh.register(selector, SelectionKey.OP_WRITE);
						} catch (Exception e) {
							logger.error("an error occurred", e);
						}
					}
				}
				logger.debug(MessageFormat.format("fanout client {0} < {1}", id, reply));
			} else {
				fireAnnouncement(channel, message);
			}
			return true;
		} else {
			// unknown message
			return false;
		}
	}

	protected void firePong(Date timestamp) {
		logger.info(MessageFormat.format("fanout client {0} < pong {1,date,yyyy-MM-dd HH:mm:ss}", id, timestamp));
		for (FanoutListener listener : listeners) {
			try {
				listener.pong(timestamp);
			} catch (Throwable t) {
				logger.error("FanoutListener threw an exception!", t);
			}
		}
	}
	protected void fireAnnouncement(String channel, String message) {
		logger.info(MessageFormat.format("fanout client {0} < announcement {1} {2}", id, channel, message));
		for (FanoutListener listener : listeners) {
			try {
				listener.announcement(channel, message);
			} catch (Throwable t) {
				logger.error("FanoutListener threw an exception!", t);
			}
		}
	}

	protected synchronized String read() throws IOException {
		readBuffer.clear();
		long len = socketCh.read(readBuffer);

		if (len == -1) {
			logger.error(MessageFormat.format("fanout client {0} lost connection to {1}:{2,number,0}, end of stream", id, host, port));
			socketCh.close();
			return null;
		} else {
			readBuffer.flip();
			String content = decoder.decode(readBuffer).toString();
			readBuffer.clear();
			return content;
		}
	}

	protected synchronized boolean write(String message) {
		try {
			logger.info(MessageFormat.format("fanout client {0} > {1}", id, message));
			byte [] bytes = message.getBytes(FanoutConstants.CHARSET);
			writeBuffer.clear();
			writeBuffer.put(bytes);
			if (bytes[bytes.length - 1] != 0xa) {
				writeBuffer.put((byte) 0xa);
			}
			writeBuffer.flip();

			// loop until write buffer has been completely sent
			long written = 0;
			long toWrite = writeBuffer.remaining();
			while (written != toWrite) {
				written += socketCh.write(writeBuffer);
				try {
					Thread.sleep(10);
				} catch (Exception x) {
				}
			}
			return true;
		} catch (IOException e) {
			logger.error("fanout client {0} error: {1}", id, e.getMessage());
		}
		return false;
	}
}