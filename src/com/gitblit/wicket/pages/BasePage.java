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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.apache.wicket.MarkupContainer;
import org.apache.wicket.PageParameters;
import org.apache.wicket.RestartResponseAtInterceptPageException;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.protocol.http.WebRequest;
import org.apache.wicket.protocol.http.WebResponse;
import org.apache.wicket.protocol.http.servlet.ServletWebRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.FederationStrategy;
import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.models.UserModel;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.LinkPanel;

public abstract class BasePage extends WebPage {

	private final Logger logger;

	public BasePage() {
		super();
		logger = LoggerFactory.getLogger(getClass());
		loginByCookie();
	}

	public BasePage(PageParameters params) {
		super(params);
		logger = LoggerFactory.getLogger(getClass());
		loginByCookie();
	}

	private void loginByCookie() {
		if (!GitBlit.getBoolean(Keys.web.allowCookieAuthentication, false)) {
			return;
		}
		UserModel user = null;

		// Grab cookie from Browser Session
		Cookie[] cookies = ((WebRequest) getRequestCycle().getRequest()).getCookies();
		if (cookies != null && cookies.length > 0) {
			user = GitBlit.self().authenticate(cookies);
		}

		// Login the user
		if (user != null) {
			// Set the user into the session
			GitBlitWebSession.get().setUser(user);

			// Set Cookie
			WebResponse response = (WebResponse) getRequestCycle().getResponse();
			GitBlit.self().setCookie(response, user);
		}
	}

	protected void setupPage(String repositoryName, String pageName) {
		if (repositoryName != null && repositoryName.trim().length() > 0) {
			add(new Label("title", getServerName() + " - " + repositoryName));
		} else {
			add(new Label("title", getServerName()));
		}

		// Feedback panel for info, warning, and non-fatal error messages
		add(new FeedbackPanel("feedback"));

		// footer
		if (GitBlit.getBoolean(Keys.web.authenticateViewPages, true)
				|| GitBlit.getBoolean(Keys.web.authenticateAdminPages, true)) {
			UserFragment userFragment = new UserFragment("userPanel", "userFragment", BasePage.this);
			add(userFragment);
		} else {
			add(new Label("userPanel", ""));
		}

		add(new Label("gbVersion", "v" + Constants.VERSION));
		if (GitBlit.getBoolean(Keys.web.aggressiveHeapManagement, false)) {
			System.gc();
		}
	}

	protected Map<AccessRestrictionType, String> getAccessRestrictions() {
		Map<AccessRestrictionType, String> map = new LinkedHashMap<AccessRestrictionType, String>();
		for (AccessRestrictionType type : AccessRestrictionType.values()) {
			switch (type) {
			case NONE:
				map.put(type, getString("gb.notRestricted"));
				break;
			case PUSH:
				map.put(type, getString("gb.pushRestricted"));
				break;
			case CLONE:
				map.put(type, getString("gb.cloneRestricted"));
				break;
			case VIEW:
				map.put(type, getString("gb.viewRestricted"));
				break;
			}
		}
		return map;
	}
	
	protected Map<FederationStrategy, String> getFederationTypes() {
		Map<FederationStrategy, String> map = new LinkedHashMap<FederationStrategy, String>();
		for (FederationStrategy type : FederationStrategy.values()) {
			switch (type) {
			case EXCLUDE:
				map.put(type, getString("gb.excludeFromFederation"));
				break;
			case FEDERATE_THIS:
				map.put(type, getString("gb.federateThis"));
				break;
			case FEDERATE_ORIGIN:
				map.put(type, getString("gb.federateOrigin"));
				break;
			}
		}
		return map;
	}

	protected TimeZone getTimeZone() {
		return GitBlit.getBoolean(Keys.web.useClientTimezone, false) ? GitBlitWebSession.get()
				.getTimezone() : TimeZone.getDefault();
	}

	protected String getServerName() {
		ServletWebRequest servletWebRequest = (ServletWebRequest) getRequest();
		HttpServletRequest req = servletWebRequest.getHttpServletRequest();
		return req.getServerName();
	}

	public void warn(String message, Throwable t) {
		logger.warn(message, t);
	}

	public void error(String message, boolean redirect) {
		logger.error(message);
		if (redirect) {
			GitBlitWebSession.get().cacheErrorMessage(message);
			throw new RestartResponseException(getApplication().getHomePage());
		} else {
			super.error(message);
		}
	}

	public void error(String message, Throwable t, boolean redirect) {
		logger.error(message, t);
		if (redirect) {
			GitBlitWebSession.get().cacheErrorMessage(message);
			throw new RestartResponseException(getApplication().getHomePage());
		} else {
			super.error(message);
		}
	}

	public void authenticationError(String message) {
		logger.error(message);
		if (GitBlitWebSession.get().isLoggedIn()) {
			error(message, true);
		} else {
			throw new RestartResponseAtInterceptPageException(LoginPage.class);
		}
	}

	/**
	 * Panel fragment for displaying login or logout/change_password links.
	 * 
	 */
	static class UserFragment extends Fragment {

		private static final long serialVersionUID = 1L;

		public UserFragment(String id, String markupId, MarkupContainer markupProvider) {
			super(id, markupId, markupProvider);

			if (GitBlitWebSession.get().isLoggedIn()) {
				// username, logout, and change password
				add(new Label("username", GitBlitWebSession.get().getUser().toString() + ":"));
				add(new LinkPanel("loginLink", null, markupProvider.getString("gb.logout"),
						LogoutPage.class));
				// quick and dirty hack for showing a separator
				add(new Label("separator", "|"));
				add(new BookmarkablePageLink<Void>("changePasswordLink", ChangePasswordPage.class));
			} else {
				// login
				add(new Label("username").setVisible(false));
				add(new LinkPanel("loginLink", null, markupProvider.getString("gb.login"),
						LoginPage.class));
				add(new Label("separator").setVisible(false));
				add(new Label("changePasswordLink").setVisible(false));
			}
		}
	}
}
