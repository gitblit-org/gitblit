package com.gitblit.wicket.pages;

import java.awt.Dimension;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.image.ContextImage;
import org.eclipse.jgit.lib.Repository;

import com.codecommit.wicket.AbstractChartData;
import com.codecommit.wicket.Chart;
import com.codecommit.wicket.ChartAxis;
import com.codecommit.wicket.ChartAxisType;
import com.codecommit.wicket.ChartProvider;
import com.codecommit.wicket.ChartType;
import com.codecommit.wicket.IChartData;
import com.gitblit.StoredSettings;
import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.GitBlitWebApp;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.models.Metric;
import com.gitblit.wicket.panels.BranchesPanel;
import com.gitblit.wicket.panels.LogPanel;
import com.gitblit.wicket.panels.TagsPanel;

public class SummaryPage extends RepositoryPage {

	public SummaryPage(PageParameters params) {
		super(params);
		
		int numCommitsDef = 20;
		int numRefsDef = 5;
		
		int numberCommits = StoredSettings.getInteger("summaryCommitCount", numCommitsDef);
		if (numberCommits <= 0) {
			numberCommits = numCommitsDef;
		}

		int numberRefs = StoredSettings.getInteger("summaryRefsCount", numRefsDef);
		if (numberRefs <= 0) {
			numberRefs = numRefsDef;
		}
		
		Repository r = getRepository();		
		List<Metric> metrics = JGitUtils.getDateMetrics(r);
		
		long numberOfCommits = 0;
		for (Metric m : metrics) {
			numberOfCommits += m.count;
		}

		String owner = JGitUtils.getRepositoryOwner(r);
		GitBlitWebSession session = GitBlitWebSession.get();
		String lastchange = session.formatDateTimeLong(JGitUtils.getLastChange(r));
		String cloneurl = GitBlitWebApp.get().getCloneUrl(repositoryName);

		// repository description
		add(new Label("repositoryDescription", description));
		add(new Label("repositoryOwner", owner));
		add(new Label("repositoryLastChange", lastchange));
		add(new Label("repositoryCloneUrl", cloneurl));

		add(new LogPanel("commitsPanel", repositoryName, null, r, numberCommits, 0));
		add(new TagsPanel("tagsPanel", repositoryName, r, numberRefs));
		add(new BranchesPanel("branchesPanel", repositoryName, r, numberRefs));
		
		// Display an activity line graph
		insertActivityGraph(metrics);
	}
	
	@Override
	protected String getPageName() {
		return getString("gb.summary");
	}

	private void insertActivityGraph(List<Metric> metrics) {
		if (StoredSettings.getBoolean("generateActivityGraph", true)) {			
			IChartData data = getChartData(metrics);

			ChartProvider provider = new ChartProvider(new Dimension(400, 80), ChartType.LINE, data);
			ChartAxis dateAxis = new ChartAxis(ChartAxisType.BOTTOM);
			dateAxis.setLabels(new String[] { metrics.get(0).name, metrics.get(metrics.size() / 2).name, metrics.get(metrics.size() - 1).name });
			provider.addAxis(dateAxis);

			ChartAxis commitAxis = new ChartAxis(ChartAxisType.LEFT);
			commitAxis.setLabels(new String[] { "", String.valueOf((int) maxValue(metrics)) });
			provider.addAxis(commitAxis);

			add(new Chart("commitsChart", provider));
		} else {
			add(new ContextImage("commitsChart", "blank.png"));			
		}
	}

	protected IChartData getChartData(List<Metric> metrics) {
		final double[] counts = new double[metrics.size()];
		int i = 0;
		double max = 0;
		for (Metric m : metrics) {
			counts[i++] = m.count;
			max = Math.max(max, m.count);
		}
		final double dmax = max;
		IChartData data = new AbstractChartData() {
			private static final long serialVersionUID = 1L;

			public double[][] getData() {
				return new double[][] { counts };
			}

			public double getMax() {
				return dmax;
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
