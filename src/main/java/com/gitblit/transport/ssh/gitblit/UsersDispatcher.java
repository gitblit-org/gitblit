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

import java.util.ArrayList;
import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.parboiled.common.StringUtils;

import com.gitblit.manager.IGitblit;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.transport.ssh.commands.CommandMetaData;
import com.gitblit.transport.ssh.commands.DispatchCommand;
import com.gitblit.transport.ssh.commands.SshCommand;
import com.gitblit.utils.FlipTable;
import com.gitblit.utils.FlipTable.Borders;

@CommandMetaData(name = "users", description = "User management commands", admin = true)
public class UsersDispatcher extends DispatchCommand {

	private static final String banner1 = "===========================================================";

	private static final String banner2 = "-----------------------------------------------------------";

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
			UserModel u = gitblit.getUserModel(username);
			if (u == null) {
				throw new UnloggedFailure(1, String.format("Unknown user \"%s\"", username));
			}

			// fields
			String [] fheaders = new String [] { "Field", "Value" };
			String [][] fdata = new String[5][];
			fdata[0] = new String [] { "Email", u.emailAddress };
			fdata[1] = new String [] { "Type", u.accountType.toString() };
			fdata[2] = new String [] { "Can Admin", u.canAdmin() ? "Y":"N" };
			fdata[3] = new String [] { "Can Fork", u.canFork() ? "Y":"N" };
			fdata[4] = new String [] { "Can Create", u.canCreate() ? "Y":"N" };
			String fields = FlipTable.of(fheaders, fdata, Borders.COLS);
			
			// teams
			String [] theaders = new String [] { "Team", "Type" };
			String [][] tdata = new String[u.teams.size()][];
			int i = 0;
			for (TeamModel t : u.teams) {
				tdata[i] = new String [] { t.name, t.accountType.toString() };
				i++;
			}
			String teams = FlipTable.of(theaders, tdata, Borders.COLS);
			
			// permissions
			List<RegistrantAccessPermission> perms = u.getRepositoryPermissions();
			String[] pheaders = { "Repository", "Permission", "Type", "Source", "Mutable" };
			String [][] pdata = new String[perms.size()][];
			for (i = 0; i < perms.size(); i++) {
				RegistrantAccessPermission ap = perms.get(i);
				pdata[i] = new String[] { ap.registrant, ap.permission.toString(), ap.permissionType.toString(), ap.source, ap.mutable ? "Y":"N" };
			}
			String permissions = FlipTable.of(pheaders, pdata, Borders.COLS);
			
			// assemble user table
			String [] headers = new String[] { u.getDisplayName() + (u.username.equals(u.getDisplayName()) ? "" : (" (" + u.username + ")")) };
			String[][] data = new String[6][];
			data[0] = new String [] { "FIELDS" };
			data[1] = new String [] { fields };
			data[2] = new String [] { "TEAMS" };
			data[3] = new String [] { teams };
			data[4] = new String [] { "PERMISSIONS" };
			data[5] = new String [] { permissions };
			stdout.println(FlipTable.of(headers, data));
		}
	}

	@CommandMetaData(name = "list", aliases= { "ls" }, description = "List users")
	public static class ListUsers extends SshCommand {

		@Option(name = "--verbose", aliases = { "-v" }, usage = "verbose")
		private boolean verbose;

		@Option(name = "--tabbed", aliases = { "-t" }, usage = "as tabbed output")
		private boolean tabbed;

		@Argument(index = 0, metaVar = "REGEX", usage = "regex filter expression")
		protected String regexFilter;

		@Override
		public void run() {
			IGitblit gitblit = getContext().getGitblit();
			List<UserModel> users = gitblit.getAllUsers();

			List<UserModel> filtered;
			if (StringUtils.isEmpty(regexFilter)) {
				// no regex filter 
				filtered = users;
			} else {
				// regex filter the list
				filtered = new ArrayList<UserModel>();
				for (UserModel u : users) {
					if (u.username.matches(regexFilter)) {
						filtered.add(u);
					}
				}
			}

			if (tabbed) {
				asTabbed(filtered);
			} else {
				asTable(filtered);
			}
		}

		protected void asTable(List<UserModel> list) {
			String[] headers;
			if (verbose) {
				String[] h = { "Name", "Display name", "Type", "Email", "Create?", "Fork?"};
				headers = h;
			} else {
				String[] h = { "Name", "Display name", "Type", "Email"};
				headers = h;
			}

			String[][] data = new String[list.size()][];
			for (int i = 0; i < list.size(); i++) {
				UserModel u = list.get(i);

				String name = u.disabled ? "-" : ((u.canAdmin() ? "*" : " ")) + u.username;
				if (verbose) {
					data[i] = new String[] { name, u.displayName, u.accountType.name(),
							u.emailAddress,	u.canCreate() ? "Y":"", u.canFork() ? "Y" : ""};
				} else {
					data[i] = new String[] { name, u.displayName, u.accountType.name(),
							u.emailAddress };
				}
			}
			stdout.print(FlipTable.of(headers, data, Borders.BODY_HCOLS));
			stdout.println("  * = admin account,  - = disabled account");
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