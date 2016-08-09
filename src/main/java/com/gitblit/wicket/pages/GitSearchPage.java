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

import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;

import com.gitblit.Constants;
import com.gitblit.wicket.CacheControl;
import com.gitblit.wicket.CacheControl.LastModified;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.SearchPanel;

@CacheControl(LastModified.REPOSITORY)
public class GitSearchPage extends RepositoryPage {

	public GitSearchPage(PageParameters params) {
		super(params);

		String value = WicketUtils.getSearchString(params);
		String type = WicketUtils.getSearchType(params);
		Constants.SearchType searchType = Constants.SearchType.forName(type);

		int pageNumber = WicketUtils.getPage(params);
		int prevPage = Math.max(0, pageNumber - 1);
		int nextPage = pageNumber + 1;

		SearchPanel search = new SearchPanel("searchPanel", repositoryName, objectId, value,
				searchType, getRepository(), -1, pageNumber - 1, getRepositoryModel().showRemoteBranches);
		boolean hasMore = search.hasMore();
		add(search);

		add(new BookmarkablePageLink<Void>("firstPageTop", GitSearchPage.class,
				WicketUtils.newSearchParameter(repositoryName, objectId, value, searchType))
				.setEnabled(pageNumber > 1));
		add(new BookmarkablePageLink<Void>("prevPageTop", GitSearchPage.class,
				WicketUtils.newSearchParameter(repositoryName, objectId, value, searchType,
						prevPage)).setEnabled(pageNumber > 1));
		add(new BookmarkablePageLink<Void>("nextPageTop", GitSearchPage.class,
				WicketUtils.newSearchParameter(repositoryName, objectId, value, searchType,
						nextPage)).setEnabled(hasMore));

		add(new BookmarkablePageLink<Void>("firstPageBottom", GitSearchPage.class,
				WicketUtils.newSearchParameter(repositoryName, objectId, value, searchType))
				.setEnabled(pageNumber > 1));
		add(new BookmarkablePageLink<Void>("prevPageBottom", GitSearchPage.class,
				WicketUtils.newSearchParameter(repositoryName, objectId, value, searchType,
						prevPage)).setEnabled(pageNumber > 1));
		add(new BookmarkablePageLink<Void>("nextPageBottom", GitSearchPage.class,
				WicketUtils.newSearchParameter(repositoryName, objectId, value, searchType,
						nextPage)).setEnabled(hasMore));

	}

	@Override
	protected String getPageName() {
		return getString("gb.search");
	}

	@Override
	protected Class<? extends BasePage> getRepoNavPageClass() {
		return LogPage.class;
	}
}
