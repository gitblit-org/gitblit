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

import org.apache.wicket.RestartResponseException;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.StatelessForm;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.protocol.http.WebResponse;

import com.gitblit.Constants;
import com.gitblit.GitBlit;
import com.gitblit.GitBlitException;
import com.gitblit.Keys;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;

public class ChangePasswordPage extends WebPage {

	IModel<String> password = new Model<String>("");
	IModel<String> confirmPassword = new Model<String>("");

	public ChangePasswordPage() {
		super();

		if (!GitBlitWebSession.get().isLoggedIn()) {
			// Change password requires a login
			throw new RestartResponseException(getApplication().getHomePage());
		}

		if (!GitBlit.getBoolean(Keys.web.authenticateAdminPages, true) && !GitBlit.getBoolean(Keys.web.authenticateViewPages, false)) {
			// no authentication enabled
			throw new RestartResponseException(getApplication().getHomePage());
		}

		add(new Label("title", GitBlit.getString(Keys.web.siteName, Constants.NAME)));
		add(new Label("name", getString("gb.changePassword") + ": "
				+ GitBlitWebSession.get().getUser().username));

		StatelessForm<Void> form = new StatelessForm<Void>("passwordForm") {

			private static final long serialVersionUID = 1L;

			@Override
			public void onSubmit() {
				String password = ChangePasswordPage.this.password.getObject();
				String confirmPassword = ChangePasswordPage.this.confirmPassword.getObject();
				// ensure passwords match
				if (!password.equals(confirmPassword)) {
					error("Passwords do not match!");
					return;
				}

				// ensure password satisfies minimum length requirement
				int minLength = GitBlit.getInteger(Keys.realm.minPasswordLength, 5);
				if (minLength < 4) {
					minLength = 4;
				}
				if (password.length() < minLength) {
					error(MessageFormat.format(
							"Password is too short. Minimum length is {0} characters.", minLength));
					return;
				}

				// convert to MD5 digest, if appropriate
				String type = GitBlit.getString(Keys.realm.passwordStorage, "md5");
				if (type.equalsIgnoreCase("md5")) {
					// store MD5 digest of password
					password = StringUtils.MD5_TYPE + StringUtils.getMD5(password);
				}

				UserModel user = GitBlitWebSession.get().getUser();
				user.password = password;
				try {
					GitBlit.self().updateUserModel(user.username, user, false);
					if (GitBlit.getBoolean(Keys.web.allowCookieAuthentication, false)) {
						WebResponse response = (WebResponse) getRequestCycle().getResponse();
						GitBlit.self().setCookie(response, user);
					}
				} catch (GitBlitException e) {
					error(e.getMessage());
					return;
				}
				setRedirect(false);
				info("Password successfully changed.");
				setResponsePage(RepositoriesPage.class);
			}
		};
		PasswordTextField passwordField = new PasswordTextField("password", password);
		passwordField.setResetPassword(false);
		form.add(passwordField);
		PasswordTextField confirmPasswordField = new PasswordTextField("confirmPassword",
				confirmPassword);
		confirmPasswordField.setResetPassword(false);
		form.add(confirmPasswordField);
		form.add(new FeedbackPanel("feedback"));
		
		form.add(new Button("save"));
		Button cancel = new Button("cancel"){          
			private static final long serialVersionUID = 1L;

			@Override
			public void onSubmit() {
                setResponsePage(RepositoriesPage.class);
            }
        };
        cancel.setDefaultFormProcessing(false);
        form.add(cancel);
        
		add(form);
	}
}
