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

import javax.servlet.http.Cookie;

import org.apache.wicket.PageParameters;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.StatelessForm;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.protocol.http.WebRequest;
import org.apache.wicket.protocol.http.WebResponse;

import com.gitblit.Constants;
import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.models.UserModel;
import com.gitblit.wicket.GitBlitWebSession;

public class LoginPage extends WebPage {

	IModel<String> username = new Model<String>("");
	IModel<String> password = new Model<String>("");

	public LoginPage(PageParameters params) {
		super(params);

		// If we are already logged in because user directly accessed
		// the login url, redirect to the home page
		if (GitBlitWebSession.get().isLoggedIn()) {
			throw new RestartResponseException(getApplication().getHomePage());
		}

		if (GitBlit.getBoolean(Keys.web.allowCookieAuthentication, false)) {
			loginByCookie();
		}

		add(new Label("title", GitBlit.getString(Keys.web.siteName, Constants.NAME)));
		add(new Label("name", GitBlit.getString(Keys.web.siteName, Constants.NAME)));

		StatelessForm<Void> loginForm = new StatelessForm<Void>("loginForm") {

			private static final long serialVersionUID = 1L;

			@Override
			public void onSubmit() {
				String username = LoginPage.this.username.getObject();
				char[] password = LoginPage.this.password.getObject().toCharArray();

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
		loginForm.add(new TextField<String>("username", username));
		loginForm.add(new PasswordTextField("password", password));
		loginForm.add(new FeedbackPanel("feedback"));
		add(loginForm);
	}

	private void loginByCookie() {
		UserModel user = null;

		// Grab cookie from Browser Session
		Cookie[] cookies = ((WebRequest) getRequestCycle().getRequest()).getCookies();
		if (cookies != null && cookies.length > 0) {
			user = GitBlit.self().authenticate(cookies);
		}

		// Login the user
		loginUser(user);
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
}
