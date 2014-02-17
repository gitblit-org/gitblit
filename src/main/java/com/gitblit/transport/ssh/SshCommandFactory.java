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

import javax.inject.Inject;

import org.apache.sshd.server.Command;
import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SessionAware;
import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PacketLineOut;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.git.RepositoryResolver;
import com.gitblit.transport.ssh.commands.DispatchCommand;
import com.gitblit.utils.WorkQueue;
import com.google.common.util.concurrent.Atomics;

/**
 *
 * @author Eric Myhre
 *
 */
public class SshCommandFactory implements CommandFactory {
  private static final Logger logger = LoggerFactory
      .getLogger(SshCommandFactory.class);
  private RepositoryResolver<SshSession> repositoryResolver;

  private UploadPackFactory<SshSession> uploadPackFactory;

  private ReceivePackFactory<SshSession> receivePackFactory;
  private final ScheduledExecutorService startExecutor;

  private CommandDispatcher dispatcher;

    @Inject
	public SshCommandFactory(RepositoryResolver<SshSession> repositoryResolver,
	    UploadPackFactory<SshSession> uploadPackFactory,
	    ReceivePackFactory<SshSession> receivePackFactory,
	    WorkQueue workQueue,
	    CommandDispatcher d) {
		this.repositoryResolver = repositoryResolver;
		this.uploadPackFactory = uploadPackFactory;
		this.receivePackFactory = receivePackFactory;
		this.dispatcher = d;
		int threads = 2;//cfg.getInt("sshd","commandStartThreads", 2);
	    startExecutor = workQueue.createQueue(threads, "SshCommandStart");
	}

	@Override
	public Command createCommand(final String commandLine) {
	  return new Trampoline(commandLine);
        /*
		if ("git-upload-pack".equals(command))
			return new UploadPackCommand(argument);
		if ("git-receive-pack".equals(command))
			return new ReceivePackCommand(argument);
		return new NonCommand();
		*/
	}

	  private class Trampoline implements Command, SessionAware {
	    private final String[] argv;
	    private InputStream in;
	    private OutputStream out;
	    private OutputStream err;
	    private ExitCallback exit;
	    private Environment env;
	    private DispatchCommand cmd;
	    private final AtomicBoolean logged;
	    private final AtomicReference<Future<?>> task;

	    Trampoline(final String cmdLine) {
	      argv = split(cmdLine);
	      logged = new AtomicBoolean();
	      task = Atomics.newReference();
	    }

	    @Override
	    public void setSession(ServerSession session) {
	    // TODO Auto-generated method stub
	    }

	    public void setInputStream(final InputStream in) {
	      this.in = in;
	    }

	    public void setOutputStream(final OutputStream out) {
	      this.out = out;
	    }

	    public void setErrorStream(final OutputStream err) {
	      this.err = err;
	    }

	    public void setExitCallback(final ExitCallback callback) {
	      this.exit = callback;
	    }

	    public void start(final Environment env) throws IOException {
	      this.env = env;
	      task.set(startExecutor.submit(new Runnable() {
	        public void run() {
	          try {
	            onStart();
	          } catch (Exception e) {
	            logger.warn("Cannot start command ", e);
	          }
	        }

	        @Override
	        public String toString() {
	          //return "start (user " + ctx.getSession().getUsername() + ")";
	          return "start (user TODO)";
	        }
	      }));
	    }

	    private void onStart() throws IOException {
	      synchronized (this) {
	        //final Context old = sshScope.set(ctx);
	        try {
	          cmd = dispatcher.get();
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
	          //sshScope.set(old);
	        }
	      }
	    }

	    private int translateExit(final int rc) {
	      return rc;
//
//	      switch (rc) {
//	        case BaseCommand.STATUS_NOT_ADMIN:
//	          return 1;
//
//	        case BaseCommand.STATUS_CANCEL:
//	          return 15 /* SIGKILL */;
//
//	        case BaseCommand.STATUS_NOT_FOUND:
//	          return 127 /* POSIX not found */;
//
//	        default:
//	          return rc;
//	      }

	    }

	    private void log(final int rc) {
	      if (logged.compareAndSet(false, true)) {
	        //log.onExecute(cmd, rc);
	        logger.info("onExecute: {} exits with: {}", cmd.getClass().getSimpleName(), rc);
	      }
	    }

	    @Override
	    public void destroy() {
	      Future<?> future = task.getAndSet(null);
	      if (future != null) {
	        future.cancel(true);
//	        destroyExecutor.execute(new Runnable() {
//	          @Override
//	          public void run() {
//	            onDestroy();
//	          }
//	        });
	      }
	    }

	    private void onDestroy() {
	      synchronized (this) {
	        if (cmd != null) {
	          //final Context old = sshScope.set(ctx);
	          try {
	            cmd.destroy();
	            //log(BaseCommand.STATUS_CANCEL);
	          } finally {
	            //ctx = null;
	            cmd = null;
	            //sshScope.set(old);
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

	public abstract class RepositoryCommand extends AbstractSshCommand {
		protected final String repositoryName;

		public RepositoryCommand(String repositoryName) {
			this.repositoryName = repositoryName;
		}

		@Override
		public void start(Environment env) throws IOException {
			Repository db = null;
			try {
				SshSession client = session.getAttribute(SshSession.KEY);
				db = selectRepository(client, repositoryName);
				if (db == null) return;
				run(client, db);
				exit.onExit(0);
			} catch (ServiceNotEnabledException e) {
				// Ignored. Client cannot use this repository.
			} catch (ServiceNotAuthorizedException e) {
				// Ignored. Client cannot use this repository.
			} finally {
				if (db != null)
					db.close();
				exit.onExit(1);
			}
		}

		protected Repository selectRepository(SshSession client, String name) throws IOException {
			try {
				return openRepository(client, name);
			} catch (ServiceMayNotContinueException e) {
				// An error when opening the repo means the client is expecting a ref
				// advertisement, so use that style of error.
				PacketLineOut pktOut = new PacketLineOut(out);
				pktOut.writeString("ERR " + e.getMessage() + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
				return null;
			}
		}

		protected Repository openRepository(SshSession client, String name)
				throws ServiceMayNotContinueException {
			// Assume any attempt to use \ was by a Windows client
			// and correct to the more typical / used in Git URIs.
			//
			name = name.replace('\\', '/');

			// ssh://git@thishost/path should always be name="/path" here
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

		protected abstract void run(SshSession client, Repository db)
			throws IOException, ServiceNotEnabledException, ServiceNotAuthorizedException;
	}

	public class UploadPackCommand extends RepositoryCommand {
		public UploadPackCommand(String repositoryName) { super(repositoryName); }

		@Override
		protected void run(SshSession client, Repository db)
				throws IOException, ServiceNotEnabledException, ServiceNotAuthorizedException {
			UploadPack up = uploadPackFactory.create(client, db);
			up.upload(in, out, null);
		}
	}

	public class ReceivePackCommand extends RepositoryCommand {
		public ReceivePackCommand(String repositoryName) { super(repositoryName); }

		@Override
		protected void run(SshSession client, Repository db)
				throws IOException, ServiceNotEnabledException, ServiceNotAuthorizedException {
			ReceivePack rp = receivePackFactory.create(client, db);
			rp.receive(in, out, null);
		}
	}

	public static class NonCommand extends AbstractSshCommand {
		@Override
		public void start(Environment env) {
			exit.onExit(127);
		}
	}
}
