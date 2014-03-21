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

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.parboiled.common.StringUtils;

import com.gitblit.manager.IGitblit;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.UserModel;
import com.gitblit.transport.ssh.commands.CommandMetaData;
import com.gitblit.transport.ssh.commands.DispatchCommand;
import com.gitblit.transport.ssh.commands.SshCommand;

@CommandMetaData(name = "users", description = "User management commands", admin = true)
public class UsersDispatcher extends DispatchCommand {

	@Override
	protected void setup(UserModel user) {
		register(user, ShowUser.class);
		register(user, ListUsers.class);
	}

	@CommandMetaData(name = "show", description = "Show a user")
	public static class ShowUser extends SshCommand {
		@Argument(index = 0, required = true, metaVar = "USERNAME", usage = "username")
		protected String username;

		@Override
		public void run() throws UnloggedFailure {
			IGitblit gitblit = getContext().getGitblit();
			UserModel user = gitblit.getUserModel(username);
			if (user == null) {
				throw new UnloggedFailure(1, String.format("Unknown user \"%s\"", username));
			}
			stdout.println();
			stdout.println(user.username);
			stdout.println();
			for (RegistrantAccessPermission ap : user.getRepositoryPermissions()) {
				stdout.println(String.format("%s %s", ap.registrant, ap.permission));
			}
		}
	}

	@CommandMetaData(name = "list", aliases= { "ls" }, description = "List users")
	public static class ListUsers extends SshCommand {

		@Option(name = "--verbose", aliases = { "-v" }, usage = "verbose")
		private boolean verbose;

		@Override
		public void run() {
			IGitblit gitblit = getContext().getGitblit();
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
}