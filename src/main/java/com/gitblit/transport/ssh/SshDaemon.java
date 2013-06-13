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

import org.apache.sshd.server.keyprovider.PEMGeneratorHostKeyProvider;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
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
 * Manager for the ssh transport. Roughly analogous to the
 * {@link com.gitblit.git.GitDaemon} class.
 *
 * @author Eric Myhre
 *
 */
public class SshDaemon {

	private final Logger logger = LoggerFactory.getLogger(SshDaemon.class);

	/**
	 * 22: IANA assigned port number for ssh. Note that this is a distinct concept
	 * from gitblit's default conf for ssh port -- this "default" is what the git
	 * protocol itself defaults to if it sees and ssh url without a port.
	 */
	public static final int DEFAULT_PORT = 22;

	private static final String HOST_KEY_STORE = "sshKeyStore.pem";

	private InetSocketAddress myAddress;

	private AtomicBoolean run;

	private SshCommandServer sshd;

	private RepositoryResolver<SshDaemonClient> repositoryResolver;

	private UploadPackFactory<SshDaemonClient> uploadPackFactory;

	private ReceivePackFactory<SshDaemonClient> receivePackFactory;

	/**
	 * Construct the Gitblit SSH daemon.
	 *
	 * @param gitblit
	 */
	public SshDaemon(IGitblit gitblit) {

		IStoredSettings settings = gitblit.getSettings();
		int port = settings.getInteger(Keys.git.sshPort, 0);
		String bindInterface = settings.getString(Keys.git.sshBindInterface, "localhost");

		if (StringUtils.isEmpty(bindInterface)) {
			myAddress = new InetSocketAddress(port);
		} else {
			myAddress = new InetSocketAddress(bindInterface, port);
		}

		sshd = new SshCommandServer();
		sshd.setPort(myAddress.getPort());
		sshd.setHost(myAddress.getHostName());
		sshd.setup();
		sshd.setKeyPairProvider(new PEMGeneratorHostKeyProvider(new File(gitblit.getBaseFolder(), HOST_KEY_STORE).getPath()));
		sshd.setPublickeyAuthenticator(new SshKeyAuthenticator(gitblit));

		run = new AtomicBoolean(false);
		repositoryResolver = new RepositoryResolver<SshDaemonClient>(gitblit);
		uploadPackFactory = new GitblitUploadPackFactory<SshDaemonClient>(gitblit);
		receivePackFactory = new GitblitReceivePackFactory<SshDaemonClient>(gitblit);

		sshd.setCommandFactory(new SshCommandFactory(
				repositoryResolver,
				uploadPackFactory,
				receivePackFactory
		));
	}

	public int getPort() {
		return myAddress.getPort();
	}

	public String formatUrl(String gituser, String servername, String repository) {
		if (getPort() == DEFAULT_PORT) {
			// standard port
			return MessageFormat.format("{0}@{1}/{2}", gituser, servername, repository);
		} else {
			// non-standard port
			return MessageFormat.format("ssh://{0}@{1}:{2,number,0}/{3}", gituser, servername, getPort(), repository);
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

		logger.info(MessageFormat.format("SSH Daemon is listening on {0}:{1,number,0}",
				myAddress.getAddress().getHostAddress(), myAddress.getPort()));
	}

	/** @return true if this daemon is receiving connections. */
	public boolean isRunning() {
		return run.get();
	}

	/** Stop this daemon. */
	public synchronized void stop() {
		if (run.get()) {
			logger.info("SSH Daemon stopping...");
			run.set(false);

			try {
				sshd.stop();
			} catch (InterruptedException e) {
				logger.error("SSH Daemon stop interrupted", e);
			}
		}
	}
}
