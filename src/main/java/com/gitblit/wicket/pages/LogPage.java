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

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;

import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.CacheControl;
import com.gitblit.wicket.CacheControl.LastModified;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.LogPanel;

@CacheControl(LastModified.REPOSITORY)
public class LogPage extends RepositoryPage {

	public LogPage(PageParameters params) {
		super(params);

		addSyndicationDiscoveryLink();

		int pageNumber = WicketUtils.getPage(params);
		int prevPage = Math.max(0, pageNumber - 1);
		int nextPage = pageNumber + 1;
		String refid = objectId;
		if (StringUtils.isEmpty(refid)) {
			refid = getRepositoryModel().HEAD;
		}
		LogPanel logPanel = new LogPanel("logPanel", repositoryName, refid, getRepository(), -1,
				pageNumber - 1, getRepositoryModel().showRemoteBranches);
		boolean hasMore = logPanel.hasMore();
		add(logPanel);

		add(new BookmarkablePageLink<Void>("firstPageTop", LogPage.class,
				WicketUtils.newObjectParameter(repositoryName, objectId))
				.setEnabled(pageNumber > 1));
		add(new BookmarkablePageLink<Void>("prevPageTop", LogPage.class,
				WicketUtils.newLogPageParameter(repositoryName, objectId, prevPage))
				.setEnabled(pageNumber > 1));
		add(new BookmarkablePageLink<Void>("nextPageTop", LogPage.class,
				WicketUtils.newLogPageParameter(repositoryName, objectId, nextPage))
				.setEnabled(hasMore));

		add(new BookmarkablePageLink<Void>("firstPageBottom", LogPage.class,
				WicketUtils.newObjectParameter(repositoryName, objectId))
				.setEnabled(pageNumber > 1));
		add(new BookmarkablePageLink<Void>("prevPageBottom", LogPage.class,
				WicketUtils.newLogPageParameter(repositoryName, objectId, prevPage))
				.setEnabled(pageNumber > 1));
		add(new BookmarkablePageLink<Void>("nextPageBottom", LogPage.class,
				WicketUtils.newLogPageParameter(repositoryName, objectId, nextPage))
				.setEnabled(hasMore));
	}

	@Override
	protected String getPageName() {
		return getString("gb.log");
	}

	@Override
	protected boolean isCommitPage() {
		return true;
	}

}
