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

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.markup.html.basic.Label;

import com.gitblit.models.RepositoryModel;
import com.gitblit.models.RepositoryUrl;
import com.gitblit.models.UserModel;
import com.gitblit.utils.GitBlitRequestUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.GitblitRedirectException;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.RepositoryUrlPanel;

public class EmptyRepositoryPage extends RepositoryPage {

	public EmptyRepositoryPage(PageParameters params) {
		super(params);

		setVersioned(false);

		String repositoryName = WicketUtils.getRepositoryName(params);
		RepositoryModel repository = app().repositories().getRepositoryModel(repositoryName);
		if (repository == null) {
			error(getString("gb.canNotLoadRepository") + " " + repositoryName, true);
		}

		if (repository.hasCommits) {
			// redirect to the summary page if this repository is not empty
			throw new GitblitRedirectException(SummaryPage.class, params);
		}

		UserModel user = GitBlitWebSession.get().getUser();
		if (user == null) {
			user = UserModel.ANONYMOUS;
		}

		HttpServletRequest req = GitBlitRequestUtils.getServletRequest();
		List<RepositoryUrl> repositoryUrls = app().services().getRepositoryUrls(req, user, repository);
		RepositoryUrl primaryUrl = repositoryUrls.size() == 0 ? null : repositoryUrls.get(0);
		String url = primaryUrl != null ? primaryUrl.url : "";

		String createSyntax = readResource("create_git.md").replace("${primaryUrl}", url);
		String existingSyntax = readResource("existing_git.md").replace("${primaryUrl}", url);

		add(new Label("repository", repositoryName));
		add(new RepositoryUrlPanel("pushurl", false, user, repository));
		add(new Label("createSyntax", createSyntax));
		add(new Label("existingSyntax", existingSyntax));
	}

	@Override
	protected String getPageName() {
		return getString("gb.summary");
	}
}
