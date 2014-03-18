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

import java.text.MessageFormat;
import java.util.List;

import org.kohsuke.args4j.Option;
import org.parboiled.common.StringUtils;

import com.gitblit.manager.IGitblit;
import com.gitblit.models.UserModel;
import com.gitblit.transport.ssh.commands.CommandMetaData;
import com.gitblit.transport.ssh.commands.SshCommand;

@CommandMetaData(name = "ls-users", description = "List users", admin = true)
public class LsUsersCommand extends SshCommand {

	@Option(name = "--verbose", aliases = { "-v" }, usage = "verbose")
	private boolean verbose;

	@Override
	public void run() {
		IGitblit gitblit = ctx.getGitblit();
		List<UserModel> users = gitblit.getAllUsers();
		int displaynameLen = 0;
		int usernameLen = 0;
		for (UserModel user : users) {
			int len = user.getDisplayName().length();
			if (len > displaynameLen) {
				displaynameLen = len;
			}
			if (!StringUtils.isEmpty(user.username)) {
				len = user.username.length();
				if (len > usernameLen) {
					usernameLen = len;
				}
			}
		}

		String pattern;
		if (verbose) {
			pattern = MessageFormat.format("%-{0,number,0}s\t%-{1,number,0}s\t%-10s\t%s", displaynameLen, usernameLen);
		} else {
			pattern = MessageFormat.format("%-{0,number,0}s\t%-{1,number,0}s", displaynameLen, usernameLen);
		}

		for (UserModel user : users) {
			if (user.disabled) {
				continue;
			}
			stdout.println(String.format(pattern,
					user.getDisplayName(),
					(user.canAdmin() ? "*":" ") + user.username,
					user.accountType,
					user.emailAddress == null ? "" : user.emailAddress));
		}
	}
}
