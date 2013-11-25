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

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.protocol.http.WebRequest;
import org.apache.wicket.protocol.http.WebResponse;

import com.gitblit.Keys;
import com.gitblit.models.UserModel;
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
		if (session.isLoggedIn() && !session.isSessionInvalidated()) {
			// already have a session, refresh usermodel to pick up
			// any changes to permissions or roles (issue-186)
			UserModel user = app().users().getUserModel(session.getUser().username);
			session.setUser(user);
			return;
		}

		// try to authenticate by servlet request
		HttpServletRequest httpRequest = ((WebRequest) getRequestCycle().getRequest())
				.getHttpServletRequest();
		UserModel user = app().authentication().authenticate(httpRequest);

		// Login the user
		if (user != null) {
			// issue 62: fix session fixation vulnerability
			session.replaceSession();
			session.setUser(user);

			// Set Cookie
			WebResponse response = (WebResponse) getRequestCycle().getResponse();
			app().authentication().setCookie(response.getHttpServletResponse(), user);

			session.continueRequest();
		}
	}
}
