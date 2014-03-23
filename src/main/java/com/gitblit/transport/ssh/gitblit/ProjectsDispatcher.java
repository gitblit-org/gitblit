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
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.parboiled.common.StringUtils;

import com.gitblit.manager.IGitblit;
import com.gitblit.models.ProjectModel;
import com.gitblit.models.UserModel;
import com.gitblit.transport.ssh.commands.CommandMetaData;
import com.gitblit.transport.ssh.commands.DispatchCommand;
import com.gitblit.transport.ssh.commands.SshCommand;
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
	public static class ListProjects extends SshCommand {

		@Option(name = "--verbose", aliases = { "-v" }, usage = "verbose")
		private boolean verbose;

		@Option(name = "--tabbed", aliases = { "-t" }, usage = "as tabbed output")
		private boolean tabbed;

		@Argument(index = 0, metaVar = "REGEX", usage = "regex filter expression")
		protected String regexFilter;

		@Override
		public void run() {
			IGitblit gitblit = getContext().getGitblit();
			UserModel user = getContext().getClient().getUser();

			List<ProjectModel> projects = gitblit.getProjectModels(user, false);
			List<ProjectModel> filtered;
			if (StringUtils.isEmpty(regexFilter)) {
				// no regex filter 
				filtered = projects;
			} else {
				// regex filter the list
				filtered = new ArrayList<ProjectModel>();
				for (ProjectModel p : projects) {
					if (p.name.matches(regexFilter)) {
						filtered.add(p);
					}
				}
			}

			if (tabbed) {
				asTabbed(filtered);
			} else {
				asTable(filtered);
			}
		}

		protected void asTable(List<ProjectModel> list) {
			String[] headers;
			if (verbose) {
				String[] h = { "Name", "Description", "Last Modified", "# Repos" };
				headers = h;
			} else {
				String[] h = { "Name", "Last Modified", "# Repos" };
				headers = h;
			}

			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
			String[][] data = new String[list.size()][];
			for (int i = 0; i < list.size(); i++) {
				ProjectModel p = list.get(i);

				if (verbose) {
					data[i] = new String[] { p.name, p.description, df.format(p.lastChange), "" + p.repositories.size() };
				} else {
					data[i] = new String[] { p.name, df.format(p.lastChange), "" + p.repositories.size() };
				}
			}
			stdout.println(FlipTable.of(headers, data, Borders.BODY_HCOLS));
		}

		protected void asTabbed(List<ProjectModel> list) {
			String pattern;
			if (verbose) {
				pattern = "%s\t%s\t%s";
			} else {
				pattern = "%s";
			}

			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
			for (ProjectModel project : list) {
				stdout.println(String.format(pattern,
						project.name,
						project.description == null ? "" : project.description,
						df.format(project.lastChange)));
			}
		}
	}
}