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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.wicket.PageParameters;
import org.apache.wicket.behavior.SimpleAttributeModifier;
import org.apache.wicket.extensions.markup.html.form.palette.Palette;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.util.CollectionModel;
import org.apache.wicket.model.util.ListModel;

import com.gitblit.Constants.RegistrantType;
import com.gitblit.GitBlit;
import com.gitblit.GitBlitException;
import com.gitblit.Keys;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.TeamModel;
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
		TeamModel model = GitBlit.self().getTeamModel(name);
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

	protected void setupPage(final TeamModel teamModel) {
		if (isCreate) {
			super.setupPage(getString("gb.newTeam"), "");
		} else {
			super.setupPage(getString("gb.edit"), teamModel.name);
		}

		CompoundPropertyModel<TeamModel> model = new CompoundPropertyModel<TeamModel>(teamModel);

		List<String> repos = getAccessRestrictedRepositoryList(true, null);

		List<String> teamUsers = new ArrayList<String>(teamModel.users);
		Collections.sort(teamUsers);
		List<String> preReceiveScripts = new ArrayList<String>();
		List<String> postReceiveScripts = new ArrayList<String>();

		final String oldName = teamModel.name;
		final List<RegistrantAccessPermission> permissions = teamModel.getRepositoryPermissions();

		// users palette
		final Palette<String> users = new Palette<String>("users", new ListModel<String>(
				new ArrayList<String>(teamUsers)), new CollectionModel<String>(GitBlit.self()
				.getAllUsernames()), new StringChoiceRenderer(), 10, false);

		// pre-receive palette
		if (teamModel.preReceiveScripts != null) {
			preReceiveScripts.addAll(teamModel.preReceiveScripts);
		}
		final Palette<String> preReceivePalette = new Palette<String>("preReceiveScripts",
				new ListModel<String>(preReceiveScripts), new CollectionModel<String>(GitBlit
						.self().getPreReceiveScriptsUnused(null)), new StringChoiceRenderer(),
						12, true);

		// post-receive palette
		if (teamModel.postReceiveScripts != null) {
			postReceiveScripts.addAll(teamModel.postReceiveScripts);
		}
		final Palette<String> postReceivePalette = new Palette<String>("postReceiveScripts",
				new ListModel<String>(postReceiveScripts), new CollectionModel<String>(GitBlit
						.self().getPostReceiveScriptsUnused(null)), new StringChoiceRenderer(),
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
					TeamModel model = GitBlit.self().getTeamModel(teamname);
					if (model != null) {
						error(MessageFormat.format(getString("gb.teamNameUnavailable"), teamname));
						return;
					}
				}
				// update team permissions
				for (RegistrantAccessPermission repositoryPermission : permissions) {
					teamModel.setRepositoryPermission(repositoryPermission.registrant, repositoryPermission.permission);
				}

				Iterator<String> selectedUsers = users.getSelectedChoices();
				List<String> members = new ArrayList<String>();
				while (selectedUsers.hasNext()) {
					members.add(selectedUsers.next().toLowerCase());
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
					GitBlit.self().updateTeamModel(oldName, teamModel, isCreate);
				} catch (GitBlitException e) {
					error(e.getMessage());
					return;
				}
				setRedirect(false);
				if (isCreate) {
					// create another team
					info(MessageFormat.format(getString("gb.teamCreated"),
							teamModel.name));
				}
				// back to users page
				setResponsePage(UsersPage.class);
			}
		};

		// do not let the browser pre-populate these fields
		form.add(new SimpleAttributeModifier("autocomplete", "off"));

		// not all user services support manipulating team memberships
		boolean editMemberships = GitBlit.self().supportsTeamMembershipChanges(null);
		
		// field names reflective match TeamModel fields
		form.add(new TextField<String>("name"));
		form.add(new CheckBox("canAdmin"));
		form.add(new CheckBox("canFork").setEnabled(GitBlit.getBoolean(Keys.web.allowForking, true)));
		form.add(new CheckBox("canCreate"));
		form.add(users.setEnabled(editMemberships));
		mailingLists = new Model<String>(teamModel.mailingLists == null ? ""
				: StringUtils.flattenStrings(teamModel.mailingLists, " "));
		form.add(new TextField<String>("mailingLists", mailingLists));

		form.add(new RegistrantPermissionsPanel("repositories", RegistrantType.REPOSITORY,
				repos, permissions, getAccessPermissions()));
		form.add(preReceivePalette);
		form.add(new BulletListPanel("inheritedPreReceive", "inherited", GitBlit.self()
				.getPreReceiveScriptsInherited(null)));
		form.add(postReceivePalette);
		form.add(new BulletListPanel("inheritedPostReceive", "inherited", GitBlit.self()
				.getPostReceiveScriptsInherited(null)));

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
