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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.extensions.markup.html.form.palette.Palette;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.util.CollectionModel;
import org.apache.wicket.model.util.ListModel;

import com.gitblit.Constants.RegistrantType;
import com.gitblit.Constants.Role;
import com.gitblit.GitBlitException;
import com.gitblit.Keys;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.NonTrimmedPasswordTextField;
import com.gitblit.wicket.RequiresAdminRole;
import com.gitblit.wicket.StringChoiceRenderer;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.RegistrantPermissionsPanel;

@RequiresAdminRole
public class EditUserPage extends RootSubPage {

	private final boolean isCreate;

	public EditUserPage() {
		// create constructor
		super();
		isCreate = true;
		setupPage(new UserModel(""));
		setStatelessHint(false);
		setOutputMarkupId(true);
	}

	public EditUserPage(PageParameters params) {
		// edit constructor
		super(params);
		isCreate = false;
		String name = WicketUtils.getUsername(params);
		UserModel model = app().users().getUserModel(name);
		setupPage(model);
		setStatelessHint(false);
		setOutputMarkupId(true);
	}

	@Override
	protected boolean requiresPageMap() {
		return true;
	}

	@Override
	protected Class<? extends BasePage> getRootNavPageClass() {
		return UsersPage.class;
	}

	protected void setupPage(final UserModel userModel) {
		if (isCreate) {
			super.setupPage(getString("gb.newUser"), "");
		} else {
			super.setupPage(getString("gb.edit"), userModel.username);
		}

		final Model<String> confirmPassword = new Model<String>(
				StringUtils.isEmpty(userModel.password) ? "" : userModel.password);
		CompoundPropertyModel<UserModel> model = new CompoundPropertyModel<UserModel>(userModel);

		// build list of projects including all repositories wildcards
		List<String> repos = getAccessRestrictedRepositoryList(true, userModel);

		List<String> userTeams = new ArrayList<String>();
		for (TeamModel team : userModel.teams) {
			userTeams.add(team.name);
		}
		Collections.sort(userTeams);

		final String oldName = userModel.username;
		final List<RegistrantAccessPermission> permissions = app().repositories().getUserAccessPermissions(userModel);

		final Palette<String> teams = new Palette<String>("teams", new ListModel<String>(
				new ArrayList<String>(userTeams)), new CollectionModel<String>(app().users()
				.getAllTeamNames()), new StringChoiceRenderer(), 10, false);
		Form<UserModel> form = new Form<UserModel>("editForm", model) {

			private static final long serialVersionUID = 1L;

			/*
			 * (non-Javadoc)
			 *
			 * @see org.apache.wicket.markup.html.form.Form#onSubmit()
			 */
			@Override
			protected void onSubmit() {
				if (StringUtils.isEmpty(userModel.username)) {
					error(getString("gb.pleaseSetUsername"));
					return;
				}
				// force username to lower-case
				userModel.username = userModel.username.toLowerCase();
				String username = userModel.username;
				if (isCreate) {
					UserModel model = app().users().getUserModel(username);
					if (model != null) {
						error(MessageFormat.format(getString("gb.usernameUnavailable"), username));
						return;
					}
				}
				boolean rename = !StringUtils.isEmpty(oldName)
						&& !oldName.equalsIgnoreCase(username);
				if (app().authentication().supportsCredentialChanges(userModel)) {
					if (!userModel.password.equals(confirmPassword.getObject())) {
						error(getString("gb.passwordsDoNotMatch"));
						return;
					}
					String password = userModel.password;
					if (!password.toUpperCase().startsWith(StringUtils.MD5_TYPE)
							&& !password.toUpperCase().startsWith(StringUtils.COMBINED_MD5_TYPE)) {
						// This is a plain text password.
						// Check length.
						int minLength = app().settings().getInteger(Keys.realm.minPasswordLength, 5);
						if (minLength < 4) {
							minLength = 4;
						}
						if (password.trim().length() < minLength) {
							error(MessageFormat.format(getString("gb.passwordTooShort"),
									minLength));
							return;
						}

						// change the cookie
						userModel.cookie = StringUtils.getSHA1(userModel.username + password);

						// Optionally store the password MD5 digest.
						String type = app().settings().getString(Keys.realm.passwordStorage, "md5");
						if (type.equalsIgnoreCase("md5")) {
							// store MD5 digest of password
							userModel.password = StringUtils.MD5_TYPE
									+ StringUtils.getMD5(userModel.password);
						} else if (type.equalsIgnoreCase("combined-md5")) {
							// store MD5 digest of username+password
							userModel.password = StringUtils.COMBINED_MD5_TYPE
									+ StringUtils.getMD5(username + userModel.password);
						}
					} else if (rename
							&& password.toUpperCase().startsWith(StringUtils.COMBINED_MD5_TYPE)) {
						error(getString("gb.combinedMd5Rename"));
						return;
					}
				}

				// update user permissions
				for (RegistrantAccessPermission repositoryPermission : permissions) {
					if (repositoryPermission.mutable) {
						userModel.setRepositoryPermission(repositoryPermission.registrant, repositoryPermission.permission);
					}
				}

				Iterator<String> selectedTeams = teams.getSelectedChoices();
				userModel.teams.clear();
				while (selectedTeams.hasNext()) {
					TeamModel team = app().users().getTeamModel(selectedTeams.next());
					if (team == null) {
						continue;
					}
					userModel.teams.add(team);
				}

				try {
					if (isCreate) {
						app().gitblit().addUser(userModel);
					} else {
						app().gitblit().reviseUser(oldName, userModel);
					}
				} catch (GitBlitException e) {
					error(e.getMessage());
					return;
				}
				if (isCreate) {
					// create another user
					info(MessageFormat.format(getString("gb.userCreated"),
							userModel.username));
					setResponsePage(EditUserPage.class);
				} else {
					// back to users page
					setResponsePage(UsersPage.class);
				}
			}
		};

		// do not let the browser pre-populate these fields
		form.add(new AttributeModifier("autocomplete", "off"));

		// not all user providers support manipulating username and password
		boolean editCredentials = app().authentication().supportsCredentialChanges(userModel);

		// not all user providers support manipulating display name
		boolean editDisplayName = app().authentication().supportsDisplayNameChanges(userModel);

		// not all user providers support manipulating email address
		boolean editEmailAddress = app().authentication().supportsEmailAddressChanges(userModel);

		// not all user providers support manipulating team memberships
		boolean editTeams = app().authentication().supportsTeamMembershipChanges(userModel);

		// not all user providers support manipulating the admin role
		boolean changeAdminRole = app().authentication().supportsRoleChanges(userModel, Role.ADMIN);

		// not all user providers support manipulating the create role
		boolean changeCreateRole = app().authentication().supportsRoleChanges(userModel, Role.CREATE);

		// not all user providers support manipulating the fork role
		boolean changeForkRole = app().authentication().supportsRoleChanges(userModel, Role.FORK);

		// field names reflective match UserModel fields
		form.add(new TextField<String>("username").setEnabled(editCredentials));
		NonTrimmedPasswordTextField passwordField = new NonTrimmedPasswordTextField("password");
		passwordField.setResetPassword(false);
		form.add(passwordField.setEnabled(editCredentials));
		NonTrimmedPasswordTextField confirmPasswordField = new NonTrimmedPasswordTextField("confirmPassword",
				confirmPassword);
		confirmPasswordField.setResetPassword(false);
		form.add(confirmPasswordField.setEnabled(editCredentials));
		form.add(new TextField<String>("displayName").setEnabled(editDisplayName));
		form.add(new TextField<String>("emailAddress").setEnabled(editEmailAddress));

		if (userModel.canAdmin() && !userModel.canAdmin) {
			// user inherits Admin permission
			// display a disabled-yet-checked checkbox
			form.add(new CheckBox("canAdmin", Model.of(true)).setEnabled(false));
		} else {
			form.add(new CheckBox("canAdmin").setEnabled(changeAdminRole));
		}

		if (userModel.canFork() && !userModel.canFork) {
			// user inherits Fork permission
			// display a disabled-yet-checked checkbox
			form.add(new CheckBox("canFork", Model.of(true)).setEnabled(false));
		} else {
			final boolean forkingAllowed = app().settings().getBoolean(Keys.web.allowForking, true);
			form.add(new CheckBox("canFork").setEnabled(forkingAllowed && changeForkRole));
		}

		if (userModel.canCreate() && !userModel.canCreate) {
			// user inherits Create permission
			// display a disabled-yet-checked checkbox
			form.add(new CheckBox("canCreate", Model.of(true)).setEnabled(false));
		} else {
			form.add(new CheckBox("canCreate").setEnabled(changeCreateRole));
		}

		form.add(new CheckBox("excludeFromFederation"));
		form.add(new CheckBox("disabled"));

		form.add(new RegistrantPermissionsPanel("repositories",	RegistrantType.REPOSITORY, repos, permissions, getAccessPermissions()));
		form.add(teams.setEnabled(editTeams));

		form.add(new TextField<String>("organizationalUnit").setEnabled(editDisplayName));
		form.add(new TextField<String>("organization").setEnabled(editDisplayName));
		form.add(new TextField<String>("locality").setEnabled(editDisplayName));
		form.add(new TextField<String>("stateProvince").setEnabled(editDisplayName));
		form.add(new TextField<String>("countryCode").setEnabled(editDisplayName));
		form.add(new Button("save"));
		Button cancel = new Button("cancel") {
			private static final long serialVersionUID = 1L;

			@Override
			public void onSubmit() {
				setResponsePage(UsersPage.class);
			}
		};
		cancel.setDefaultFormProcessing(false);
		form.add(cancel);

		add(form);
	}
}
