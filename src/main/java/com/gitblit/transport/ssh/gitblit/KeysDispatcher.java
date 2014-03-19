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
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.sshd.common.util.Buffer;
import org.kohsuke.args4j.Argument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.models.UserModel;
import com.gitblit.transport.ssh.IPublicKeyManager;
import com.gitblit.transport.ssh.commands.CommandMetaData;
import com.gitblit.transport.ssh.commands.DispatchCommand;
import com.gitblit.transport.ssh.commands.SshCommand;

/**
 * The dispatcher and it's commands for SSH public key management.
 *
 * @author James Moger
 *
 */
@CommandMetaData(name = "keys", description = "SSH public key management commands")
public class KeysDispatcher extends DispatchCommand {

	@Override
	protected void registerCommands(UserModel user) {
		registerCommand(user, AddKey.class);
		registerCommand(user, RemoveKey.class);
		registerCommand(user, ListKeys.class);
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
				getKeyManager().addKey(username, key);
				log.info("added SSH public key for {}", username);
			}
		}
	}

	@CommandMetaData(name = "remove", aliases = { "rm" }, description = "Remove an SSH public key from your account")
	public static class RemoveKey extends BaseKeyCommand {

		protected final Logger log = LoggerFactory.getLogger(getClass());

		private final String ALL = "ALL";

		@Argument(metaVar = "<stdin>|<KEY>|ALL", usage = "the key to remove")
		private List<String> removeKeys = new ArrayList<String>();

		@Override
		public void run() throws IOException, UnloggedFailure {
			String username = getContext().getClient().getUsername();
			List<String> keys = readKeys(removeKeys);
			if (keys.contains(ALL)) {
				getKeyManager().removeAllKeys(username);
				log.info("removed all SSH public keys from {}", username);
			} else {
				for (String key : keys) {
					getKeyManager().removeKey(username, key);
					log.info("removed SSH public key from {}", username);
				}
			}
		}
	}

	@CommandMetaData(name = "list", aliases = { "ls" }, description = "List your public keys")
	public static class ListKeys extends SshCommand {

		@Override
		public void run() {
			IPublicKeyManager keyManager = getContext().getGitblit().getPublicKeyManager();
			List<PublicKey> keys = keyManager.getKeys(getContext().getClient().getUsername());

			for (PublicKey key : keys) {
				// two-steps - perhaps this could be improved
				Buffer buf = new Buffer();

				// 1: identify the algorithm
				buf.putRawPublicKey(key);
				String alg = buf.getString();

				// 2: encode the key
				buf.clear();
				buf.putPublicKey(key);
				String b64 = Base64.encodeBase64String(buf.getBytes());

				stdout.println(alg + " " + b64);
			}
		}
	}
}
