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
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.extensions.markup.html.form.palette.Palette;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.ChoiceRenderer;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.util.CollectionModel;
import org.apache.wicket.model.util.ListModel;

import com.gitblit.Constants.RegistrantType;
import com.gitblit.Constants.Role;
import com.gitblit.GitBlitException;
import com.gitblit.Keys;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserChoice;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.RequiresAdminRole;
import com.gitblit.wicket.StringChoiceRenderer;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.BulletListPanel;
import com.gitblit.wicket.panels.RegistrantPermissionsPanel;

@RequiresAdminRole
public class EditTeamPage extends RootSubPage {

	private final boolean isCreate;

	private IModel<String> mailingLists;

	public EditTeamPage() {
		// create constructor
		super();
		isCreate = true;
		setupPage(new TeamModel(""));
		setStatelessHint(false);
		setOutputMarkupId(true);
	}

	public EditTeamPage(PageParameters params) {
		// edit constructor
		super(params);
		isCreate = false;
		String name = WicketUtils.getTeamname(params);
		TeamModel model = app().users().getTeamModel(name);
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
		return TeamsPage.class;
	}

	protected void setupPage(final TeamModel teamModel) {
		if (isCreate) {
			super.setupPage(getString("gb.newTeam"), "");
		} else {
			super.setupPage(getString("gb.edit"), teamModel.name);
		}

		CompoundPropertyModel<TeamModel> model = new CompoundPropertyModel<TeamModel>(teamModel);

		List<String> repos = getAccessRestrictedRepositoryList(true, null);

		List<String> teamUsers = new ArrayList<String>(teamModel.users);
		List<String> preReceiveScripts = new ArrayList<String>();
		List<String> postReceiveScripts = new ArrayList<String>();

		final String oldName = teamModel.name;
		final List<RegistrantAccessPermission> permissions = teamModel.getRepositoryPermissions();

		// users palette
		final Palette<UserChoice> users = new Palette<UserChoice>("users", new ListModel<UserChoice>(
				getTeamUsers(teamUsers)), new CollectionModel<UserChoice>(sortByDisplayName(getTeamUsers(app().users().getAllUsernames()))), new ChoiceRenderer<UserChoice>(null, "userId"), 10, false);

		// pre-receive palette
		if (teamModel.preReceiveScripts != null) {
			preReceiveScripts.addAll(teamModel.preReceiveScripts);
		}
		final Palette<String> preReceivePalette = new Palette<String>("preReceiveScripts",
				new ListModel<String>(preReceiveScripts), new CollectionModel<String>(app().repositories()
						.getPreReceiveScriptsUnused(null)), new StringChoiceRenderer(),
						12, true);

		// post-receive palette
		if (teamModel.postReceiveScripts != null) {
			postReceiveScripts.addAll(teamModel.postReceiveScripts);
		}
		final Palette<String> postReceivePalette = new Palette<String>("postReceiveScripts",
				new ListModel<String>(postReceiveScripts), new CollectionModel<String>(app().repositories()
						.getPostReceiveScriptsUnused(null)), new StringChoiceRenderer(),
								12, true);

		Form<TeamModel> form = new Form<TeamModel>("editForm", model) {

			private static final long serialVersionUID = 1L;

			/*
			 * (non-Javadoc)
			 *
			 * @see org.apache.wicket.markup.html.form.Form#onSubmit()
			 */
			@Override
			protected void onSubmit() {
				String teamname = teamModel.name;
				if (StringUtils.isEmpty(teamname)) {
					error(getString("gb.pleaseSetTeamName"));
					return;
				}
				if (isCreate) {
					TeamModel model = app().users().getTeamModel(teamname);
					if (model != null) {
						error(MessageFormat.format(getString("gb.teamNameUnavailable"), teamname));
						return;
					}
				}
				// update team permissions
				for (RegistrantAccessPermission repositoryPermission : permissions) {
					teamModel.setRepositoryPermission(repositoryPermission.registrant, repositoryPermission.permission);
				}

				Iterator<UserChoice> selectedUsers = users.getSelectedChoices();
				List<String> members = new ArrayList<String>();
				while (selectedUsers.hasNext()) {
					members.add(selectedUsers.next().getUserId().toLowerCase());
				}
				teamModel.users.clear();
				teamModel.users.addAll(members);

				// set mailing lists
				String ml = mailingLists.getObject();
				if (!StringUtils.isEmpty(ml)) {
					Set<String> list = new HashSet<String>();
					for (String address : ml.split("(,|\\s)")) {
						if (StringUtils.isEmpty(address)) {
							continue;
						}
						list.add(address.toLowerCase());
					}
					teamModel.mailingLists.clear();
					teamModel.mailingLists.addAll(list);
				}

				// pre-receive scripts
				List<String> preReceiveScripts = new ArrayList<String>();
				Iterator<String> pres = preReceivePalette.getSelectedChoices();
				while (pres.hasNext()) {
					preReceiveScripts.add(pres.next());
				}
				teamModel.preReceiveScripts.clear();
				teamModel.preReceiveScripts.addAll(preReceiveScripts);

				// post-receive scripts
				List<String> postReceiveScripts = new ArrayList<String>();
				Iterator<String> post = postReceivePalette.getSelectedChoices();
				while (post.hasNext()) {
					postReceiveScripts.add(post.next());
				}
				teamModel.postReceiveScripts.clear();
				teamModel.postReceiveScripts.addAll(postReceiveScripts);

				try {
					if (isCreate) {
						app().gitblit().addTeam(teamModel);
					} else {
						app().gitblit().reviseTeam(oldName, teamModel);
					}
				} catch (GitBlitException e) {
					error(e.getMessage());
					return;
				}
				if (isCreate) {
					// create another team
					info(MessageFormat.format(getString("gb.teamCreated"),
							teamModel.name));
				}
				// back to users page
				setResponsePage(TeamsPage.class);
			}
		};

		// do not let the browser pre-populate these fields
		form.add(new AttributeModifier("autocomplete", "off"));

		// not all user providers support manipulating team memberships
		boolean editMemberships = app().authentication().supportsTeamMembershipChanges(teamModel);

		// not all user providers support manipulating the admin role
		boolean changeAdminRole = app().authentication().supportsRoleChanges(teamModel, Role.ADMIN);

		// not all user providers support manipulating the create role
		boolean changeCreateRole = app().authentication().supportsRoleChanges(teamModel, Role.CREATE);

		// not all user providers support manipulating the fork role
		boolean changeForkRole = app().authentication().supportsRoleChanges(teamModel, Role.FORK);

		// field names reflective match TeamModel fields
		form.add(new TextField<String>("name"));
		form.add(new CheckBox("canAdmin").setEnabled(changeAdminRole));
		form.add(new CheckBox("canFork").setEnabled(app().settings().getBoolean(Keys.web.allowForking, true) && changeForkRole));
		form.add(new CheckBox("canCreate").setEnabled(changeCreateRole));
		form.add(users.setEnabled(editMemberships));
		mailingLists = new Model<String>(teamModel.mailingLists == null ? ""
				: StringUtils.flattenStrings(teamModel.mailingLists, " "));
		form.add(new TextField<String>("mailingLists", mailingLists));

		form.add(new RegistrantPermissionsPanel("repositories", RegistrantType.REPOSITORY,
				repos, permissions, getAccessPermissions()));
		form.add(preReceivePalette);
		form.add(new BulletListPanel("inheritedPreReceive", "inherited", app().repositories()
				.getPreReceiveScriptsInherited(null)));
		form.add(postReceivePalette);
		form.add(new BulletListPanel("inheritedPostReceive", "inherited", app().repositories()
				.getPostReceiveScriptsInherited(null)));

		form.add(new Button("save"));
		Button cancel = new Button("cancel") {
			private static final long serialVersionUID = 1L;

			@Override
			public void onSubmit() {
				setResponsePage(TeamsPage.class);
			}
		};
		cancel.setDefaultFormProcessing(false);
		form.add(cancel);

		add(form);
	}

	private List<UserChoice> getTeamUsers(List<String> teamUserIds) {
		List<UserChoice> teamUsers = new ArrayList<UserChoice>();
		for (String teamUserId : teamUserIds) {
			UserModel userModel = app().users().getUserModel(teamUserId);
			if (userModel!=null) {
				teamUsers.add(new UserChoice(userModel.displayName, userModel.username, userModel.emailAddress));
			}
		}
		return sortByDisplayName(teamUsers);
	}

	private List<UserChoice> sortByDisplayName(List<UserChoice> teamUsers) {
		Collections.sort(teamUsers, new Comparator<UserChoice>() {

			@Override
			public int compare(UserChoice o1, UserChoice o2) {
				return o1.getDisplayNameOrUserId().compareTo(o2.getDisplayNameOrUserId());
			}
		});
		return teamUsers;
	}
}
