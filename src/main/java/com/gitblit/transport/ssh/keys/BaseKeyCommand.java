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
package com.gitblit.transport.ssh.keys;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.List;

import com.gitblit.transport.ssh.IPublicKeyManager;
import com.gitblit.transport.ssh.SshKey;
import com.gitblit.transport.ssh.commands.SshCommand;
import com.google.common.base.Charsets;

/**
 *
 * Base class for commands that read SSH keys from stdin or a parameter list.
 *
 */
abstract class BaseKeyCommand extends SshCommand {

	protected List<String> readKeys(List<String> sshKeys)
			throws UnsupportedEncodingException, IOException {
		int idx = -1;
		if (sshKeys.isEmpty() || (idx = sshKeys.indexOf("-")) >= 0) {
			String content = "";
			BufferedReader br = new BufferedReader(new InputStreamReader(
					in, Charsets.UTF_8));
			String line;
			while ((line = br.readLine()) != null) {
				content += line + "\n";
			}
			final String sshKey = content.trim();
			if (!sshKey.isEmpty()) {
				if (idx == -1) {
					sshKeys.add(sshKey);
				} else {
					sshKeys.set(idx, sshKey);
				}
			}
		}
		return sshKeys;
	}

	protected IPublicKeyManager getKeyManager() {
		return getContext().getGitblit().getPublicKeyManager();
	}

	protected SshKey parseKey(String rawData) throws UnloggedFailure {
		if (rawData.contains("PRIVATE")) {
			throw new UnloggedFailure(1,  "Please provide a PUBLIC key, not a PRIVATE key!");
		}
		SshKey key = new SshKey(rawData);
		return key;
	}
}
