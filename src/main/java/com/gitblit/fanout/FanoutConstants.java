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

import java.net.Socket;

public class FanoutConstants {

	public final static String CHARSET = "ISO-8859-1";
	public final static int BUFFER_LENGTH = 512;
	public final static String CH_ALL = "all";
	public final static String CH_DEBUG = "debug";
	public final static String MSG_CONNECTED = "connected...";
	public final static String MSG_BUSY = "busy";

	public static String getRemoteSocketId(Socket socket) {
		return socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
	}

	public static String getLocalSocketId(Socket socket) {
		return socket.getInetAddress().getHostAddress() + ":" + socket.getLocalPort();
	}
}
