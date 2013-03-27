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

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.models.PathModel;
import com.gitblit.utils.ByteFormat;
import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.LinkPanel;

public class DocsPage extends RepositoryPage {

	public DocsPage(PageParameters params) {
		super(params);

		Repository r = getRepository();
		List<String> extensions = GitBlit.getStrings(Keys.web.markdownExtensions);
		List<PathModel> paths = JGitUtils.getDocuments(r, extensions);

		final ByteFormat byteFormat = new ByteFormat();

		add(new Label("header", getString("gb.docs")));

		// documents list
		ListDataProvider<PathModel> pathsDp = new ListDataProvider<PathModel>(paths);
		DataView<PathModel> pathsView = new DataView<PathModel>("document", pathsDp) {
			private static final long serialVersionUID = 1L;
			int counter;

			public void populateItem(final Item<PathModel> item) {
				PathModel entry = item.getModelObject();
				item.add(WicketUtils.newImage("docIcon", "file_world_16x16.png"));
				item.add(new Label("docSize", byteFormat.format(entry.size)));
				item.add(new LinkPanel("docName", "list", entry.name, BlobPage.class, WicketUtils
						.newPathParameter(repositoryName, entry.commitId, entry.path)));

				// links
				item.add(new BookmarkablePageLink<Void>("view", BlobPage.class, WicketUtils
						.newPathParameter(repositoryName, entry.commitId, entry.path)));
				item.add(new BookmarkablePageLink<Void>("raw", RawPage.class, WicketUtils
						.newPathParameter(repositoryName, entry.commitId, entry.path)));
				item.add(new BookmarkablePageLink<Void>("blame", BlamePage.class, WicketUtils
						.newPathParameter(repositoryName, entry.commitId, entry.path)));
				item.add(new BookmarkablePageLink<Void>("history", HistoryPage.class, WicketUtils
						.newPathParameter(repositoryName, entry.commitId, entry.path)));
				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(pathsView);
	}

	@Override
	protected String getPageName() {
		return getString("gb.docs");
	}
}
