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

import com.gitblit.manager.IGitblit;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.transport.ssh.commands.CommandMetaData;
import com.gitblit.transport.ssh.commands.DispatchCommand;
import com.gitblit.transport.ssh.commands.ListRegexCommand;
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
	public static class ListRepositories extends ListRegexCommand<RepositoryModel> {

		@Override
		protected List<RepositoryModel> getItems() {
			IGitblit gitblit = getContext().getGitblit();
			UserModel user = getContext().getClient().getUser();
			List<RepositoryModel> repositories = gitblit.getRepositoryModels(user);
			return repositories;
		}
		
		@Override
		protected boolean matches(RepositoryModel r) {
			return r.name.matches(regexFilter);
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

			String[][] data = new String[list.size()][];
			for (int i = 0; i < list.size(); i++) {
				RepositoryModel r = list.get(i);

				String lm = formatDate(r.lastChange);
				String size = r.size;
				if (!r.hasCommits) {
					lm = "";
					size = "(empty)";
				}
				if (verbose) {
					String owners = "";
					if (!ArrayUtils.isEmpty(r.owners)) {
						owners = Joiner.on(",").join(r.owners);
					}
					data[i] = new String[] { r.name, r.description, owners, lm, size };
				} else {
					data[i] = new String[] { r.name, lm, size };
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