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
package com.gitblit.wicket.pages;

import org.apache.wicket.PageParameters;

public class NoDocsPage extends RepositoryPage {

	public NoDocsPage(PageParameters params) {
		super(params);

		//UserModel user = GitBlitWebSession.get().getUser();
		//boolean isAuthenticated = user != null && user.isAuthenticated;
		//add(new BookmarkablePageLink<Void>("newreadme", NewTicketPage.class, WicketUtils.newRepositoryParameter(repositoryName)).setVisible(isAuthenticated));
	}

	@Override
	protected String getPageName() {
		return getString("gb.docs");
	}

	@Override
	protected boolean isCommitPage() {
		return true;
	}

	@Override
	protected Class<? extends BasePage> getRepoNavPageClass() {
		return DocsPage.class;
	}
}
