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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.protocol.http.WebRequest;
import org.apache.wicket.protocol.http.WebResponse;

import com.gitblit.Constants;
import com.gitblit.Constants.AuthenticationType;
import com.gitblit.Keys;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebApp;
import com.gitblit.wicket.GitBlitWebSession;

public abstract class SessionPage extends WebPage {

	public SessionPage() {
		super();
		login();
	}

	public SessionPage(final PageParameters params) {
		super(params);
		login();
	}

	protected String [] getEncodings() {
		return app().settings().getStrings(Keys.web.blobEncodings).toArray(new String[0]);
	}

	protected GitBlitWebApp app() {
		return GitBlitWebApp.get();
	}

	private void login() {
		GitBlitWebSession session = GitBlitWebSession.get();
		HttpServletRequest request = ((WebRequest) getRequest()).getHttpServletRequest();
		HttpServletResponse response = ((WebResponse) getResponse()).getHttpServletResponse();

		if (session.isLoggedIn() && !session.isSessionInvalidated()) {
			// already have a session, refresh usermodel to pick up
			// any changes to permissions or roles (issue-186)
			UserModel user = app().users().getUserModel(session.getUser().username);

			if (user == null || user.disabled) {
				// user was deleted/disabled during session
				app().authentication().logout(request, response, user);
				session.setUser(null);
				session.invalidateNow();
				return;
			}

			// validate cookie during session (issue-361)
			if (user != null && app().settings().getBoolean(Keys.web.allowCookieAuthentication, true)) {
				String requestCookie = app().authentication().getCookie(request);
				if (!StringUtils.isEmpty(requestCookie) && !StringUtils.isEmpty(user.cookie)) {
					if (!requestCookie.equals(user.cookie)) {
						// cookie was changed during our session
						app().authentication().logout(request, response, user);
						session.setUser(null);
						session.invalidateNow();
						return;
					}
				}
			}
			session.setUser(user);
			return;
		}

		// try to authenticate by servlet request
		UserModel user = app().authentication().authenticate(request);

		// Login the user
		if (user != null) {
			// preserve the authentication type across session replacement
			AuthenticationType authenticationType = (AuthenticationType) request.getSession()
					.getAttribute(Constants.AUTHENTICATION_TYPE);

			// issue 62: fix session fixation vulnerability
			// but only if authentication was done in the container.
			// It avoid double change of session, that some authentication method
			// don't like
			if (AuthenticationType.CONTAINER != authenticationType) {
				session.replaceSession();
			}			
			session.setUser(user);

			request.getSession().setAttribute(Constants.AUTHENTICATION_TYPE, authenticationType);

			// Set Cookie
			app().authentication().setCookie(request, response, user);

			session.continueRequest();
		}
	}
}
