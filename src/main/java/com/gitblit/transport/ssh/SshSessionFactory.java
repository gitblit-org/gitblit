/*
 * Copyright 2014 gitblit.com.
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
package com.gitblit.transport.ssh;

import java.net.SocketAddress;

import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.transport.socket.SocketSessionConfig;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.session.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.utils.IdGenerator;


/**
 *
 * @author James Moger
 *
 */
public class SshSessionFactory extends SessionFactory {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final IdGenerator idGenerator;

	public SshSessionFactory(IdGenerator idGenerator) {
		this.idGenerator = idGenerator;
	}

	@Override
	protected ServerSession createSession(final IoSession io) throws Exception {
		log.info("connection accepted on " + io);

		if (io.getConfig() instanceof SocketSessionConfig) {
			final SocketSessionConfig c = (SocketSessionConfig) io.getConfig();
			c.setKeepAlive(true);
		}

		final ServerSession s = (ServerSession) super.createSession(io);
		SocketAddress peer = io.getRemoteAddress();
		SshSession session = new SshSession(idGenerator.next(), peer);
		s.setAttribute(SshSession.KEY, session);

		io.getCloseFuture().addListener(new IoFutureListener<IoFuture>() {
			@Override
			public void operationComplete(IoFuture future) {
				log.info("connection closed on " + io);
			}
		});
		return s;
	}
}
