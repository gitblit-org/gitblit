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
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.StatelessForm;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.protocol.http.WebResponse;

import com.gitblit.GitBlit;
import com.gitblit.GitBlitException;
import com.gitblit.Keys;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;

public class ChangePasswordPage extends RootSubPage {

	IModel<String> password = new Model<String>("");
	IModel<String> confirmPassword = new Model<String>("");

	public ChangePasswordPage() {
		super();

		if (!GitBlitWebSession.get().isLoggedIn()) {
			// Change password requires a login
			throw new RestartResponseException(getApplication().getHomePage());
		}

		if (!GitBlit.getBoolean(Keys.web.authenticateAdminPages, true)
				&& !GitBlit.getBoolean(Keys.web.authenticateViewPages, false)) {
			// no authentication enabled
			throw new RestartResponseException(getApplication().getHomePage());
		}
		setupPage(getString("gb.changePassword"), GitBlitWebSession.get().getUser().username);

		StatelessForm<Void> form = new StatelessForm<Void>("passwordForm") {

			private static final long serialVersionUID = 1L;

			@Override
			public void onSubmit() {
				String password = ChangePasswordPage.this.password.getObject();
				String confirmPassword = ChangePasswordPage.this.confirmPassword.getObject();
				// ensure passwords match
				if (!password.equals(confirmPassword)) {
					error(getString("gb.passwordsDoNotMatch"));
					return;
				}

				// ensure password satisfies minimum length requirement
				int minLength = GitBlit.getInteger(Keys.realm.minPasswordLength, 5);
				if (minLength < 4) {
					minLength = 4;
				}
				if (password.length() < minLength) {
					error(MessageFormat.format(getString("gb.passwordTooShort"), minLength));
					return;
				}

				UserModel user = GitBlitWebSession.get().getUser();

				// convert to MD5 digest, if appropriate
				String type = GitBlit.getString(Keys.realm.passwordStorage, "md5");
				if (type.equalsIgnoreCase("md5")) {
					// store MD5 digest of password
					password = StringUtils.MD5_TYPE + StringUtils.getMD5(password);
				} else if (type.equalsIgnoreCase("combined-md5")) {
					// store MD5 digest of username+password
					password = StringUtils.COMBINED_MD5_TYPE
							+ StringUtils.getMD5(user.username.toLowerCase() + password);
				}

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
				info(getString("gb.passwordChanged"));
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

		form.add(new Button(getString("gb.save")));
		Button cancel = new Button(getString("gb.cancel")) {
			private static final long serialVersionUID = 1L;

			@Override
			public void onSubmit() {
				setRedirect(false);
				error(getString("gb.passwordChangeAborted"));
				setResponsePage(RepositoriesPage.class);
			}
		};
		cancel.setDefaultFormProcessing(false);
		form.add(cancel);

		add(form);
	}
}
