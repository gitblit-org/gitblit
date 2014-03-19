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
import java.text.SimpleDateFormat;
import java.util.List;

import org.kohsuke.args4j.Option;
import org.parboiled.common.StringUtils;

import com.gitblit.manager.IGitblit;
import com.gitblit.models.ProjectModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.transport.ssh.commands.CommandMetaData;
import com.gitblit.transport.ssh.commands.DispatchCommand;
import com.gitblit.transport.ssh.commands.SshCommand;

/**
 * The dispatcher and it's commands for Gitblit object listing.
 *
 * @author James Moger
 *
 */
@CommandMetaData(name = "list", aliases = { "ls" }, description = "Gitblit object list commands")
public class ListDispatcher extends DispatchCommand {

	@Override
	protected void registerCommands(UserModel user) {
		registerCommand(user, ListRepositories.class);
		registerCommand(user, ListProjects.class);
		registerCommand(user, ListUsers.class);
		registerCommand(user, ListKeys.class);
	}

	/* List SSH public keys */
	@CommandMetaData(name = "keys",  description = "List your public keys")
	public static class ListKeys extends KeysDispatcher.ListKeys {
	}

	/* List repositories */
	@CommandMetaData(name = "repositories", aliases = { "repos" }, description = "List repositories")
	public static class ListRepositories extends SshCommand {

		@Option(name = "--verbose", aliases = { "-v" }, usage = "verbose")
		private boolean verbose;

		@Override
		public void run() {
			IGitblit gitblit = getContext().getGitblit();
			UserModel user = getContext().getClient().getUser();
			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");

			List<RepositoryModel> repositories = gitblit.getRepositoryModels(user);
			int nameLen = 0;
			int descLen = 0;
			for (RepositoryModel repo : repositories) {
				int len = repo.name.length();
				if (len > nameLen) {
					nameLen = len;
				}
				if (!StringUtils.isEmpty(repo.description)) {
					len = repo.description.length();
					if (len > descLen) {
						descLen = len;
					}
				}
			}

			String pattern;
			if (verbose) {
				pattern = MessageFormat.format("%-{0,number,0}s\t%-{1,number,0}s\t%s", nameLen, descLen);
			} else {
				pattern = "%s";
			}

			for (RepositoryModel repo : repositories) {
				stdout.println(String.format(pattern,
						repo.name,
						repo.description == null ? "" : repo.description,
						df.format(repo.lastChange)));
			}
		}
	}

	/* List projects */
	@CommandMetaData(name = "projects", description = "List projects")
	public static class ListProjects extends SshCommand {

		@Option(name = "--verbose", aliases = { "-v" }, usage = "verbose")
		private boolean verbose;

		@Override
		public void run() {
			IGitblit gitblit = getContext().getGitblit();
			UserModel user = getContext().getClient().getUser();
			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");

			List<ProjectModel> projects = gitblit.getProjectModels(user, false);
			int nameLen = 0;
			int descLen = 0;
			for (ProjectModel project : projects) {
				int len = project.name.length();
				if (len > nameLen) {
					nameLen = len;
				}
				if (!StringUtils.isEmpty(project.description)) {
					len = project.description.length();
					if (len > descLen) {
						descLen = len;
					}
				}
			}

			String pattern;
			if (verbose) {
				pattern = MessageFormat.format("%-{0,number,0}s\t%-{1,number,0}s\t%s", nameLen, descLen);
			} else {
				pattern = "%s";
			}

			for (ProjectModel project : projects) {
				stdout.println(String.format(pattern,
						project.name,
						project.description == null ? "" : project.description,
						df.format(project.lastChange)));
			}
		}
	}

	/* List users */
	@CommandMetaData(name = "users", description = "List users", admin = true)
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
