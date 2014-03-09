/*
 * Copyright (C) 2013 gitblit.com
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
package com.gitblit.transport.git;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.text.MessageFormat;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.git.GitblitReceivePackFactory;
import com.gitblit.git.GitblitUploadPackFactory;
import com.gitblit.git.RepositoryResolver;
import com.gitblit.manager.IGitblit;
import com.gitblit.utils.StringUtils;

/**
 * Gitblit's Git Daemon ignores any and all per-repository daemon settings and
 * integrates into Gitblit's security model.
 *
 * @author James Moger
 *
 */
public class GitDaemon {

	private final Logger logger = LoggerFactory.getLogger(GitDaemon.class);

	/** 9418: IANA assigned port number for Git. */
	public static final int DEFAULT_PORT = 9418;

	private static final int BACKLOG = 5;

	private InetSocketAddress myAddress;

	private final GitDaemonService[] services;

	private final ThreadGroup processors;

	private AtomicBoolean run;

	private ServerSocket acceptSocket;

	private Thread acceptThread;

	private int timeout;

	private RepositoryResolver<GitDaemonClient> repositoryResolver;

	private UploadPackFactory<GitDaemonClient> uploadPackFactory;

	private ReceivePackFactory<GitDaemonClient> receivePackFactory;

	public GitDaemon(IGitblit gitblit) {

		IStoredSettings settings = gitblit.getSettings();
		int port = settings.getInteger(Keys.git.daemonPort, 0);
		String bindInterface = settings.getString(Keys.git.daemonBindInterface, "localhost");

		if (StringUtils.isEmpty(bindInterface)) {
			myAddress = new InetSocketAddress(port);
		} else {
			myAddress = new InetSocketAddress(bindInterface, port);
		}

		repositoryResolver = new RepositoryResolver<GitDaemonClient>(gitblit);
		uploadPackFactory = new GitblitUploadPackFactory<GitDaemonClient>(gitblit);
		receivePackFactory = new GitblitReceivePackFactory<GitDaemonClient>(gitblit);

		run = new AtomicBoolean(false);
		processors = new ThreadGroup("Git-Daemon");
		services = new GitDaemonService[] { new GitDaemonService("upload-pack", "uploadpack") {
					{
						setEnabled(true);
						setOverridable(false);
					}

					@Override
					protected void execute(final GitDaemonClient dc, final Repository db)
							throws IOException, ServiceNotEnabledException,
							ServiceNotAuthorizedException {
						UploadPack up = uploadPackFactory.create(dc, db);
						InputStream in = dc.getInputStream();
						OutputStream out = dc.getOutputStream();
						up.upload(in, out, null);
					}
				}, new GitDaemonService("receive-pack", "receivepack") {
					{
						setEnabled(true);
						setOverridable(false);
					}

					@Override
					protected void execute(final GitDaemonClient dc, final Repository db)
							throws IOException, ServiceNotEnabledException,
							ServiceNotAuthorizedException {
						ReceivePack rp = receivePackFactory.create(dc, db);
						InputStream in = dc.getInputStream();
						OutputStream out = dc.getOutputStream();
						rp.receive(in, out, null);
					}
				} };
	}

	public int getPort() {
		return myAddress.getPort();
	}

	public String formatUrl(String servername, String repository) {
		if (getPort() == 9418) {
			// standard port
			return MessageFormat.format("git://{0}/{1}", servername, repository);
		} else {
			// non-standard port
			return MessageFormat.format("git://{0}:{1,number,0}/{2}", servername, getPort(), repository);
		}
	}

	/** @return timeout (in seconds) before aborting an IO operation. */
	public int getTimeout() {
		return timeout;
	}

	/**
	 * Set the timeout before willing to abort an IO call.
	 *
	 * @param seconds
	 *            number of seconds to wait (with no data transfer occurring)
	 *            before aborting an IO read or write operation with the
	 *            connected client.
	 */
	public void setTimeout(final int seconds) {
		timeout = seconds;
	}

	/**
	 * Start this daemon on a background thread.
	 *
	 * @throws IOException
	 *             the server socket could not be opened.
	 * @throws IllegalStateException
	 *             the daemon is already running.
	 */
	public synchronized void start() throws IOException {
		if (acceptThread != null)
			throw new IllegalStateException(JGitText.get().daemonAlreadyRunning);

		final ServerSocket listenSock = new ServerSocket(myAddress != null ? myAddress.getPort()
				: 0, BACKLOG, myAddress != null ? myAddress.getAddress() : null);
		myAddress = (InetSocketAddress) listenSock.getLocalSocketAddress();

		run.set(true);
		acceptSocket = listenSock;
		acceptThread = new Thread(processors, "Git-Daemon-Accept") {
			@Override
			public void run() {
				while (isRunning()) {
					try {
						startClient(listenSock.accept());
					} catch (InterruptedIOException e) {
						// Test again to see if we should keep accepting.
					} catch (IOException e) {
						break;
					}
				}

				try {
					listenSock.close();
				} catch (IOException err) {
					//
				} finally {
					acceptSocket = null;
				}

			}
		};
		acceptThread.start();

		logger.info(MessageFormat.format("Git Daemon is listening on {0}:{1,number,0}", myAddress.getAddress().getHostAddress(), myAddress.getPort()));
	}

	/** @return true if this daemon is receiving connections. */
	public boolean isRunning() {
		return run.get();
	}

	/** Stop this daemon. */
	public synchronized void stop() {
		if (isRunning() && acceptThread != null) {
			run.set(false);
			logger.info("Git Daemon stopping...");
			try {
				// close the accept socket
				// this throws a SocketException in the accept thread
				acceptSocket.close();
			} catch (IOException e1) {
			}
			try {
				// join the accept thread
				acceptThread.join();
				logger.info("Git Daemon stopped.");
			} catch (InterruptedException e) {
				logger.error("Accept thread join interrupted", e);
			} finally {
				acceptThread = null;
			}
		}
	}

	private void startClient(final Socket s) {
		final GitDaemonClient dc = new GitDaemonClient(this);

		final SocketAddress peer = s.getRemoteSocketAddress();
		if (peer instanceof InetSocketAddress)
			dc.setRemoteAddress(((InetSocketAddress) peer).getAddress());

		new Thread(processors, "Git-Daemon-Client " + peer.toString()) {
			@Override
			public void run() {
				try {
					dc.execute(s);
				} catch (ServiceNotEnabledException e) {
					// Ignored. Client cannot use this repository.
				} catch (ServiceNotAuthorizedException e) {
					// Ignored. Client cannot use this repository.
				} catch (IOException e) {
					// Ignore unexpected IO exceptions from clients
				} finally {
					try {
						s.getInputStream().close();
					} catch (IOException e) {
						// Ignore close exceptions
					}
					try {
						s.getOutputStream().close();
					} catch (IOException e) {
						// Ignore close exceptions
					}
				}
			}
		}.start();
	}

	synchronized GitDaemonService matchService(final String cmd) {
		for (final GitDaemonService d : services) {
			if (d.handles(cmd))
				return d;
		}
		return null;
	}

	Repository openRepository(GitDaemonClient client, String name)
			throws ServiceMayNotContinueException {
		// Assume any attempt to use \ was by a Windows client
		// and correct to the more typical / used in Git URIs.
		//
		name = name.replace('\\', '/');

		// git://thishost/path should always be name="/path" here
		//
		if (!name.startsWith("/")) //$NON-NLS-1$
			return null;

		try {
			return repositoryResolver.open(client, name.substring(1));
		} catch (RepositoryNotFoundException e) {
			// null signals it "wasn't found", which is all that is suitable
			// for the remote client to know.
			return null;
		} catch (ServiceNotEnabledException e) {
			// null signals it "wasn't found", which is all that is suitable
			// for the remote client to know.
			return null;
		}
	}
}
