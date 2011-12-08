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
import org.apache.wicket.markup.html.form.Button;
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
import com.gitblit.Constants.FederationStrategy;
import com.gitblit.GitBlit;
import com.gitblit.GitBlitException;
import com.gitblit.Keys;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;

public class EditRepositoryPage extends RootSubPage {

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

		List<String> federationSets = new ArrayList<String>();
		List<String> repositoryUsers = new ArrayList<String>();
		List<String> repositoryTeams = new ArrayList<String>();
		if (isCreate) {
			super.setupPage(getString("gb.newRepository"), "");
		} else {
			super.setupPage(getString("gb.edit"), repositoryModel.name);
			if (repositoryModel.accessRestriction.exceeds(AccessRestrictionType.NONE)) {
				repositoryUsers.addAll(GitBlit.self().getRepositoryUsers(repositoryModel));
				repositoryTeams.addAll(GitBlit.self().getRepositoryTeams(repositoryModel));
				Collections.sort(repositoryUsers);
			}
			federationSets.addAll(repositoryModel.federationSets);
		}		
		

		final String oldName = repositoryModel.name;
		// users palette
		final Palette<String> usersPalette = new Palette<String>("users", new ListModel<String>(
				repositoryUsers), new CollectionModel<String>(GitBlit.self().getAllUsernames()),
				new ChoiceRenderer<String>("", ""), 10, false);

		// teams palette
		final Palette<String> teamsPalette = new Palette<String>("teams", new ListModel<String>(
				repositoryTeams), new CollectionModel<String>(GitBlit.self().getAllTeamnames()),
				new ChoiceRenderer<String>("", ""), 10, false);

		// federation sets palette
		List<String> sets = GitBlit.getStrings(Keys.federation.sets);
		final Palette<String> federationSetsPalette = new Palette<String>("federationSets",
				new ListModel<String>(federationSets), new CollectionModel<String>(sets),
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
					Character c = StringUtils.findInvalidCharacter(repositoryModel.name);
					if (c != null) {
						error(MessageFormat.format("Illegal character ''{0}'' in repository name!",
								c));
						return;
					}

					// confirm access restriction selection
					if (repositoryModel.accessRestriction == null) {
						error("Please select access restriction!");
						return;
					}

					// confirm federation strategy selection
					if (repositoryModel.federationStrategy == null) {
						error("Please select federation strategy!");
						return;
					}

					// save federation set preferences
					if (repositoryModel.federationStrategy.exceeds(FederationStrategy.EXCLUDE)) {
						repositoryModel.federationSets.clear();
						Iterator<String> sets = federationSetsPalette.getSelectedChoices();
						while (sets.hasNext()) {
							repositoryModel.federationSets.add(sets.next());
						}
					}

					// save the repository
					GitBlit.self().updateRepositoryModel(oldName, repositoryModel, isCreate);

					// repository access
					if (repositoryModel.accessRestriction.exceeds(AccessRestrictionType.NONE)) {
						// save the user access list
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
						
						// save the team access list
						Iterator<String> teams = teamsPalette.getSelectedChoices();
						List<String> repositoryTeams = new ArrayList<String>();
						while (teams.hasNext()) {
							repositoryTeams.add(teams.next());
						}
						GitBlit.self().setRepositoryTeams(repositoryModel, repositoryTeams);
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
		// TODO enable origin definition
		form.add(new TextField<String>("origin").setEnabled(false/* isCreate */));

		// federation strategies - remove ORIGIN choice if this repository has
		// no origin.
		List<FederationStrategy> federationStrategies = new ArrayList<FederationStrategy>(
				Arrays.asList(FederationStrategy.values()));
		if (StringUtils.isEmpty(repositoryModel.origin)) {
			federationStrategies.remove(FederationStrategy.FEDERATE_ORIGIN);
		}
		form.add(new DropDownChoice<FederationStrategy>("federationStrategy", federationStrategies,
				new FederationTypeRenderer()));
		form.add(new CheckBox("useTickets"));
		form.add(new CheckBox("useDocs"));
		form.add(new CheckBox("showRemoteBranches"));
		form.add(new CheckBox("showReadme"));
		form.add(new CheckBox("skipSizeCalculation"));
		form.add(new CheckBox("skipSummaryMetrics"));
		form.add(usersPalette);
		form.add(teamsPalette);
		form.add(federationSetsPalette);

		form.add(new Button("save"));
		Button cancel = new Button("cancel") {
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

	private class FederationTypeRenderer implements IChoiceRenderer<FederationStrategy> {

		private static final long serialVersionUID = 1L;

		private final Map<FederationStrategy, String> map;

		public FederationTypeRenderer() {
			map = getFederationTypes();
		}

		@Override
		public String getDisplayValue(FederationStrategy type) {
			return map.get(type);
		}

		@Override
		public String getIdValue(FederationStrategy type, int index) {
			return Integer.toString(index);
		}
	}
}
