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
package com.gitblit.transport.ssh.keys;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.models.UserModel;
import com.gitblit.transport.ssh.IPublicKeyManager;
import com.gitblit.transport.ssh.SshKey;
import com.gitblit.transport.ssh.commands.CommandMetaData;
import com.gitblit.transport.ssh.commands.DispatchCommand;
import com.gitblit.transport.ssh.commands.SshCommand;
import com.gitblit.transport.ssh.commands.UsageExample;
import com.gitblit.utils.FlipTable;
import com.gitblit.utils.FlipTable.Borders;
import com.gitblit.utils.StringUtils;
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
	protected void setup() {
		IPublicKeyManager km = getContext().getGitblit().getPublicKeyManager();
		UserModel user = getContext().getClient().getUser();
		if (km != null && km.supportsWritingKeys(user)) {
			register(AddKey.class);
			register(RemoveKey.class);
		}
		register(ListKeys.class);
		register(WhichKey.class);
		if (km != null && km.supportsCommentChanges(user)) {
			register(CommentKey.class);
		}
		if (km != null && km.supportsPermissionChanges(user)) {
			register(PermissionKey.class);
		}
	}

	@CommandMetaData(name = "add", description = "Add an SSH public key to your account")
	@UsageExample(syntax = "cat ~/.ssh/id_rsa.pub | ${ssh} ${cmd}", description = "Upload your SSH public key and add it to your account")
	public static class AddKey extends BaseKeyCommand {

		protected final Logger log = LoggerFactory.getLogger(getClass());

		@Argument(metaVar = "<STDIN>", usage = "the key to add")
		private List<String> addKeys = new ArrayList<String>();

		@Option(name = "--permission", aliases = { "-p" }, metaVar = "PERMISSION", usage = "set the key access permission")
		private String permission;

		@Override
		protected String getUsageText() {
			String permissions = Joiner.on(", ").join(AccessPermission.SSHPERMISSIONS);
			StringBuilder sb = new StringBuilder();
			sb.append("Valid SSH public key permissions are:\n   ").append(permissions);
			return sb.toString();
		}

		@Override
		public void run() throws IOException, Failure {
			String username = getContext().getClient().getUsername();
			List<String> keys = readKeys(addKeys);
			if (keys.isEmpty()) {
				throw new UnloggedFailure("No public keys were read from STDIN!");
			}
			for (String key : keys) {
				SshKey sshKey = parseKey(key);
				try {
					// this method parses the rawdata and produces a public key
					// if it fails it will throw a Buffer.BufferException
					// the null check is a QC verification on top of that
					if (sshKey.getPublicKey() == null) {
						throw new RuntimeException();
					}
				} catch (RuntimeException e) {
					throw new UnloggedFailure("The data read from SDTIN can not be parsed as an SSH public key!");
				}
				if (!StringUtils.isEmpty(permission)) {
					AccessPermission ap = AccessPermission.fromCode(permission);
					if (ap.exceeds(AccessPermission.NONE)) {
						try {
							sshKey.setPermission(ap);
						} catch (IllegalArgumentException e) {
							throw new Failure(1, e.getMessage());
						}
					}
				}
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

		@Argument(metaVar = "<INDEX>|ALL", usage = "the key to remove", required = true)
		private List<String> keyParameters = new ArrayList<String>();

		@Override
		public void run() throws IOException, Failure {
			String username = getContext().getClient().getUsername();
			// remove a key that has been piped to the command
			// or remove all keys

			List<SshKey> registeredKeys = new ArrayList<SshKey>(getKeyManager().getKeys(username));
			if (registeredKeys.isEmpty()) {
				throw new UnloggedFailure(1, "There are no registered keys!");
			}

			if (keyParameters.contains(ALL)) {
				if (getKeyManager().removeAllKeys(username)) {
					stdout.println("Removed all keys.");
					log.info("removed all SSH public keys from {}", username);
				} else {
					log.warn("failed to remove all SSH public keys from {}", username);
				}
			} else {
				for (String keyParameter : keyParameters) {
					try {
						// remove a key by it's index (1-based indexing)
						int index = Integer.parseInt(keyParameter);
						if (index > registeredKeys.size()) {
							if (keyParameters.size() == 1) {
								throw new Failure(1, "Invalid index specified. There is only 1 registered key.");
							}
							throw new Failure(1, String.format("Invalid index specified. There are %d registered keys.", registeredKeys.size()));
						}
						SshKey sshKey = registeredKeys.get(index - 1);
						if (getKeyManager().removeKey(username, sshKey)) {
							stdout.println(String.format("Removed %s", sshKey.getFingerprint()));
						} else {
							throw new Failure(1,  String.format("failed to remove #%s: %s", keyParameter, sshKey.getFingerprint()));
						}
					} catch (NumberFormatException e) {
						log.warn("failed to remove SSH public key {} from {}", keyParameter, username);
						throw new Failure(1,  String.format("failed to remove key %s", keyParameter));
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
			String[] headers = { "#", "Fingerprint", "Comment", "Permission", "Type" };
			int len = keys == null ? 0 : keys.size();
			Object[][] data = new Object[len][];
			for (int i = 0; i < len; i++) {
				// show 1-based index numbers with the fingerprint
				// this is useful for comparing with "ssh-add -l"
				SshKey k = keys.get(i);
				data[i] = new Object[] { (i + 1), k.getFingerprint(), k.getComment(),
						k.getPermission(), k.getAlgorithm() };
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
			String[] headers = { "#", "Fingerprint", "Comment", "Permission", "Type" };
			Object[][] data = new Object[1][];
			data[0] = new Object[] { index, key.getFingerprint(), key.getComment(), key.getPermission(), key.getAlgorithm() };

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
		public void run() throws Failure {
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
				throw new Failure(1, String.format("Failed to update the comment for key #%d!", index));
			}
		}

	}

	@CommandMetaData(name = "permission", description = "Set the permission of an SSH public key")
	@UsageExample(syntax = "${cmd} 3 RW", description = "Set the permission for key #3 to PUSH (PW)")
	public static class PermissionKey extends SshCommand {

		@Argument(index = 0, metaVar = "INDEX", usage = "the key index", required = true)
		private int index;

		@Argument(index = 1, metaVar = "PERMISSION", usage = "the new permission", required = true)
		private String value;

		@Override
		public void run() throws Failure {
			final String username = getContext().getClient().getUsername();
			IPublicKeyManager keyManager = getContext().getGitblit().getPublicKeyManager();
			List<SshKey> keys = keyManager.getKeys(username);
			if (index > keys.size()) {
				throw new UnloggedFailure(1,  "Invalid key index!");
			}

			SshKey key = keys.get(index - 1);
			AccessPermission permission = AccessPermission.fromCode(value);
			if (permission.exceeds(AccessPermission.NONE)) {
				try {
					key.setPermission(permission);
				} catch (IllegalArgumentException e) {
					throw new Failure(1, e.getMessage());
				}
			}
			if (keyManager.addKey(username, key)) {
				stdout.println(String.format("Updated the permission for key #%d.", index));
			} else {
				throw new Failure(1, String.format("Failed to update the comment for key #%d!", index));
			}
		}

	}
}
