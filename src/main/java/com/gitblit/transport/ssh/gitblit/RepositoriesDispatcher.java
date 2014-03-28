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
import java.util.Collection;
import java.util.List;

import org.kohsuke.args4j.Argument;

import com.gitblit.GitBlitException;
import com.gitblit.Keys;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.AuthorizationControl;
import com.gitblit.manager.IGitblit;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.transport.ssh.commands.CommandMetaData;
import com.gitblit.transport.ssh.commands.DispatchCommand;
import com.gitblit.transport.ssh.commands.ListFilterCommand;
import com.gitblit.transport.ssh.commands.SshCommand;
import com.gitblit.transport.ssh.commands.UsageExample;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.FlipTable;
import com.gitblit.utils.FlipTable.Borders;
import com.gitblit.utils.StringUtils;
import com.google.common.base.Joiner;

@CommandMetaData(name = "repositories", aliases = { "repos" }, description = "Repository management commands")
public class RepositoriesDispatcher extends DispatchCommand {

	@Override
	protected void setup(UserModel user) {
		// primary commands
		register(user, NewRepository.class);
		register(user, RenameRepository.class);
		register(user, RemoveRepository.class);
		register(user, ShowRepository.class);
		register(user, ListRepositories.class);

		// repository-specific commands
		register(user, SetField.class);
	}

	public static abstract class RepositoryCommand extends SshCommand {
		@Argument(index = 0, required = true, metaVar = "REPOSITORY", usage = "repository")
		protected String repository;

		protected RepositoryModel getRepository(boolean requireRepository) throws UnloggedFailure {
			IGitblit gitblit = getContext().getGitblit();
			RepositoryModel repo = gitblit.getRepositoryModel(repository);
			if (requireRepository && repo == null) {
				throw new UnloggedFailure(1, String.format("Repository %s does not exist!", repository));
			}
			return repo;
		}
		
		protected String sanitize(String name) throws UnloggedFailure {
			// automatically convert backslashes to forward slashes
			name = name.replace('\\', '/');
			// Automatically replace // with /
			name = name.replace("//", "/");

			// prohibit folder paths
			if (name.startsWith("/")) {
				throw new UnloggedFailure(1,  "Illegal leading slash");
			}
			if (name.startsWith("../")) {
				throw new UnloggedFailure(1,  "Illegal relative slash");
			}
			if (name.contains("/../")) {
				throw new UnloggedFailure(1,  "Illegal relative slash");
			}
			if (name.endsWith("/")) {
				name = name.substring(0, name.length() - 1);
			}
			return name;
		}
	}

	@CommandMetaData(name = "new", aliases = { "add" }, description = "Create a new repository")
	@UsageExample(syntax = "${cmd} myRepo")
	public static class NewRepository extends RepositoryCommand {

		@Override
		public void run() throws UnloggedFailure {

			UserModel user = getContext().getClient().getUser();

			String name = sanitize(repository);
			
			if (!user.canCreate(name)) {
				// try to prepend personal path
				String path  = StringUtils.getFirstPathElement(name);
				if ("".equals(path)) {
					name = user.getPersonalPath() + "/" + name;
				}
			}

			if (getRepository(false) != null) {
				throw new UnloggedFailure(1, String.format("Repository %s already exists!", name));
			}
						
			if (!user.canCreate(name)) {
				throw new UnloggedFailure(1,  String.format("Sorry, you do not have permission to create %s", name));
			}

			IGitblit gitblit = getContext().getGitblit();

			RepositoryModel repo = new RepositoryModel();
			repo.name = name;
			repo.projectPath = StringUtils.getFirstPathElement(name);
			String restriction = gitblit.getSettings().getString(Keys.git.defaultAccessRestriction, "PUSH");
			repo.accessRestriction = AccessRestrictionType.fromName(restriction);
			String authorization = gitblit.getSettings().getString(Keys.git.defaultAuthorizationControl, null);
			repo.authorizationControl = AuthorizationControl.fromName(authorization);

			if (user.isMyPersonalRepository(name)) {
				// personal repositories are private by default
				repo.addOwner(user.username);
				repo.accessRestriction = AccessRestrictionType.VIEW;
				repo.authorizationControl = AuthorizationControl.NAMED;
			}

			try {
				gitblit.updateRepositoryModel(repository,  repo, true);
				stdout.println(String.format("%s created.", repo.name));
			} catch (GitBlitException e) {
				log.error("Failed to add " + repository, e);
				throw new UnloggedFailure(1, e.getMessage());
			}
		}
	}

	@CommandMetaData(name = "rename", aliases = { "mv" }, description = "Rename a repository")
	@UsageExample(syntax = "${cmd} myRepo.git otherRepo.git", description = "Rename the repository from myRepo.git to otherRepo.git")
	public static class RenameRepository extends RepositoryCommand {
		@Argument(index = 1, required = true, metaVar = "NEWNAME", usage = "the new repository name")
		protected String newRepositoryName;

				@Override
		public void run() throws UnloggedFailure {
			RepositoryModel repo = getRepository(true);
			IGitblit gitblit = getContext().getGitblit();
			UserModel user = getContext().getClient().getUser();

			String name = sanitize(newRepositoryName);
			if (!user.canCreate(name)) {
				// try to prepend personal path
				String path  = StringUtils.getFirstPathElement(name);
				if ("".equals(path)) {
					name = user.getPersonalPath() + "/" + name;
				}
			}

			if (null != gitblit.getRepositoryModel(name)) {
				throw new UnloggedFailure(1, String.format("Repository %s already exists!", name));
			}

			if (repo.name.equalsIgnoreCase(name)) {
				throw new UnloggedFailure(1, "Repository names are identical");
			}
			
			if (!user.canAdmin(repo)) {
				throw new UnloggedFailure(1,  String.format("Sorry, you do not have permission to rename %s", repository));
			}
			
			if (!user.canCreate(name)) {
				throw new UnloggedFailure(1, String.format("Sorry, you don't have permission to move %s to %s/", repository, name));
			}

			// set the new name
			repo.name = name;

			try {
				gitblit.updateRepositoryModel(repository, repo, false);
				stdout.println(String.format("Renamed repository %s to %s.", repository, name));
			} catch (GitBlitException e) {
				String msg = String.format("Failed to rename repository from %s to %s", repository, name);
				log.error(msg, e);
				throw new UnloggedFailure(1, msg);
			}
		}
	}

	@CommandMetaData(name = "set", description = "Set the specified field of a repository")
	@UsageExample(syntax = "${cmd} myRepo description John's personal projects", description = "Set the description of a repository")
	public static class SetField extends RepositoryCommand {

		@Argument(index = 1, required = true, metaVar = "FIELD", usage = "the field to update")
		protected String fieldName;

		@Argument(index = 2, required = true, metaVar = "VALUE", usage = "the new value")
		protected List<String> fieldValues = new ArrayList<String>();

		protected enum Field {
			description;

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
			RepositoryModel repo = getRepository(true);

			Field field = Field.fromString(fieldName);
			if (field == null) {
				throw new UnloggedFailure(1, String.format("Unknown field %s", fieldName));
			}

			if (!getContext().getClient().getUser().canAdmin(repo)) {
				throw new UnloggedFailure(1,  String.format("Sorry, you do not have permission to administer %s", repository));
			}

			String value = Joiner.on(" ").join(fieldValues).trim();
			IGitblit gitblit = getContext().getGitblit();

			switch(field) {
			case description:
				repo.description = value;
				break;
			default:
				throw new UnloggedFailure(1,  String.format("Field %s was not properly handled by the set command.", fieldName));
			}

			try {
				gitblit.updateRepositoryModel(repo.name,  repo, false);
				stdout.println(String.format("Set %s.%s = %s", repo.name, fieldName, value));
			} catch (GitBlitException e) {
				String msg = String.format("Failed to set %s.%s = %s", repo.name, fieldName, value);
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

	@CommandMetaData(name = "remove", aliases = { "rm" }, description = "Remove a repository")
	@UsageExample(syntax = "${cmd} myRepo.git", description = "Delete myRepo.git")
	public static class RemoveRepository extends RepositoryCommand {

		@Override
		public void run() throws UnloggedFailure {

			RepositoryModel repo = getRepository(true);
			
			if (!getContext().getClient().getUser().canAdmin(repo)) {
				throw new UnloggedFailure(1,  String.format("Sorry, you do not have permission to delete %s", repository));
			}

			IGitblit gitblit = getContext().getGitblit();
			if (gitblit.deleteRepositoryModel(repo)) {
				stdout.println(String.format("%s has been deleted.", repository));
			} else {
				throw new UnloggedFailure(1, String.format("Failed to delete %s!", repository));
			}
		}
	}

	@CommandMetaData(name = "show", description = "Show the details of a repository")
	@UsageExample(syntax = "${cmd} myRepo.git", description = "Display myRepo.git")
	public static class ShowRepository extends RepositoryCommand {

		@Override
		public void run() throws UnloggedFailure {

			RepositoryModel r = getRepository(true);

			if (!getContext().getClient().getUser().canAdmin(r)) {
				throw new UnloggedFailure(1,  String.format("Sorry, you do not have permission to see the %s settings.", repository));
			}

			IGitblit gitblit = getContext().getGitblit();

			// fields
			StringBuilder fb = new StringBuilder();
			fb.append("Description    : ").append(toString(r.description)).append('\n');
			fb.append("Origin         : ").append(toString(r.origin)).append('\n');
			fb.append("Default Branch : ").append(toString(r.HEAD)).append('\n');
			fb.append('\n');
			fb.append("GC Period    : ").append(r.gcPeriod).append('\n');
			fb.append("GC Threshold : ").append(r.gcThreshold).append('\n');
			fb.append('\n');
			fb.append("Accept Tickets   : ").append(toString(r.acceptNewTickets)).append('\n');
			fb.append("Accept Patchsets : ").append(toString(r.acceptNewPatchsets)).append('\n');
			fb.append("Require Approval : ").append(toString(r.requireApproval)).append('\n');
			fb.append("Merge To         : ").append(toString(r.mergeTo)).append('\n');
			fb.append('\n');
			fb.append("Incremental push tags    : ").append(toString(r.useIncrementalPushTags)).append('\n');
			fb.append("Show remote branches     : ").append(toString(r.showRemoteBranches)).append('\n');
			fb.append("Skip size calculations   : ").append(toString(r.skipSizeCalculation)).append('\n');
			fb.append("Skip summary metrics     : ").append(toString(r.skipSummaryMetrics)).append('\n');
			fb.append("Max activity commits     : ").append(r.maxActivityCommits).append('\n');
			fb.append("Author metric exclusions : ").append(toString(r.metricAuthorExclusions)).append('\n');
			fb.append("Commit Message Renderer  : ").append(r.commitMessageRenderer).append('\n');
			fb.append("Mailing Lists            : ").append(toString(r.mailingLists)).append('\n');
			fb.append('\n');
			fb.append("Access Restriction    : ").append(r.accessRestriction).append('\n');
			fb.append("Authorization Control : ").append(r.authorizationControl).append('\n');
			fb.append('\n');
			fb.append("Is Frozen        : ").append(toString(r.isFrozen)).append('\n');
			fb.append("Allow Forks      : ").append(toString(r.allowForks)).append('\n');
			fb.append("Verify Committer : ").append(toString(r.verifyCommitter)).append('\n');
			fb.append('\n');
			fb.append("Federation Strategy : ").append(r.federationStrategy).append('\n');
			fb.append("Federation Sets     : ").append(toString(r.federationSets)).append('\n');
			fb.append('\n');
			fb.append("Indexed Branches : ").append(toString(r.indexedBranches)).append('\n');
			fb.append('\n');
			fb.append("Pre-Receive Scripts  : ").append(toString(r.preReceiveScripts)).append('\n');
			fb.append("           inherited : ").append(toString(gitblit.getPreReceiveScriptsInherited(r))).append('\n');
			fb.append("Post-Receive Scripts : ").append(toString(r.postReceiveScripts)).append('\n');
			fb.append("           inherited : ").append(toString(gitblit.getPostReceiveScriptsInherited(r))).append('\n');
			String fields = fb.toString();

			// owners
			String owners;
			if (r.owners.isEmpty()) {
				owners = FlipTable.EMPTY;
			} else {
				String[] pheaders = { "Account", "Name" };
				Object [][] pdata = new Object[r.owners.size()][];
				for (int i = 0; i < r.owners.size(); i++) {
					String owner = r.owners.get(i);
					UserModel u = gitblit.getUserModel(owner);
					pdata[i] = new Object[] { owner, u == null ? "" : u.getDisplayName() };
				}
				owners = FlipTable.of(pheaders, pdata, Borders.COLS);
			}

			// team permissions
			List<RegistrantAccessPermission> tperms = gitblit.getTeamAccessPermissions(r);
			String tpermissions;
			if (tperms.isEmpty()) {
				tpermissions = FlipTable.EMPTY;
			} else {
				String[] pheaders = { "Team", "Permission", "Type" };
				Object [][] pdata = new Object[tperms.size()][];
				for (int i = 0; i < tperms.size(); i++) {
					RegistrantAccessPermission ap = tperms.get(i);
					pdata[i] = new Object[] { ap.registrant, ap.permission, ap.permissionType };
				}
				tpermissions = FlipTable.of(pheaders, pdata, Borders.COLS);
			}

			// user permissions
			List<RegistrantAccessPermission> uperms = gitblit.getUserAccessPermissions(r);
			String upermissions;
			if (uperms.isEmpty()) {
				upermissions = FlipTable.EMPTY;
			} else {
				String[] pheaders = { "Account", "Name", "Permission", "Type", "Source", "Mutable" };
				Object [][] pdata = new Object[uperms.size()][];
				for (int i = 0; i < uperms.size(); i++) {
					RegistrantAccessPermission ap = uperms.get(i);
					String name = "";
					try {
						String dn = gitblit.getUserModel(ap.registrant).displayName;
						if (dn != null) {
							name = dn;
						}
					} catch (Exception e) {
					}
					pdata[i] = new Object[] { ap.registrant, name, ap.permission, ap.permissionType, ap.source, ap.mutable ? "Y":"" };
				}
				upermissions = FlipTable.of(pheaders, pdata, Borders.COLS);
			}

			// assemble table
			String title = r.name;
			String [] headers = new String[] { title };
			String[][] data = new String[8][];
			data[0] = new String [] { "FIELDS" };
			data[1] = new String [] {fields };
			data[2] = new String [] { "OWNERS" };
			data[3] = new String [] { owners };
			data[4] = new String [] { "TEAM PERMISSIONS" };
			data[5] = new String [] { tpermissions };
			data[6] = new String [] { "USER PERMISSIONS" };
			data[7] = new String [] { upermissions };
			stdout.println(FlipTable.of(headers, data));
		}
		
		protected String toString(String val) {
			if (val == null) {
				return "";
			}
			return val;
		}
		
		protected String toString(Collection<?> collection) {
			if (collection == null) {
				return "";
			}
			return Joiner.on(", ").join(collection);
		}
		
		protected String toString(boolean val) {
			if (val) {
				return "Y";
			}
			return "";
		}

	}

	/* List repositories */
	@CommandMetaData(name = "list", aliases = { "ls" }, description = "List repositories")
	@UsageExample(syntax = "${cmd} mirror/.* -v", description = "Verbose list of all repositories in the 'mirror' directory")
	public static class ListRepositories extends ListFilterCommand<RepositoryModel> {

		@Override
		protected List<RepositoryModel> getItems() {
			IGitblit gitblit = getContext().getGitblit();
			UserModel user = getContext().getClient().getUser();
			List<RepositoryModel> repositories = gitblit.getRepositoryModels(user);
			return repositories;
		}

		@Override
		protected boolean matches(String filter, RepositoryModel r) {
			return r.name.matches(filter);
		}

		@Override
		protected void asTable(List<RepositoryModel> list) {
			String[] headers;
			if (verbose) {
				String[] h = { "Name", "Description", "Owners", "Last Modified", "Size" };
				headers = h;
			} else {
				String[] h = { "Name", "Last Modified", "Size" };
				headers = h;
			}

			Object[][] data = new Object[list.size()][];
			for (int i = 0; i < list.size(); i++) {
				RepositoryModel r = list.get(i);

				String lm = formatDate(r.lastChange);
				String size = r.size;
				if (!r.hasCommits) {
					lm = "";
					size = FlipTable.EMPTY;
				}
				if (verbose) {
					String owners = "";
					if (!ArrayUtils.isEmpty(r.owners)) {
						owners = Joiner.on(",").join(r.owners);
					}
					data[i] = new Object[] { r.name, r.description, owners, lm, size };
				} else {
					data[i] = new Object[] { r.name, lm, size };
				}
			}
			stdout.println(FlipTable.of(headers, data, Borders.BODY_HCOLS));
		}

		@Override
		protected void asTabbed(List<RepositoryModel> list) {
			if (verbose) {
				for (RepositoryModel r : list) {
					String lm = formatDate(r.lastChange);
					String owners = "";
					if (!ArrayUtils.isEmpty(r.owners)) {
						owners = Joiner.on(",").join(r.owners);
					}
					String size = r.size;
					if (!r.hasCommits) {
						lm = "";
						size = "(empty)";
					}

					outTabbed(r.name, r.description == null ? "" : r.description,
							owners, lm, size);
				}
			} else {
				for (RepositoryModel r : list) {
					outTabbed(r.name);
				}
			}
		}
	}
}