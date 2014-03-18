//Copyright (C) 2012 The Android Open Source Project
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.

package com.gitblit.transport.ssh.gitblit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import com.gitblit.transport.ssh.commands.CommandMetaData;

/** Set a user's account settings. **/
@CommandMetaData(name = "set-account", description = "Change an account's settings", admin = true)
public class SetAccountCommand extends BaseKeyCommand {

	private static final String ALL = "ALL";

	@Argument(index = 0, required = true, metaVar = "USER", usage = "full name, email-address, ssh username or account id")
	private String user;

	@Option(name = "--add-ssh-key", metaVar = "-|KEY", usage = "public keys to add to the account")
	private List<String> addSshKeys = new ArrayList<String>();

	@Option(name = "--delete-ssh-key", metaVar = "-|KEY", usage = "public keys to delete from the account")
	private List<String> deleteSshKeys = new ArrayList<String>();

	@Override
	public void run() throws IOException, UnloggedFailure {
		validate();
		setAccount();
	}

	private void validate() throws UnloggedFailure {
		if (addSshKeys.contains("-") && deleteSshKeys.contains("-")) {
			throw new UnloggedFailure(1, "Only one option may use the stdin");
		}
		if (deleteSshKeys.contains(ALL)) {
			deleteSshKeys = Collections.singletonList(ALL);
		}
	}

	private void setAccount() throws IOException, UnloggedFailure {
		addSshKeys = readKeys(addSshKeys);
		if (!addSshKeys.isEmpty()) {
			addSshKeys(addSshKeys);
		}

		deleteSshKeys = readKeys(deleteSshKeys);
		if (!deleteSshKeys.isEmpty()) {
			deleteSshKeys(deleteSshKeys);
		}
	}

	private void addSshKeys(List<String> sshKeys) throws UnloggedFailure,
			IOException {
		for (String sshKey : sshKeys) {
			getKeyManager().addKey(user, sshKey);
		}
	}

	private void deleteSshKeys(List<String> sshKeys) {
		if (sshKeys.contains(ALL)) {
			getKeyManager().removeAllKeys(user);
		} else {
			for (String sshKey : sshKeys) {
				getKeyManager().removeKey(user, sshKey);
			}
		}
	}
}
