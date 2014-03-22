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
package com.gitblit.transport.ssh.gitblit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.models.UserModel;
import com.gitblit.transport.ssh.IPublicKeyManager;
import com.gitblit.transport.ssh.SshKey;
import com.gitblit.transport.ssh.commands.CommandMetaData;
import com.gitblit.transport.ssh.commands.DispatchCommand;
import com.gitblit.transport.ssh.commands.SshCommand;
import com.gitblit.utils.FlipTable;
import com.gitblit.utils.FlipTable.Borders;

/**
 * The dispatcher and it's commands for SSH public key management.
 *
 * @author James Moger
 *
 */
@CommandMetaData(name = "keys", description = "SSH public key management commands")
public class KeysDispatcher extends DispatchCommand {

	@Override
	protected void setup(UserModel user) {
		register(user, AddKey.class);
		register(user, RemoveKey.class);
		register(user, ListKeys.class);
	}

	@CommandMetaData(name = "add", description = "Add an SSH public key to your account")
	public static class AddKey extends BaseKeyCommand {

		protected final Logger log = LoggerFactory.getLogger(getClass());

		@Argument(metaVar = "<stdin>|KEY", usage = "the key to add")
		private List<String> addKeys = new ArrayList<String>();

		@Override
		public void run() throws IOException, UnloggedFailure {
			String username = getContext().getClient().getUsername();
			List<String> keys = readKeys(addKeys);
			for (String key : keys) {
				SshKey sshKey = parseKey(key);
				getKeyManager().addKey(username, sshKey);
				log.info("added SSH public key for {}", username);
			}
		}
	}

	@CommandMetaData(name = "remove", aliases = { "rm" }, description = "Remove an SSH public key from your account")
	public static class RemoveKey extends BaseKeyCommand {

		protected final Logger log = LoggerFactory.getLogger(getClass());

		private final String ALL = "ALL";

		@Argument(metaVar = "-|<INDEX>|<KEY>|ALL", usage = "the key to remove", required = true)
		private List<String> removeKeys = new ArrayList<String>();

		@Override
		public void run() throws IOException, UnloggedFailure {
			String username = getContext().getClient().getUsername();
			// remove a key that has been piped to the command
			// or remove all keys

			List<SshKey> currentKeys = getKeyManager().getKeys(username);
			if (currentKeys == null || currentKeys.isEmpty()) {
				throw new UnloggedFailure(1, "There are no registered keys!");
			}

			List<String> keys = readKeys(removeKeys);
			if (keys.contains(ALL)) {
				if (getKeyManager().removeAllKeys(username)) {
					stdout.println("Removed all keys.");
					log.info("removed all SSH public keys from {}", username);
				} else {
					log.warn("failed to remove all SSH public keys from {}", username);
				}
			} else {
				for (String key : keys) {
					try {
						// remove a key by it's index (1-based indexing)
						int index = Integer.parseInt(key);
						if (index > keys.size()) {
							if (keys.size() == 1) {
								throw new UnloggedFailure(1, "Invalid index specified. There is only 1 registered key.");
							}
							throw new UnloggedFailure(1, String.format("Invalid index specified. There are %d registered keys.", keys.size()));
						}
						SshKey sshKey = currentKeys.get(index - 1);
						if (getKeyManager().removeKey(username, sshKey)) {
							stdout.println(String.format("Removed %s", sshKey.getFingerprint()));
						} else {
							throw new UnloggedFailure(1,  String.format("failed to remove #%s: %s", key, sshKey.getFingerprint()));
						}
					} catch (Exception e) {
						// remove key by raw key data
						SshKey sshKey = parseKey(key);
						if (getKeyManager().removeKey(username, sshKey)) {
							stdout.println(String.format("Removed %s", sshKey.getFingerprint()));
							log.info("removed SSH public key {} from {}", sshKey.getFingerprint(), username);
						} else {
							log.warn("failed to remove SSH public key {} from {}", sshKey.getFingerprint(), username);
							throw new UnloggedFailure(1,  String.format("failed to remove %s", sshKey.getFingerprint()));
						}
					}
				}
			}
		}
	}

	@CommandMetaData(name = "list", aliases = { "ls" }, description = "List your registered public keys")
	public static class ListKeys extends SshCommand {

		@Option(name = "-L", usage = "list complete public key parameters")
		private boolean showRaw;

		@Override
		public void run() {
			IPublicKeyManager keyManager = getContext().getGitblit().getPublicKeyManager();
			String username = getContext().getClient().getUsername();
			List<SshKey> keys = keyManager.getKeys(username);
			if (keys == null || keys.isEmpty()) {
				stdout.println("You have not registered any public keys for ssh authentication.");
				return;
			}

			if (showRaw) {
				asRaw(keys);
			} else {
				asTable(keys);
			}
		}

		/* output in the same format as authorized_keys */
		protected void asRaw(List<SshKey> keys) {
			for (SshKey key : keys) {
				stdout.println(key.getRawData());
			}
		}

		protected void asTable(List<SshKey> keys) {
			String[] headers = { "#", "Fingerprint", "Comment", "Type" };
			String[][] data = new String[keys.size()][];
			for (int i = 0; i < keys.size(); i++) {
				// show 1-based index numbers with the fingerprint
				// this is useful for comparing with "ssh-add -l"
				SshKey k = keys.get(i);
				data[i] = new String[] { "" + (i + 1), k.getFingerprint(), k.getComment(), k.getAlgorithm() };
			}

			stdout.println(FlipTable.of(headers, data, Borders.BODY_COLS));
		}
	}
}
