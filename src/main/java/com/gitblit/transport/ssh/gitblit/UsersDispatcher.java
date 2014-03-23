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

import com.gitblit.manager.IGitblit;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.transport.ssh.commands.CommandMetaData;
import com.gitblit.transport.ssh.commands.DispatchCommand;
import com.gitblit.transport.ssh.commands.ListFilterCommand;
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
			UserModel u = gitblit.getUserModel(username);
			if (u == null) {
				throw new UnloggedFailure(1, String.format("Unknown user \"%s\"", username));
			}

			// fields
			String [] fheaders = new String [] { "Field", "Value" };
			Object [][] fdata = new Object[5][];
			fdata[0] = new Object [] { "Email", u.emailAddress };
			fdata[1] = new Object [] { "Type", u.accountType };
			fdata[2] = new Object [] { "Can Admin", u.canAdmin() ? "Y":"N" };
			fdata[3] = new Object [] { "Can Fork", u.canFork() ? "Y":"N" };
			fdata[4] = new Object [] { "Can Create", u.canCreate() ? "Y":"N" };
			String fields = FlipTable.of(fheaders, fdata, Borders.COLS);

			// teams
			String [] theaders = new String [] { "Team", "Type" };
			Object [][] tdata = new Object[u.teams.size()][];
			int i = 0;
			for (TeamModel t : u.teams) {
				tdata[i] = new Object [] { t.name, t.accountType };
				i++;
			}
			String teams = FlipTable.of(theaders, tdata, Borders.COLS);

			// permissions
			List<RegistrantAccessPermission> perms = u.getRepositoryPermissions();
			String[] pheaders = { "Repository", "Permission", "Type", "Source", "Mutable" };
			Object [][] pdata = new Object[perms.size()][];
			for (i = 0; i < perms.size(); i++) {
				RegistrantAccessPermission ap = perms.get(i);
				pdata[i] = new Object[] { ap.registrant, ap.permission, ap.permissionType, ap.source, ap.mutable ? "Y":"N" };
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
	public static class ListUsers extends ListFilterCommand<UserModel> {

		@Override
		protected List<UserModel> getItems() {
			IGitblit gitblit = getContext().getGitblit();
			List<UserModel> users = gitblit.getAllUsers();
			return users;
		}

		@Override
		protected boolean matches(String filter, UserModel u) {
			return u.username.matches(filter);
		}

		@Override
		protected void asTable(List<UserModel> list) {
			String[] headers;
			if (verbose) {
				String[] h = { "Name", "Display name", "Type", "Email", "Create?", "Fork?"};
				headers = h;
			} else {
				String[] h = { "Name", "Display name", "Type", "Email"};
				headers = h;
			}

			Object[][] data = new Object[list.size()][];
			for (int i = 0; i < list.size(); i++) {
				UserModel u = list.get(i);

				String name = u.disabled ? "-" : ((u.canAdmin() ? "*" : " ")) + u.username;
				if (verbose) {
					data[i] = new Object[] { name, u.displayName, u.accountType,
							u.emailAddress,	u.canCreate() ? "Y":"", u.canFork() ? "Y" : ""};
				} else {
					data[i] = new Object[] { name, u.displayName, u.accountType,
							u.emailAddress };
				}
			}
			stdout.print(FlipTable.of(headers, data, Borders.BODY_HCOLS));
			stdout.println("  * = admin account,  - = disabled account");
			stdout.println();
		}

		@Override
		protected void asTabbed(List<UserModel> users) {
			if (verbose) {
				for (UserModel u : users) {
					outTabbed(u.disabled ? "-" : ((u.canAdmin() ? "*" : " ")) + u.username,
							u.getDisplayName(),
							u.accountType,
							u.emailAddress == null ? "" : u.emailAddress,
							u.canCreate() ? "Y":"",
							u.canFork() ? "Y" : "");
				}
			} else {
				for (UserModel u : users) {
					outTabbed(u.disabled ? "-" : ((u.canAdmin() ? "*" : " ")) + u.username);
				}
			}
		}
	}
}