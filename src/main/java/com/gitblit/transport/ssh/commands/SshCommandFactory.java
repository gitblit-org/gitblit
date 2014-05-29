/*
 * Copyright (C) 2009 The Android Open Source Project
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
package com.gitblit.transport.ssh.commands;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.sshd.server.Command;
import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SessionAware;
import org.apache.sshd.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Keys;
import com.gitblit.manager.IGitblit;
import com.gitblit.transport.ssh.SshDaemonClient;
import com.gitblit.utils.WorkQueue;
import com.google.common.util.concurrent.Atomics;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class SshCommandFactory implements CommandFactory {
	private static final Logger logger = LoggerFactory.getLogger(SshCommandFactory.class);

	private final WorkQueue workQueue;
	private final IGitblit gitblit;
	private final ScheduledExecutorService startExecutor;
	private final ExecutorService destroyExecutor;

	public SshCommandFactory(IGitblit gitblit, WorkQueue workQueue) {
		this.gitblit = gitblit;
		this.workQueue = workQueue;

		int threads = gitblit.getSettings().getInteger(Keys.git.sshCommandStartThreads, 2);
		startExecutor = workQueue.createQueue(threads, "SshCommandStart");
		destroyExecutor = Executors.newSingleThreadExecutor(
				new ThreadFactoryBuilder()
					.setNameFormat("SshCommandDestroy-%s")
					.setDaemon(true)
					.build());
	}

	public void stop() {
		destroyExecutor.shutdownNow();
	}

	public RootDispatcher createRootDispatcher(SshDaemonClient client, String commandLine) {
		return new RootDispatcher(gitblit, client, commandLine, workQueue);
	}

	@Override
	public Command createCommand(final String commandLine) {
		return new Trampoline(commandLine);
	}

	private class Trampoline implements Command, SessionAware {
		private final String[] argv;
		private ServerSession session;
		private InputStream in;
		private OutputStream out;
		private OutputStream err;
		private ExitCallback exit;
		private Environment env;
		private String cmdLine;
		private DispatchCommand cmd;
		private final AtomicBoolean logged;
		private final AtomicReference<Future<?>> task;

		Trampoline(String line) {
			if (line.startsWith("git-")) {
				line = "git " + line;
			}
			cmdLine = line;
			argv = split(line);
			logged = new AtomicBoolean();
			task = Atomics.newReference();
		}

		@Override
		public void setSession(ServerSession session) {
			this.session = session;
		}

		@Override
		public void setInputStream(final InputStream in) {
			this.in = in;
		}

		@Override
		public void setOutputStream(final OutputStream out) {
			this.out = out;
		}

		@Override
		public void setErrorStream(final OutputStream err) {
			this.err = err;
		}

		@Override
		public void setExitCallback(final ExitCallback callback) {
			this.exit = callback;
		}

		@Override
		public void start(final Environment env) throws IOException {
			this.env = env;
			task.set(startExecutor.submit(new Runnable() {
				@Override
				public void run() {
					try {
						onStart();
					} catch (Exception e) {
						logger.warn("Cannot start command ", e);
					}
				}

				@Override
				public String toString() {
					return "start (user " + session.getUsername() + ")";
				}
			}));
		}

		private void onStart() throws IOException {
			synchronized (this) {
				SshDaemonClient client = session.getAttribute(SshDaemonClient.KEY);
				try {
					cmd = createRootDispatcher(client, cmdLine);
					cmd.setArguments(argv);
					cmd.setInputStream(in);
					cmd.setOutputStream(out);
					cmd.setErrorStream(err);
					cmd.setExitCallback(new ExitCallback() {
						@Override
						public void onExit(int rc, String exitMessage) {
							exit.onExit(translateExit(rc), exitMessage);
							log(rc);
						}

						@Override
						public void onExit(int rc) {
							exit.onExit(translateExit(rc));
							log(rc);
						}
					});
					cmd.start(env);
				} finally {
					client = null;
				}
			}
		}

		private int translateExit(final int rc) {
			switch (rc) {
			case BaseCommand.STATUS_NOT_ADMIN:
				return 1;

			case BaseCommand.STATUS_CANCEL:
				return 15 /* SIGKILL */;

			case BaseCommand.STATUS_NOT_FOUND:
				return 127 /* POSIX not found */;

			default:
				return rc;
			}
		}

		private void log(final int rc) {
			if (logged.compareAndSet(false, true)) {
				logger.info("onExecute: {} exits with: {}", cmd.getClass().getSimpleName(), rc);
			}
		}

		@Override
		public void destroy() {
			Future<?> future = task.getAndSet(null);
			if (future != null) {
				future.cancel(true);
				destroyExecutor.execute(new Runnable() {
					@Override
					public void run() {
						onDestroy();
					}
				});
			}
		}

		private void onDestroy() {
			synchronized (this) {
				if (cmd != null) {
					try {
						cmd.destroy();
					} finally {
						cmd = null;
					}
				}
			}
		}
	}

	/** Split a command line into a string array. */
	static public String[] split(String commandLine) {
		final List<String> list = new ArrayList<String>();
		boolean inquote = false;
		boolean inDblQuote = false;
		StringBuilder r = new StringBuilder();
		for (int ip = 0; ip < commandLine.length();) {
			final char b = commandLine.charAt(ip++);
			switch (b) {
			case '\t':
			case ' ':
				if (inquote || inDblQuote)
					r.append(b);
				else if (r.length() > 0) {
					list.add(r.toString());
					r = new StringBuilder();
				}
				continue;
			case '\"':
				if (inquote)
					r.append(b);
				else
					inDblQuote = !inDblQuote;
				continue;
			case '\'':
				if (inDblQuote)
					r.append(b);
				else
					inquote = !inquote;
				continue;
			case '\\':
				if (inquote || ip == commandLine.length())
					r.append(b); // literal within a quote
				else
					r.append(commandLine.charAt(ip++));
				continue;
			default:
				r.append(b);
				continue;
			}
		}
		if (r.length() > 0) {
			list.add(r.toString());
		}
		return list.toArray(new String[list.size()]);
	}
}
