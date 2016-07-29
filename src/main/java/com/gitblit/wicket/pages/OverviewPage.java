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
import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.Keys;
import com.gitblit.models.Metric;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.CacheControl;
import com.gitblit.wicket.CacheControl.LastModified;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.charting.Chart;
import com.gitblit.wicket.charting.Charts;
import com.gitblit.wicket.charting.Flotr2Charts;
import com.gitblit.wicket.panels.BranchesPanel;
import com.gitblit.wicket.panels.LinkPanel;
import com.gitblit.wicket.panels.ReflogPanel;
import com.gitblit.wicket.panels.RepositoryUrlPanel;
import com.gitblit.wicket.panels.TagsPanel;

@CacheControl(LastModified.REPOSITORY)
public class OverviewPage extends RepositoryPage {

	public OverviewPage(PageParameters params) {
		super(params);

		int numberRefs = app().settings().getInteger(Keys.web.summaryRefsCount, 5);

		Repository r = getRepository();
		final RepositoryModel model = getRepositoryModel();
		UserModel user = GitBlitWebSession.get().getUser();
		if (user == null) {
			user = UserModel.ANONYMOUS;
		}

		List<Metric> metrics = null;
		Metric metricsTotal = null;
		if (!model.skipSummaryMetrics && app().settings().getBoolean(Keys.web.generateActivityGraph, true)) {
			metrics = app().repositories().getRepositoryDefaultMetrics(model, r);
			metricsTotal = metrics.remove(0);
		}

		addSyndicationDiscoveryLink();

		// repository description
		add(new Label("repositoryDescription", getRepositoryModel().description));

		// owner links
		final List<String> owners = new ArrayList<String>(getRepositoryModel().owners);
		ListDataProvider<String> ownersDp = new ListDataProvider<String>(owners);
		DataView<String> ownersView = new DataView<String>("repositoryOwners", ownersDp) {
			private static final long serialVersionUID = 1L;
			int counter = 0;
			@Override
			public void populateItem(final Item<String> item) {
				String ownername = item.getModelObject();
				UserModel ownerModel = app().users().getUserModel(ownername);
				if (ownerModel != null) {
					item.add(new LinkPanel("owner", null, ownerModel.getDisplayName(), UserPage.class,
							WicketUtils.newUsernameParameter(ownerModel.username)).setRenderBodyOnly(true));
				} else {
					Label owner = new Label("owner", ownername);
					WicketUtils.setCssStyle(owner, "text-decoration: line-through;");
					WicketUtils.setHtmlTooltip(owner,  MessageFormat.format(getString("gb.failedToFindAccount"), ownername));
					item.add(owner);
				}
				counter++;
				item.add(new Label("comma", ",").setVisible(counter < owners.size()));
				item.setRenderBodyOnly(true);
			}
		};
		ownersView.setRenderBodyOnly(true);
		add(ownersView);

		add(WicketUtils.createTimestampLabel("repositoryLastChange",
				JGitUtils.getLastChange(r).when, getTimeZone(), getTimeUtils()));
		add(new Label("repositorySize", model.size));

		if (metricsTotal == null) {
			add(new Label("branchStats", ""));
		} else {
			add(new Label("branchStats",
					MessageFormat.format(getString("gb.branchStats"), metricsTotal.count,
							metricsTotal.tag, getTimeUtils().duration(metricsTotal.duration))));
		}
		add(new BookmarkablePageLink<Void>("metrics", MetricsPage.class,
				WicketUtils.newRepositoryParameter(repositoryName)));

		add(new RepositoryUrlPanel("repositoryUrlPanel", false, user, model));

		int reflogCount = app().settings().getInteger(Keys.web.overviewReflogCount, 5);
		ReflogPanel reflog = new ReflogPanel("reflogPanel", getRepositoryModel(), r, reflogCount, 0);
		add(reflog);
		add(new TagsPanel("tagsPanel", repositoryName, r, numberRefs).hideIfEmpty());
		add(new BranchesPanel("branchesPanel", getRepositoryModel(), r, numberRefs, false).hideIfEmpty());

		// Display an activity line graph
		insertActivityGraph(metrics);
	}

	@Override
	protected String getPageName() {
		return getString("gb.overview");
	}

	private void insertActivityGraph(List<Metric> metrics) {
		if ((metrics != null) && (metrics.size() > 0)
				&& app().settings().getBoolean(Keys.web.generateActivityGraph, true)) {

			Charts charts = new Flotr2Charts();
			
			// daily line chart
			Chart chart = charts.createLineChart("chartDaily", "", "unit",
					getString("gb.commits"));
			for (Metric metric : metrics) {
				chart.addValue(metric.name, metric.count);
			}
			chart.setWidth(375);
			chart.setHeight(150);

			charts.addChart(chart);
			add(charts);
		}
	}
}
