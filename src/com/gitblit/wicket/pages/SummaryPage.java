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

import java.awt.Color;
import java.awt.Dimension;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.protocol.http.WebRequest;
import org.eclipse.jgit.lib.Repository;
import org.wicketstuff.googlecharts.AbstractChartData;
import org.wicketstuff.googlecharts.Chart;
import org.wicketstuff.googlecharts.ChartAxis;
import org.wicketstuff.googlecharts.ChartAxisType;
import org.wicketstuff.googlecharts.ChartProvider;
import org.wicketstuff.googlecharts.ChartType;
import org.wicketstuff.googlecharts.IChartData;
import org.wicketstuff.googlecharts.LineStyle;
import org.wicketstuff.googlecharts.MarkerType;
import org.wicketstuff.googlecharts.ShapeMarker;

import com.gitblit.Constants;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;
import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.models.Metric;
import com.gitblit.wicket.panels.BranchesPanel;
import com.gitblit.wicket.panels.LogPanel;
import com.gitblit.wicket.panels.TagsPanel;

public class SummaryPage extends RepositoryPage {

	public SummaryPage(PageParameters params) {
		super(params);

		int numCommitsDef = 20;
		int numRefsDef = 5;

		int numberCommits = GitBlit.self().settings().getInteger(Keys.web.summaryCommitCount, numCommitsDef);
		if (numberCommits <= 0) {
			numberCommits = numCommitsDef;
		}

		int numberRefs = GitBlit.self().settings().getInteger(Keys.web.summaryRefsCount, numRefsDef);
		if (numberRefs <= 0) {
			numberRefs = numRefsDef;
		}

		Repository r = getRepository();
		List<Metric> metrics = null;
		Metric metricsTotal = null;
		if (GitBlit.self().settings().getBoolean(Keys.web.generateActivityGraph, true)) {
			metrics = JGitUtils.getDateMetrics(r);
			metricsTotal = metrics.remove(0);
		}

		// repository description
		add(new Label("repositoryDescription", getRepositoryModel().description));
		add(new Label("repositoryOwner", getRepositoryModel().owner));

		add(WicketUtils.createTimestampLabel("repositoryLastChange", JGitUtils.getLastChange(r), getTimeZone()));
		if (metricsTotal == null) {
			add(new Label("repositoryStats", ""));
		} else {
			add(new Label("repositoryStats", MessageFormat.format("{0} commits and {1} tags in {2}", metricsTotal.count, metricsTotal.tag, TimeUtils.duration(metricsTotal.duration))));
		}

		List<String> repositoryUrls = new ArrayList<String>();

		if (GitBlit.self().settings().getBoolean(Keys.git.enableGitServlet, true)) {
			AccessRestrictionType accessRestriction = getRepositoryModel().accessRestriction;
			switch (accessRestriction) {
			case NONE:
				add(WicketUtils.newClearPixel("accessRestrictionIcon").setVisible(false));
				break;
			case PUSH:
				add(WicketUtils.newImage("accessRestrictionIcon", "lock_go_16x16.png", getAccessRestrictions().get(accessRestriction)));
				break;
			case CLONE:
				add(WicketUtils.newImage("accessRestrictionIcon", "lock_pull_16x16.png", getAccessRestrictions().get(accessRestriction)));
				break;
			case VIEW:
				add(WicketUtils.newImage("accessRestrictionIcon", "shield_16x16.png", getAccessRestrictions().get(accessRestriction)));
				break;
			default:
				add(WicketUtils.newClearPixel("accessRestrictionIcon").setVisible(false));
			}

			HttpServletRequest req = ((WebRequest) getRequestCycle().getRequest()).getHttpServletRequest();
			StringBuilder sb = new StringBuilder();
			sb.append(req.getScheme());
			sb.append("://");
			sb.append(req.getServerName());
			if ((req.getScheme().equals("http") && req.getServerPort() != 80) || (req.getScheme().equals("https") && req.getServerPort() != 443)) {
				sb.append(":" + req.getServerPort());
			}
			sb.append(Constants.GIT_SERVLET_PATH);
			sb.append(repositoryName);
			repositoryUrls.add(sb.toString());
		} else {
			add(WicketUtils.newClearPixel("accessRestrictionIcon").setVisible(false));
		}
		repositoryUrls.addAll(GitBlit.self().getOtherCloneUrls(repositoryName));

		add(new Label("repositoryCloneUrl", StringUtils.flattenStrings(repositoryUrls, "<br/>")).setEscapeModelStrings(false));

		add(new LogPanel("commitsPanel", repositoryName, null, r, numberCommits, 0));
		add(new TagsPanel("tagsPanel", repositoryName, r, numberRefs));
		add(new BranchesPanel("branchesPanel", getRepositoryModel(), r, numberRefs));

		// Display an activity line graph
		insertActivityGraph(metrics);
	}

	@Override
	protected String getPageName() {
		return getString("gb.summary");
	}

	private void insertActivityGraph(List<Metric> metrics) {
		if (metrics.size() > 0 && GitBlit.self().settings().getBoolean(Keys.web.generateActivityGraph, true)) {
			IChartData data = getChartData(metrics);

			ChartProvider provider = new ChartProvider(new Dimension(400, 100), ChartType.LINE, data);
			ChartAxis dateAxis = new ChartAxis(ChartAxisType.BOTTOM);
			dateAxis.setLabels(new String[] { metrics.get(0).name, metrics.get(metrics.size() / 2).name, metrics.get(metrics.size() - 1).name });
			provider.addAxis(dateAxis);

			ChartAxis commitAxis = new ChartAxis(ChartAxisType.LEFT);
			commitAxis.setLabels(new String[] { "", String.valueOf((int) maxValue(metrics)) });
			provider.addAxis(commitAxis);

			provider.setLineStyles(new LineStyle[] { new LineStyle(2, 4, 0), new LineStyle(0, 4, 1) });
			provider.addShapeMarker(new ShapeMarker(MarkerType.CIRCLE, Color.BLUE, 1, -1, 5));

			add(new Chart("commitsChart", provider));
		} else {
			add(WicketUtils.newBlankImage("commitsChart"));
		}
	}

	protected IChartData getChartData(List<Metric> metrics) {
		final double[] commits = new double[metrics.size()];
		final double[] tags = new double[metrics.size()];
		int i = 0;
		double max = 0;
		for (Metric m : metrics) {
			commits[i] = m.count;
			if (m.tag > 0) {
				tags[i] = m.count;
			} else {
				tags[i] = -1d;
			}
			max = Math.max(max, m.count);
			i++;
		}
		IChartData data = new AbstractChartData(max) {
			private static final long serialVersionUID = 1L;

			public double[][] getData() {
				return new double[][] { commits, tags };
			}
		};
		return data;
	}

	protected String[] getNames(List<Metric> results) {
		String[] names = new String[results.size()];
		for (int i = 0; i < results.size(); i++) {
			names[i] = results.get(i).name;
		}
		return names;
	}

	protected double maxValue(List<Metric> metrics) {
		double max = Double.MIN_VALUE;
		for (Metric m : metrics) {
			if (m.count > max) {
				max = m.count;
			}
		}
		return max;
	}
}
