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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.eclipse.jgit.lib.Repository;
import org.wicketstuff.googlecharts.Chart;
import org.wicketstuff.googlecharts.ChartAxis;
import org.wicketstuff.googlecharts.ChartAxisType;
import org.wicketstuff.googlecharts.ChartProvider;
import org.wicketstuff.googlecharts.ChartType;
import org.wicketstuff.googlecharts.IChartData;
import org.wicketstuff.googlecharts.LineStyle;
import org.wicketstuff.googlecharts.MarkerType;
import org.wicketstuff.googlecharts.ShapeMarker;

import com.gitblit.models.Metric;
import com.gitblit.utils.MetricUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.WicketUtils;

public class MetricsPage extends RepositoryPage {

	public MetricsPage(PageParameters params) {
		super(params);
		Repository r = getRepository();
		if (StringUtils.isEmpty(objectId)) {
			add(new Label("branchTitle", getRepositoryModel().HEAD));
		} else {
			add(new Label("branchTitle", objectId));
		}
		Metric metricsTotal = null;
		List<Metric> metrics = MetricUtils.getDateMetrics(r, objectId, true, null, getTimeZone());
		metricsTotal = metrics.remove(0);
		if (metricsTotal == null) {
			add(new Label("branchStats", ""));
		} else {
			add(new Label("branchStats",
					MessageFormat.format(getString("gb.branchStats"), metricsTotal.count,
							metricsTotal.tag, getTimeUtils().duration(metricsTotal.duration))));
		}
		insertLinePlot("commitsChart", metrics);
		insertBarPlot("dayOfWeekChart", getDayOfWeekMetrics(r, objectId));
		insertPieChart("authorsChart", getAuthorMetrics(r, objectId));
	}

	private void insertLinePlot(String wicketId, List<Metric> metrics) {
		if ((metrics != null) && (metrics.size() > 0)) {
			IChartData data = WicketUtils.getChartData(metrics);

			ChartProvider provider = new ChartProvider(new Dimension(400, 100), ChartType.LINE,
					data);
			ChartAxis dateAxis = new ChartAxis(ChartAxisType.BOTTOM);
			dateAxis.setLabels(new String[] { metrics.get(0).name,
					metrics.get(metrics.size() / 2).name, metrics.get(metrics.size() - 1).name });
			provider.addAxis(dateAxis);

			ChartAxis commitAxis = new ChartAxis(ChartAxisType.LEFT);
			commitAxis.setLabels(new String[] { "",
					String.valueOf((int) WicketUtils.maxValue(metrics)) });
			provider.addAxis(commitAxis);

			provider.setLineStyles(new LineStyle[] { new LineStyle(2, 4, 0), new LineStyle(0, 4, 1) });
			provider.addShapeMarker(new ShapeMarker(MarkerType.CIRCLE, Color.BLUE, 1, -1, 5));

			add(new Chart(wicketId, provider));
		} else {
			add(WicketUtils.newBlankImage(wicketId));
		}
	}

	private void insertBarPlot(String wicketId, List<Metric> metrics) {
		if ((metrics != null) && (metrics.size() > 0)) {
			IChartData data = WicketUtils.getChartData(metrics);

			ChartProvider provider = new ChartProvider(new Dimension(400, 100),
					ChartType.BAR_VERTICAL_SET, data);
			ChartAxis dateAxis = new ChartAxis(ChartAxisType.BOTTOM);
			List<String> labels = new ArrayList<String>();
			for (Metric metric : metrics) {
				labels.add(metric.name);
			}
			dateAxis.setLabels(labels.toArray(new String[labels.size()]));
			provider.addAxis(dateAxis);

			ChartAxis commitAxis = new ChartAxis(ChartAxisType.LEFT);
			commitAxis.setLabels(new String[] { "",
					String.valueOf((int) WicketUtils.maxValue(metrics)) });
			provider.addAxis(commitAxis);

			add(new Chart(wicketId, provider));
		} else {
			add(WicketUtils.newBlankImage(wicketId));
		}
	}

	private void insertPieChart(String wicketId, List<Metric> metrics) {
		if ((metrics != null) && (metrics.size() > 0)) {
			IChartData data = WicketUtils.getChartData(metrics);
			List<String> labels = new ArrayList<String>();
			for (Metric metric : metrics) {
				labels.add(metric.name);
			}
			ChartProvider provider = new ChartProvider(new Dimension(800, 200), ChartType.PIE, data);
			provider.setPieLabels(labels.toArray(new String[labels.size()]));
			add(new Chart(wicketId, provider));
		} else {
			add(WicketUtils.newBlankImage(wicketId));
		}
	}

	private List<Metric> getDayOfWeekMetrics(Repository repository, String objectId) {
		List<Metric> list = MetricUtils.getDateMetrics(repository, objectId, false, "E", getTimeZone());
		SimpleDateFormat sdf = new SimpleDateFormat("E");
		Calendar cal = Calendar.getInstance();

		List<Metric> sorted = new ArrayList<Metric>();
		int firstDayOfWeek = cal.getFirstDayOfWeek();
		int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);

		// rewind date to first day of week
		cal.add(Calendar.DATE, firstDayOfWeek - dayOfWeek);
		for (int i = 0; i < 7; i++) {
			String day = sdf.format(cal.getTime());
			for (Metric metric : list) {
				if (metric.name.equals(day)) {
					sorted.add(metric);
					list.remove(metric);
					break;
				}
			}
			cal.add(Calendar.DATE, 1);
		}
		return sorted;
	}

	private List<Metric> getAuthorMetrics(Repository repository, String objectId) {
		List<Metric> authors = MetricUtils.getAuthorMetrics(repository, objectId, true);
		Collections.sort(authors, new Comparator<Metric>() {
			@Override
			public int compare(Metric o1, Metric o2) {
				if (o1.count > o2.count) {
					return -1;
				} else if (o1.count < o2.count) {
					return 1;
				}
				return 0;
			}
		});
		if (authors.size() > 10) {
			return authors.subList(0, 9);
		}
		return authors;
	}

	@Override
	protected String getPageName() {
		return getString("gb.metrics");
	}
}
