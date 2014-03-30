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
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.security.KeyPair;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.sshd.ClientChannel;
import org.apache.sshd.ClientSession;
import org.apache.sshd.SshClient;
import org.apache.sshd.common.KeyPairProvider;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gitblit.Constants;
import com.gitblit.transport.ssh.IPublicKeyManager;
import com.gitblit.transport.ssh.MemoryKeyManager;
import com.gitblit.transport.ssh.SshKey;

public class SshDaemonTest extends GitblitUnitTest {

	private static final AtomicBoolean started = new AtomicBoolean(false);
	private static KeyPair pair;

	@BeforeClass
	public static void startGitblit() throws Exception {
		started.set(GitBlitSuite.startGitblit());
		pair = SshUtils.createTestHostKeyProvider().loadKey(KeyPairProvider.SSH_RSA);
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
		MemoryKeyManager keyMgr = getKeyManager();
		keyMgr.addKey("admin", new SshKey(pair.getPublic()));
	}

	@After
	public void tearDown() {
		MemoryKeyManager keyMgr = getKeyManager();
		keyMgr.removeAllKeys("admin");
	}

	@Test
	public void testPublicKeyAuthentication() throws Exception {
		SshClient client = SshClient.setUpDefaultClient();
        client.start();
        ClientSession session = client.connect("localhost", GitBlitSuite.sshPort).await().getSession();
        pair.getPublic().getEncoded();
        assertTrue(session.authPublicKey("admin", pair).await().isSuccess());
	}

	@Test
	public void testVersionCommand() throws Exception {
		SshClient client = SshClient.setUpDefaultClient();
        client.start();
        ClientSession session = client.connect("localhost", GitBlitSuite.sshPort).await().getSession();
        pair.getPublic().getEncoded();
        assertTrue(session.authPublicKey("admin", pair).await().isSuccess());

        ClientChannel channel = session.createChannel(ClientChannel.CHANNEL_EXEC, "version");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Writer w = new OutputStreamWriter(baos);
        w.close();
        channel.setIn(new ByteArrayInputStream(baos.toByteArray()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        channel.setOut(out);
        channel.setErr(err);
        channel.open();

        channel.waitFor(ClientChannel.CLOSED, 0);

        String result = out.toString().trim();
        channel.close(false);
        client.stop();

        assertEquals(Constants.getGitBlitVersion(), result);
     }
}
