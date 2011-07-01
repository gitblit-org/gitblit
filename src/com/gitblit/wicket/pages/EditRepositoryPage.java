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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.wicket.PageParameters;
import org.apache.wicket.extensions.markup.html.form.palette.Palette;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.ChoiceRenderer;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.util.CollectionModel;
import org.apache.wicket.model.util.ListModel;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.GitBlit;
import com.gitblit.GitBlitException;
import com.gitblit.Keys;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;

public class EditRepositoryPage extends BasePage {

	private final boolean isCreate;

	private boolean isAdmin;

	public EditRepositoryPage() {
		// create constructor
		super();
		isCreate = true;
		setupPage(new RepositoryModel());
	}

	public EditRepositoryPage(PageParameters params) {
		// edit constructor
		super(params);
		isCreate = false;
		String name = WicketUtils.getRepositoryName(params);
		RepositoryModel model = GitBlit.self().getRepositoryModel(name);
		setupPage(model);
	}

	protected void setupPage(final RepositoryModel repositoryModel) {
		// ensure this user can create or edit this repository
		checkPermissions(repositoryModel);

		List<String> repositoryUsers = new ArrayList<String>();
		if (isCreate) {
			super.setupPage("", getString("gb.newRepository"));
		} else {
			super.setupPage("", getString("gb.edit"));
			if (repositoryModel.accessRestriction.exceeds(AccessRestrictionType.NONE)) {
				repositoryUsers.addAll(GitBlit.self().getRepositoryUsers(repositoryModel));
				Collections.sort(repositoryUsers);
			}
		}

		final String oldName = repositoryModel.name;
		final Palette<String> usersPalette = new Palette<String>("users", new ListModel<String>(
				repositoryUsers), new CollectionModel<String>(GitBlit.self().getAllUsernames()),
				new ChoiceRenderer<String>("", ""), 10, false);
		CompoundPropertyModel<RepositoryModel> model = new CompoundPropertyModel<RepositoryModel>(
				repositoryModel);
		Form<RepositoryModel> form = new Form<RepositoryModel>("editForm", model) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void onSubmit() {
				try {
					// confirm a repository name was entered
					if (StringUtils.isEmpty(repositoryModel.name)) {
						error("Please set repository name!");
						return;
					}

					// automatically convert backslashes to forward slashes
					repositoryModel.name = repositoryModel.name.replace('\\', '/');
					// Automatically replace // with /
					repositoryModel.name = repositoryModel.name.replace("//", "/");

					// prohibit folder paths
					if (repositoryModel.name.startsWith("/")) {
						error("Leading root folder references (/) are prohibited.");
						return;
					}
					if (repositoryModel.name.startsWith("../")) {
						error("Relative folder references (../) are prohibited.");
						return;
					}
					if (repositoryModel.name.contains("/../")) {
						error("Relative folder references (../) are prohibited.");
						return;
					}

					// confirm valid characters in repository name
					char[] validChars = { '/', '.', '_', '-' };
					for (char c : repositoryModel.name.toCharArray()) {
						if (!Character.isLetterOrDigit(c)) {
							boolean ok = false;
							for (char vc : validChars) {
								ok |= c == vc;
							}
							if (!ok) {
								error(MessageFormat.format(
										"Illegal character ''{0}'' in repository name!", c));
								return;
							}
						}
					}

					// confirm access restriction selection
					if (repositoryModel.accessRestriction == null) {
						error("Please select access restriction!");
						return;
					}

					// save the repository
					GitBlit.self().updateRepositoryModel(oldName, repositoryModel, isCreate);

					// save the repository access list
					if (repositoryModel.accessRestriction.exceeds(AccessRestrictionType.NONE)) {
						Iterator<String> users = usersPalette.getSelectedChoices();
						List<String> repositoryUsers = new ArrayList<String>();
						while (users.hasNext()) {
							repositoryUsers.add(users.next());
						}
						// ensure the owner is added to the user list
						if (repositoryModel.owner != null
								&& !repositoryUsers.contains(repositoryModel.owner)) {
							repositoryUsers.add(repositoryModel.owner);
						}
						GitBlit.self().setRepositoryUsers(repositoryModel, repositoryUsers);
					}
				} catch (GitBlitException e) {
					error(e.getMessage());
					return;
				}
				setRedirect(false);
				setResponsePage(RepositoriesPage.class);
			}
		};

		// field names reflective match RepositoryModel fields
		form.add(new TextField<String>("name").setEnabled(isCreate || isAdmin));
		form.add(new TextField<String>("description"));
		form.add(new DropDownChoice<String>("owner", GitBlit.self().getAllUsernames())
				.setEnabled(GitBlitWebSession.get().canAdmin()));
		form.add(new DropDownChoice<AccessRestrictionType>("accessRestriction", Arrays
				.asList(AccessRestrictionType.values()), new AccessRestrictionRenderer()));
		form.add(new CheckBox("isFrozen"));
		form.add(new CheckBox("useTickets"));
		form.add(new CheckBox("useDocs"));
		form.add(new CheckBox("showRemoteBranches"));
		form.add(new CheckBox("showReadme"));
		form.add(usersPalette);

		add(form);
	}

	/**
	 * Unfortunately must repeat part of AuthorizaitonStrategy here because that
	 * mechanism does not take PageParameters into consideration, only page
	 * instantiation.
	 * 
	 * Repository Owners should be able to edit their repository.
	 */
	private void checkPermissions(RepositoryModel model) {
		boolean authenticateAdmin = GitBlit.getBoolean(Keys.web.authenticateAdminPages, true);
		boolean allowAdmin = GitBlit.getBoolean(Keys.web.allowAdministration, true);

		GitBlitWebSession session = GitBlitWebSession.get();
		UserModel user = session.getUser();

		if (allowAdmin) {
			if (authenticateAdmin) {
				if (user == null) {
					// No Login Available
					error("Administration requires a login", true);
				}
				if (isCreate) {
					// Create Repository
					if (!user.canAdmin) {
						// Only Administrators May Create
						error("Only an administrator may create a repository", true);
					}
				} else {
					// Edit Repository
					if (user.canAdmin) {
						// Admins can edit everything
						isAdmin = true;
						return;
					} else {
						if (!model.owner.equalsIgnoreCase(user.username)) {
							// User is not an Admin nor Owner
							error("Only an administrator or the owner may edit a repository", true);
						}
					}
				}
			}
		} else {
			// No Administration Permitted
			error("Administration is disabled", true);
		}
	}

	private class AccessRestrictionRenderer implements IChoiceRenderer<AccessRestrictionType> {

		private static final long serialVersionUID = 1L;

		private final Map<AccessRestrictionType, String> map;

		public AccessRestrictionRenderer() {
			map = getAccessRestrictions();
		}

		@Override
		public String getDisplayValue(AccessRestrictionType type) {
			return map.get(type);
		}

		@Override
		public String getIdValue(AccessRestrictionType type, int index) {
			return Integer.toString(index);
		}
	}
}
