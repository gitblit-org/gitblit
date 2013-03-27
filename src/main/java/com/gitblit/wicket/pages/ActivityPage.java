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

import org.apache.wicket.PageParameters;
import org.apache.wicket.behavior.HeaderContributor;
import org.apache.wicket.markup.html.basic.Label;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.models.Activity;
import com.gitblit.models.Metric;
import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.ActivityUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.PageRegistration;
import com.gitblit.wicket.PageRegistration.DropDownMenuItem;
import com.gitblit.wicket.PageRegistration.DropDownMenuRegistration;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.charting.GoogleChart;
import com.gitblit.wicket.charting.GoogleCharts;
import com.gitblit.wicket.charting.GoogleLineChart;
import com.gitblit.wicket.charting.GooglePieChart;
import com.gitblit.wicket.panels.ActivityPanel;

/**
 * Activity Page shows a list of recent commits across all visible Gitblit
 * repositories.
 * 
 * @author James Moger
 * 
 */
public class ActivityPage extends RootPage {

	public ActivityPage(PageParameters params) {
		super(params);
		setupPage("", "");

		// parameters
		int daysBack = WicketUtils.getDaysBack(params);
		if (daysBack < 1) {
			daysBack = 14;
		}
		String objectId = WicketUtils.getObject(params);

		// determine repositories to view and retrieve the activity
		List<RepositoryModel> models = getRepositories(params);
		List<Activity> recentActivity = ActivityUtils.getRecentActivity(models, 
				daysBack, objectId, getTimeZone());

		if (recentActivity.size() == 0) {
			// no activity, skip graphs and activity panel
			add(new Label("subheader", MessageFormat.format(getString("gb.recentActivityNone"),
					daysBack)));
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
			add(new Label("subheader", MessageFormat.format(getString("gb.recentActivityStats"),
					daysBack, totalCommits, totalAuthors)));

			// create the activity charts
			GoogleCharts charts = createCharts(recentActivity);
			add(new HeaderContributor(charts));

			// add activity panel
			add(new ActivityPanel("activityPanel", recentActivity));
		}
	}

	@Override
	protected boolean reusePageParameters() {
		return true;
	}

	@Override
	protected void addDropDownMenus(List<PageRegistration> pages) {
		DropDownMenuRegistration filters = new DropDownMenuRegistration("gb.filters",
				ActivityPage.class);

		PageParameters currentParameters = getPageParameters();
		int daysBack = GitBlit.getInteger(Keys.web.activityDuration, 14);
		if (currentParameters != null && !currentParameters.containsKey("db")) {
			currentParameters.put("db", daysBack);
		}

		// preserve time filter options on repository choices
		filters.menuItems.addAll(getRepositoryFilterItems(currentParameters));

		// preserve repository filter options on time choices
		filters.menuItems.addAll(getTimeFilterItems(currentParameters));

		if (filters.menuItems.size() > 0) {
			// Reset Filter
			filters.menuItems.add(new DropDownMenuItem(getString("gb.reset"), null, null));
		}
		pages.add(filters);
	}

	/**
	 * Creates the daily activity line chart, the active repositories pie chart,
	 * and the active authors pie chart
	 * 
	 * @param recentActivity
	 * @return
	 */
	private GoogleCharts createCharts(List<Activity> recentActivity) {
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

		// build google charts
		int w = 310;
		int h = 150;
		GoogleCharts charts = new GoogleCharts();

		// sort in reverse-chronological order and then reverse that
		Collections.sort(recentActivity);
		Collections.reverse(recentActivity);

		// daily line chart
		GoogleChart chart = new GoogleLineChart("chartDaily", getString("gb.dailyActivity"), "day",
				getString("gb.commits"));
		SimpleDateFormat df = new SimpleDateFormat("MMM dd");
		df.setTimeZone(getTimeZone());
		for (Activity metric : recentActivity) {
			chart.addValue(df.format(metric.startDate), metric.getCommitCount());
		}
		chart.setWidth(w);
		chart.setHeight(h);
		charts.addChart(chart);

		// active repositories pie chart
		chart = new GooglePieChart("chartRepositories", getString("gb.activeRepositories"),
				getString("gb.repository"), getString("gb.commits"));
		for (Metric metric : repositoryMetrics.values()) {
			chart.addValue(metric.name, metric.count);
		}
		chart.setWidth(w);
		chart.setHeight(h);
		charts.addChart(chart);

		// active authors pie chart
		chart = new GooglePieChart("chartAuthors", getString("gb.activeAuthors"),
				getString("gb.author"), getString("gb.commits"));
		for (Metric metric : authorMetrics.values()) {
			chart.addValue(metric.name, metric.count);
		}
		chart.setWidth(w);
		chart.setHeight(h);
		charts.addChart(chart);

		return charts;
	}
}
