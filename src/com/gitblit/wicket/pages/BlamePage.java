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

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.CommitHeaderPanel;
import com.gitblit.wicket.panels.LinkPanel;
import com.gitblit.wicket.panels.PathBreadcrumbsPanel;

public class BlamePage extends RepositoryPage {

	public BlamePage(PageParameters params) {
		super(params);

		final String blobPath = WicketUtils.getPath(params);

		RevCommit commit = getCommit();

		add(new BookmarkablePageLink<Void>("blobLink", BlobPage.class,
				WicketUtils.newPathParameter(repositoryName, objectId,
							blobPath)));
		add(new BookmarkablePageLink<Void>("commitLink", CommitPage.class,
				WicketUtils.newObjectParameter(repositoryName, objectId)));
		add(new BookmarkablePageLink<Void>("commitDiffLink", CommitDiffPage.class,
				WicketUtils.newObjectParameter(repositoryName, objectId)));

		// blame page links
		add(new BookmarkablePageLink<Void>("headLink", BlamePage.class,
				WicketUtils.newPathParameter(repositoryName, Constants.HEAD, blobPath)));
		add(new BookmarkablePageLink<Void>("historyLink", HistoryPage.class,
				WicketUtils.newPathParameter(repositoryName, objectId, blobPath)));

		add(new CommitHeaderPanel("commitHeader", repositoryName, commit));

		add(new PathBreadcrumbsPanel("breadcrumbs", repositoryName, blobPath, objectId));

		List<BlameLine> blame = Arrays.asList(new BlameLine("HEAD", "1", "Under Construction"));
		ListDataProvider<BlameLine> blameDp = new ListDataProvider<BlameLine>(blame);
		DataView<BlameLine> blameView = new DataView<BlameLine>("annotation", blameDp) {
			private static final long serialVersionUID = 1L;

			public void populateItem(final Item<BlameLine> item) {
				BlameLine entry = item.getModelObject();
				item.add(new LinkPanel("commit", "list", entry.objectId, CommitPage.class,
						newCommitParameter(entry.objectId)));
				item.add(new Label("line", entry.line));
				item.add(new Label("data", entry.data));
			}
		};
		add(blameView);
	}

	@Override
	protected String getPageName() {
		return getString("gb.blame");
	}
	
	private class BlameLine implements Serializable {
		
		private static final long serialVersionUID = 1L;
		
		final String objectId;
		final String line;
		final String data;
		BlameLine(String objectId, String line, String data) {
			this.objectId = objectId;
			this.line = line;
			this.data = data;
		}
	}
}
