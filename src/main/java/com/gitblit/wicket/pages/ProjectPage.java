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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.ExternalLink;

import com.gitblit.Keys;
import com.gitblit.models.Menu.MenuDivider;
import com.gitblit.models.Menu.MenuItem;
import com.gitblit.models.Menu.ParameterMenuItem;
import com.gitblit.models.NavLink.DropDownPageMenuNavLink;
import com.gitblit.models.NavLink;
import com.gitblit.models.ProjectModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.servlet.SyndicationServlet;
import com.gitblit.utils.MarkdownUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.CacheControl;
import com.gitblit.wicket.CacheControl.LastModified;
import com.gitblit.wicket.GitBlitWebApp;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.GitblitRedirectException;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.FilterableRepositoryList;

@CacheControl(LastModified.PROJECT)
public class ProjectPage extends DashboardPage {

	List<ProjectModel> projectModels = new ArrayList<ProjectModel>();

	public ProjectPage() {
		super();
		throw new GitblitRedirectException(GitBlitWebApp.get().getHomePage());
	}

	public ProjectPage(PageParameters params) {
		super(params);
		setup(params);
	}

	@Override
	protected Class<? extends BasePage> getRootNavPageClass() {
		return RepositoriesPage.class;
	}

	@Override
	protected void setLastModified() {
		if (getClass().isAnnotationPresent(CacheControl.class)) {
			CacheControl cacheControl = getClass().getAnnotation(CacheControl.class);
			switch (cacheControl.value()) {
			case PROJECT:
				String projectName = WicketUtils.getProjectName(getPageParameters());
				if (!StringUtils.isEmpty(projectName)) {
					ProjectModel project = getProjectModel(projectName);
					if (project != null) {
						setLastModified(project.lastChange);
					}
				}
				break;
			default:
				super.setLastModified();
			}
		}
	}

	private void setup(PageParameters params) {
		setupPage("", "");
		// check to see if we should display a login message
		boolean authenticateView = app().settings().getBoolean(Keys.web.authenticateViewPages, true);
		if (authenticateView && !GitBlitWebSession.get().isLoggedIn()) {
			authenticationError("Please login");
			return;
		}

		String projectName = params == null ? null : WicketUtils.getProjectName(params);
		if (StringUtils.isEmpty(projectName)) {
			throw new GitblitRedirectException(GitBlitWebApp.get().getHomePage());
		}

		ProjectModel project = getProjectModel(projectName);
		if (project == null) {
			throw new GitblitRedirectException(GitBlitWebApp.get().getHomePage());
		}

		add(new Label("projectTitle", project.getDisplayName()));
		add(new Label("projectDescription", project.description));

		String feedLink = SyndicationServlet.asLink(getRequest().getRelativePathPrefixToContextRoot(), projectName, null, 0);
		add(new ExternalLink("syndication", feedLink));

		add(WicketUtils.syndicationDiscoveryLink(SyndicationServlet.getTitle(project.getDisplayName(),
				null), feedLink));

		// project markdown message
		String pmessage = transformMarkdown(project.projectMarkdown);
		Component projectMessage = new Label("projectMessage", pmessage)
				.setEscapeModelStrings(false).setVisible(pmessage.length() > 0);
		add(projectMessage);

		// markdown message above repositories list
		String rmessage = transformMarkdown(project.repositoriesMarkdown);
		Component repositoriesMessage = new Label("repositoriesMessage", rmessage)
				.setEscapeModelStrings(false).setVisible(rmessage.length() > 0);
		add(repositoriesMessage);

		UserModel user = GitBlitWebSession.get().getUser();
		if (user == null) {
			user = UserModel.ANONYMOUS;
		}
		int daysBack = params == null ? 0 : WicketUtils.getDaysBack(params);
		if (daysBack < 1) {
			daysBack = app().settings().getInteger(Keys.web.activityDuration, 7);
		}
		// reset the daysback parameter so that we have a complete project
		// repository list.  the recent activity will be built up by the
		// reflog utils.
		if (params != null) {
			params.remove("db");
		}

		List<RepositoryModel> repositories = getRepositories(params);
		Collections.sort(repositories, new Comparator<RepositoryModel>() {
			@Override
			public int compare(RepositoryModel o1, RepositoryModel o2) {
				// reverse-chronological sort
				return o2.lastChange.compareTo(o1.lastChange);
			}
		});

		addActivity(user, repositories, getString("gb.recentActivity"), daysBack);

		if (repositories.isEmpty()) {
			add(new Label("repositoryList").setVisible(false));
		} else {
			FilterableRepositoryList repoList = new FilterableRepositoryList("repositoryList", repositories);
			repoList.setAllowCreate(user.canCreate(project.name + "/"));
			add(repoList);
		}
	}

	@Override
	protected void addDropDownMenus(List<NavLink> navLinks) {
		PageParameters params = getPageParameters();

		DropDownPageMenuNavLink menu = new DropDownPageMenuNavLink("gb.filters",
				ProjectPage.class);
		// preserve time filter option on repository choices
		menu.menuItems.addAll(getRepositoryFilterItems(params));

		// preserve repository filter option on time choices
		menu.menuItems.addAll(getTimeFilterItems(params));

		if (menu.menuItems.size() > 0) {
			// Reset Filter
			menu.menuItems.add(new ParameterMenuItem(getString("gb.reset"), "p", WicketUtils.getProjectName(params)));
		}

		navLinks.add(menu);

		DropDownPageMenuNavLink projects = new DropDownPageMenuNavLink("gb.projects",
				ProjectPage.class);
		projects.menuItems.addAll(getProjectsMenu());
		navLinks.add(projects);
	}

	@Override
	protected List<ProjectModel> getProjectModels() {
		if (projectModels.isEmpty()) {
			List<RepositoryModel> repositories = getRepositoryModels();
			List<ProjectModel> projects = app().projects().getProjectModels(repositories, false);
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

	protected List<MenuItem> getProjectsMenu() {
		List<MenuItem> menu = new ArrayList<MenuItem>();
		List<ProjectModel> projects = new ArrayList<ProjectModel>();
		for (ProjectModel model : getProjectModels()) {
			if (!model.isUserProject()) {
				projects.add(model);
			}
		}
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
			menu.add(new ParameterMenuItem(project.getDisplayName(), "p", project.name));
		}
		if (showAllProjects) {
			menu.add(new MenuDivider());
			menu.add(new ParameterMenuItem("all projects"));
		}
		return menu;
	}

	private String transformMarkdown(String markdown) {
		String message = "";
		if (!StringUtils.isEmpty(markdown)) {
			// Read user-supplied message
			try {
				message = MarkdownUtils.transformMarkdown(markdown);
			} catch (Throwable t) {
				message = getString("gb.failedToRead") + " " + markdown;
				warn(message, t);
			}
		}
		return message;
	}
}
