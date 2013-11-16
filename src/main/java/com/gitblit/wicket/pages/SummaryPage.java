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

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.wicketstuff.googlecharts.ChartAxis;
import org.wicketstuff.googlecharts.ChartAxisType;
import org.wicketstuff.googlecharts.ChartProvider;
import org.wicketstuff.googlecharts.ChartType;
import org.wicketstuff.googlecharts.IChartData;
import org.wicketstuff.googlecharts.LineStyle;
import org.wicketstuff.googlecharts.MarkerType;
import org.wicketstuff.googlecharts.ShapeMarker;

import com.gitblit.Keys;
import com.gitblit.models.Metric;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.CacheControl;
import com.gitblit.wicket.CacheControl.LastModified;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.MarkupProcessor;
import com.gitblit.wicket.MarkupProcessor.MarkupDocument;
import com.gitblit.wicket.MarkupProcessor.MarkupSyntax;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.charting.SecureChart;
import com.gitblit.wicket.panels.BranchesPanel;
import com.gitblit.wicket.panels.LinkPanel;
import com.gitblit.wicket.panels.LogPanel;
import com.gitblit.wicket.panels.RepositoryUrlPanel;
import com.gitblit.wicket.panels.TagsPanel;

@CacheControl(LastModified.REPOSITORY)
public class SummaryPage extends RepositoryPage {

	public SummaryPage(PageParameters params) {
		super(params);

		int numberCommits = app().settings().getInteger(Keys.web.summaryCommitCount, 20);
		if (numberCommits <= 0) {
			numberCommits = 20;
		}
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
		add(new Label("repositorySize", getRepositoryModel().size));
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

		add(new LogPanel("commitsPanel", repositoryName, getRepositoryModel().HEAD, r, numberCommits, 0, getRepositoryModel().showRemoteBranches));
		add(new TagsPanel("tagsPanel", repositoryName, r, numberRefs).hideIfEmpty());
		add(new BranchesPanel("branchesPanel", getRepositoryModel(), r, numberRefs, false).hideIfEmpty());

		if (app().settings().getBoolean(Keys.web.summaryShowReadme, false)) {
			// show a readme on the summary page
			RevCommit head = JGitUtils.getCommit(r, null);
			MarkupProcessor processor = new MarkupProcessor(app().settings());
			MarkupDocument markupDoc = processor.getReadme(r, repositoryName, getBestCommitId(head));
			if (markupDoc == null || markupDoc.markup == null) {
				add(new Label("readme").setVisible(false));
			} else {
				Fragment fragment = new Fragment("readme", MarkupSyntax.PLAIN.equals(markupDoc.syntax) ? "plaintextPanel" : "markdownPanel", this);
				fragment.add(new Label("readmeFile", markupDoc.documentPath));
				// Add the html to the page
				Component content = new Label("readmeContent", markupDoc.html).setEscapeModelStrings(false);
				fragment.add(content.setVisible(!StringUtils.isEmpty(markupDoc.html)));
				add(fragment);
			}
		} else {
			// global, no readme on summary page
			add(new Label("readme").setVisible(false));
		}

		// Display an activity line graph
		insertActivityGraph(metrics);
	}

	@Override
	protected String getPageName() {
		return getString("gb.summary");
	}

	private void insertActivityGraph(List<Metric> metrics) {
		if ((metrics != null) && (metrics.size() > 0)
				&& app().settings().getBoolean(Keys.web.generateActivityGraph, true)) {
			IChartData data = WicketUtils.getChartData(metrics);

			ChartProvider provider = new ChartProvider(new Dimension(290, 100), ChartType.LINE,
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
			provider.addShapeMarker(new ShapeMarker(MarkerType.CIRCLE, Color.decode("#002060"), 1, -1, 5));

			add(new SecureChart("commitsChart", provider));
		} else {
			add(WicketUtils.newBlankImage("commitsChart"));
		}
	}
}
