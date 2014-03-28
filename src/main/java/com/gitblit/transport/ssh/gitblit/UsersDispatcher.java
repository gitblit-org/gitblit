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
import com.gitblit.GitBlitException;
import com.gitblit.Keys;
import com.gitblit.manager.IGitblit;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.transport.ssh.SshKey;
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
		register(user, RenameUser.class);
		register(user, RemoveUser.class);
		register(user, ShowUser.class);
		register(user, ListUsers.class);

		// user-specific commands
		register(user, SetField.class);
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
			try {
				gitblit.addUser(user);
				stdout.println(String.format("%s created.", username));
			} catch (GitBlitException e) {
				log.error("Failed to add " + username, e);
				throw new UnloggedFailure(1, e.getMessage());
			}
		}
	}

	@CommandMetaData(name = "rename", aliases = { "mv" }, description = "Rename an account")
	@UsageExample(syntax = "${cmd} john frank", description = "Rename the account from john to frank")
	public static class RenameUser extends UserCommand {
		@Argument(index = 1, required = true, metaVar = "NEWNAME", usage = "the new account name")
		protected String newUserName;

				@Override
		public void run() throws UnloggedFailure {
			UserModel user = getUser(true);
			IGitblit gitblit = getContext().getGitblit();
			if (null != gitblit.getTeamModel(newUserName)) {
				throw new UnloggedFailure(1, String.format("Team %s already exists!", newUserName));
			}

			// set the new name
			user.username = newUserName;

			try {
				gitblit.reviseUser(username, user);
				stdout.println(String.format("Renamed user %s to %s.", username, newUserName));
			} catch (GitBlitException e) {
				String msg = String.format("Failed to rename user from %s to %s", username, newUserName);
				log.error(msg, e);
				throw new UnloggedFailure(1, msg);
			}
		}
	}

	@CommandMetaData(name = "set", description = "Set the specified field of an account")
	@UsageExample(syntax = "${cmd} john name John Smith", description = "Set the display name to \"John Smith\" for john's account")
	public static class SetField extends UserCommand {

		@Argument(index = 1, required = true, metaVar = "FIELD", usage = "the field to update")
		protected String fieldName;

		@Argument(index = 2, required = true, metaVar = "VALUE", usage = "the new value")
		protected List<String> fieldValues = new ArrayList<String>();

		protected enum Field {
			name, displayName, email, password, canAdmin, canFork, canCreate;

			static Field fromString(String name) {
				for (Field field : values()) {
					if (field.name().equalsIgnoreCase(name)) {
						return field;
					}
				}
				return null;
			}
		}

		@Override
		protected String getUsageText() {
			String fields = Joiner.on(", ").join(Field.values());
			StringBuilder sb = new StringBuilder();
			sb.append("Valid fields are:\n   ").append(fields);
			return sb.toString();
		}

		@Override
		public void run() throws UnloggedFailure {
			UserModel user = getUser(true);

			Field field = Field.fromString(fieldName);
			if (field == null) {
				throw new UnloggedFailure(1, String.format("Unknown field %s", fieldName));
			}

			String value = Joiner.on(" ").join(fieldValues).trim();
			IGitblit gitblit = getContext().getGitblit();

			boolean editCredentials = gitblit.supportsCredentialChanges(user);
			boolean editDisplayName = gitblit.supportsDisplayNameChanges(user);
			boolean editEmailAddress = gitblit.supportsEmailAddressChanges(user);

			String m = String.format("Can not edit %s for %s (%s)", field, user.username, user.accountType);

			switch(field) {
			case name:
			case displayName:
				if (!editDisplayName) {
					throw new UnloggedFailure(1, m);
				}
				user.displayName = value;
				break;
			case email:
				if (!editEmailAddress) {
					throw new UnloggedFailure(1, m);
				}
				user.emailAddress = value;
				break;
			case password:
				if (!editCredentials) {
					throw new UnloggedFailure(1, m);
				}
				int minLength = gitblit.getSettings().getInteger(Keys.realm.minPasswordLength, 5);
				if (minLength < 4) {
					minLength = 4;
				}
				if (value.trim().length() < minLength) {
					throw new UnloggedFailure(1,  "Password is too short.");
				}

				// Optionally store the password MD5 digest.
				String type = gitblit.getSettings().getString(Keys.realm.passwordStorage, "md5");
				if (type.equalsIgnoreCase("md5")) {
					// store MD5 digest of password
					user.password = StringUtils.MD5_TYPE + StringUtils.getMD5(value);
				} else if (type.equalsIgnoreCase("combined-md5")) {
					// store MD5 digest of username+password
					user.password = StringUtils.COMBINED_MD5_TYPE + StringUtils.getMD5(username + value);
				} else {
					user.password = value;
				}

				// reset the cookie
				user.cookie = StringUtils.getSHA1(user.username + value);
				break;
			case canAdmin:
				user.canAdmin = toBool(value);
				break;
			case canFork:
				user.canFork = toBool(value);
				break;
			case canCreate:
				user.canCreate = toBool(value);
				break;
			default:
				throw new UnloggedFailure(1,  String.format("Field %s was not properly handled by the set command.", fieldName));
			}

			try {
				gitblit.reviseUser(username, user);
				stdout.println(String.format("Set %s.%s = %s", username, fieldName, value));
			} catch (GitBlitException e) {
				String msg = String.format("Failed to set %s.%s = %s", username, fieldName, value);
				log.error(msg, e);
				throw new UnloggedFailure(1, msg);
			}
		}

		protected boolean toBool(String value) throws UnloggedFailure {
			String v = value.toLowerCase();
			if (v.equals("t")
					|| v.equals("true")
					|| v.equals("yes")
					|| v.equals("on")
					|| v.equals("y")
					|| v.equals("1")) {
				return true;
			} else if (v.equals("f")
					|| v.equals("false")
					|| v.equals("no")
					|| v.equals("off")
					|| v.equals("n")
					|| v.equals("0")) {
				return false;
			}
			throw new UnloggedFailure(1,  String.format("Invalid boolean value %s", value));
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
			StringBuilder fb = new StringBuilder();
			fb.append("Email      : ").append(u.emailAddress == null ? "": u.emailAddress).append('\n');
			fb.append("Type       : ").append(u.accountType).append('\n');
			fb.append("Can Admin  : ").append(u.canAdmin() ? "Y":"").append('\n');
			fb.append("Can Fork   : ").append(u.canFork() ? "Y":"").append('\n');
			fb.append("Can Create : ").append(u.canCreate() ? "Y":"").append('\n');
			String fields = fb.toString();

			// teams
			String teams;
			if (u.teams.size() == 0) {
				teams = FlipTable.EMPTY;
			} else {
				teams = Joiner.on(", ").join(u.teams);
			}

			// owned repositories
			String ownedTable;
			List<RepositoryModel> owned = new ArrayList<RepositoryModel>();
			for (RepositoryModel repository : getContext().getGitblit().getRepositoryModels(u)) {
				if (repository.isOwner(u.username)) {
					owned.add(repository);
				}
			}
			if (owned.isEmpty()) {
				ownedTable = FlipTable.EMPTY;
			} else {
				String [] theaders = new String [] { "Repository", "Description" };
				Object [][] tdata = new Object[owned.size()][];
				int i = 0;
				for (RepositoryModel r : owned) {
					tdata[i] = new Object [] { r.name, r.description };
					i++;
				}
				ownedTable = FlipTable.of(theaders, tdata, Borders.COLS);
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

			// keys
			String keyTable;
			List<SshKey> keys = getContext().getGitblit().getPublicKeyManager().getKeys(u.username);
			if (ArrayUtils.isEmpty(keys)) {
				keyTable = FlipTable.EMPTY;
			} else {
				String[] headers = { "#", "Fingerprint", "Comment", "Type" };
				int len = keys == null ? 0 : keys.size();
				Object[][] data = new Object[len][];
				for (int i = 0; i < len; i++) {
					// show 1-based index numbers with the fingerprint
					// this is useful for comparing with "ssh-add -l"
					SshKey k = keys.get(i);
					data[i] = new Object[] { (i + 1), k.getFingerprint(), k.getComment(), k.getAlgorithm() };
				}
				keyTable = FlipTable.of(headers, data, Borders.COLS);
			}

			// assemble user table
			String userTitle = u.getDisplayName() + (u.username.equals(u.getDisplayName()) ? "" : (" (" + u.username + ")"));
			if (u.disabled) {
				userTitle += "  [DISABLED]";
			}
			String [] headers = new String[] { userTitle };
			String[][] data = new String[8][];
			data[0] = new String [] { "FIELDS" };
			data[1] = new String [] { fields };
			data[2] = new String [] { "TEAMS" };
			data[3] = new String [] { teams };
			data[4] = new String [] { "OWNED REPOSITORIES" };
			data[5] = new String [] { ownedTable };
			data[4] = new String [] { "PERMISSIONS" };
			data[5] = new String [] { permissions };
			data[6] = new String [] { "SSH PUBLIC KEYS" };
			data[7] = new String [] { keyTable };
			stdout.println(FlipTable.of(headers, data));
		}
	}

	@CommandMetaData(name = "list", aliases= { "ls" }, description = "List accounts")
	@UsageExamples(examples = {
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
				String[] h = { "Name", "Display name", "Email", "Type", "Teams", "Create?", "Fork?"};
				headers = h;
			} else {
				String[] h = { "Name", "Display name", "Email", "Type"};
				headers = h;
			}

			Object[][] data = new Object[list.size()][];
			for (int i = 0; i < list.size(); i++) {
				UserModel u = list.get(i);

				String name = (u.disabled ? "-" : ((u.canAdmin() ? "*" : " "))) + u.username;
				if (verbose) {
					data[i] = new Object[] {
							name,
							u.displayName,
							u.emailAddress,
							u.accountType + (u.canAdmin() ? ",admin":""),
							u.teams.isEmpty() ? "" : u.teams.size(),
							(u.canAdmin() || u.canCreate()) ? "Y":"",
							(u.canAdmin() || u.canFork()) ? "Y" : ""};
				} else {
					data[i] = new Object[] {
							name,
							u.displayName,
							u.emailAddress,
							u.accountType + (u.canAdmin() ? ",admin":"")};
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
					outTabbed(
							u.disabled ? "-" : ((u.canAdmin() ? "*" : " ")) + u.username,
							u.getDisplayName(),
							u.emailAddress == null ? "" : u.emailAddress,
							u.accountType + (u.canAdmin() ? ",admin":""),
							u.teams.isEmpty() ? "" : u.teams.size(),
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