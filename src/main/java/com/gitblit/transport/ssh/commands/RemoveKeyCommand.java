/*
 * Copyright 2014 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.gitblit.transport.ssh.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.args4j.Argument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.transport.ssh.CommandMetaData;
import com.gitblit.transport.ssh.IKeyManager;


/**
 * Remove an SSH public key from the current user's authorized key list.
 *
 * @author James Moger
 *
 */
@CommandMetaData(name = "rm-key", description = "Remove an SSH public key from your account")
public class RemoveKeyCommand extends BaseKeyCommand {

	protected final Logger log = LoggerFactory.getLogger(getClass());

	private static final String ALL = "ALL";

	@Argument(metaVar = "<stdin>|<KEY>|ALL", usage = "the key to remove")
	private List<String> removeKeys = new ArrayList<String>();

	@Override
	public void run() throws IOException, UnloggedFailure {
		String username = ctx.getClient().getUsername();
		List<String> keys = readKeys(removeKeys);
		IKeyManager keyManager = authenticator.getKeyManager();
		if (keys.contains(ALL)) {
			keyManager.removeAllKeys(username);
			log.info("removed all SSH public keys from {}", username);
		} else {
			for (String key : keys) {
				keyManager.removeKey(username, key);
				log.info("removed SSH public key from {}", username);
			}
		}
	}
}
