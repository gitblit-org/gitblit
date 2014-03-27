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

import com.gitblit.Constants.AccessPermission;
import com.gitblit.manager.IGitblit;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.transport.ssh.commands.CommandMetaData;
import com.gitblit.transport.ssh.commands.DispatchCommand;
import com.gitblit.transport.ssh.commands.ListFilterCommand;
import com.gitblit.transport.ssh.commands.SshCommand;
import com.gitblit.transport.ssh.commands.UsageExample;
import com.gitblit.transport.ssh.commands.UsageExamples;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.FlipTable;
import com.gitblit.utils.FlipTable.Borders;
import com.gitblit.utils.StringUtils;
import com.google.common.base.Joiner;

@CommandMetaData(name = "users", description = "User management commands", admin = true)
public class UsersDispatcher extends DispatchCommand {

	@Override
	protected void setup(UserModel user) {
		// primary user commands
		register(user, NewUser.class);
		register(user, RemoveUser.class);
		register(user, ShowUser.class);
		register(user, ListUsers.class);

		// user-specific commands
		register(user, SetName.class);
		register(user, Permissions.class);
		register(user, DisableUser.class);
		register(user, EnableUser.class);
	}

	public static abstract class UserCommand extends SshCommand {
		@Argument(index = 0, required = true, metaVar = "USERNAME", usage = "username")
		protected String username;

		protected UserModel getUser(boolean requireUser) throws UnloggedFailure {
			IGitblit gitblit = getContext().getGitblit();
			UserModel user = gitblit.getUserModel(username);
			if (requireUser && user == null) {
				throw new UnloggedFailure(1, String.format("User %s does not exist!", username));
			}
			return user;
		}
	}

	@CommandMetaData(name = "new", description = "Create a new user account")
	@UsageExample(syntax = "${cmd} john 12345 --email john@smith.com --canFork --canCreate")
	public static class NewUser extends UserCommand {

		@Argument(index = 1, required = true, metaVar = "PASSWORD", usage = "password")
		protected String password;

		@Option(name = "--email", metaVar = "ADDRESS", usage = "email address")
		protected String email;

		@Option(name = "--canAdmin", usage = "can administer the server")
		protected boolean canAdmin;

		@Option(name = "--canFork", usage = "can fork repositories")
		protected boolean canFork;

		@Option(name = "--canCreate", usage = "can create personal repositories")
		protected boolean canCreate;

		@Option(name = "--disabled", usage = "create a disabled user account")
		protected boolean disabled;

		@Override
		public void run() throws UnloggedFailure {

			if (getUser(false) != null) {
				throw new UnloggedFailure(1, String.format("User %s already exists!", username));
			}

			UserModel user = new UserModel(username);
			user.password = password;

			if (email != null) {
				user.emailAddress = email;
			}

			user.canAdmin = canAdmin;
			user.canFork = canFork;
			user.canCreate = canCreate;
			user.disabled = disabled;

			IGitblit gitblit = getContext().getGitblit();
			if (gitblit.updateUserModel(username, user)) {
				stdout.println(String.format("%s created.", username));
			} else {
				throw new UnloggedFailure(1, String.format("Failed to create %s!", username));
			}
		}
	}

	@CommandMetaData(name = "set-name", description = "Set the display name of an account")
	@UsageExample(syntax = "${cmd} john John Smith", description = "The display name to \"John Smith\" for john's account")
	public static class SetName extends UserCommand {

		@Argument(index = 1, multiValued = true, required = true, metaVar = "NAME", usage = "display name")
		protected List<String> displayName = new ArrayList<String>();

		@Override
		public void run() throws UnloggedFailure {
			UserModel user = getUser(true);

			IGitblit gitblit = getContext().getGitblit();
			user.displayName = Joiner.on(" ").join(displayName);
			if (gitblit.updateUserModel(username, user)) {
				stdout.println(String.format("Set the display name of %s to \"%s\".", username, user.displayName));
			} else {
				throw new UnloggedFailure(1, String.format("Failed to set the display name of %s!", username));
			}
		}
	}

	@CommandMetaData(name = "disable", description = "Prohibit an account from authenticating")
	@UsageExample(syntax = "${cmd} john", description = "Prevent John from authenticating")
	public static class DisableUser extends UserCommand {

		@Override
		public void run() throws UnloggedFailure {

			UserModel user = getUser(true);
			user.disabled = true;

			IGitblit gitblit = getContext().getGitblit();
			if (gitblit.updateUserModel(username, user)) {
				stdout.println(String.format("%s is not allowed to authenticate.", username));
			} else {
				throw new UnloggedFailure(1, String.format("Failed to disable %s!", username));
			}
		}
	}

	@CommandMetaData(name = "enable", description = "Allow an account to authenticate")
	@UsageExample(syntax = "${cmd} john", description = "Allow John to authenticate")
	public static class EnableUser extends UserCommand {

		@Override
		public void run() throws UnloggedFailure {

			UserModel user = getUser(true);
			user.disabled = false;

			IGitblit gitblit = getContext().getGitblit();
			if (gitblit.updateUserModel(username, user)) {
				stdout.println(String.format("%s may now authenticate.", username));
			} else {
				throw new UnloggedFailure(1, String.format("Failed to enable %s!", username));
			}
		}
	}

	@CommandMetaData(name = "permissions", aliases = { "perms" }, description = "Add or remove permissions from an account")
	@UsageExample(syntax = "${cmd} john RW:alpha/repo.git RWC:alpha/repo2.git", description = "Add or set permissions for John")
	public static class Permissions extends UserCommand {

		@Argument(index = 1, multiValued = true, metaVar = "[PERMISSION:]REPOSITORY", usage = "a repository expression")
		protected List<String> permissions;

		@Option(name = "--remove", aliases = { "-r" }, metaVar = "REPOSITORY|ALL", usage = "remove a repository permission")
		protected List<String> removals;

		@Override
		public void run() throws UnloggedFailure {
			IGitblit gitblit = getContext().getGitblit();
			UserModel user = getUser(true);

			boolean modified = false;
			if (!ArrayUtils.isEmpty(removals)) {
				if (removals.contains("ALL")) {
					user.permissions.clear();
				} else {
					for (String repo : removals) {
						user.removeRepositoryPermission(repo);
						log.info(String.format("Removing permission for %s from %s", repo, username));
					}
				}
				modified = true;
			}

			if (!ArrayUtils.isEmpty(permissions)) {
				for (String perm : permissions) {
					String repo = AccessPermission.repositoryFromRole(perm);
					if (StringUtils.findInvalidCharacter(repo) == null) {
						// explicit permision, confirm repository
						RepositoryModel r = gitblit.getRepositoryModel(repo);
						if (r == null) {
							throw new UnloggedFailure(1, String.format("Repository %s does not exist!", repo));
						}
					}
					AccessPermission ap = AccessPermission.permissionFromRole(perm);
					user.setRepositoryPermission(repo, ap);
					log.info(String.format("Setting %s:%s for %s", ap.name(), repo, username));
				}
				modified = true;
			}

			if (modified && gitblit.updateUserModel(username, user)) {
				// reload & display new permissions
				user = gitblit.getUserModel(username);
			}

			showPermissions(user);
		}

		protected void showPermissions(UserModel user) {
			List<RegistrantAccessPermission> perms = user.getRepositoryPermissions();
			String[] pheaders = { "Repository", "Permission", "Type", "Source", "Mutable" };
			Object [][] pdata = new Object[perms.size()][];
			for (int i = 0; i < perms.size(); i++) {
				RegistrantAccessPermission ap = perms.get(i);
				pdata[i] = new Object[] { ap.registrant, ap.permission, ap.permissionType, ap.source, ap.mutable ? "Y":"" };
			}
			stdout.println(FlipTable.of(pheaders, pdata, Borders.BODY_HCOLS));
		}
	}

	@CommandMetaData(name = "remove", aliases = { "rm" }, description = "Remove a user account")
	@UsageExample(syntax = "${cmd} john", description = "Delete john's account")
	public static class RemoveUser extends UserCommand {

		@Override
		public void run() throws UnloggedFailure {

			UserModel user = getUser(true);
			IGitblit gitblit = getContext().getGitblit();
			if (gitblit.deleteUserModel(user)) {
				stdout.println(String.format("%s has been deleted.", username));
			} else {
				throw new UnloggedFailure(1, String.format("Failed to delete %s!", username));
			}
		}
	}

	@CommandMetaData(name = "show", description = "Show the details of an account")
	@UsageExample(syntax = "${cmd} john", description = "Display john's account")
	public static class ShowUser extends UserCommand {

		@Override
		public void run() throws UnloggedFailure {

			UserModel u = getUser(true);

			// fields
			String [] fheaders = new String [] { "Field", "Value" };
			Object [][] fdata = new Object[5][];
			fdata[0] = new Object [] { "Email", u.emailAddress };
			fdata[1] = new Object [] { "Type", u.accountType };
			fdata[2] = new Object [] { "Can Admin", u.canAdmin() ? "Y":"" };
			fdata[3] = new Object [] { "Can Fork", u.canFork() ? "Y":"" };
			fdata[4] = new Object [] { "Can Create", u.canCreate() ? "Y":"" };
			String fields = FlipTable.of(fheaders, fdata, Borders.COLS);

			// teams
			String teams;
			if (u.teams.size() == 0) {
				teams = FlipTable.EMPTY;
			} else {
				String [] theaders = new String [] { "Team", "Type" };
				Object [][] tdata = new Object[u.teams.size()][];
				int i = 0;
				for (TeamModel t : u.teams) {
					tdata[i] = new Object [] { t.name, t.accountType };
					i++;
				}
				teams = FlipTable.of(theaders, tdata, Borders.COLS);
			}

			// permissions
			List<RegistrantAccessPermission> perms = u.getRepositoryPermissions();
			String permissions;
			if (perms.isEmpty()) {
				permissions = FlipTable.EMPTY;
			} else {
				String[] pheaders = { "Repository", "Permission", "Type", "Source", "Mutable" };
				Object [][] pdata = new Object[perms.size()][];
				for (int i = 0; i < perms.size(); i++) {
					RegistrantAccessPermission ap = perms.get(i);
					pdata[i] = new Object[] { ap.registrant, ap.permission, ap.permissionType, ap.source, ap.mutable ? "Y":"" };
				}
				permissions = FlipTable.of(pheaders, pdata, Borders.COLS);
			}

			// assemble user table
			String userTitle = u.getDisplayName() + (u.username.equals(u.getDisplayName()) ? "" : (" (" + u.username + ")"));
			if (u.disabled) {
				userTitle += "  [DISABLED]";
			}
			String [] headers = new String[] { userTitle };
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

	@CommandMetaData(name = "list", aliases= { "ls" }, description = "List accounts")
	@UsageExamples( examples = {
		@UsageExample(syntax = "${cmd}", description = "List accounts as a table"),
		@UsageExample(syntax = "${cmd} j.*", description = "List all accounts that start with 'j'"),
	})
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

				String name = (u.disabled ? "-" : ((u.canAdmin() ? "*" : " "))) + u.username;
				if (verbose) {
					data[i] = new Object[] { name, u.displayName, u.accountType,
							u.emailAddress,
							(u.canAdmin() || u.canCreate()) ? "Y":"",
							(u.canAdmin() || u.canFork()) ? "Y" : ""};
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
							(u.canAdmin() || u.canCreate()) ? "Y":"",
							(u.canAdmin() || u.canFork()) ? "Y" : "");
				}
			} else {
				for (UserModel u : users) {
					outTabbed(u.disabled ? "-" : ((u.canAdmin() ? "*" : " ")) + u.username);
				}
			}
		}
	}
}