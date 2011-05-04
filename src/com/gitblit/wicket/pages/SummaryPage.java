package com.gitblit.wicket.pages;

import java.awt.Color;
import java.awt.Dimension;
import java.text.MessageFormat;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
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

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.utils.JGitUtils;
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
		add(new Label("repositoryCloneUrl", GitBlit.self().getCloneUrl(repositoryName)));

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

			provider.setLineStyles(new LineStyle[] {new LineStyle(2, 4, 0), new LineStyle(0, 4, 1)});	
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
