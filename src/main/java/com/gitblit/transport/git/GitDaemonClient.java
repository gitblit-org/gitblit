package com.gitblit.transport.git;

/*
 * Copyright (C) 2008-2009, Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

import org.eclipse.jgit.transport.Daemon;
import org.eclipse.jgit.transport.PacketLineIn;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.util.io.SafeBufferedOutputStream;

/** Active network client of {@link Daemon}. */
public class GitDaemonClient {
	private final GitDaemon daemon;

	private InetAddress peer;

	private InputStream rawIn;

	private OutputStream rawOut;

	private String repositoryName;

	GitDaemonClient(final GitDaemon d) {
		daemon = d;
	}

	void setRemoteAddress(final InetAddress ia) {
		peer = ia;
	}

	/** @return the daemon which spawned this client. */
	public GitDaemon getDaemon() {
		return daemon;
	}

	/** @return Internet address of the remote client. */
	public InetAddress getRemoteAddress() {
		return peer;
	}

	/** @return input stream to read from the connected client. */
	public InputStream getInputStream() {
		return rawIn;
	}

	/** @return output stream to send data to the connected client. */
	public OutputStream getOutputStream() {
		return rawOut;
	}

	public void setRepositoryName(String repositoryName) {
		this.repositoryName = repositoryName;
	}

	/** @return the name of the requested repository. */
	public String getRepositoryName() {
		return repositoryName;
	}

	void execute(final Socket sock) throws IOException,
			ServiceNotEnabledException, ServiceNotAuthorizedException {
		rawIn = new BufferedInputStream(sock.getInputStream());
		rawOut = new SafeBufferedOutputStream(sock.getOutputStream());

		if (0 < daemon.getTimeout())
			sock.setSoTimeout(daemon.getTimeout() * 1000);
		String cmd = new PacketLineIn(rawIn).readStringRaw();
		final int nul = cmd.indexOf('\0');
		if (nul >= 0) {
			// Newer clients hide a "host" header behind this byte.
			// Currently we don't use it for anything, so we ignore
			// this portion of the command.
			//
			cmd = cmd.substring(0, nul);
		}

		final GitDaemonService srv = getDaemon().matchService(cmd);
		if (srv == null)
			return;
		sock.setSoTimeout(0);
		srv.execute(this, cmd);
	}
}