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
import org.apache.wicket.markup.html.form.ListMultipleChoice;
import org.apache.wicket.markup.html.form.StatelessForm;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.model.Model;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.Constants.SearchType;
import com.gitblit.GitBlit;
import com.gitblit.models.SearchResult;
import com.gitblit.utils.ArrayUtils;
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
		
		// default values
		ArrayList<String> repositories = new ArrayList<String>();				
		String query = "";

		if (params != null) {
			String repository = WicketUtils.getRepositoryName(params);
			if (!StringUtils.isEmpty(repository)) {
				repositories.add(repository);
			}
			
			if (params.containsKey("repositories")) {
				String value = params.getString("repositories", "");
				List<String> list = StringUtils.getStringsFromValue(value);			
				repositories.addAll(list);
			}

			if (params.containsKey("query")) {
				query = params.getString("query", "");	
			} else {
				String value = WicketUtils.getSearchString(params);
				String type = WicketUtils.getSearchType(params);
				com.gitblit.Constants.SearchType searchType = com.gitblit.Constants.SearchType.forName(type);
				if (!StringUtils.isEmpty(value)) {
					if (searchType == SearchType.COMMIT) {
						query = "type:" + searchType.name().toLowerCase() + " AND \"" + value + "\"";	
					} else {
						query = searchType.name().toLowerCase() + ":\"" + value + "\"";
					}
				}
			}
		}
		
		// search form
		final Model<String> queryModel = new Model<String>(query);
		final Model<ArrayList<String>> repositoriesModel = new Model<ArrayList<String>>(repositories);
		StatelessForm<Void> form = new StatelessForm<Void>("searchForm") {
			
			private static final long serialVersionUID = 1L;

			@Override
			public void onSubmit() {
				String q = queryModel.getObject();
				if (StringUtils.isEmpty(q)) {
					error("Query is empty!");
					return;
				}				
				if (repositoriesModel.getObject().size() == 0) {
					error("Please select one or more repositories!");
					return;
				}
				PageParameters params = new PageParameters();
				params.put("repositories", StringUtils.flattenStrings(repositoriesModel.getObject()));
				params.put("query", queryModel.getObject());
				setResponsePage(LucenePage.class, params);
			}
		};
		ListMultipleChoice<String> selections = new ListMultipleChoice<String>("repositories", repositoriesModel, GitBlit.self().getRepositoryList());
		selections.setMaxRows(10);
		form.add(selections);
		form.add(new TextField<String>("query", queryModel));
		add(form);
				
		// execute search
		final List<SearchResult> results = new ArrayList<SearchResult>();
		results.addAll(search(repositories, query));
		
		// search results view
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
					item.add(new LinkPanel("summary", null, sr.summary, CommitPage.class, WicketUtils.newObjectParameter(sr.repository, sr.commitId)));
					break;
				}
				case blob: {
					Label icon = WicketUtils.newIcon("type", "icon-file");
					WicketUtils.setHtmlTooltip(icon, "blob");
					item.add(icon);
					item.add(new LinkPanel("summary", null, sr.path, BlobPage.class, WicketUtils.newPathParameter(sr.repository, sr.branch, sr.path)));
					break;
				}
				case issue: {
					Label icon = WicketUtils.newIcon("type", "icon-file");
					WicketUtils.setHtmlTooltip(icon, "issue");
					item.add(icon);
					item.add(new Label("summary", "issue: " + sr.issueId));
					break;
				}
				}
				item.add(new Label("fragment", sr.fragment).setEscapeModelStrings(false).setVisible(!StringUtils.isEmpty(sr.fragment)));
				item.add(new LinkPanel("repository", null, sr.repository, SummaryPage.class, WicketUtils.newRepositoryParameter(sr.repository)));
				item.add(new LinkPanel("branch", "branch", StringUtils.getRelativePath(Constants.R_HEADS, sr.branch), LogPage.class, WicketUtils.newObjectParameter(sr.repository, sr.branch)));
				item.add(new Label("author", sr.author));
				item.add(WicketUtils.createTimestampLabel("date", sr.date, getTimeZone()));
			}
		};
		add(resultsView.setVisible(results.size() > 0));
	}
	
	private List<SearchResult> search(List<String> repositories, String query) {
		if (ArrayUtils.isEmpty(repositories) || StringUtils.isEmpty(query)) {
			return new ArrayList<SearchResult>();
		}
		List<Repository> repos = new ArrayList<Repository>();
		for (String r : repositories) {
			repos.add(GitBlit.self().getRepository(r));
		}
		List<SearchResult> srs = LuceneUtils.search(query, 100, repos.toArray(new Repository[repos.size()]));
		for (Repository r : repos) {
			r.close();
		}
		return srs;
	}
}
