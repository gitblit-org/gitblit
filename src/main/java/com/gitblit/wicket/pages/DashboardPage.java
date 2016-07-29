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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Fragment;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.Keys;
import com.gitblit.models.DailyLogEntry;
import com.gitblit.models.Menu.ParameterMenuItem;
import com.gitblit.models.NavLink.DropDownPageMenuNavLink;
import com.gitblit.models.Metric;
import com.gitblit.models.NavLink;
import com.gitblit.models.RefLogEntry;
import com.gitblit.models.RepositoryCommit;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.RefLogUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebApp;
import com.gitblit.wicket.charting.Chart;
import com.gitblit.wicket.charting.Charts;
import com.gitblit.wicket.charting.Flotr2Charts;
import com.gitblit.wicket.panels.DigestsPanel;
import com.gitblit.wicket.panels.LinkPanel;

public abstract class DashboardPage extends RootPage {

	public DashboardPage() {
		super();
	}

	public DashboardPage(PageParameters params) {
		super(params);
	}

	@Override
	protected boolean reusePageParameters() {
		return true;
	}

	protected void addActivity(UserModel user, Collection<RepositoryModel> repositories, String feedTitle, int daysBack) {
		Calendar c = Calendar.getInstance();
		c.add(Calendar.DATE, -1*daysBack);
		Date minimumDate = c.getTime();
		TimeZone timezone = getTimeZone();

		// create daily commit digest feed
		List<DailyLogEntry> digests = new ArrayList<DailyLogEntry>();
		for (RepositoryModel model : repositories) {
			if (model.isCollectingGarbage) {
				continue;
			}
			if (model.hasCommits && model.lastChange.after(minimumDate)) {
				Repository repository = app().repositories().getRepository(model.name);
				List<DailyLogEntry> entries = RefLogUtils.getDailyLogByRef(model.name, repository, minimumDate, timezone);
				digests.addAll(entries);
				repository.close();
			}
		}

		Fragment activityFragment = new Fragment("activity", "activityFragment", this);
		add(activityFragment);
		activityFragment.add(new Label("feedTitle", feedTitle));
		if (digests.size() == 0) {
			// quiet or no starred repositories
			if (repositories.size() == 0) {
				if (UserModel.ANONYMOUS.equals(user)) {
					if (daysBack == 1) {
						activityFragment.add(new Label("digests", getString("gb.noActivityToday")));
					} else {
						activityFragment.add(new Label("digests", MessageFormat.format(getString("gb.noActivity"), daysBack)));
					}
				} else {
					activityFragment.add(new LinkPanel("digests", null, getString("gb.findSomeRepositories"), RepositoriesPage.class));
				}
			} else {
				if (daysBack == 1) {
					activityFragment.add(new Label("digests", getString("gb.noActivityToday")));
				} else {
					activityFragment.add(new Label("digests", MessageFormat.format(getString("gb.noActivity"), daysBack)));
				}
			}
		} else {
			// show daily commit digest feed
			Collections.sort(digests);
			DigestsPanel digestsPanel = new DigestsPanel("digests", digests);
			activityFragment.add(digestsPanel);
		}

		// add the nifty charts
		if (!ArrayUtils.isEmpty(digests)) {
			// aggregate author exclusions
			Set<String> authorExclusions = new TreeSet<String>();
			for (String author : app().settings().getStrings(Keys.web.metricAuthorExclusions)) {
				authorExclusions.add(author.toLowerCase());
			}
			for (RepositoryModel model : repositories) {
				if (!ArrayUtils.isEmpty(model.metricAuthorExclusions)) {
					for (String author : model.metricAuthorExclusions) {
						authorExclusions.add(author.toLowerCase());
					}
				}
			}

			addCharts(activityFragment, digests, authorExclusions, daysBack);
		} else {
			activityFragment.add(new Label("charts").setVisible(false));
			activityFragment.add(new Label("feedheader").setVisible(false));
		}
	}

	@Override
	protected void addDropDownMenus(List<NavLink> navLinks) {
		PageParameters params = getPageParameters();

		DropDownPageMenuNavLink menu = new DropDownPageMenuNavLink("gb.filters",
				GitBlitWebApp.get().getHomePage());

		// preserve repository filter option on time choices
		menu.menuItems.addAll(getTimeFilterItems(params));

		if (menu.menuItems.size() > 0) {
			// Reset Filter
			menu.menuItems.add(new ParameterMenuItem(getString("gb.reset")));
		}

		navLinks.add(menu);
	}


	/**
	 * Creates the daily activity line chart, the active repositories pie chart,
	 * and the active authors pie chart
	 *
	 * @param recentChanges
	 * @param authorExclusions
	 * @param daysBack
	 */
	protected void addCharts(Fragment frag, List<DailyLogEntry> recentChanges, Set<String> authorExclusions, int daysBack) {
		// activity metrics
		Map<String, Metric> repositoryMetrics = new HashMap<String, Metric>();
		Map<String, Metric> authorMetrics = new HashMap<String, Metric>();

		// aggregate repository and author metrics
		int totalCommits = 0;
		for (RefLogEntry change : recentChanges) {

			// aggregate repository metrics
			String repository = StringUtils.stripDotGit(change.repository);
			if (!repositoryMetrics.containsKey(repository)) {
				repositoryMetrics.put(repository, new Metric(repository));
			}
			repositoryMetrics.get(repository).count += 1;

			for (RepositoryCommit commit : change.getCommits()) {
				totalCommits++;
				String author = StringUtils.removeNewlines(commit.getAuthorIdent().getName());
				String authorName = author.toLowerCase();
				String authorEmail = StringUtils.removeNewlines(commit.getAuthorIdent().getEmailAddress()).toLowerCase();
				if (!authorExclusions.contains(authorName) && !authorExclusions.contains(authorEmail)) {
					if (!authorMetrics.containsKey(author)) {
						authorMetrics.put(author, new Metric(author));
					}
					authorMetrics.get(author).count += 1;
				}
			}
		}

		String headerPattern;
		if (daysBack == 1) {
			// today
			if (totalCommits == 0) {
				headerPattern = getString("gb.todaysActivityNone");
			} else {
				headerPattern = getString("gb.todaysActivityStats");
			}
		} else {
			// multiple days
			if (totalCommits == 0) {
				headerPattern = getString("gb.recentActivityNone");
			} else {
				headerPattern = getString("gb.recentActivityStats");
			}
		}
		frag.add(new Label("feedheader", MessageFormat.format(headerPattern,
				daysBack, totalCommits, authorMetrics.size())));

		if (app().settings().getBoolean(Keys.web.generateActivityGraph, true)) {
			// build google charts
			Charts charts = new Flotr2Charts();

			// active repositories pie chart
			Chart chart = charts.createPieChart("chartRepositories", getString("gb.activeRepositories"),
					getString("gb.repository"), getString("gb.commits"));
			for (Metric metric : repositoryMetrics.values()) {
				chart.addValue(metric.name, metric.count);
			}
			chart.setShowLegend(false);
			String url = urlFor(SummaryPage.class, null).toString() + "?r=";
			chart.setClickUrl(url);
			charts.addChart(chart);

			// active authors pie chart
			chart = charts.createPieChart("chartAuthors", getString("gb.activeAuthors"),
					getString("gb.author"), getString("gb.commits"));
			for (Metric metric : authorMetrics.values()) {
				chart.addValue(metric.name, metric.count);
			}
			chart.setShowLegend(false);
			charts.addChart(chart);

			add(charts);
			frag.add(new Fragment("charts", "chartsFragment", this));
		} else {
			frag.add(new Label("charts").setVisible(false));
		}
	}
}
