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
import java.util.Iterator;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.extensions.markup.html.form.palette.Palette;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.ChoiceRenderer;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.util.CollectionModel;
import org.apache.wicket.model.util.ListModel;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.GitBlit;
import com.gitblit.GitBlitException;
import com.gitblit.Keys;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.RequiresAdminRole;
import com.gitblit.wicket.WicketUtils;

@RequiresAdminRole
public class EditUserPage extends BasePage {

	private final boolean isCreate;

	public EditUserPage() {
		// create constructor
		super();
		isCreate = true;
		setupPage(new UserModel(""));
	}

	public EditUserPage(PageParameters params) {
		// edit constructor
		super(params);
		isCreate = false;
		String name = WicketUtils.getUsername(params);
		UserModel model = GitBlit.self().getUserModel(name);
		setupPage(model);
	}

	protected void setupPage(final UserModel userModel) {
		if (isCreate) {
			super.setupPage("", getString("gb.newUser"));
		} else {
			super.setupPage("", getString("gb.edit"));
		}
		final Model<String> confirmPassword = new Model<String>(
				StringUtils.isEmpty(userModel.password) ? "" : userModel.password);
		CompoundPropertyModel<UserModel> model = new CompoundPropertyModel<UserModel>(userModel);

		List<String> repos = new ArrayList<String>();
		for (String repo : GitBlit.self().getRepositoryList()) {
			RepositoryModel repositoryModel = GitBlit.self().getRepositoryModel(repo);
			if (repositoryModel.accessRestriction.exceeds(AccessRestrictionType.NONE)) {
				repos.add(repo);
			}
		}
		final String oldName = userModel.username;
		final Palette<String> repositories = new Palette<String>("repositories",
				new ListModel<String>(userModel.repositories), new CollectionModel<String>(repos),
				new ChoiceRenderer<String>("", ""), 10, false);
		Form<UserModel> form = new Form<UserModel>("editForm", model) {

			private static final long serialVersionUID = 1L;

			/*
			 * (non-Javadoc)
			 * 
			 * @see org.apache.wicket.markup.html.form.Form#onSubmit()
			 */
			@Override
			protected void onSubmit() {
				String username = userModel.username;
				if (StringUtils.isEmpty(username)) {
					error("Please enter a username!");
					return;
				}
				if (isCreate) {
					UserModel model = GitBlit.self().getUserModel(username);
					if (model != null) {
						error(MessageFormat.format("Username ''{0}'' is unavailable.", username));
						return;
					}
				}
				if (!userModel.password.equals(confirmPassword.getObject())) {
					error("Passwords do not match!");
					return;
				}
				String password = userModel.password;
				if (!password.toUpperCase().startsWith(StringUtils.MD5_TYPE)) {
					// This is a plain text password.
					// Check length.
					int minLength = GitBlit.getInteger(Keys.realm.minPasswordLength, 5);
					if (minLength < 4) {
						minLength = 4;
					}
					if (password.trim().length() < minLength) {
						error(MessageFormat.format(
								"Password is too short. Minimum length is {0} characters.",
								minLength));
						return;
					}

					// Optionally store the password MD5 digest.
					String type = GitBlit.getString(Keys.realm.passwordStorage, "md5");
					if (type.equalsIgnoreCase("md5")) {
						// store MD5 digest of password
						userModel.password = StringUtils.MD5_TYPE
								+ StringUtils.getMD5(userModel.password);
					}
				}

				Iterator<String> selectedRepositories = repositories.getSelectedChoices();
				List<String> repos = new ArrayList<String>();
				while (selectedRepositories.hasNext()) {
					repos.add(selectedRepositories.next().toLowerCase());
				}
				userModel.repositories.clear();
				userModel.repositories.addAll(repos);
				try {
					GitBlit.self().updateUserModel(oldName, userModel, isCreate);
				} catch (GitBlitException e) {
					error(e.getMessage());
					return;
				}
				setRedirect(false);
				if (isCreate) {
					// create another user
					info(MessageFormat.format("New user ''{0}'' successfully created.",
							userModel.username));
					setResponsePage(EditUserPage.class);
				} else {
					// back to home
					setResponsePage(RepositoriesPage.class);
				}
			}
		};

		// field names reflective match UserModel fields
		form.add(new TextField<String>("username"));
		PasswordTextField passwordField = new PasswordTextField("password");
		passwordField.setResetPassword(false);
		form.add(passwordField);
		PasswordTextField confirmPasswordField = new PasswordTextField("confirmPassword",
				confirmPassword);
		confirmPasswordField.setResetPassword(false);
		form.add(confirmPasswordField);
		form.add(new CheckBox("canAdmin"));
		form.add(repositories);
		
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
