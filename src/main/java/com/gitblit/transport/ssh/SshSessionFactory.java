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

import org.apache.mina.transport.socket.SocketSessionConfig;
import org.apache.sshd.common.future.CloseFuture;
import org.apache.sshd.common.future.SshFutureListener;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.common.io.mina.MinaSession;
import org.apache.sshd.common.session.AbstractSession;
import org.apache.sshd.server.session.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 * @author James Moger
 *
 */
public class SshSessionFactory extends SessionFactory {

	private final Logger log = LoggerFactory.getLogger(getClass());

	public SshSessionFactory() {
	}

	@Override
	protected AbstractSession createSession(final IoSession io)
			throws Exception {
		log.info("connection accepted on " + io);

		if (io instanceof MinaSession) {
			if (((MinaSession) io).getSession().getConfig() instanceof SocketSessionConfig) {
				((SocketSessionConfig) ((MinaSession) io).getSession()
						.getConfig()).setKeepAlive(true);
			}
		}

		final GitblitServerSession s = (GitblitServerSession) super
				.createSession(io);
		SocketAddress peer = io.getRemoteAddress();
		SshSession session = new SshSession(idGenerator.next(), peer);
		s.setAttribute(SshSession.KEY, session);

		// TODO(davido): Log a session close without authentication as a
		// failure.
		s.addCloseSessionListener(new SshFutureListener<CloseFuture>() {
			@Override
			public void operationComplete(CloseFuture future) {
				log.info("connection closed on " + io);
			}
		});
		return s;
	}

	@Override
	protected AbstractSession doCreateSession(IoSession ioSession)
			throws Exception {
		return new GitblitServerSession(server, ioSession);
	}
}
