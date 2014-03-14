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
 * Add a key to the current user's authorized keys list.
 *
 * @author James Moger
 *
 */
@CommandMetaData(name = "add-key", description = "Add an SSH public key to your account")
public class AddKeyCommand extends BaseKeyCommand {

	protected final Logger log = LoggerFactory.getLogger(getClass());

	@Argument(metaVar = "<stdin>|KEY", usage = "the key to add")
	private List<String> addKeys = new ArrayList<String>();

	@Override
	public void run() throws IOException, UnloggedFailure {
		String username = ctx.getClient().getUsername();
		List<String> keys = readKeys(addKeys);
		IKeyManager keyManager = authenticator.getKeyManager();
		for (String key : keys) {
			keyManager.addKey(username, key);
			log.info("added SSH public key for {}", username);
		}
		authenticator.getKeyCache().invalidate(username);
	}
}
