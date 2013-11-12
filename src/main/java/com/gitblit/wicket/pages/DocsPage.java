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

import java.util.Arrays;
import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.GitBlit;
import com.gitblit.models.PathModel;
import com.gitblit.utils.ByteFormat;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.CacheControl;
import com.gitblit.wicket.CacheControl.LastModified;
import com.gitblit.wicket.MarkupProcessor;
import com.gitblit.wicket.MarkupProcessor.MarkupDocument;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.LinkPanel;

@CacheControl(LastModified.REPOSITORY)
public class DocsPage extends RepositoryPage {

	public DocsPage(PageParameters params) {
		super(params);

		MarkupProcessor processor = new MarkupProcessor(GitBlit.getSettings());

		Repository r = getRepository();
		RevCommit head = JGitUtils.getCommit(r, null);
		List<String> extensions = processor.getMarkupExtensions();
		List<PathModel> paths = JGitUtils.getDocuments(r, extensions);

		String doc = null;
		String markup = null;
		String html = null;

		List<String> roots = Arrays.asList("home");

		// try to find a custom index/root page
		for (PathModel path : paths) {
			String name = path.name.toLowerCase();
			name = StringUtils.stripFileExtension(name);
			if (roots.contains(name)) {
				doc = path.name;
				break;
			}
		}

		if (!StringUtils.isEmpty(doc)) {
			// load the document
			String [] encodings = GitBlit.getEncodings();
			markup = JGitUtils.getStringContent(r, head.getTree(), doc, encodings);

			// parse document
			MarkupDocument markupDoc = processor.parse(repositoryName, getBestCommitId(head), doc, markup);
			html = markupDoc.html;
		}

		Fragment fragment = null;
		if (StringUtils.isEmpty(html)) {
			// no custom index/root, use the standard document list
			fragment = new Fragment("docs", "noIndexFragment", this);
			fragment.add(new Label("header", getString("gb.docs")));
		} else {
			// custom index/root, use tabbed ui of index/root and document list
			fragment = new Fragment("docs", "indexFragment", this);
			Component content = new Label("index", html).setEscapeModelStrings(false);
			fragment.add(content);
		}

		// document list
		final String id = getBestCommitId(head);
		final ByteFormat byteFormat = new ByteFormat();
		Fragment docs = new Fragment("documents", "documentsFragment", this);
		ListDataProvider<PathModel> pathsDp = new ListDataProvider<PathModel>(paths);
		DataView<PathModel> pathsView = new DataView<PathModel>("document", pathsDp) {
			private static final long serialVersionUID = 1L;
			int counter;

			@Override
			public void populateItem(final Item<PathModel> item) {
				PathModel entry = item.getModelObject();
				item.add(WicketUtils.newImage("docIcon", "file_world_16x16.png"));
				item.add(new Label("docSize", byteFormat.format(entry.size)));
				item.add(new LinkPanel("docName", "list", entry.name, DocPage.class, WicketUtils
						.newPathParameter(repositoryName, id, entry.path)));

				// links
				item.add(new BookmarkablePageLink<Void>("view", DocPage.class, WicketUtils
						.newPathParameter(repositoryName, id, entry.path)));
				item.add(new BookmarkablePageLink<Void>("raw", RawPage.class, WicketUtils
						.newPathParameter(repositoryName, id, entry.path)));
				item.add(new BookmarkablePageLink<Void>("blame", BlamePage.class, WicketUtils
						.newPathParameter(repositoryName, id, entry.path)));
				item.add(new BookmarkablePageLink<Void>("history", HistoryPage.class, WicketUtils
						.newPathParameter(repositoryName, id, entry.path)));
				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		docs.add(pathsView);
		fragment.add(docs);
		add(fragment);
	}

	@Override
	protected String getPageName() {
		return getString("gb.docs");
	}
}
