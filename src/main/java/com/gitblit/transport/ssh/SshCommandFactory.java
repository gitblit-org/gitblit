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
import java.util.Scanner;

import org.apache.sshd.server.Command;
import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.Environment;
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

import com.gitblit.git.RepositoryResolver;

/**
 *
 * @author Eric Myhre
 *
 */
public class SshCommandFactory implements CommandFactory {
	public SshCommandFactory(RepositoryResolver<SshDaemonClient> repositoryResolver, UploadPackFactory<SshDaemonClient> uploadPackFactory, ReceivePackFactory<SshDaemonClient> receivePackFactory) {
		this.repositoryResolver = repositoryResolver;
		this.uploadPackFactory = uploadPackFactory;
		this.receivePackFactory = receivePackFactory;
	}

	private RepositoryResolver<SshDaemonClient> repositoryResolver;

	private UploadPackFactory<SshDaemonClient> uploadPackFactory;

	private ReceivePackFactory<SshDaemonClient> receivePackFactory;

	@Override
	public Command createCommand(final String commandLine) {
		Scanner commandScanner = new Scanner(commandLine);
		final String command = commandScanner.next();
		final String argument = commandScanner.nextLine();

		if ("git-upload-pack".equals(command))
			return new UploadPackCommand(argument);
		if ("git-receive-pack".equals(command))
			return new ReceivePackCommand(argument);
		return new NonCommand();
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
				SshDaemonClient client = session.getAttribute(SshDaemonClient.ATTR_KEY);
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

		protected Repository selectRepository(SshDaemonClient client, String name) throws IOException {
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

		protected Repository openRepository(SshDaemonClient client, String name)
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

		protected abstract void run(SshDaemonClient client, Repository db)
			throws IOException, ServiceNotEnabledException, ServiceNotAuthorizedException;
	}

	public class UploadPackCommand extends RepositoryCommand {
		public UploadPackCommand(String repositoryName) { super(repositoryName); }

		@Override
		protected void run(SshDaemonClient client, Repository db)
				throws IOException, ServiceNotEnabledException, ServiceNotAuthorizedException {
			UploadPack up = uploadPackFactory.create(client, db);
			up.upload(in, out, null);
		}
	}

	public class ReceivePackCommand extends RepositoryCommand {
		public ReceivePackCommand(String repositoryName) { super(repositoryName); }

		@Override
		protected void run(SshDaemonClient client, Repository db)
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
