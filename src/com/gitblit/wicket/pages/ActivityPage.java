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

import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.wicket.PageParameters;
import org.apache.wicket.behavior.HeaderContributor;
import org.apache.wicket.markup.html.basic.Label;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.GitBlit;
import com.gitblit.models.DailyActivity;
import com.gitblit.models.Metric;
import com.gitblit.models.RefModel;
import com.gitblit.models.RepositoryCommit;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;
import com.gitblit.wicket.GitBlitWebSession;
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
		super();
		setupPage("", "");
		final UserModel user = GitBlitWebSession.get().getUser();

		// parameters
		int daysBack = WicketUtils.getDaysBack(params);
		if (daysBack < 1) {
			daysBack = 14;
		}		
		String set = WicketUtils.getSet(params);
		String repositoryName = WicketUtils.getRepositoryName(params);
		String objectId = WicketUtils.getObject(params);

		List<RepositoryModel> models = null;
		if (!StringUtils.isEmpty(repositoryName)) {
			// named repository
			models = new ArrayList<RepositoryModel>();
			RepositoryModel model = GitBlit.self().getRepositoryModel(repositoryName);
			if (user.canAccessRepository(model)) {
				models.add(model);
			}
		}

		// get all user accessible repositories
		if (models == null) {
			models = GitBlit.self().getRepositoryModels(user);
		}

		// filter the repositories by the specified set
		if (!StringUtils.isEmpty(set)) {
			List<String> sets = StringUtils.getStringsFromValue(set, ",");
			List<RepositoryModel> setModels = new ArrayList<RepositoryModel>();
			for (RepositoryModel model : models) {
				for (String curr : sets) {
					if (model.federationSets.contains(curr)) {
						setModels.add(model);
					}
				}
			}
			models = setModels;
		}

		// Activity panel shows last daysBack of activity across all
		// repositories.
		Date thresholdDate = new Date(System.currentTimeMillis() - daysBack * TimeUtils.ONEDAY);

		// Build a map of DailyActivity from the available repositories for the
		// specified threshold date.
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		Calendar cal = Calendar.getInstance();

		Map<String, DailyActivity> activity = new HashMap<String, DailyActivity>();
		for (RepositoryModel model : models) {
			if (model.hasCommits && model.lastChange.after(thresholdDate)) {
				Repository repository = GitBlit.self().getRepository(model.name);
				List<RevCommit> commits = JGitUtils.getRevLog(repository, objectId, thresholdDate);
				Map<ObjectId, List<RefModel>> allRefs = JGitUtils.getAllRefs(repository);
				repository.close();

				// determine commit branch
				String branch = objectId;
				if (StringUtils.isEmpty(branch)) {
					List<RefModel> headRefs = allRefs.get(commits.get(0).getId());
					List<String> localBranches = new ArrayList<String>();
					for (RefModel ref : headRefs) {
						if (ref.getName().startsWith(Constants.R_HEADS)) {
							localBranches.add(ref.getName().substring(Constants.R_HEADS.length()));
						}
					}
					// determine branch
					if (localBranches.size() == 1) {
						// only one branch, choose it
						branch = localBranches.get(0);
					} else if (localBranches.size() > 1) {
						if (localBranches.contains("master")) {
							// choose master
							branch = "master";
						} else {
							// choose first branch
							branch = localBranches.get(0);
						}
					}
				}

				for (RevCommit commit : commits) {
					Date date = JGitUtils.getCommitDate(commit);
					String dateStr = df.format(date);
					if (!activity.containsKey(dateStr)) {
						// Normalize the date to midnight
						cal.setTime(date);
						cal.set(Calendar.HOUR_OF_DAY, 0);
						cal.set(Calendar.MINUTE, 0);
						cal.set(Calendar.SECOND, 0);
						cal.set(Calendar.MILLISECOND, 0);
						activity.put(dateStr, new DailyActivity(cal.getTime()));
					}
					RepositoryCommit commitModel = new RepositoryCommit(model.name, branch, commit);
					commitModel.setRefs(allRefs.get(commit.getId()));
					activity.get(dateStr).commits.add(commitModel);
				}
			}
		}

		// activity metrics
		Map<String, Metric> dayMetrics = new HashMap<String, Metric>();
		Map<String, Metric> repositoryMetrics = new HashMap<String, Metric>();
		Map<String, Metric> authorMetrics = new HashMap<String, Metric>();

		// prepare day metrics
		cal.setTimeInMillis(System.currentTimeMillis());
		for (int i = 0; i < daysBack; i++) {
			cal.add(Calendar.DATE, -1);
			String key = df.format(cal.getTime());
			dayMetrics.put(key, new Metric(key));
		}

		// calculate activity metrics
		for (Map.Entry<String, DailyActivity> entry : activity.entrySet()) {
			// day metrics
			Metric day = dayMetrics.get(entry.getKey());
			day.count = entry.getValue().commits.size();

			for (RepositoryCommit commit : entry.getValue().commits) {
				// repository metrics
				String repository = commit.repository;
				if (!repositoryMetrics.containsKey(repository)) {
					repositoryMetrics.put(repository, new Metric(repository));
				}
				repositoryMetrics.get(repository).count++;

				// author metrics
				String author = commit.getAuthorIdent().getEmailAddress().toLowerCase();
				if (!authorMetrics.containsKey(author)) {
					authorMetrics.put(author, new Metric(author));
				}
				authorMetrics.get(author).count++;
			}
		}

		// sort the activity groups and their commit contents
		int totalCommits = 0;
		List<DailyActivity> recentActivity = new ArrayList<DailyActivity>(activity.values());
		for (DailyActivity daily : recentActivity) {
			Collections.sort(daily.commits);
			totalCommits += daily.commits.size();
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
		df = new SimpleDateFormat("MMM dd");
		for (DailyActivity metric : recentActivity) {
			chart.addValue(df.format(metric.date), metric.commits.size());
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

		add(new HeaderContributor(charts));

		add(new Label("subheader", MessageFormat.format(getString("gb.recentActivitySubheader"),
				daysBack, totalCommits, authorMetrics.size())));

		// add activity panel
		add(new ActivityPanel("activityPanel", recentActivity));
	}
}
