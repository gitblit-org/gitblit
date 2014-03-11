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

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.text.MessageFormat;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Singleton;

import org.apache.sshd.SshServer;
import org.apache.sshd.server.keyprovider.PEMGeneratorHostKeyProvider;
import org.eclipse.jgit.internal.JGitText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.git.GitblitReceivePackFactory;
import com.gitblit.git.GitblitUploadPackFactory;
import com.gitblit.git.RepositoryResolver;
import com.gitblit.manager.IGitblit;
import com.gitblit.transport.ssh.commands.CreateRepository;
import com.gitblit.transport.ssh.commands.DispatchCommand;
import com.gitblit.transport.ssh.commands.Receive;
import com.gitblit.transport.ssh.commands.SetAccountCommand;
import com.gitblit.transport.ssh.commands.Upload;
import com.gitblit.transport.ssh.commands.VersionCommand;
import com.gitblit.utils.IdGenerator;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.WorkQueue;

import dagger.Module;
import dagger.ObjectGraph;
import dagger.Provides;

/**
 * Manager for the ssh transport. Roughly analogous to the
 * {@link com.gitblit.transport.git.GitDaemon} class.
 *
 * @author Eric Myhre
 *
 */
public class SshDaemon {

	private final Logger log = LoggerFactory.getLogger(SshDaemon.class);

	/**
	 * 22: IANA assigned port number for ssh. Note that this is a distinct
	 * concept from gitblit's default conf for ssh port -- this "default" is
	 * what the git protocol itself defaults to if it sees and ssh url without a
	 * port.
	 */
	public static final int DEFAULT_PORT = 22;

	private static final String HOST_KEY_STORE = "sshKeyStore.pem";

	private final AtomicBoolean run;

	private final IGitblit gitblit;
	private final SshServer sshd;
	private final ObjectGraph injector;

	/**
	 * Construct the Gitblit SSH daemon.
	 *
	 * @param gitblit
	 */
	public SshDaemon(IGitblit gitblit, IdGenerator idGenerator) {
		this.gitblit = gitblit;
		this.injector = ObjectGraph.create(new SshModule());
		
		IStoredSettings settings = gitblit.getSettings();
		int port = settings.getInteger(Keys.git.sshPort, 0);
		String bindInterface = settings.getString(Keys.git.sshBindInterface,
				"localhost");

		IKeyManager keyManager = getKeyManager();
		
		InetSocketAddress addr;
		if (StringUtils.isEmpty(bindInterface)) {
			addr = new InetSocketAddress(port);
		} else {
			addr = new InetSocketAddress(bindInterface, port);
		}

		SshKeyAuthenticator publickeyAuthenticator = new SshKeyAuthenticator(
				keyManager, gitblit);
		sshd = SshServer.setUpDefaultServer();
		sshd.setPort(addr.getPort());
		sshd.setHost(addr.getHostName());
		sshd.setKeyPairProvider(new PEMGeneratorHostKeyProvider(new File(
				gitblit.getBaseFolder(), HOST_KEY_STORE).getPath()));
		sshd.setPublickeyAuthenticator(publickeyAuthenticator);
		sshd.setPasswordAuthenticator(new SshPasswordAuthenticator(gitblit));
		sshd.setSessionFactory(new SshSessionFactory(idGenerator));
		sshd.setFileSystemFactory(new DisabledFilesystemFactory());
		sshd.setForwardingFilter(new NonForwardingFilter());

		DispatchCommand gitblitCmd = new DispatchCommand();
		gitblitCmd.registerCommand(CreateRepository.class);
		gitblitCmd.registerCommand(VersionCommand.class);
		gitblitCmd.registerCommand(SetAccountCommand.class);

		DispatchCommand gitCmd = new DispatchCommand();
		gitCmd.registerCommand(Upload.class);
		gitCmd.registerCommand(Receive.class);

		DispatchCommand root = new DispatchCommand();
		root.registerDispatcher("gitblit", gitblitCmd);
		root.registerDispatcher("git", gitCmd);

		root.setRepositoryResolver(new RepositoryResolver<SshSession>(gitblit));
		root.setUploadPackFactory(new GitblitUploadPackFactory<SshSession>(gitblit));
		root.setReceivePackFactory(new GitblitReceivePackFactory<SshSession>(gitblit));
		root.setAuthenticator(publickeyAuthenticator);

		SshCommandFactory commandFactory = new SshCommandFactory(
				new WorkQueue(idGenerator),
				root);

		sshd.setCommandFactory(commandFactory);

		run = new AtomicBoolean(false);
	}

	public String formatUrl(String gituser, String servername, String repository) {
		if (sshd.getPort() == DEFAULT_PORT) {
			// standard port
			return MessageFormat.format("{0}@{1}/{2}", gituser, servername,
					repository);
		} else {
			// non-standard port
			return MessageFormat.format("ssh://{0}@{1}:{2,number,0}/{3}",
					gituser, servername, sshd.getPort(), repository);
		}
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
		if (run.get()) {
			throw new IllegalStateException(JGitText.get().daemonAlreadyRunning);
		}

		sshd.start();
		run.set(true);

		log.info(MessageFormat.format(
				"SSH Daemon is listening on {0}:{1,number,0}",
				sshd.getHost(), sshd.getPort()));
	}

	/** @return true if this daemon is receiving connections. */
	public boolean isRunning() {
		return run.get();
	}

	/** Stop this daemon. */
	public synchronized void stop() {
		if (run.get()) {
			log.info("SSH Daemon stopping...");
			run.set(false);

			try {
				sshd.stop();
			} catch (InterruptedException e) {
				log.error("SSH Daemon stop interrupted", e);
			}
		}
	}
	
	protected IKeyManager getKeyManager() {
		IKeyManager keyManager = null;
		IStoredSettings settings = gitblit.getSettings();
		String clazz = settings.getString(Keys.git.sshKeysManager, FileKeyManager.class.getName());
		if (StringUtils.isEmpty(clazz)) {
			clazz = FileKeyManager.class.getName();
		}		
		try {
			Class<? extends IKeyManager> managerClass = (Class<? extends IKeyManager>) Class.forName(clazz);
			keyManager = injector.get(managerClass).start();
			if (keyManager.isReady()) {
				log.info("{} is ready.", keyManager);
			} else {
				log.warn("{} is disabled.", keyManager);
			}
		} catch (Exception e) {
			log.error("failed to create ssh key manager " + clazz, e);
			keyManager = injector.get(NullKeyManager.class).start();
		}
		return keyManager;
	}
	
	/**
	 * A nested Dagger graph is used for constructor dependency injection of
	 * complex classes.
	 *
	 * @author James Moger
	 *
	 */
	@Module(
			library = true,
			injects = {
					NullKeyManager.class,
					FileKeyManager.class
			}
			)
	class SshModule {

		@Provides @Singleton NullKeyManager provideNullKeyManager() {
			return new NullKeyManager();
		}
		
		@Provides @Singleton FileKeyManager provideFileKeyManager() {
			return new FileKeyManager(SshDaemon.this.gitblit);
		}
	}
}
