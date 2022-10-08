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
package com.gitblit.tests;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.SocketAddress;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.config.keys.ClientIdentityLoader;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.transport.ssh.IPublicKeyManager;
import com.gitblit.transport.ssh.MemoryKeyManager;
import com.gitblit.transport.ssh.SshKey;

/**
 * Base class for SSH unit tests.
 */
public abstract class SshUnitTest extends GitblitUnitTest {

	protected static final AtomicBoolean started = new AtomicBoolean(false);
	protected static KeyPairGenerator generator;
	protected KeyPair rwKeyPair;
	protected KeyPair roKeyPair;
	protected String username = "admin";
	protected String password = "admin";

	@BeforeClass
	public static void startGitblit() throws Exception {
		generator = SecurityUtils.getKeyPairGenerator("RSA");
		started.set(GitBlitSuite.startGitblit());

		final SystemReader dsr  = SystemReader.getInstance();
		SystemReader.setInstance(new SystemReader()
		{
			final SystemReader defaultsr = dsr;

			@Override
			public String getHostname()
			{
				return defaultsr.getHostname();
			}

			@Override
			public String getenv(String variable)
			{
				if ("GIT_SSH".equalsIgnoreCase(variable)) {
					return null;
				}
				return defaultsr.getenv(variable);
			}

			@Override
			public String getProperty(String key)
			{
				return defaultsr.getProperty(key);
			}

			@Override
			public FileBasedConfig openUserConfig(Config parent, FS fs)
			{
				return defaultsr.openUserConfig(parent, fs);
			}

			@Override
			public FileBasedConfig openSystemConfig(Config parent, FS fs)
			{
				return defaultsr.openSystemConfig(parent, fs);
			}

			@Override
			public long getCurrentTime()
			{
				return defaultsr.getCurrentTime();
			}

			@Override
			public int getTimezone(long when)
			{
				return defaultsr.getTimezone(when);
			}
		});
	}

	@AfterClass
	public static void stopGitblit() throws Exception {
		if (started.get()) {
			GitBlitSuite.stopGitblit();
		}
	}

	protected MemoryKeyManager getKeyManager() {
		IPublicKeyManager mgr = gitblit().getPublicKeyManager();
		if (mgr instanceof MemoryKeyManager) {
			return (MemoryKeyManager) gitblit().getPublicKeyManager();
		} else {
			throw new RuntimeException("unexpected key manager type " + mgr.getClass().getName());
		}
	}

	@Before
	public void prepare() {
		rwKeyPair = generator.generateKeyPair();

		MemoryKeyManager keyMgr = getKeyManager();
		keyMgr.addKey(username, new SshKey(rwKeyPair.getPublic()));

		roKeyPair = generator.generateKeyPair();
		SshKey sshKey = new SshKey(roKeyPair.getPublic());
		sshKey.setPermission(AccessPermission.CLONE);
		keyMgr.addKey(username, sshKey);
	}

	@After
	public void tearDown() {
		MemoryKeyManager keyMgr = getKeyManager();
		keyMgr.removeAllKeys(username);
	}

	protected SshClient getClient() {
		SshClient client = SshClient.setUpDefaultClient();
		client.setClientIdentityLoader(new ClientIdentityLoader() {	// Ignore the files under ~/.ssh
				@Override
				public boolean isValidLocation(String location) throws IOException {
					return true;
				}
				@Override
				public KeyPair loadClientIdentity(String location, FilePasswordProvider provider) throws IOException, GeneralSecurityException {
					return null;
				}
			});
		client.setServerKeyVerifier(new ServerKeyVerifier() {
			@Override
			public boolean verifyServerKey(ClientSession sshClientSession, SocketAddress remoteAddress, PublicKey serverKey) {
				return true;
			}
		});
		client.start();
		return client;
	}

	protected String testSshCommand(String cmd) throws IOException, InterruptedException {
		return testSshCommand(cmd, null);
	}

	protected String testSshCommand(String cmd, String stdin) throws IOException, InterruptedException {
		SshClient client = getClient();
		ClientSession session = client.connect(username, "localhost", GitBlitSuite.sshPort).verify().getSession();
		session.addPublicKeyIdentity(rwKeyPair);
		AuthFuture authFuture = session.auth();
		assertTrue(authFuture.await());
		assertTrue(authFuture.isSuccess());

		ClientChannel channel = session.createChannel(ClientChannel.CHANNEL_EXEC, cmd);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		if (stdin != null) {
			Writer w = new OutputStreamWriter(baos);
			w.write(stdin);
			w.close();
		}
		channel.setIn(new ByteArrayInputStream(baos.toByteArray()));

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		channel.setOut(out);
		channel.setErr(err);
		channel.open();

		channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED, ClientChannelEvent.EOF), 0);

		String result = out.toString().trim();
		channel.close(false);
		client.stop();
		return result;
	}
}
