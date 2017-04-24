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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.ListMultipleChoice;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.model.Model;
import org.eclipse.jgit.lib.Constants;

import com.gitblit.Constants.SearchType;
import com.gitblit.Keys;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.SearchResult;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.SessionlessForm;
import com.gitblit.wicket.StringChoiceRenderer;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.LinkPanel;
import com.gitblit.wicket.panels.PagerPanel;

public class LuceneSearchPage extends RootPage {

	private final static String LUCENE_QUERY_SYNTAX_LINK = "https://lucene.apache.org/core/5_5_0/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package_description";

	public LuceneSearchPage() {
		super();
		setup(null);
	}

	public LuceneSearchPage(PageParameters params) {
		super(params);
		setup(params);
	}

	private void setup(PageParameters params) {
		setupPage("", "");

		// default values
		ArrayList<String> repositories = new ArrayList<String>();
		String query = "";
		boolean allRepos = false;

		int page = 1;
		int pageSize = app().settings().getInteger(Keys.web.itemsPerPage, 50);

		// display user-accessible selections
		UserModel user = GitBlitWebSession.get().getUser();
		List<String> availableRepositories = new ArrayList<String>();
		for (RepositoryModel model : app().repositories().getRepositoryModels(user)) {
			if (model.hasCommits && !ArrayUtils.isEmpty(model.indexedBranches)) {
				availableRepositories.add(model.name);
			}
		}

		if (params != null) {
			String repository = WicketUtils.getRepositoryName(params);
			if (!StringUtils.isEmpty(repository)) {
				repositories.add(repository);
			}

			page = WicketUtils.getPage(params);

			if (params.containsKey("repositories")) {
				String value = params.getString("repositories", "");
				List<String> list = StringUtils.getStringsFromValue(value);
				repositories.addAll(list);
			}

			allRepos = params.getAsBoolean("allrepos", false);
			if (allRepos) {
				repositories.addAll(availableRepositories);
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

		boolean luceneEnabled = app().settings().getBoolean(Keys.web.allowLuceneIndexing, true);
		if (luceneEnabled) {
			if (availableRepositories.size() == 0) {
				info(getString("gb.noIndexedRepositoriesWarning"));
			}
		} else {
			error(getString("gb.luceneDisabled"));
		}

		// enforce user-accessible repository selections
		Set<String> uniqueRepositories = new LinkedHashSet<String>();
		for (String selectedRepository : repositories) {
			if (availableRepositories.contains(selectedRepository)) {
				uniqueRepositories.add(selectedRepository);
			}
		}
		ArrayList<String> searchRepositories = new ArrayList<String>(uniqueRepositories);

		// search form
		final Model<String> queryModel = new Model<String>(query);
		final Model<ArrayList<String>> repositoriesModel = new Model<ArrayList<String>>(searchRepositories);
		final Model<Boolean> allreposModel = new Model<Boolean>(allRepos);
		SessionlessForm<Void> form = new SessionlessForm<Void>("searchForm", getClass()) {

			private static final long serialVersionUID = 1L;

			@Override
			public void onSubmit() {
				String q = queryModel.getObject();
				if (StringUtils.isEmpty(q)) {
					error(getString("gb.undefinedQueryWarning"));
					return;
				}
				if (repositoriesModel.getObject().size() == 0 && !allreposModel.getObject()) {
					error(getString("gb.noSelectedRepositoriesWarning"));
					return;
				}
				PageParameters params = new PageParameters();
				params.put("repositories", StringUtils.flattenStrings(repositoriesModel.getObject()));
				params.put("query", queryModel.getObject());
				params.put("allrepos", allreposModel.getObject());
				LuceneSearchPage page = new LuceneSearchPage(params);
				setResponsePage(page);
			}
		};

		ListMultipleChoice<String> selections = new ListMultipleChoice<String>("repositories",
				repositoriesModel, availableRepositories, new StringChoiceRenderer());
		selections.setMaxRows(8);
		form.add(selections.setEnabled(luceneEnabled));
		form.add(new TextField<String>("query", queryModel).setEnabled(luceneEnabled));
		form.add(new CheckBox("allrepos", allreposModel));
		form.add(new ExternalLink("querySyntax", LUCENE_QUERY_SYNTAX_LINK));
		add(form.setEnabled(luceneEnabled));

		// execute search
		final List<SearchResult> results = new ArrayList<SearchResult>();
		if (!ArrayUtils.isEmpty(searchRepositories) && !StringUtils.isEmpty(query)) {
			results.addAll(app().repositories().search(query, page, pageSize, searchRepositories));
		}

		// results header
		if (results.size() == 0) {
			if (!ArrayUtils.isEmpty(searchRepositories) && !StringUtils.isEmpty(query)) {
				add(new Label("resultsHeader", query).setRenderBodyOnly(true));
				add(new Label("resultsCount", getString("gb.noHits")).setRenderBodyOnly(true));
			} else {
				add(new Label("resultsHeader").setVisible(false));
				add(new Label("resultsCount").setVisible(false));
			}
		} else {
			add(new Label("resultsHeader", query).setRenderBodyOnly(true));
			add(new Label("resultsCount", MessageFormat.format(getString("gb.queryResults"),
					results.get(0).hitId, results.get(results.size() - 1).hitId, results.get(0).totalHits)).
					setRenderBodyOnly(true));
		}

		// search results view
		ListDataProvider<SearchResult> resultsDp = new ListDataProvider<SearchResult>(results);
		final DataView<SearchResult> resultsView = new DataView<SearchResult>("searchResults", resultsDp) {
			private static final long serialVersionUID = 1L;
			@Override
			public void populateItem(final Item<SearchResult> item) {
				final SearchResult sr = item.getModelObject();
				switch(sr.type) {
				case commit: {
					Label icon = WicketUtils.newIcon("type", "icon-refresh");
					WicketUtils.setHtmlTooltip(icon, "commit");
					item.add(icon);
					item.add(new LinkPanel("summary", null, sr.summary, CommitPage.class, WicketUtils.newObjectParameter(sr.repository, sr.commitId)));
					// show tags
					Fragment fragment = new Fragment("tags", "tagsPanel", LuceneSearchPage.this);
					List<String> tags = sr.tags;
					if (tags == null) {
						tags = new ArrayList<String>();
					}
					ListDataProvider<String> tagsDp = new ListDataProvider<String>(tags);
					final DataView<String> tagsView = new DataView<String>("tag", tagsDp) {
						private static final long serialVersionUID = 1L;
						@Override
						public void populateItem(final Item<String> item) {
							String tag = item.getModelObject();
							Component c = new LinkPanel("tagLink", null, tag, TagPage.class,
									WicketUtils.newObjectParameter(sr.repository, Constants.R_TAGS + tag));
							WicketUtils.setCssClass(c, "tagRef");
							item.add(c);
						}
					};
					fragment.add(tagsView);
					item.add(fragment);
					break;
				}
				case blob: {
					Label icon = WicketUtils.newIcon("type", "icon-file");
					WicketUtils.setHtmlTooltip(icon, "blob");
					item.add(icon);
					item.add(new LinkPanel("summary", null, sr.path, BlobPage.class, WicketUtils.newPathParameter(sr.repository, sr.branch, sr.path)));
					item.add(new Label("tags").setVisible(false));
					break;
				}
				}
				item.add(new Label("fragment", sr.fragment).setEscapeModelStrings(false).setVisible(!StringUtils.isEmpty(sr.fragment)));
				item.add(new LinkPanel("repository", null, sr.repository, SummaryPage.class, WicketUtils.newRepositoryParameter(sr.repository)));
				if (StringUtils.isEmpty(sr.branch)) {
					item.add(new Label("branch", "null"));
				} else {
					item.add(new LinkPanel("branch", "branch", StringUtils.getRelativePath(Constants.R_HEADS, sr.branch), LogPage.class, WicketUtils.newObjectParameter(sr.repository, sr.branch)));
				}
				item.add(new Label("author", sr.author));
				item.add(WicketUtils.createDatestampLabel("date", sr.date, getTimeZone(), getTimeUtils()));
			}
		};
		add(resultsView.setVisible(results.size() > 0));

		PageParameters pagerParams = new PageParameters();
		pagerParams.put("repositories", StringUtils.flattenStrings(repositoriesModel.getObject()));
		pagerParams.put("query", queryModel.getObject());

		boolean showPager = false;
		int totalPages = 0;
		if (results.size() > 0) {
			totalPages = (results.get(0).totalHits / pageSize) + (results.get(0).totalHits % pageSize > 0 ? 1 : 0);
			showPager = results.get(0).totalHits > pageSize;
		}

		add(new PagerPanel("topPager", page, totalPages, LuceneSearchPage.class, pagerParams).setVisible(showPager));
		add(new PagerPanel("bottomPager", page, totalPages, LuceneSearchPage.class, pagerParams).setVisible(showPager));
	}
}
