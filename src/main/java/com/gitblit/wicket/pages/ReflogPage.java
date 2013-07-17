/*
 * Copyright 2013 gitblit.com.
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

import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.ReflogPanel;

public class ReflogPage extends RepositoryPage {

	public ReflogPage(PageParameters params) {
		super(params);

		addSyndicationDiscoveryLink();

		int pageNumber = WicketUtils.getPage(params);
		int prevPage = Math.max(0, pageNumber - 1);
		int nextPage = pageNumber + 1;

		ReflogPanel reflogPanel = new ReflogPanel("reflogPanel", getRepositoryModel(), getRepository(), -1,
				pageNumber - 1);
		boolean hasMore = reflogPanel.hasMore();
		add(reflogPanel);

		add(new BookmarkablePageLink<Void>("firstPage", ReflogPage.class,
				WicketUtils.newObjectParameter(repositoryName, objectId))
				.setEnabled(pageNumber > 1));
		add(new BookmarkablePageLink<Void>("prevPage", ReflogPage.class,
				WicketUtils.newLogPageParameter(repositoryName, objectId, prevPage))
				.setEnabled(pageNumber > 1));
		add(new BookmarkablePageLink<Void>("nextPage", ReflogPage.class,
				WicketUtils.newLogPageParameter(repositoryName, objectId, nextPage))
				.setEnabled(hasMore));
	}

	@Override
	protected String getPageName() {
		return getString("gb.reflog");
	}
}
