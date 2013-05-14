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
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.protocol.http.WebResponse;

import com.gitblit.Constants;
import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.PageRegistration;
import com.gitblit.wicket.PageRegistration.DropDownMenuItem;
import com.gitblit.wicket.SessionlessForm;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.NavigationPanel;

/**
 * Root page is a topbar, navigable page like Repositories, Users, or
 * Federation.
 * 
 * @author James Moger
 * 
 */
public abstract class RootPage extends BasePage {

	boolean showAdmin;

	IModel<String> username = new Model<String>("");
	IModel<String> password = new Model<String>("");
	List<RepositoryModel> repositoryModels = new ArrayList<RepositoryModel>();

	public RootPage() {
		super();
	}

	public RootPage(PageParameters params) {
		super(params);
	}

	@Override
	protected void setupPage(String repositoryName, String pageName) {
		boolean authenticateView = GitBlit.getBoolean(Keys.web.authenticateViewPages, false);
		boolean authenticateAdmin = GitBlit.getBoolean(Keys.web.authenticateAdminPages, true);
		boolean allowAdmin = GitBlit.getBoolean(Keys.web.allowAdministration, true);

		if (authenticateAdmin) {
			showAdmin = allowAdmin && GitBlitWebSession.get().canAdmin();
			// authentication requires state and session
			setStatelessHint(false);
		} else {
			showAdmin = allowAdmin;
			if (authenticateView) {
				// authentication requires state and session
				setStatelessHint(false);
			} else {
				// no authentication required, no state and no session required
				setStatelessHint(true);
			}
		}
		boolean showRegistrations = GitBlit.canFederate()
				&& GitBlit.getBoolean(Keys.web.showFederationRegistrations, false);

		// navigation links
		List<PageRegistration> pages = new ArrayList<PageRegistration>();
		pages.add(new PageRegistration("gb.repositories", RepositoriesPage.class,
				getRootPageParameters()));
		pages.add(new PageRegistration("gb.activity", ActivityPage.class, getRootPageParameters()));
		if (GitBlit.getBoolean(Keys.web.allowLuceneIndexing, true)) {
			pages.add(new PageRegistration("gb.search", LuceneSearchPage.class));
		}
		if (showAdmin) {
			pages.add(new PageRegistration("gb.users", UsersPage.class));
		}
		if (showAdmin || showRegistrations) {
			pages.add(new PageRegistration("gb.federation", FederationPage.class));
		}
		if (showAdmin || showRegistrations) {
			pages.add(new PageRegistration("gb.system", SystemManagementPage.class));
		}

		if (!authenticateView || (authenticateView && GitBlitWebSession.get().isLoggedIn())) {
			addDropDownMenus(pages);
		}

		NavigationPanel navPanel = new NavigationPanel("navPanel", getClass(), pages);
		add(navPanel);

		// login form
		SessionlessForm<Void> loginForm = new SessionlessForm<Void>("loginForm", getClass(), getPageParameters()) {

			private static final long serialVersionUID = 1L;

			@Override
			public void onSubmit() {
				String username = RootPage.this.username.getObject();
				char[] password = RootPage.this.password.getObject().toCharArray();

				UserModel user = GitBlit.self().authenticate(username, password);
				if (user == null) {
					error(getString("gb.invalidUsernameOrPassword"));
				} else if (user.username.equals(Constants.FEDERATION_USER)) {
					// disallow the federation user from logging in via the
					// web ui
					error(getString("gb.invalidUsernameOrPassword"));
					user = null;
				} else {
					loginUser(user);
				}
			}
		};
		TextField<String> unameField = new TextField<String>("username", username);
		WicketUtils.setInputPlaceholder(unameField, getString("gb.username"));
		loginForm.add(unameField);
		PasswordTextField pwField = new PasswordTextField("password", password);
		WicketUtils.setInputPlaceholder(pwField, getString("gb.password"));
		loginForm.add(pwField);
		add(loginForm);

		if (authenticateView || authenticateAdmin) {
			loginForm.setVisible(!GitBlitWebSession.get().isLoggedIn());
		} else {
			loginForm.setVisible(false);
		}

		// display an error message cached from a redirect
		String cachedMessage = GitBlitWebSession.get().clearErrorMessage();
		if (!StringUtils.isEmpty(cachedMessage)) {
			error(cachedMessage);
		} else if (showAdmin) {
			int pendingProposals = GitBlit.self().getPendingFederationProposals().size();
			if (pendingProposals == 1) {
				info(getString("gb.OneProposalToReview"));
			} else if (pendingProposals > 1) {
				info(MessageFormat.format(getString("gb.nFederationProposalsToReview"),
						pendingProposals));
			}
		}

		super.setupPage(repositoryName, pageName);
	}

	private PageParameters getRootPageParameters() {
		if (reusePageParameters()) {
			PageParameters pp = getPageParameters();
			if (pp != null) {
				PageParameters params = new PageParameters(pp);
				// remove named project parameter
				params.remove("p");

				// remove named repository parameter
				params.remove("r");

				// remove named user parameter
				params.remove("user");

				// remove days back parameter if it is the default value
				if (params.containsKey("db")
						&& params.getInt("db") == GitBlit.getInteger(Keys.web.activityDuration, 14)) {
					params.remove("db");
				}
				return params;
			}			
		}
		return null;
	}

	protected boolean reusePageParameters() {
		return false;
	}

	private void loginUser(UserModel user) {
		if (user != null) {
			// Set the user into the session
			GitBlitWebSession session = GitBlitWebSession.get();
			// issue 62: fix session fixation vulnerability
			session.replaceSession();
			session.setUser(user);

			// Set Cookie
			if (GitBlit.getBoolean(Keys.web.allowCookieAuthentication, false)) {
				WebResponse response = (WebResponse) getRequestCycle().getResponse();
				GitBlit.self().setCookie(response, user);
			}

			if (!session.continueRequest()) {
				PageParameters params = getPageParameters();
				if (params == null) {
					// redirect to this page
					setResponsePage(getClass());
				} else {
					// Strip username and password and redirect to this page
					params.remove("username");
					params.remove("password");
					setResponsePage(getClass(), params);
				}
			}
		}
	}
	
	protected List<RepositoryModel> getRepositoryModels() {
		if (repositoryModels.isEmpty()) {
			final UserModel user = GitBlitWebSession.get().getUser();
			List<RepositoryModel> repositories = GitBlit.self().getRepositoryModels(user);
			repositoryModels.addAll(repositories);
			Collections.sort(repositoryModels);
		}
		return repositoryModels;
	}

	protected void addDropDownMenus(List<PageRegistration> pages) {

	}

	protected List<DropDownMenuItem> getRepositoryFilterItems(PageParameters params) {
		final UserModel user = GitBlitWebSession.get().getUser();
		Set<DropDownMenuItem> filters = new LinkedHashSet<DropDownMenuItem>();
		List<RepositoryModel> repositories = getRepositoryModels();

		// accessible repositories by federation set
		Map<String, AtomicInteger> setMap = new HashMap<String, AtomicInteger>();
		for (RepositoryModel repository : repositories) {
			for (String set : repository.federationSets) {
				String key = set.toLowerCase();
				if (setMap.containsKey(key)) {
					setMap.get(key).incrementAndGet();
				} else {
					setMap.put(key, new AtomicInteger(1));
				}
			}
		}
		if (setMap.size() > 0) {
			List<String> sets = new ArrayList<String>(setMap.keySet());
			Collections.sort(sets);
			for (String set : sets) {
				filters.add(new DropDownMenuItem(MessageFormat.format("{0} ({1})", set,
						setMap.get(set).get()), "set", set, params));
			}
			// divider
			filters.add(new DropDownMenuItem());
		}

		// user's team memberships
		if (user != null && user.teams.size() > 0) {
			List<TeamModel> teams = new ArrayList<TeamModel>(user.teams);
			Collections.sort(teams);
			for (TeamModel team : teams) {
				filters.add(new DropDownMenuItem(MessageFormat.format("{0} ({1})", team.name,
						team.repositories.size()), "team", team.name, params));
			}
			// divider
			filters.add(new DropDownMenuItem());
		}

		// custom filters
		String customFilters = GitBlit.getString(Keys.web.customFilters, null);
		if (!StringUtils.isEmpty(customFilters)) {
			boolean addedExpression = false;
			List<String> expressions = StringUtils.getStringsFromValue(customFilters, "!!!");
			for (String expression : expressions) {
				if (!StringUtils.isEmpty(expression)) {
					addedExpression = true;
					filters.add(new DropDownMenuItem(null, "x", expression, params));
				}
			}
			// if we added any custom expressions, add a divider
			if (addedExpression) {
				filters.add(new DropDownMenuItem());
			}
		}
		return new ArrayList<DropDownMenuItem>(filters);
	}

	protected List<DropDownMenuItem> getTimeFilterItems(PageParameters params) {
		// days back choices - additive parameters
		int daysBack = GitBlit.getInteger(Keys.web.activityDuration, 14);
		if (daysBack < 1) {
			daysBack = 14;
		}
		List<DropDownMenuItem> items = new ArrayList<DropDownMenuItem>();
		Set<Integer> choicesSet = new HashSet<Integer>(Arrays.asList(daysBack, 14, 28, 60, 90, 180));
		List<Integer> choices = new ArrayList<Integer>(choicesSet);
		Collections.sort(choices);
		String lastDaysPattern = getString("gb.lastNDays");
		for (Integer db : choices) {
			String txt = MessageFormat.format(lastDaysPattern, db);
			items.add(new DropDownMenuItem(txt, "db", db.toString(), params));
		}
		items.add(new DropDownMenuItem());
		return items;
	}

	protected List<RepositoryModel> getRepositories(PageParameters params) {
		if (params == null) {
			return getRepositoryModels();
		}

		boolean hasParameter = false;
		String projectName = WicketUtils.getProjectName(params);
		String userName = WicketUtils.getUsername(params);
		if (StringUtils.isEmpty(projectName)) {
			if (!StringUtils.isEmpty(userName)) {
				projectName = "~" + userName;
			}
		}
		String repositoryName = WicketUtils.getRepositoryName(params);
		String set = WicketUtils.getSet(params);
		String regex = WicketUtils.getRegEx(params);
		String team = WicketUtils.getTeam(params);
		int daysBack = params.getInt("db", 0);

		List<RepositoryModel> availableModels = getRepositoryModels();
		Set<RepositoryModel> models = new HashSet<RepositoryModel>();

		if (!StringUtils.isEmpty(repositoryName)) {
			// try named repository
			hasParameter = true;
			for (RepositoryModel model : availableModels) {
				if (model.name.equalsIgnoreCase(repositoryName)) {
					models.add(model);
					break;
				}
			}
		}

		if (!StringUtils.isEmpty(projectName)) {
			// try named project
			hasParameter = true;			
			if (projectName.equalsIgnoreCase(GitBlit.getString(Keys.web.repositoryRootGroupName, "main"))) {
				// root project/group
				for (RepositoryModel model : availableModels) {
					if (model.name.indexOf('/') == -1) {
						models.add(model);
					}
				}
			} else {
				// named project/group
				String group = projectName.toLowerCase() + "/";
				for (RepositoryModel model : availableModels) {
					if (model.name.toLowerCase().startsWith(group)) {
						models.add(model);
					}
				}
			}
		}

		if (!StringUtils.isEmpty(regex)) {
			// filter the repositories by the regex
			hasParameter = true;
			Pattern pattern = Pattern.compile(regex);
			for (RepositoryModel model : availableModels) {
				if (pattern.matcher(model.name).find()) {
					models.add(model);
				}
			}
		}

		if (!StringUtils.isEmpty(set)) {
			// filter the repositories by the specified sets
			hasParameter = true;
			List<String> sets = StringUtils.getStringsFromValue(set, ",");
			for (RepositoryModel model : availableModels) {
				for (String curr : sets) {
					if (model.federationSets.contains(curr)) {
						models.add(model);
					}
				}
			}
		}

		if (!StringUtils.isEmpty(team)) {
			// filter the repositories by the specified teams
			hasParameter = true;
			List<String> teams = StringUtils.getStringsFromValue(team, ",");

			// need TeamModels first
			List<TeamModel> teamModels = new ArrayList<TeamModel>();
			for (String name : teams) {
				TeamModel teamModel = GitBlit.self().getTeamModel(name);
				if (teamModel != null) {
					teamModels.add(teamModel);
				}
			}

			// brute-force our way through finding the matching models
			for (RepositoryModel repositoryModel : availableModels) {
				for (TeamModel teamModel : teamModels) {
					if (teamModel.hasRepositoryPermission(repositoryModel.name)) {
						models.add(repositoryModel);
					}
				}
			}
		}

		if (!hasParameter) {
			models.addAll(availableModels);
		}

		// time-filter the list
		if (daysBack > 0) {
			Calendar cal = Calendar.getInstance();
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			cal.set(Calendar.MILLISECOND, 0);
			cal.add(Calendar.DATE, -1 * daysBack);
			Date threshold = cal.getTime();
			Set<RepositoryModel> timeFiltered = new HashSet<RepositoryModel>();
			for (RepositoryModel model : models) {
				if (model.lastChange.after(threshold)) {
					timeFiltered.add(model);
				}
			}
			models = timeFiltered;
		}
		
		List<RepositoryModel> list = new ArrayList<RepositoryModel>(models);
		Collections.sort(list);
		return list;
	}
}
