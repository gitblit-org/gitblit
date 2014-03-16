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

package com.gitblit.transport.ssh.commands;

import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.List;

import org.kohsuke.args4j.Option;
import org.parboiled.common.StringUtils;

import com.gitblit.manager.IGitblit;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.transport.ssh.CommandMetaData;

@CommandMetaData(name = "repositories", description = "List the available repositories")
public class ListRepositoriesCommand extends SshCommand {

	@Option(name = "--verbose", aliases = { "-v" }, usage = "verbose")
	private boolean verbose;

	@Override
	public void run() {
		IGitblit gitblit = ctx.getGitblit();
		UserModel user = ctx.getClient().getUser();
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
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");

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
