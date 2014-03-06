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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FanoutServiceConnection handles reading/writing messages from a remote fanout
 * connection.
 *
 * @author James Moger
 *
 */
public abstract class FanoutServiceConnection implements Comparable<FanoutServiceConnection> {

	private static final Logger logger = LoggerFactory.getLogger(FanoutServiceConnection.class);

	public final String id;

	protected FanoutServiceConnection(Socket socket) {
		this.id = FanoutConstants.getRemoteSocketId(socket);
	}

	protected abstract void reply(String content) throws IOException;

	/**
	 * Send the connection a debug channel connected message.
	 *
	 * @param message
	 */
	protected void connected() {
		reply(FanoutConstants.CH_DEBUG, FanoutConstants.MSG_CONNECTED);
	}

	/**
	 * Send the connection a debug channel busy message.
	 *
	 * @param message
	 */
	protected void busy() {
		reply(FanoutConstants.CH_DEBUG, FanoutConstants.MSG_BUSY);
	}

	/**
	 * Send the connection a message for the specified channel.
	 *
	 * @param channel
	 * @param message
	 * @return the reply
	 */
	protected String reply(String channel, String message) {
		String content;
		if (channel != null) {
			content = channel + "!" + message;
		} else {
			content = message;
		}
		try {
			reply(content);
		} catch (Exception e) {
			logger.error("failed to reply to fanout connection " + id, e);
		}
		return content;
	}

	@Override
	public int compareTo(FanoutServiceConnection c) {
		return id.compareTo(c.id);
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof FanoutServiceConnection) {
			return id.equals(((FanoutServiceConnection) o).id);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public String toString() {
		return id;
	}
}