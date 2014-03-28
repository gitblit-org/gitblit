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
import com.gitblit.transport.ssh.commands.UsageExample;
import com.gitblit.utils.FlipTable;
import com.gitblit.utils.FlipTable.Borders;
import com.google.common.base.Joiner;

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
		register(user, WhichKey.class);
		register(user, CommentKey.class);
	}

	@CommandMetaData(name = "add", description = "Add an SSH public key to your account")
	@UsageExample(syntax = "cat ~/.ssh/id_rsa.pub | ${ssh} ${cmd} -", description = "Upload your SSH public key and add it to your account")
	public static class AddKey extends BaseKeyCommand {

		protected final Logger log = LoggerFactory.getLogger(getClass());

		@Argument(metaVar = "<KEY>", usage = "the key(s) to add")
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
	@UsageExample(syntax = "${cmd} 2", description = "Remove the SSH key identified as #2 in `keys list`")
	public static class RemoveKey extends BaseKeyCommand {

		protected final Logger log = LoggerFactory.getLogger(getClass());

		private final String ALL = "ALL";

		@Argument(metaVar = "<INDEX>|<KEY>|ALL", usage = "the key to remove", required = true)
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

	@CommandMetaData(name = "list", aliases = { "ls" }, description = "List your registered SSH public keys")
	public static class ListKeys extends SshCommand {

		@Option(name = "-L", usage = "list complete public key parameters")
		private boolean showRaw;

		@Override
		public void run() {
			IPublicKeyManager keyManager = getContext().getGitblit().getPublicKeyManager();
			String username = getContext().getClient().getUsername();
			List<SshKey> keys = keyManager.getKeys(username);

			if (showRaw) {
				asRaw(keys);
			} else {
				asTable(keys);
			}
		}

		/* output in the same format as authorized_keys */
		protected void asRaw(List<SshKey> keys) {
			if (keys == null) {
				return;
			}
			for (SshKey key : keys) {
				stdout.println(key.getRawData());
			}
		}

		protected void asTable(List<SshKey> keys) {
			String[] headers = { "#", "Fingerprint", "Comment", "Type" };
			int len = keys == null ? 0 : keys.size();
			Object[][] data = new Object[len][];
			for (int i = 0; i < len; i++) {
				// show 1-based index numbers with the fingerprint
				// this is useful for comparing with "ssh-add -l"
				SshKey k = keys.get(i);
				data[i] = new Object[] { (i + 1), k.getFingerprint(), k.getComment(), k.getAlgorithm() };
			}

			stdout.println(FlipTable.of(headers, data, Borders.BODY_HCOLS));
		}
	}

	@CommandMetaData(name = "which", description = "Display the SSH public key used for this session")
	public static class WhichKey extends SshCommand {

		@Option(name = "-L", usage = "list complete public key parameters")
		private boolean showRaw;

		@Override
		public void run() throws UnloggedFailure {
			SshKey key = getContext().getClient().getKey();
			if (key == null) {
				throw new UnloggedFailure(1,  "You have not authenticated with an SSH public key.");
			}

			if (showRaw) {
				stdout.println(key.getRawData());
			} else {
				final String username = getContext().getClient().getUsername();
				List<SshKey> keys = getContext().getGitblit().getPublicKeyManager().getKeys(username);
				int index = 0;
				for (int i = 0; i < keys.size(); i++) {
					if (key.equals(keys.get(i))) {
						index = i + 1;
						break;
					}
				}
				asTable(index, key);
			}
		}

		protected void asTable(int index, SshKey key) {
			String[] headers = { "#", "Fingerprint", "Comment", "Type" };
			Object[][] data = new Object[1][];
			data[0] = new Object[] { index, key.getFingerprint(), key.getComment(), key.getAlgorithm() };

			stdout.println(FlipTable.of(headers, data, Borders.BODY_HCOLS));
		}
	}

	@CommandMetaData(name = "comment", description = "Set the comment for an SSH public key")
	@UsageExample(syntax = "${cmd} 3 Home workstation", description = "Set the comment for key #3")
	public static class CommentKey extends SshCommand {

		@Argument(index = 0, metaVar = "INDEX", usage = "the key index", required = true)
		private int index;

		@Argument(index = 1, metaVar = "COMMENT", usage = "the new comment", required = true)
		private List<String> values = new ArrayList<String>();

		@Override
		public void run() throws UnloggedFailure {
			final String username = getContext().getClient().getUsername();
			IPublicKeyManager keyManager = getContext().getGitblit().getPublicKeyManager();
			List<SshKey> keys = keyManager.getKeys(username);
			if (index > keys.size()) {
				throw new UnloggedFailure(1,  "Invalid key index!");
			}

			String comment = Joiner.on(" ").join(values);
			SshKey key = keys.get(index - 1);
			key.setComment(comment);
			if (keyManager.addKey(username, key)) {
				stdout.println(String.format("Updated the comment for key #%d.", index));
			} else {
				throw new UnloggedFailure(1, String.format("Failed to update the comment for key #%d!", index));
			}
		}

	}
}
