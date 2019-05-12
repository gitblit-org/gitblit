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
package com.gitblit.servertests;

import java.security.KeyPair;
import java.util.List;

import org.junit.Test;
import org.parboiled.common.StringUtils;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.transport.ssh.SshKey;

/**
 * Tests the Keys Dispatcher and it's commands.
 *
 * @author James Moger
 *
 */
public class SshKeysDispatcherTest extends SshUnitTest {

	@Test
	public void testKeysListCommand() throws Exception {
		String result = testSshCommand("keys ls -L");
		List<SshKey> keys = getKeyManager().getKeys(username);
		assertEquals(String.format("There are %d keys!", keys.size()), 2, keys.size());
		assertEquals(String.format("%s%n%s", keys.get(0).getRawData(), keys.get(1).getRawData()), result);
	}

	@Test
	public void testKeysWhichCommand() throws Exception {
		String result = testSshCommand("keys which -L");
		List<SshKey> keys = getKeyManager().getKeys(username);
		assertEquals(String.format("There are %d keys!", keys.size()), 2, keys.size());
		assertEquals(keys.get(0).getRawData(), result);
	}

	@Test
	public void testKeysRmCommand() throws Exception {
		testSshCommand("keys rm 2");
		String result = testSshCommand("keys ls -L");
		List<SshKey> keys = getKeyManager().getKeys(username);
		assertEquals(String.format("There are %d keys!", keys.size()), 1, keys.size());
		assertEquals(keys.get(0).getRawData(), result);
	}

	@Test
	public void testKeysRmAllByIndexCommand() throws Exception {
		testSshCommand("keys rm 1 2");
		List<SshKey> keys = getKeyManager().getKeys(username);
		assertEquals(String.format("There are %d keys!", keys.size()), 0, keys.size());
		try {
			testSshCommand("keys ls -L");
			fail("Authentication worked without a public key?!");
		} catch (AssertionError e) {
			// expected
		}
	}

	@Test
	public void testKeysRmAllCommand() throws Exception {
		testSshCommand("keys rm ALL");
		List<SshKey> keys = getKeyManager().getKeys(username);
		assertEquals(String.format("There are %d keys!", keys.size()), 0, keys.size());
		try {
			testSshCommand("keys ls -L");
			fail("Authentication worked without a public key?!");
		} catch (AssertionError e) {
			// expected
		}
	}

	@Test
	public void testKeysAddCommand() throws Exception {
		KeyPair kp = generator.generateKeyPair();
		SshKey key = new SshKey(kp.getPublic());
		testSshCommand("keys add --permission R", key.getRawData());
		List<SshKey> keys = getKeyManager().getKeys(username);
		assertEquals(String.format("There are %d keys!", keys.size()), 3, keys.size());
		assertEquals(AccessPermission.CLONE, keys.get(2).getPermission());

		String result = testSshCommand("keys ls -L");
		StringBuilder sb = new StringBuilder();
		for (SshKey sk : keys) {
			sb.append(sk.getRawData());
			sb.append(System.getProperty("line.separator", "\n"));
		}
		sb.setLength(sb.length() - System.getProperty("line.separator", "\n").length());
		assertEquals(sb.toString(), result);
	}

	@Test
	public void testKeysAddBlankCommand() throws Exception {
		testSshCommand("keys add --permission R", "\n");
		List<SshKey> keys = getKeyManager().getKeys(username);
		assertEquals(String.format("There are %d keys!", keys.size()), 2, keys.size());
	}

	@Test
	public void testKeysAddInvalidCommand() throws Exception {
		testSshCommand("keys add --permission R", "My invalid key\n");
		List<SshKey> keys = getKeyManager().getKeys(username);
		assertEquals(String.format("There are %d keys!", keys.size()), 2, keys.size());
	}

	@Test
	public void testKeysCommentCommand() throws Exception {
		List<SshKey> keys = getKeyManager().getKeys(username);
		assertTrue(StringUtils.isEmpty(keys.get(0).getComment()));
		String comment = "this is my comment";
		testSshCommand(String.format("keys comment 1 %s", comment));

		keys = getKeyManager().getKeys(username);
		assertEquals(comment, keys.get(0).getComment());
	}

	@Test
	public void testKeysPermissionCommand() throws Exception {
		List<SshKey> keys = getKeyManager().getKeys(username);
		assertTrue(StringUtils.isEmpty(keys.get(0).getComment()));
		testSshCommand(String.format("keys permission 1 %s", AccessPermission.CLONE));

		keys = getKeyManager().getKeys(username);
		assertEquals(AccessPermission.CLONE, keys.get(0).getPermission());

		testSshCommand(String.format("keys permission 1 %s", AccessPermission.PUSH));

		keys = getKeyManager().getKeys(username);
		assertEquals(AccessPermission.PUSH, keys.get(0).getPermission());

	}
}
