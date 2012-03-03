/*
 * Copyright 2012 gitblit.com.
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

import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.model.Model;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.GitBlit;
import com.gitblit.models.SearchResult;
import com.gitblit.utils.LuceneUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.LinkPanel;

public class LucenePage extends RootPage {

	public LucenePage() {
		super();
		setup(null);
	}

	public LucenePage(PageParameters params) {
		super(params);
		setup(params);
	}

	private void setup(PageParameters params) {
		setupPage("", "");
		final List<SearchResult> results = new ArrayList<SearchResult>();
		ListDataProvider<SearchResult> resultsDp = new ListDataProvider<SearchResult>(results);
		final DataView<SearchResult> resultsView = new DataView<SearchResult>("searchResults", resultsDp) {
			private static final long serialVersionUID = 1L;
			public void populateItem(final Item<SearchResult> item) {
				SearchResult sr = item.getModelObject();
				switch(sr.type) {
				case commit: {
					Label icon = WicketUtils.newIcon("type", "icon-refresh");
					WicketUtils.setHtmlTooltip(icon, "commit");
					item.add(icon);
					item.add(new LinkPanel("summary", null, sr.summary, CommitPage.class, WicketUtils.newObjectParameter(sr.repository, sr.id)));
					break;
				}
				case blob: {
					Label icon = WicketUtils.newIcon("type", "icon-file");
					WicketUtils.setHtmlTooltip(icon, "blob");
					item.add(icon);
					item.add(new LinkPanel("summary", null, sr.id, BlobPage.class, WicketUtils.newPathParameter(sr.repository, sr.branch, sr.id)));
					break;
				}
				case issue: {
					Label icon = WicketUtils.newIcon("type", "icon-file");
					WicketUtils.setHtmlTooltip(icon, "issue");
					item.add(icon);
					item.add(new Label("summary", "issue: " + sr.id));
					break;
				}
				}
				item.add(new LinkPanel("repository", null, sr.repository, SummaryPage.class, WicketUtils.newRepositoryParameter(sr.repository)));
				item.add(new LinkPanel("branch", "branch", StringUtils.getRelativePath(Constants.R_HEADS, sr.branch), LogPage.class, WicketUtils.newObjectParameter(sr.repository, sr.branch)));
				item.add(new Label("author", sr.author));
				item.add(WicketUtils.createTimestampLabel("date", sr.date, getTimeZone()));
			}
		};		
		
		final Model<String> fragment = new Model<String>();
		Form<Void> form = new Form<Void>("searchForm") {
			@Override
			public void onSubmit() {
				String f = fragment.getObject();
				List<Repository> repositories = new ArrayList<Repository>();
				for (String r : GitBlit.self().getRepositoryList()) {
					repositories.add(GitBlit.self().getRepository(r));
				}
				List<SearchResult> srs = LuceneUtils.search(f, 100, repositories.toArray(new Repository[repositories.size()]));
				results.clear();
				results.addAll(srs);
				for (Repository r : repositories) {
					r.close();
				}
				resultsView.setVisible(true);
			}
		};
		form.add(new TextField<String>("fragment", fragment));
		add(form);
		add(resultsView.setVisible(false));
	}
}
