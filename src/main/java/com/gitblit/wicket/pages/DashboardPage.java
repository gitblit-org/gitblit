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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.behavior.HeaderContributor;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Fragment;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.models.DailyLogEntry;
import com.gitblit.models.Metric;
import com.gitblit.models.PushLogEntry;
import com.gitblit.models.RepositoryCommit;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.MarkdownUtils;
import com.gitblit.utils.PushLogUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebApp;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.PageRegistration;
import com.gitblit.wicket.PageRegistration.DropDownMenuItem;
import com.gitblit.wicket.PageRegistration.DropDownMenuRegistration;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.charting.GoogleChart;
import com.gitblit.wicket.charting.GoogleCharts;
import com.gitblit.wicket.charting.GooglePieChart;
import com.gitblit.wicket.ng.NgController;
import com.gitblit.wicket.panels.LinkPanel;
import com.gitblit.wicket.panels.PushesPanel;

public class DashboardPage extends RootPage {

	public DashboardPage() {
		super();
		setup(null);
	}

	public DashboardPage(PageParameters params) {
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
			add(new Label("repositoriesPanel"));
			return;
		}

		// Load the markdown welcome message
		String messageSource = GitBlit.getString(Keys.web.repositoriesMessage, "gitblit");
		String message = readMarkdown(messageSource, "welcome.mkd");
		Component repositoriesMessage = new Label("repositoriesMessage", message)
				.setEscapeModelStrings(false).setVisible(message.length() > 0);
		add(repositoriesMessage);

		UserModel user = GitBlitWebSession.get().getUser();

		Comparator<RepositoryModel> lastUpdateSort = new Comparator<RepositoryModel>() {
			@Override
			public int compare(RepositoryModel o1, RepositoryModel o2) {
				return o2.lastChange.compareTo(o1.lastChange);
			}
		};
		
		Map<String, RepositoryModel> reposMap = new HashMap<String, RepositoryModel>();

		// owned repositories 
		List<RepositoryModel> owned = new ArrayList<RepositoryModel>();
		if (user != null && !UserModel.ANONYMOUS.equals(user)) {
			for (RepositoryModel model : GitBlit.self().getRepositoryModels(user)) {
				reposMap.put(model.name, model);
				if (model.isUsersPersonalRepository(user.username) || model.isOwner(user.username)) {
					owned.add(model);
				}
			}
		}
		Collections.sort(owned, lastUpdateSort);

		// starred repositories
		List<RepositoryModel> starred = new ArrayList<RepositoryModel>();
		if (user != null && !UserModel.ANONYMOUS.equals(user)) {
			for (String name : user.getPreferences().getStarredRepositories()) {
				if (!reposMap.containsKey(name)) {
					RepositoryModel repo = GitBlit.self().getRepositoryModel(name);
					reposMap.put(name, repo);
				}
				starred.add(reposMap.get(name));
			}
		}
		Collections.sort(starred, lastUpdateSort);
				
		// parameters
		int daysBack = params == null ? 0 : WicketUtils.getDaysBack(params);
		if (daysBack < 1) {
			daysBack = 14;
		}
		Calendar c = Calendar.getInstance();
		c.add(Calendar.DATE, -1*daysBack);
		Date minimumDate = c.getTime();
		
		// active repositories (displayed for anonymous users)
		List<RepositoryModel> active = new ArrayList<RepositoryModel>();
		if (user == null || UserModel.ANONYMOUS.equals(user)) {
			List<RepositoryModel> list = GitBlit.self().getRepositoryModels(UserModel.ANONYMOUS);
			for (RepositoryModel model : list) {
				if (model.lastChange.after(minimumDate)) {
					active.add(model);
					reposMap.put(model.name, model);
				}
			}
			Collections.sort(active, lastUpdateSort);
		}
		
		// show pushlog feed
		List<PushLogEntry> pushes = new ArrayList<PushLogEntry>();
		for (RepositoryModel model : reposMap.values()) {
			Repository repository = GitBlit.self().getRepository(model.name);
			List<DailyLogEntry> entries = PushLogUtils.getDailyLogByRef(model.name, repository, minimumDate);
			pushes.addAll(entries);
			repository.close();
		}
		
		if (pushes.size() == 0) {
			if (reposMap.size() == 0) {
				add(new LinkPanel("pushes", null, "find some repositories", RepositoriesPage.class));
			} else {
				add(new Label("pushes", "all is quiet"));
			}
		} else {
			Collections.sort(pushes);
			add(new PushesPanel("pushes", pushes));
		}
		
		// add the nifty charts
		if (!ArrayUtils.isEmpty(pushes)) {
			GoogleCharts charts = createCharts(pushes);
			add(new HeaderContributor(charts));
		}
		
		// active repository list
		if (ArrayUtils.isEmpty(active)) {
			add(new Label("active").setVisible(false));
		} else {
			Fragment activeView = createNgList("active", "activeListFragment", "activeCtrl", active);
			add(activeView);
		}
		
		// starred repository list
		if (ArrayUtils.isEmpty(starred)) {
			add(new Label("starred").setVisible(false));
		} else {
			Fragment starredView = createNgList("starred", "starredListFragment", "starredCtrl", starred);
			add(starredView);
		}
		
		// owned repository list
		if (ArrayUtils.isEmpty(owned)) {
			add(new Label("owned").setVisible(false));
		} else {
			Fragment ownedView = createNgList("owned", "ownedListFragment", "ownedCtrl", owned);
			if (user.canCreate) {
				// create button
				ownedView.add(new LinkPanel("create", "btn btn-mini", getString("gb.newRepository"), EditRepositoryPage.class));
			} else {
				// no button
				ownedView.add(new Label("create").setVisible(false));
			}
			add(ownedView);
		}
	}
	
	protected Fragment createNgList(String wicketId, String fragmentId, String ngController, List<RepositoryModel> repositories) {
		String format = GitBlit.getString(Keys.web.datestampShortFormat, "MM/dd/yy");
		final DateFormat df = new SimpleDateFormat(format);
		df.setTimeZone(getTimeZone());

		Fragment fragment = new Fragment(wicketId, fragmentId, this);
		
		List<RepoListItem> list = new ArrayList<RepoListItem>();
		for (RepositoryModel repo : repositories) {
			String name = StringUtils.stripDotGit(repo.name); 
			String path = "";
			if (name.indexOf('/') > -1) {
				path = name.substring(0, name.lastIndexOf('/') + 1);
				name = name.substring(name.lastIndexOf('/') + 1);
			}
			
			RepoListItem item = new RepoListItem();
			item.n = name;
			item.p = path;
			item.r = repo.name;
			item.s = GitBlit.self().getStarCount(repo);
			item.t = getTimeUtils().timeAgo(repo.lastChange);
			item.d = df.format(repo.lastChange);
			item.c = StringUtils.getColor(StringUtils.stripDotGit(repo.name));
			item.wc = repo.isBare ? 0 : 1;
			list.add(item);
		}
		
		// inject an AngularJS controller with static data
		NgController ctrl = new NgController(ngController);
		ctrl.addVariable(wicketId, list);
		add(new HeaderContributor(ctrl));
		
		return fragment;
	}

	@Override
	protected void addDropDownMenus(List<PageRegistration> pages) {
		PageParameters params = getPageParameters();

		DropDownMenuRegistration menu = new DropDownMenuRegistration("gb.filters",
				GitBlitWebApp.HOME_PAGE_CLASS);
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
	
	/**
	 * Creates the daily activity line chart, the active repositories pie chart,
	 * and the active authors pie chart
	 * 
	 * @param recentPushes
	 * @return
	 */
	private GoogleCharts createCharts(List<PushLogEntry> recentPushes) {
		// activity metrics
		Map<String, Metric> repositoryMetrics = new HashMap<String, Metric>();
		Map<String, Metric> authorMetrics = new HashMap<String, Metric>();

		// aggregate repository and author metrics
		for (PushLogEntry push : recentPushes) {

			// aggregate repository metrics
			String repository = StringUtils.stripDotGit(push.repository);
			if (!repositoryMetrics.containsKey(repository)) {
				repositoryMetrics.put(repository, new Metric(repository));
			}
			repositoryMetrics.get(repository).count += 1;
			
			for (RepositoryCommit commit : push.getCommits()) {
				String author = commit.getAuthorIdent().getName();
				if (!authorMetrics.containsKey(author)) {
					authorMetrics.put(author, new Metric(author));
				}
				authorMetrics.get(author).count += 1;
			}
		}

		// build google charts
		GoogleCharts charts = new GoogleCharts();

		// active repositories pie chart
		GoogleChart chart = new GooglePieChart("chartRepositories", getString("gb.activeRepositories"),
				getString("gb.repository"), getString("gb.commits"));
		for (Metric metric : repositoryMetrics.values()) {
			chart.addValue(metric.name, metric.count);
		}
		chart.setShowLegend(false);
		charts.addChart(chart);

		// active authors pie chart
		chart = new GooglePieChart("chartAuthors", getString("gb.activeAuthors"),
				getString("gb.author"), getString("gb.commits"));
		for (Metric metric : authorMetrics.values()) {
			chart.addValue(metric.name, metric.count);
		}
		chart.setShowLegend(false);
		charts.addChart(chart);

		return charts;
	}
	
	class RepoListItem implements Serializable {

		private static final long serialVersionUID = 1L;
		
		String r; // repository
		String n; // name
		String p; // project/path
		String t; // time ago
		String d; // last updated
		long s; // stars
		String c; // html color
		int wc; // working copy, 1 = true
	}
}
