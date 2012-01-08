/*
 * Copyright 2011 gitblit.com.
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
package com.gitblit.wicket.pages;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.RepositoryUrlPanel;

public class EmptyRepositoryPage extends RootPage {

	public EmptyRepositoryPage(PageParameters params) {
		super(params);

		String repositoryName = WicketUtils.getRepositoryName(params);
		RepositoryModel repository = GitBlit.self().getRepositoryModel(repositoryName);
		setupPage(repositoryName, getString("gb.emptyRepository"));

		List<String> repositoryUrls = new ArrayList<String>();

		if (GitBlit.getBoolean(Keys.git.enableGitServlet, true)) {
			// add the Gitblit repository url
			repositoryUrls.add(getRepositoryUrl(repository));
		}
		repositoryUrls.addAll(GitBlit.self().getOtherCloneUrls(repositoryName));
		
		String primaryUrl = ArrayUtils.isEmpty(repositoryUrls) ? "" : repositoryUrls.get(0);
		add(new Label("repository", repositoryName));
		add(new RepositoryUrlPanel("pushurl", primaryUrl));
		add(new Label("cloneSyntax", MessageFormat.format("git clone {0}", repositoryUrls.get(0))));
		add(new Label("remoteSyntax", MessageFormat.format("git remote add gitblit {0}\ngit push gitblit master", primaryUrl)));
	}
}
