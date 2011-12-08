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

import org.apache.wicket.PageParameters;
import org.apache.wicket.extensions.markup.html.form.palette.Palette;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.ChoiceRenderer;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.util.CollectionModel;
import org.apache.wicket.model.util.ListModel;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.GitBlit;
import com.gitblit.GitBlitException;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TeamModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.RequiresAdminRole;
import com.gitblit.wicket.WicketUtils;

@RequiresAdminRole
public class EditTeamPage extends RootSubPage {

	private final boolean isCreate;

	public EditTeamPage() {
		// create constructor
		super();
		isCreate = true;
		setupPage(new TeamModel(""));
	}

	public EditTeamPage(PageParameters params) {
		// edit constructor
		super(params);
		isCreate = false;
		String name = WicketUtils.getTeamname(params);
		TeamModel model = GitBlit.self().getTeamModel(name);
		setupPage(model);
	}

	protected void setupPage(final TeamModel teamModel) {
		if (isCreate) {
			super.setupPage(getString("gb.newTeam"), "");
		} else {
			super.setupPage(getString("gb.edit"), teamModel.name);
		}
		
		CompoundPropertyModel<TeamModel> model = new CompoundPropertyModel<TeamModel>(teamModel);

		List<String> repos = new ArrayList<String>();
		for (String repo : GitBlit.self().getRepositoryList()) {
			RepositoryModel repositoryModel = GitBlit.self().getRepositoryModel(repo);
			if (repositoryModel.accessRestriction.exceeds(AccessRestrictionType.NONE)) {
				repos.add(repo);
			}
		}
		StringUtils.sortRepositorynames(repos);
		
		List<String> teamUsers = new ArrayList<String>(teamModel.users);
		Collections.sort(teamUsers);
		
		final String oldName = teamModel.name;
		final Palette<String> repositories = new Palette<String>("repositories",
				new ListModel<String>(new ArrayList<String>(teamModel.repositories)),
				new CollectionModel<String>(repos), new ChoiceRenderer<String>("", ""), 10, false);
		final Palette<String> users = new Palette<String>("users", new ListModel<String>(
				new ArrayList<String>(teamUsers)), new CollectionModel<String>(GitBlit.self()
				.getAllUsernames()), new ChoiceRenderer<String>("", ""), 10, false);
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
					error("Please enter a teamname!");
					return;
				}
				if (isCreate) {
					TeamModel model = GitBlit.self().getTeamModel(teamname);
					if (model != null) {
						error(MessageFormat.format("Team name ''{0}'' is unavailable.", teamname));
						return;
					}
				}
				Iterator<String> selectedRepositories = repositories.getSelectedChoices();
				List<String> repos = new ArrayList<String>();
				while (selectedRepositories.hasNext()) {
					repos.add(selectedRepositories.next().toLowerCase());
				}
				teamModel.repositories.clear();
				teamModel.repositories.addAll(repos);

				Iterator<String> selectedUsers = users.getSelectedChoices();
				List<String> members = new ArrayList<String>();
				while (selectedUsers.hasNext()) {
					members.add(selectedUsers.next().toLowerCase());
				}
				teamModel.users.clear();
				teamModel.users.addAll(members);

				try {
					GitBlit.self().updateTeamModel(oldName, teamModel, isCreate);
				} catch (GitBlitException e) {
					error(e.getMessage());
					return;
				}
				setRedirect(false);
				if (isCreate) {
					// create another team
					info(MessageFormat.format("New team ''{0}'' successfully created.",
							teamModel.name));
					setResponsePage(EditTeamPage.class);
				} else {
					// back to users page
					setResponsePage(UsersPage.class);
				}
			}
		};

		// field names reflective match TeamModel fields
		form.add(new TextField<String>("name"));
		form.add(repositories);
		form.add(users);

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
