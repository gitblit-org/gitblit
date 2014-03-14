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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
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

import com.gitblit.git.GitblitReceivePackFactory;
import com.gitblit.git.GitblitUploadPackFactory;
import com.gitblit.git.RepositoryResolver;
import com.gitblit.manager.IGitblit;
import com.gitblit.models.UserModel;
import com.gitblit.transport.ssh.commands.AddKeyCommand;
import com.gitblit.transport.ssh.commands.CreateRepository;
import com.gitblit.transport.ssh.commands.DispatchCommand;
import com.gitblit.transport.ssh.commands.Receive;
import com.gitblit.transport.ssh.commands.RemoveKeyCommand;
import com.gitblit.transport.ssh.commands.ReviewCommand;
import com.gitblit.transport.ssh.commands.SetAccountCommand;
import com.gitblit.transport.ssh.commands.Upload;
import com.gitblit.transport.ssh.commands.VersionCommand;
import com.gitblit.utils.IdGenerator;
import com.gitblit.utils.WorkQueue;
import com.google.common.util.concurrent.Atomics;

/**
 *
 * @author Eric Myhre
 *
 */
public class SshCommandFactory implements CommandFactory {
	private static final Logger logger = LoggerFactory.getLogger(SshCommandFactory.class);

	private final IGitblit gitblit;
	private final PublicKeyAuthenticator keyAuthenticator;
	private final ScheduledExecutorService startExecutor;

	public SshCommandFactory(IGitblit gitblit, PublicKeyAuthenticator keyAuthenticator, IdGenerator idGenerator) {
		this.gitblit = gitblit;
		this.keyAuthenticator = keyAuthenticator;

		int threads = 2;// cfg.getInt("sshd","commandStartThreads", 2);
		WorkQueue workQueue = new WorkQueue(idGenerator);
		startExecutor = workQueue.createQueue(threads, "SshCommandStart");
	}

	/**
	 * Creates the root dispatcher command which builds up the available commands.
	 *
	 * @param the client
	 * @param the command line
	 * @return the root dispatcher command
	 */
	protected DispatchCommand createRootDispatcher(SshDaemonClient client, String cmdLine) {
		final UserModel user = client.getUser();

		DispatchCommand gitblitCmd = new DispatchCommand();
		gitblitCmd.registerCommand(user, VersionCommand.class);
		gitblitCmd.registerCommand(user, AddKeyCommand.class);
		gitblitCmd.registerCommand(user, RemoveKeyCommand.class);
		gitblitCmd.registerCommand(user, ReviewCommand.class);

		gitblitCmd.registerCommand(user, CreateRepository.class);
		gitblitCmd.registerCommand(user, SetAccountCommand.class);

		DispatchCommand gitCmd = new DispatchCommand();
		gitCmd.registerCommand(user, Upload.class);
		gitCmd.registerCommand(user, Receive.class);

		DispatchCommand root = new DispatchCommand();
		root.registerDispatcher("gitblit", gitblitCmd);
		root.registerDispatcher("git", gitCmd);

		root.setRepositoryResolver(new RepositoryResolver<SshDaemonClient>(gitblit));
		root.setUploadPackFactory(new GitblitUploadPackFactory<SshDaemonClient>(gitblit));
		root.setReceivePackFactory(new GitblitReceivePackFactory<SshDaemonClient>(gitblit));
		root.setAuthenticator(keyAuthenticator);

		root.setContext(new SshCommandContext(client, cmdLine));

		return root;
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
			return rc;
			//
			// switch (rc) {
			// case BaseCommand.STATUS_NOT_ADMIN:
			// return 1;
			//
			// case BaseCommand.STATUS_CANCEL:
			// return 15 /* SIGKILL */;
			//
			// case BaseCommand.STATUS_NOT_FOUND:
			// return 127 /* POSIX not found */;
			//
			// default:
			// return rc;
			// }

		}

		private void log(final int rc) {
			if (logged.compareAndSet(false, true)) {
				// log.onExecute(cmd, rc);
				logger.info("onExecute: {} exits with: {}", cmd.getClass().getSimpleName(), rc);
			}
		}

		@Override
		public void destroy() {
			Future<?> future = task.getAndSet(null);
			if (future != null) {
				future.cancel(true);
				// destroyExecutor.execute(new Runnable() {
				// @Override
				// public void run() {
				// onDestroy();
				// }
				// });
			}
		}

		private void onDestroy() {
			synchronized (this) {
				if (cmd != null) {
					// final Context old = sshScope.set(ctx);
					try {
						cmd.destroy();
						// log(BaseCommand.STATUS_CANCEL);
					} finally {
						// ctx = null;
						cmd = null;
						// sshScope.set(old);
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
