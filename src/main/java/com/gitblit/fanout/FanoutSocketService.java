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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.text.MessageFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A multi-threaded socket implementation of https://github.com/travisghansen/fanout
 *
 * This implementation creates a master acceptor thread which accepts incoming
 * fanout connections and then spawns a daemon thread for each accepted connection.
 * If there are 100 concurrent fanout connections, there are 101 threads.
 *
 * @author James Moger
 *
 */
public class FanoutSocketService extends FanoutService {

	private final static Logger logger = LoggerFactory.getLogger(FanoutSocketService.class);

	private volatile ServerSocket serviceSocket;

	public static void main(String[] args) throws Exception {
		FanoutSocketService pubsub = new FanoutSocketService(null, DEFAULT_PORT);
		pubsub.setStrictRequestTermination(false);
		pubsub.setAllowAllChannelAnnouncements(false);
		pubsub.start();
	}

	/**
	 * Create a multi-threaded fanout service.
	 *
	 * @param port
	 *            the port for running the fanout PubSub service
	 * @throws IOException
	 */
	public FanoutSocketService(int port) {
		this(null, port);
	}

	/**
	 * Create a multi-threaded fanout service.
	 *
	 * @param bindInterface
	 *            the ip address to bind for the service, may be null
	 * @param port
	 *            the port for running the fanout PubSub service
	 * @throws IOException
	 */
	public FanoutSocketService(String bindInterface, int port) {
		super(bindInterface, port, "Fanout socket service");
	}

	@Override
	protected boolean isConnected() {
		return serviceSocket != null;
	}

	@Override
	protected boolean connect() {
		if (serviceSocket == null) {
			try {
				serviceSocket = new ServerSocket();
				serviceSocket.setReuseAddress(true);
				serviceSocket.setSoTimeout(serviceTimeout);
				serviceSocket.bind(host == null ? new InetSocketAddress(port) : new InetSocketAddress(host, port));
				logger.info(MessageFormat.format("{0} is ready on {1}:{2,number,0}",
						name, host == null ? "0.0.0.0" : host, serviceSocket.getLocalPort()));
			} catch (IOException e) {
				logger.error(MessageFormat.format("failed to open {0} on {1}:{2,number,0}",
						name, host == null ? "0.0.0.0" : host, port), e);
				return false;
			}
		}
		return true;
	}

	@Override
	protected void disconnect() {
		try {
			if (serviceSocket != null) {
				logger.debug(MessageFormat.format("closing {0} server socket", name));
				serviceSocket.close();
				serviceSocket = null;
			}
		} catch (IOException e) {
			logger.error(MessageFormat.format("failed to disconnect {0}", name), e);
		}
	}

	/**
	 * This accepts incoming fanout connections and spawns connection threads.
	 */
	@Override
	protected void listen() throws IOException {
		try {
			Socket socket;
			socket = serviceSocket.accept();
			configureClientSocket(socket);

			FanoutSocketConnection connection = new FanoutSocketConnection(socket);

			if (addConnection(connection)) {
				// spawn connection daemon thread
				Thread connectionThread = new Thread(connection);
				connectionThread.setDaemon(true);
				connectionThread.setName("Fanout " + connection.id);
				connectionThread.start();
			} else {
				// synchronously close the connection and remove it
				removeConnection(connection);
				connection.closeConnection();
				connection = null;
			}
		} catch (SocketTimeoutException e) {
			// ignore accept timeout exceptions
		}
	}

	/**
	 * FanoutSocketConnection handles reading/writing messages from a remote fanout
	 * connection.
	 *
	 * @author James Moger
	 *
	 */
	class FanoutSocketConnection extends FanoutServiceConnection implements Runnable {
		Socket socket;

		FanoutSocketConnection(Socket socket) {
			super(socket);
			this.socket = socket;
		}

		/**
		 * Connection thread read/write method.
		 */
		@Override
		public void run() {
			try {
				StringBuilder sb = new StringBuilder();
				BufferedInputStream is = new BufferedInputStream(socket.getInputStream());
				byte[] buffer = new byte[FanoutConstants.BUFFER_LENGTH];
				int len = 0;
				while (true) {
					while (is.available() > 0) {
						len = is.read(buffer);
						for (int i = 0; i < len; i++) {
							byte b = buffer[i];
							if (b == 0xa || (!isStrictRequestTermination() && b == 0xd)) {
								String req = sb.toString();
								sb.setLength(0);
								if (req.length() > 0) {
									// ignore empty request strings
									processRequest(this, req);
								}
							} else {
								sb.append((char) b);
							}
						}
					}

					if (!isRunning.get()) {
						// service has stopped, terminate client connection
						break;
					} else {
						Thread.sleep(500);
					}
				}
			} catch (Throwable t) {
				if (t instanceof SocketException) {
					logger.error(MessageFormat.format("fanout connection {0}: {1}", id, t.getMessage()));
				} else if (t instanceof SocketTimeoutException) {
					logger.error(MessageFormat.format("fanout connection {0}: {1}", id, t.getMessage()));
				} else {
					logger.error(MessageFormat.format("exception while handling fanout connection {0}", id), t);
				}
			} finally {
				closeConnection();
			}

			logger.info(MessageFormat.format("thread for fanout connection {0} is finished", id));
		}

		@Override
		protected void reply(String content) throws IOException {
			// synchronously send reply
			logger.debug(MessageFormat.format("fanout reply to {0}: {1}", id, content));
			OutputStream os = socket.getOutputStream();
			byte [] bytes = content.getBytes(FanoutConstants.CHARSET);
			os.write(bytes);
			if (bytes[bytes.length - 1] != 0xa) {
				os.write(0xa);
			}
			os.flush();
		}

		protected void closeConnection() {
			// close the connection socket
			try {
				socket.close();
			} catch (IOException e) {
			}
			socket = null;

			// remove this connection from the service
			removeConnection(this);
		}
	}
}