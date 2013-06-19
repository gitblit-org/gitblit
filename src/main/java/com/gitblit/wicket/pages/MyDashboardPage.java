/*
 * Copyright 2013 gitblit.com.
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.behavior.HeaderContributor;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Fragment;
import org.eclipse.jgit.lib.Constants;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.models.ProjectModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.MarkdownUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.ng.NgController;
import com.gitblit.wicket.panels.LinkPanel;

public class MyDashboardPage extends DashboardPage {

	public MyDashboardPage() {
		super();
		setup(null);
	}

	public MyDashboardPage(PageParameters params) {
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
			String messageSource = GitBlit.getString(Keys.web.loginMessage, "gitblit");
			String message = readMarkdown(messageSource, "login.mkd");
			Component repositoriesMessage = new Label("repositoriesMessage", message);
			add(repositoriesMessage.setEscapeModelStrings(false));
			add(new Label("activity").setVisible(false));
			add(new Label("repositoryTabs").setVisible(false));
			return;
		}

		// Load the markdown welcome message
		String messageSource = GitBlit.getString(Keys.web.repositoriesMessage, "gitblit");
		String message = readMarkdown(messageSource, "welcome.mkd");
		Component repositoriesMessage = new Label("repositoriesMessage", message)
				.setEscapeModelStrings(false).setVisible(message.length() > 0);
		add(repositoriesMessage);

		UserModel user = GitBlitWebSession.get().getUser();
		if (user == null) {
			user = UserModel.ANONYMOUS;
		}

		// parameters
		int daysBack = params == null ? 0 : WicketUtils.getDaysBack(params);
		if (daysBack < 1) {
			daysBack = 7;
		}
		Calendar c = Calendar.getInstance();
		c.add(Calendar.DATE, -1*daysBack);
		Date minimumDate = c.getTime();
		
		// build repo lists 
		List<RepositoryModel> starred = new ArrayList<RepositoryModel>();
		List<RepositoryModel> owned = new ArrayList<RepositoryModel>();
		List<RepositoryModel> active = new ArrayList<RepositoryModel>();

		for (RepositoryModel model : getRepositoryModels()) {
			if (model.isUsersPersonalRepository(user.username) || model.isOwner(user.username)) {
				owned.add(model);
			}
			
			if (user.getPreferences().isStarredRepository(model.name)) {
				starred.add(model);
			}
			
			if (model.isShowActivity() && model.lastChange.after(minimumDate)) {
				active.add(model);
			}
		}
		
		Comparator<RepositoryModel> lastUpdateSort = new Comparator<RepositoryModel>() {
			@Override
			public int compare(RepositoryModel o1, RepositoryModel o2) {
				return o2.lastChange.compareTo(o1.lastChange);
			}
		};
		
		Collections.sort(owned, lastUpdateSort);
		Collections.sort(starred, lastUpdateSort);
		Collections.sort(active, lastUpdateSort);
		
		Set<RepositoryModel> feed = new HashSet<RepositoryModel>();
		feed.addAll(starred);
		feed.addAll(owned);
		if (feed.isEmpty()) {
			feed.addAll(active);
		}
		
		addActivity(user, feed, starred.size() > 0 || owned.size() > 0, daysBack);
		
		Fragment repositoryTabs;
		if (UserModel.ANONYMOUS.equals(user)) {
			repositoryTabs = new Fragment("repositoryTabs", "anonymousTabsFragment", this);
		} else {
			repositoryTabs = new Fragment("repositoryTabs", "authenticatedTabsFragment", this);
		}
		
		add(repositoryTabs);
		
		Fragment projectList = createProjectList();
		repositoryTabs.add(projectList);
		
		// active repository list
		if (active.isEmpty()) {
			repositoryTabs.add(new Label("active").setVisible(false));
		} else {
			Fragment activeView = createNgList("active", "activeListFragment", "activeCtrl", active);
			repositoryTabs.add(activeView);
		}
		
		// starred repository list
		if (ArrayUtils.isEmpty(starred)) {
			repositoryTabs.add(new Label("starred").setVisible(false));
		} else {
			Fragment starredView = createNgList("starred", "starredListFragment", "starredCtrl", starred);
			repositoryTabs.add(starredView);
		}
		
		// owned repository list
		if (ArrayUtils.isEmpty(owned)) {
			repositoryTabs.add(new Label("owned").setVisible(false));
		} else {
			Fragment ownedView = createNgList("owned", "ownedListFragment", "ownedCtrl", owned);
			if (user.canCreate) {
				// create button
				ownedView.add(new LinkPanel("create", "btn btn-mini", getString("gb.newRepository"), EditRepositoryPage.class));
			} else {
				// no button
				ownedView.add(new Label("create").setVisible(false));
			}
			repositoryTabs.add(ownedView);
		}
	}
	
	private String readMarkdown(String messageSource, String resource) {
		String message = "";
		if (messageSource.equalsIgnoreCase("gitblit")) {
			// Read default message
			message = readDefaultMarkdown(resource);
		} else {
			// Read user-supplied message
			if (!StringUtils.isEmpty(messageSource)) {
				File file = GitBlit.getFileOrFolder(messageSource);
				if (file.exists()) {
					try {
						FileInputStream fis = new FileInputStream(file);
						InputStreamReader reader = new InputStreamReader(fis,
								Constants.CHARACTER_ENCODING);
						message = MarkdownUtils.transformMarkdown(reader);
						reader.close();
					} catch (Throwable t) {
						message = getString("gb.failedToRead") + " " + file;
						warn(message, t);
					}
				} else {
					message = messageSource + " " + getString("gb.isNotValidFile");
				}
			}
		}
		return message;
	}

	private String readDefaultMarkdown(String file) {
		String base = file.substring(0, file.lastIndexOf('.'));
		String ext = file.substring(file.lastIndexOf('.'));
		String lc = getLanguageCode();
		String cc = getCountryCode();

		// try to read file_en-us.ext, file_en.ext, file.ext
		List<String> files = new ArrayList<String>();
		if (!StringUtils.isEmpty(lc)) {
			if (!StringUtils.isEmpty(cc)) {
				files.add(base + "_" + lc + "-" + cc + ext);
				files.add(base + "_" + lc + "_" + cc + ext);
			}
			files.add(base + "_" + lc + ext);
		}
		files.add(file);

		for (String name : files) {
			String message;
			InputStreamReader reader = null;
			try {
				InputStream is = getClass().getResourceAsStream("/" + name);
				if (is == null) {
					continue;
				}
				reader = new InputStreamReader(is, Constants.CHARACTER_ENCODING);
				message = MarkdownUtils.transformMarkdown(reader);
				reader.close();
				return message;
			} catch (Throwable t) {
				message = MessageFormat.format(getString("gb.failedToReadMessage"), file);
				error(message, t, false);
				return message;
			} finally {
				if (reader != null) {
					try {
						reader.close();
					} catch (Exception e) {
					}
				}
			}			
		}
		return MessageFormat.format(getString("gb.failedToReadMessage"), file);
	}
	
	protected Fragment createProjectList() {
		String format = GitBlit.getString(Keys.web.datestampShortFormat, "MM/dd/yy");
		final DateFormat df = new SimpleDateFormat(format);
		df.setTimeZone(getTimeZone());
		List<ProjectModel> projects = GitBlit.self().getProjectModels(getRepositoryModels(), false);
		Collections.sort(projects, new Comparator<ProjectModel>() {
			@Override
			public int compare(ProjectModel o1, ProjectModel o2) {
				return o2.lastChange.compareTo(o1.lastChange);
			}
		});

		List<ProjectListItem> list = new ArrayList<ProjectListItem>();
		for (ProjectModel proj : projects) {
			if (proj.isUserProject() || proj.repositories.isEmpty()) {
				// exclude user projects from list
				continue;
			}
			ProjectListItem item = new ProjectListItem();
			item.p = proj.name;
			item.n = StringUtils.isEmpty(proj.title) ? proj.name : proj.title;
			item.i = proj.description;
			item.t = getTimeUtils().timeAgo(proj.lastChange);
			item.d = df.format(proj.lastChange);
			item.c = proj.repositories.size();
			list.add(item);
		}
		
		// inject an AngularJS controller with static data
		NgController ctrl = new NgController("projectListCtrl");
		ctrl.addVariable("projectList", list);
		add(new HeaderContributor(ctrl));
		
		Fragment fragment = new Fragment("projectList", "projectListFragment", this);
		return fragment;
	}
	
	protected class ProjectListItem implements Serializable {

		private static final long serialVersionUID = 1L;
		
		String p; // path
		String n; // name
		String t; // time ago
		String d; // last updated
		String i; // information/description
		long c;   // repository count
	}
}
