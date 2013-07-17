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

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;

import com.gitblit.GitBlit;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.GitblitRedirectException;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.RepositoryUrlPanel;

public class EmptyRepositoryPage extends RootPage {

	public EmptyRepositoryPage(PageParameters params) {
		super(params);

		setVersioned(false);

		String repositoryName = WicketUtils.getRepositoryName(params);
		RepositoryModel repository = GitBlit.self().getRepositoryModel(repositoryName);
		if (repository == null) {
			error(getString("gb.canNotLoadRepository") + " " + repositoryName, true);
		}
		
		if (repository.hasCommits) {
			// redirect to the summary page if this repository is not empty
			throw new GitblitRedirectException(SummaryPage.class, params);
		}
		
		setupPage(repositoryName, getString("gb.emptyRepository"));

		UserModel user = GitBlitWebSession.get().getUser();
		if (user == null) {
			user = UserModel.ANONYMOUS;
		}
		
		RepositoryUrlPanel urlPanel = new RepositoryUrlPanel("pushurl", false, user, repository);
		String primaryUrl = urlPanel.getPrimaryUrl();
		
		add(new Label("repository", repositoryName));
		add(urlPanel);
		add(new Label("cloneSyntax", MessageFormat.format("git clone {0}", primaryUrl)));
		add(new Label("remoteSyntax", MessageFormat.format("git remote add gitblit {0}\ngit push gitblit master", primaryUrl)));
	}
	
	@Override
	protected Class<? extends BasePage> getRootNavPageClass() {
		return RepositoriesPage.class;
	}
}
