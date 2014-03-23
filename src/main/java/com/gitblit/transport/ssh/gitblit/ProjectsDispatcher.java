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
import com.gitblit.models.ProjectModel;
import com.gitblit.models.UserModel;
import com.gitblit.transport.ssh.commands.CommandMetaData;
import com.gitblit.transport.ssh.commands.DispatchCommand;
import com.gitblit.transport.ssh.commands.ListCommand;
import com.gitblit.utils.FlipTable;
import com.gitblit.utils.FlipTable.Borders;

@CommandMetaData(name = "projects", description = "Project management commands")
public class ProjectsDispatcher extends DispatchCommand {

	@Override
	protected void setup(UserModel user) {
		register(user, ListProjects.class);
	}

	/* List projects */
	@CommandMetaData(name = "list", aliases= { "ls" }, description = "List projects")
	public static class ListProjects extends ListCommand<ProjectModel> {

		@Override
		protected List<ProjectModel> getItems() {
			IGitblit gitblit = getContext().getGitblit();
			UserModel user = getContext().getClient().getUser();

			List<ProjectModel> projects = gitblit.getProjectModels(user, false);
			return projects;
		}
		
		@Override
		protected boolean matches(ProjectModel p) {
			return p.name.matches(regexFilter);
		}

		@Override
		protected void asTable(List<ProjectModel> list) {
			String[] headers;
			if (verbose) {
				String[] h = { "Name", "Description", "Last Modified", "# Repos" };
				headers = h;
			} else {
				String[] h = { "Name", "Last Modified", "# Repos" };
				headers = h;
			}

			String[][] data = new String[list.size()][];
			for (int i = 0; i < list.size(); i++) {
				ProjectModel p = list.get(i);

				if (verbose) {
					data[i] = new String[] { p.name, p.description, formatDate(p.lastChange), "" + p.repositories.size() };
				} else {
					data[i] = new String[] { p.name, formatDate(p.lastChange), "" + p.repositories.size() };
				}
			}
			stdout.println(FlipTable.of(headers, data, Borders.BODY_HCOLS));
		}

		@Override
		protected void asTabbed(List<ProjectModel> list) {
			if (verbose) {
				for (ProjectModel project : list) {
					outTabbed(project.name,
							project.description == null ? "" : project.description,
									formatDate(project.lastChange));
				}
			} else {
				for (ProjectModel project : list) {
					outTabbed(project.name);
				}
			}
		}
	}
}