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

import java.text.SimpleDateFormat;
import java.util.List;

import org.kohsuke.args4j.Option;

import com.gitblit.manager.IGitblit;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.transport.ssh.commands.CommandMetaData;
import com.gitblit.transport.ssh.commands.DispatchCommand;
import com.gitblit.transport.ssh.commands.SshCommand;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.FlipTable;
import com.gitblit.utils.FlipTable.Borders;
import com.google.common.base.Joiner;

@CommandMetaData(name = "repositories", aliases = { "repos" }, description = "Repository management commands")
public class RepositoriesDispatcher extends DispatchCommand {

	@Override
	protected void setup(UserModel user) {
		register(user, ListRepositories.class);
	}

	/* List repositories */
	@CommandMetaData(name = "list", aliases = { "ls" }, description = "List repositories")
	public static class ListRepositories extends SshCommand {

		@Option(name = "--verbose", aliases = { "-v" }, usage = "verbose")
		private boolean verbose;

		@Option(name = "--tabbed", aliases = { "-t" }, usage = "as tabbed output")
		private boolean tabbed;

		@Override
		public void run() {
			IGitblit gitblit = getContext().getGitblit();
			UserModel user = getContext().getClient().getUser();

			List<RepositoryModel> repositories = gitblit.getRepositoryModels(user);
			if (tabbed) {
				asTabbed(repositories);
			} else {
				asTable(repositories);
			}
		}

		protected void asTable(List<RepositoryModel> list) {
			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
			String[] headers;
			if (verbose) {
				String[] h = { "Name", "Description", "Owners", "Last Modified", "Size" };
				headers = h;
			} else {
				String[] h = { "Name", "Description" };
				headers = h;
			}

			String[][] data = new String[list.size()][];
			for (int i = 0; i < list.size(); i++) {
				RepositoryModel r = list.get(i);

				if (verbose) {
					String lm = df.format(r.lastChange);
					String owners = "";
					if (!ArrayUtils.isEmpty(r.owners)) {
						owners = Joiner.on(",").join(r.owners);
					}
					String size = r.size;
					if (!r.hasCommits) {
						size = "(empty)";
					}
					data[i] = new String[] { r.name, r.description, owners, lm, size };
				} else {
					data[i] = new String[] { r.name, r.description };
				}
			}
			stdout.println(FlipTable.of(headers, data, Borders.BODY_COLS));
		}

		protected void asTabbed(List<RepositoryModel> list) {
			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
			String pattern;
			if (verbose) {
				pattern = "%s\t%s\t%s\t%s\t%s";
			} else {
				pattern = "%s";
			}

			for (RepositoryModel r : list) {
				String lm = df.format(r.lastChange);
				String owners = "";
				if (!ArrayUtils.isEmpty(r.owners)) {
					owners = Joiner.on(",").join(r.owners);
				}
				String size = r.size;
				if (!r.hasCommits) {
					size = "(empty)";
				}

				stdout.println(String.format(pattern, r.name, r.description == null ? "" : r.description,
						owners, lm, size));
			}
		}
	}
}