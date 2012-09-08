/*
 * Copyright 2012 gitblit.com.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.RedirectException;
import org.apache.wicket.behavior.HeaderContributor;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.Constants;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.SyndicationServlet;
import com.gitblit.models.Activity;
import com.gitblit.models.Metric;
import com.gitblit.models.ProjectModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ActivityUtils;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.MarkdownUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebApp;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.PageRegistration;
import com.gitblit.wicket.PageRegistration.DropDownMenuItem;
import com.gitblit.wicket.PageRegistration.DropDownMenuRegistration;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.charting.GoogleChart;
import com.gitblit.wicket.charting.GoogleCharts;
import com.gitblit.wicket.charting.GoogleLineChart;
import com.gitblit.wicket.charting.GooglePieChart;
import com.gitblit.wicket.panels.ActivityPanel;
import com.gitblit.wicket.panels.BasePanel.JavascriptEventConfirmation;
import com.gitblit.wicket.panels.LinkPanel;
import com.gitblit.wicket.panels.RepositoryUrlPanel;

public class ProjectPage extends RootPage {
	
	List<ProjectModel> projectModels = new ArrayList<ProjectModel>();

	public ProjectPage() {
		super();
		throw new RedirectException(GitBlitWebApp.get().getHomePage());
	}

	public ProjectPage(PageParameters params) {
		super(params);
		setup(params);
	}

	@Override
	protected boolean reusePageParameters() {
		return true;
	}

	private void setup(PageParameters params) {
		setupPage("", "");
		// check to see if we should display a login message
		boolean authenticateView = GitBlit.getBoolean(Keys.web.authenticateViewPages, true);
		if (authenticateView && !GitBlitWebSession.get().isLoggedIn()) {
			authenticationError("Please login");
			return;
		}

		String projectName = WicketUtils.getProjectName(params);
		if (StringUtils.isEmpty(projectName)) {
			throw new RedirectException(GitBlitWebApp.get().getHomePage());
		}
		
		ProjectModel project = getProjectModel(projectName);
		if (project == null) {
			throw new RedirectException(GitBlitWebApp.get().getHomePage());
		}
		
		add(new Label("projectTitle", project.getDisplayName()));
		add(new Label("projectDescription", project.description));
		
		String feedLink = SyndicationServlet.asLink(getRequest().getRelativePathPrefixToContextRoot(), projectName, null, 0);
		add(new ExternalLink("syndication", feedLink));

		add(WicketUtils.syndicationDiscoveryLink(SyndicationServlet.getTitle(project.getDisplayName(),
				null), feedLink));
		
		String groupName = projectName;
		if (project.isRoot) {
			groupName = "";
		} else {
			groupName += "/";
		}
		
		// project markdown message
		File pmkd = new File(GitBlit.getRepositoriesFolder(),  groupName + "project.mkd");
		String pmessage = readMarkdown(projectName, pmkd);
		Component projectMessage = new Label("projectMessage", pmessage)
				.setEscapeModelStrings(false).setVisible(pmessage.length() > 0);
		add(projectMessage);

		// markdown message above repositories list
		File rmkd = new File(GitBlit.getRepositoriesFolder(),  groupName + "repositories.mkd");
		String rmessage = readMarkdown(projectName, rmkd);
		Component repositoriesMessage = new Label("repositoriesMessage", rmessage)
				.setEscapeModelStrings(false).setVisible(rmessage.length() > 0);
		add(repositoriesMessage);

		List<RepositoryModel> repositories = getRepositories(params);
		
		Collections.sort(repositories, new Comparator<RepositoryModel>() {
			@Override
			public int compare(RepositoryModel o1, RepositoryModel o2) {
				// reverse-chronological sort
				return o2.lastChange.compareTo(o1.lastChange);
			}
		});

		final boolean showSwatch = GitBlit.getBoolean(Keys.web.repositoryListSwatches, true);
		final boolean gitServlet = GitBlit.getBoolean(Keys.git.enableGitServlet, true);
		final boolean showSize = GitBlit.getBoolean(Keys.web.showRepositorySizes, true);
		
		final ListDataProvider<RepositoryModel> dp = new ListDataProvider<RepositoryModel>(repositories);
		DataView<RepositoryModel> dataView = new DataView<RepositoryModel>("repository", dp) {
			private static final long serialVersionUID = 1L;

			public void populateItem(final Item<RepositoryModel> item) {
				final RepositoryModel entry = item.getModelObject();

				// repository swatch
				Component swatch;
				if (entry.isBare){
					swatch = new Label("repositorySwatch", "&nbsp;").setEscapeModelStrings(false);
				} else {
					swatch = new Label("repositorySwatch", "!");
					WicketUtils.setHtmlTooltip(swatch, getString("gb.workingCopyWarning"));
				}
				WicketUtils.setCssBackground(swatch, entry.toString());
				item.add(swatch);
				swatch.setVisible(showSwatch);
				
				PageParameters pp = WicketUtils.newRepositoryParameter(entry.name);
				item.add(new LinkPanel("repositoryName", "list", entry.name, SummaryPage.class, pp));
				item.add(new Label("repositoryDescription", entry.description).setVisible(!StringUtils.isEmpty(entry.description)));
				
				item.add(new BookmarkablePageLink<Void>("tickets", TicketsPage.class, pp).setVisible(entry.useTickets));
				item.add(new BookmarkablePageLink<Void>("docs", DocsPage.class, pp).setVisible(entry.useDocs));

				if (entry.isFrozen) {
					item.add(WicketUtils.newImage("frozenIcon", "cold_16x16.png",
							getString("gb.isFrozen")));
				} else {
					item.add(WicketUtils.newClearPixel("frozenIcon").setVisible(false));
				}

				if (entry.isFederated) {
					item.add(WicketUtils.newImage("federatedIcon", "federated_16x16.png",
							getString("gb.isFederated")));
				} else {
					item.add(WicketUtils.newClearPixel("federatedIcon").setVisible(false));
				}
				switch (entry.accessRestriction) {
				case NONE:
					item.add(WicketUtils.newBlankImage("accessRestrictionIcon").setVisible(false));
					break;
				case PUSH:
					item.add(WicketUtils.newImage("accessRestrictionIcon", "lock_go_16x16.png",
							getAccessRestrictions().get(entry.accessRestriction)));
					break;
				case CLONE:
					item.add(WicketUtils.newImage("accessRestrictionIcon", "lock_pull_16x16.png",
							getAccessRestrictions().get(entry.accessRestriction)));
					break;
				case VIEW:
					item.add(WicketUtils.newImage("accessRestrictionIcon", "shield_16x16.png",
							getAccessRestrictions().get(entry.accessRestriction)));
					break;
				default:
					item.add(WicketUtils.newBlankImage("accessRestrictionIcon"));
				}

				item.add(new Label("repositoryOwner", StringUtils.isEmpty(entry.owner) ? "" : (entry.owner + " (" + getString("gb.owner") + ")")));
				
				
				UserModel user = GitBlitWebSession.get().getUser();
				Fragment repositoryLinks;				
				boolean showOwner = user != null && user.username.equalsIgnoreCase(entry.owner);
				if (showAdmin || showOwner) {
					repositoryLinks = new Fragment("repositoryLinks",
							showAdmin ? "repositoryAdminLinks" : "repositoryOwnerLinks", this);
					repositoryLinks.add(new BookmarkablePageLink<Void>("editRepository",
							EditRepositoryPage.class, WicketUtils
									.newRepositoryParameter(entry.name)));
					if (showAdmin) {
						Link<Void> deleteLink = new Link<Void>("deleteRepository") {

							private static final long serialVersionUID = 1L;

							@Override
							public void onClick() {
								if (GitBlit.self().deleteRepositoryModel(entry)) {
									info(MessageFormat.format(getString("gb.repositoryDeleted"), entry));
									// TODO dp.remove(entry);
								} else {
									error(MessageFormat.format(getString("gb.repositoryDeleteFailed"), entry));
								}
							}
						};
						deleteLink.add(new JavascriptEventConfirmation("onclick", MessageFormat.format(
								getString("gb.deleteRepository"), entry)));
						repositoryLinks.add(deleteLink);
					}
				} else {
					repositoryLinks = new Fragment("repositoryLinks", "repositoryUserLinks", this);
				}
				
				repositoryLinks.add(new BookmarkablePageLink<Void>("tree", TreePage.class,
						WicketUtils.newRepositoryParameter(entry.name)).setEnabled(entry.hasCommits));

				repositoryLinks.add(new BookmarkablePageLink<Void>("log", LogPage.class,
						WicketUtils.newRepositoryParameter(entry.name)).setEnabled(entry.hasCommits));

				item.add(repositoryLinks);
				
				String lastChange;
				if (entry.lastChange.getTime() == 0) {
					lastChange = "--";
				} else {
					lastChange = getTimeUtils().timeAgo(entry.lastChange);
				}
				Label lastChangeLabel = new Label("repositoryLastChange", lastChange);
				item.add(lastChangeLabel);
				WicketUtils.setCssClass(lastChangeLabel, getTimeUtils().timeAgoCss(entry.lastChange));
				
				if (entry.hasCommits) {
					// Existing repository
					item.add(new Label("repositorySize", entry.size).setVisible(showSize));
				} else {
					// New repository
					item.add(new Label("repositorySize", getString("gb.empty"))
							.setEscapeModelStrings(false));
				}
				
				item.add(new ExternalLink("syndication", SyndicationServlet.asLink("",
						entry.name, null, 0)));
				
				List<String> repositoryUrls = new ArrayList<String>();
				if (gitServlet) {
					// add the Gitblit repository url
					repositoryUrls.add(getRepositoryUrl(entry));
				}
				repositoryUrls.addAll(GitBlit.self().getOtherCloneUrls(entry.name));
				
				String primaryUrl = ArrayUtils.isEmpty(repositoryUrls) ? "" : repositoryUrls.remove(0);
				item.add(new RepositoryUrlPanel("repositoryCloneUrl", primaryUrl));
			}
		};
		add(dataView);

		// project activity
		// parameters
		int daysBack = WicketUtils.getDaysBack(params);
		if (daysBack < 1) {
			daysBack = 14;
		}
		String objectId = WicketUtils.getObject(params);

		List<Activity> recentActivity = ActivityUtils.getRecentActivity(repositories, 
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

	@Override
	protected void addDropDownMenus(List<PageRegistration> pages) {
		PageParameters params = getPageParameters();

		DropDownMenuRegistration projects = new DropDownMenuRegistration("gb.projects",
				ProjectPage.class);
		projects.menuItems.addAll(getProjectsMenu());
		pages.add(0, projects);

		DropDownMenuRegistration menu = new DropDownMenuRegistration("gb.filters",
				ProjectPage.class);
		// preserve time filter option on repository choices
		menu.menuItems.addAll(getRepositoryFilterItems(params));

		// preserve repository filter option on time choices
		menu.menuItems.addAll(getTimeFilterItems(params));

		if (menu.menuItems.size() > 0) {
			// Reset Filter
			menu.menuItems.add(new DropDownMenuItem(getString("gb.reset"), null, null));
		}

		pages.add(menu);
	}
	
	@Override
	protected List<ProjectModel> getProjectModels() {
		if (projectModels.isEmpty()) {
			final UserModel user = GitBlitWebSession.get().getUser();
			List<ProjectModel> projects = GitBlit.self().getProjectModels(user);
			projectModels.addAll(projects);
		}
		return projectModels;
	}
	
	private ProjectModel getProjectModel(String name) {
		for (ProjectModel project : getProjectModels()) {
			if (name.equalsIgnoreCase(project.name)) {
				return project;
			}
		}
		return null;
	}
	
	protected List<DropDownMenuItem> getProjectsMenu() {
		List<DropDownMenuItem> menu = new ArrayList<DropDownMenuItem>();
		List<ProjectModel> projects = getProjectModels();
		int maxProjects = 15;
		boolean showAllProjects = projects.size() > maxProjects;
		if (showAllProjects) {

			// sort by last changed
			Collections.sort(projects, new Comparator<ProjectModel>() {
				@Override
				public int compare(ProjectModel o1, ProjectModel o2) {
					return o2.lastChange.compareTo(o1.lastChange);
				}
			});

			// take most recent subset
			projects = projects.subList(0, maxProjects);

			// sort those by name
			Collections.sort(projects);
		}

		for (ProjectModel project : projects) {
			menu.add(new DropDownMenuItem(project.getDisplayName(), "p", project.name));
		}
		if (showAllProjects) {
			menu.add(new DropDownMenuItem());
			menu.add(new DropDownMenuItem("all projects", null, null));
		}
		return menu;
	}


	private String readMarkdown(String projectName, File projectMessage) {
		String message = "";
		if (projectMessage.exists()) {
			// Read user-supplied message
			try {
				FileInputStream fis = new FileInputStream(projectMessage);
				InputStreamReader reader = new InputStreamReader(fis,
						Constants.CHARACTER_ENCODING);
				message = MarkdownUtils.transformMarkdown(reader);
				reader.close();
			} catch (Throwable t) {
				message = getString("gb.failedToRead") + " " + projectMessage;
				warn(message, t);
			}
		}
		return message;
	}
}
