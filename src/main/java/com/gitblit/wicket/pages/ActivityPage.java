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

import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Fragment;

import com.gitblit.Keys;
import com.gitblit.models.Activity;
import com.gitblit.models.Menu.ParameterMenuItem;
import com.gitblit.models.NavLink.DropDownPageMenuNavLink;
import com.gitblit.models.Metric;
import com.gitblit.models.NavLink;
import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.ActivityUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.CacheControl;
import com.gitblit.wicket.CacheControl.LastModified;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.charting.Chart;
import com.gitblit.wicket.charting.Charts;
import com.gitblit.wicket.charting.Flotr2Charts;
import com.gitblit.wicket.panels.ActivityPanel;

/**
 * Activity Page shows a list of recent commits across all visible Gitblit
 * repositories.
 *
 * @author James Moger
 *
 */

@CacheControl(LastModified.ACTIVITY)
public class ActivityPage extends RootPage {

	public ActivityPage(PageParameters params) {
		super(params);
		setupPage("", "");

		// parameters
		int daysBack = WicketUtils.getDaysBack(params);
		if (daysBack < 1) {
			daysBack = app().settings().getInteger(Keys.web.activityDuration, 7);
		}
		String objectId = WicketUtils.getObject(params);

		// determine repositories to view and retrieve the activity
		List<RepositoryModel> models = getRepositories(params);
		List<Activity> recentActivity = ActivityUtils.getRecentActivity(
				app().settings(),
				app().repositories(),
				models,
				daysBack,
				objectId,
				getTimeZone());

		String headerPattern;
		if (daysBack == 1) {
			// today
			if (recentActivity.size() == 0) {
				headerPattern = getString("gb.todaysActivityNone");
			} else {
				headerPattern = getString("gb.todaysActivityStats");
			}
		} else {
			// multiple days
			if (recentActivity.size() == 0) {
				headerPattern = getString("gb.recentActivityNone");
			} else {
				headerPattern = getString("gb.recentActivityStats");
			}
		}

		if (recentActivity.size() == 0) {
			// no activity, skip graphs and activity panel
			add(new Label("subheader", MessageFormat.format(headerPattern,
					daysBack)));
			add(new Label("chartsPanel").setVisible(false));
			add(new Label("activityPanel"));
		} else {
			// calculate total commits and total authors
			int totalCommits = 0;
			Set<String> uniqueAuthors = new HashSet<String>();
			for (Activity activity : recentActivity) {
				totalCommits += activity.getCommitCount();
				uniqueAuthors.addAll(activity.getAuthorMetrics().keySet());
			}
			int totalAuthors = uniqueAuthors.size();

			// add the subheader with stat numbers
			add(new Label("subheader", MessageFormat.format(headerPattern,
					daysBack, totalCommits, totalAuthors)));

			// create the activity charts
			if (app().settings().getBoolean(Keys.web.generateActivityGraph, true)) {
				Charts charts = createCharts(recentActivity);
				add(charts);
				add(new Fragment("chartsPanel", "chartsFragment", ActivityPage.this));
			} else {
				add(new Label("chartsPanel").setVisible(false));
			}

			// add activity panel
			add(new ActivityPanel("activityPanel", recentActivity));
		}
	}

	@Override
	protected boolean reusePageParameters() {
		return true;
	}

	@Override
	protected void addDropDownMenus(List<NavLink> navLinks) {
		DropDownPageMenuNavLink filters = new DropDownPageMenuNavLink("gb.filters",
				ActivityPage.class);

		PageParameters currentParameters = getPageParameters();
		int daysBack = app().settings().getInteger(Keys.web.activityDuration, 7);
		if (currentParameters != null && currentParameters.get("db").isEmpty()) {
			currentParameters.add("db", daysBack);
		}

		// preserve time filter options on repository choices
		filters.menuItems.addAll(getRepositoryFilterItems(currentParameters));

		// preserve repository filter options on time choices
		filters.menuItems.addAll(getTimeFilterItems(currentParameters));

		if (filters.menuItems.size() > 0) {
			// Reset Filter
			filters.menuItems.add(new ParameterMenuItem(getString("gb.reset")));
		}
		navLinks.add(filters);
	}

	/**
	 * Creates the daily activity line chart, the active repositories pie chart,
	 * and the active authors pie chart
	 *
	 * @param recentActivity
	 * @return
	 */
	private Charts createCharts(List<Activity> recentActivity) {
		// activity metrics
		Map<String, Metric> repositoryMetrics = new HashMap<String, Metric>();
		Map<String, Metric> authorMetrics = new HashMap<String, Metric>();

		// aggregate repository and author metrics
		for (Activity activity : recentActivity) {

			// aggregate author metrics
			for (Map.Entry<String, Metric> entry : activity.getAuthorMetrics().entrySet()) {
				String author = entry.getKey();
				if (!authorMetrics.containsKey(author)) {
					authorMetrics.put(author, new Metric(author));
				}
				authorMetrics.get(author).count += entry.getValue().count;
			}

			// aggregate repository metrics
			for (Map.Entry<String, Metric> entry : activity.getRepositoryMetrics().entrySet()) {
				String repository = StringUtils.stripDotGit(entry.getKey());
				if (!repositoryMetrics.containsKey(repository)) {
					repositoryMetrics.put(repository, new Metric(repository));
				}
				repositoryMetrics.get(repository).count += entry.getValue().count;
			}
		}

		// build charts
		Charts charts = new Flotr2Charts();

		// sort in reverse-chronological order and then reverse that
		Collections.sort(recentActivity);
		Collections.reverse(recentActivity);

		// daily line chart
		Chart chart = charts.createLineChart("chartDaily", getString("gb.dailyActivity"), "day",
				getString("gb.commits"));
		SimpleDateFormat df = new SimpleDateFormat("MMM dd");
		df.setTimeZone(getTimeZone());
		for (Activity metric : recentActivity) {
			chart.addValue(metric.startDate, metric.getCommitCount());
		}
		charts.addChart(chart);

		// active repositories pie chart
		chart = charts.createPieChart("chartRepositories", getString("gb.activeRepositories"),
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

		return charts;
	}
}
