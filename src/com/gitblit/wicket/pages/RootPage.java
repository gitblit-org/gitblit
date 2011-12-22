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
import java.util.regex.Pattern;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.StatelessForm;
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

	public RootPage() {
		super();
	}

	public RootPage(PageParameters params) {
		super(params);
	}

	@Override
	protected void setupPage(String repositoryName, String pageName) {
		if (GitBlit.getBoolean(Keys.web.authenticateAdminPages, true)) {
			boolean allowAdmin = GitBlit.getBoolean(Keys.web.allowAdministration, false);
			showAdmin = allowAdmin && GitBlitWebSession.get().canAdmin();
			// authentication requires state and session
			setStatelessHint(false);
		} else {
			showAdmin = GitBlit.getBoolean(Keys.web.allowAdministration, false);
			if (GitBlit.getBoolean(Keys.web.authenticateViewPages, false)) {
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
		pages.add(new PageRegistration("gb.repositories", RepositoriesPage.class));
		pages.add(new PageRegistration("gb.activity", ActivityPage.class));
		if (showAdmin) {
			pages.add(new PageRegistration("gb.users", UsersPage.class));
		}
		if (showAdmin || showRegistrations) {
			pages.add(new PageRegistration("gb.federation", FederationPage.class));
		}
		NavigationPanel navPanel = new NavigationPanel("navPanel", getClass(), pages);
		add(navPanel);

		// login form
		StatelessForm<Void> loginForm = new StatelessForm<Void>("loginForm") {

			private static final long serialVersionUID = 1L;

			@Override
			public void onSubmit() {
				String username = RootPage.this.username.getObject();
				char[] password = RootPage.this.password.getObject().toCharArray();

				UserModel user = GitBlit.self().authenticate(username, password);
				if (user == null) {
					error("Invalid username or password!");
				} else if (user.username.equals(Constants.FEDERATION_USER)) {
					// disallow the federation user from logging in via the
					// web ui
					error("Invalid username or password!");
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
		if (GitBlit.getBoolean(Keys.web.authenticateViewPages, true)
				|| GitBlit.getBoolean(Keys.web.authenticateAdminPages, true)) {
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
				info("There is 1 federation proposal awaiting review.");
			} else if (pendingProposals > 1) {
				info(MessageFormat.format("There are {0} federation proposals awaiting review.",
						pendingProposals));
			}
		}

		super.setupPage(repositoryName, pageName);
	}

	private void loginUser(UserModel user) {
		if (user != null) {
			// Set the user into the session
			GitBlitWebSession.get().setUser(user);

			// Set Cookie
			if (GitBlit.getBoolean(Keys.web.allowCookieAuthentication, false)) {
				WebResponse response = (WebResponse) getRequestCycle().getResponse();
				GitBlit.self().setCookie(response, user);
			}

			if (!continueToOriginalDestination()) {
				// Redirect to home page
				setResponsePage(getApplication().getHomePage());
			}
		}
	}

	protected List<RepositoryModel> getRepositories(PageParameters params) {
		final UserModel user = GitBlitWebSession.get().getUser();
		if (params == null) {
			return GitBlit.self().getRepositoryModels(user);
		}

		String repositoryName = WicketUtils.getRepositoryName(params);
		String set = WicketUtils.getSet(params);
		String regex = WicketUtils.getRegEx(params);
		String team = WicketUtils.getTeam(params);

		List<RepositoryModel> models = null;

		if (!StringUtils.isEmpty(repositoryName)) {
			// try named repository
			models = new ArrayList<RepositoryModel>();
			RepositoryModel model = GitBlit.self().getRepositoryModel(repositoryName);
			if (user.canAccessRepository(model)) {
				models.add(model);
			}
		}

		// get all user accessible repositories
		if (models == null) {
			models = GitBlit.self().getRepositoryModels(user);
		}

		if (!StringUtils.isEmpty(regex)) {
			// filter the repositories by the regex
			List<RepositoryModel> accessible = GitBlit.self().getRepositoryModels(user);
			List<RepositoryModel> matchingModels = new ArrayList<RepositoryModel>();
			Pattern pattern = Pattern.compile(regex);
			for (RepositoryModel aModel : accessible) {
				if (pattern.matcher(aModel.name).find()) {
					matchingModels.add(aModel);
				}
			}
			models = matchingModels;
		} else if (!StringUtils.isEmpty(set)) {
			// filter the repositories by the specified sets
			List<String> sets = StringUtils.getStringsFromValue(set, ",");
			List<RepositoryModel> matchingModels = new ArrayList<RepositoryModel>();
			for (RepositoryModel model : models) {
				for (String curr : sets) {
					if (model.federationSets.contains(curr)) {
						matchingModels.add(model);
					}
				}
			}
			models = matchingModels;
		} else if (!StringUtils.isEmpty(team)) {
			// filter the repositories by the specified teams
			List<String> teams = StringUtils.getStringsFromValue(team, ",");
			
			// need TeamModels first
			List<TeamModel> teamModels = new ArrayList<TeamModel>();
			for (String name : teams) {
				TeamModel model = GitBlit.self().getTeamModel(name);
				if (model != null) {
					teamModels.add(model);
				}
			}
			
			// brute-force our way through finding the matching models
			List<RepositoryModel> matchingModels = new ArrayList<RepositoryModel>();
			for (RepositoryModel repositoryModel : models) {
				for (TeamModel teamModel : teamModels) {
					if (teamModel.hasRepository(repositoryModel.name)) {
						matchingModels.add(repositoryModel);
					}
				}
			}
			models = matchingModels;
		}
		return models;
	}
}
