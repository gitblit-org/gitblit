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

import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import com.gitblit.manager.IGitblit;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.UserModel;
import com.gitblit.transport.ssh.commands.CommandMetaData;
import com.gitblit.transport.ssh.commands.DispatchCommand;
import com.gitblit.transport.ssh.commands.SshCommand;
import com.gitblit.utils.FlipTable;
import com.gitblit.utils.FlipTable.Borders;

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

		@Option(name = "--tabbed", aliases = { "-t" }, usage = "as tabbed output")
		private boolean tabbed;

		@Override
		public void run() {
			IGitblit gitblit = getContext().getGitblit();
			List<UserModel> users = gitblit.getAllUsers();

			if (tabbed) {
				asTabbed(users);
			} else {
				asTable(users);
			}
		}

		protected void asTable(List<UserModel> list) {
			String[] headers;
			if (verbose) {
				String[] h = { "Name", "Display name", "Type", "E-mail", "Create?", "Fork?"};
				headers = h;
			} else {
				String[] h = { "Name", "Display name", "Type", "E-mail"};
				headers = h;
			}

			String[][] data = new String[list.size()][];
			for (int i = 0; i < list.size(); i++) {
				UserModel u = list.get(i);

				String name = u.disabled ? "-" : ((u.canAdmin() ? "*" : " ")) + u.username;
				if (verbose) {
					data[i] = new String[] { name, u.displayName == null ? "" : u.displayName,
							u.accountType.name(), u.emailAddress == null ? "" : u.emailAddress ,
									u.canCreate() ? "Y":"", u.canFork() ? "Y" : ""};
				} else {
					data[i] = new String[] { name, u.displayName == null ? "" : u.displayName,
							u.accountType.name(), u.emailAddress == null ? "" : u.emailAddress };
				}
			}
			stdout.print(FlipTable.of(headers, data, Borders.BODY_COLS));
			stdout.println("* = admin account, - = disabled account");
			stdout.println();
		}

		protected void asTabbed(List<UserModel> users) {
			String pattern;
			if (verbose) {
				pattern = "%s\ts\t%s\t%s\t%s\t%s";
			} else {
				pattern = "%s";
			}

			for (UserModel u : users) {
				stdout.println(String.format(pattern,
						u.disabled ? "-" : ((u.canAdmin() ? "*" : " ")) + u.username,
						u.getDisplayName(),
						u.accountType,
						u.emailAddress == null ? "" : u.emailAddress,
						u.canCreate() ? "Y":"",
						u.canFork() ? "Y" : ""));
			}
		}
	}
}