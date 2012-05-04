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
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.wicket.PageParameters;
import org.apache.wicket.behavior.SimpleAttributeModifier;
import org.apache.wicket.extensions.markup.html.form.palette.Palette;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListItemModel;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.util.CollectionModel;
import org.apache.wicket.model.util.ListModel;

import com.gitblit.Constants;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.FederationStrategy;
import com.gitblit.GitBlit;
import com.gitblit.GitBlitException;
import com.gitblit.Keys;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.StringChoiceRenderer;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.BulletListPanel;

public class EditRepositoryPage extends RootSubPage {

	private final boolean isCreate;

	private boolean isAdmin;

	private IModel<String> mailingLists;

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

		List<String> indexedBranches = new ArrayList<String>();
		List<String> federationSets = new ArrayList<String>();
		List<String> repositoryUsers = new ArrayList<String>();
		List<String> repositoryTeams = new ArrayList<String>();
		List<String> preReceiveScripts = new ArrayList<String>();
		List<String> postReceiveScripts = new ArrayList<String>();

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
			if (!ArrayUtils.isEmpty(repositoryModel.indexedBranches)) {
				indexedBranches.addAll(repositoryModel.indexedBranches);
			}
		}

		final String oldName = repositoryModel.name;
		// users palette
		final Palette<String> usersPalette = new Palette<String>("users", new ListModel<String>(
				repositoryUsers), new CollectionModel<String>(GitBlit.self().getAllUsernames()),
				new StringChoiceRenderer(), 10, false);

		// teams palette
		final Palette<String> teamsPalette = new Palette<String>("teams", new ListModel<String>(
				repositoryTeams), new CollectionModel<String>(GitBlit.self().getAllTeamnames()),
				new StringChoiceRenderer(), 8, false);

		// indexed local branches palette
		List<String> allLocalBranches = new ArrayList<String>();
		allLocalBranches.add(Constants.DEFAULT_BRANCH);
		allLocalBranches.addAll(repositoryModel.getLocalBranches());
		boolean luceneEnabled = GitBlit.getBoolean(Keys.web.allowLuceneIndexing, true);
		final Palette<String> indexedBranchesPalette = new Palette<String>("indexedBranches", new ListModel<String>(
				indexedBranches), new CollectionModel<String>(allLocalBranches),
				new StringChoiceRenderer(), 8, false);
		indexedBranchesPalette.setEnabled(luceneEnabled);
		
		// federation sets palette
		List<String> sets = GitBlit.getStrings(Keys.federation.sets);
		final Palette<String> federationSetsPalette = new Palette<String>("federationSets",
				new ListModel<String>(federationSets), new CollectionModel<String>(sets),
				new StringChoiceRenderer(), 8, false);

		// pre-receive palette
		if (!ArrayUtils.isEmpty(repositoryModel.preReceiveScripts)) {
			preReceiveScripts.addAll(repositoryModel.preReceiveScripts);
		}
		final Palette<String> preReceivePalette = new Palette<String>("preReceiveScripts",
				new ListModel<String>(preReceiveScripts), new CollectionModel<String>(GitBlit
						.self().getPreReceiveScriptsUnused(repositoryModel)),
				new StringChoiceRenderer(), 12, true);

		// post-receive palette
		if (!ArrayUtils.isEmpty(repositoryModel.postReceiveScripts)) {
			postReceiveScripts.addAll(repositoryModel.postReceiveScripts);
		}
		final Palette<String> postReceivePalette = new Palette<String>("postReceiveScripts",
				new ListModel<String>(postReceiveScripts), new CollectionModel<String>(GitBlit
						.self().getPostReceiveScriptsUnused(repositoryModel)),
				new StringChoiceRenderer(), 12, true);
		
		// Dynamic Custom Defined Properties Properties
		final List<Entry<String, String>> definedProperties = new ArrayList<Entry<String, String>>();
		List<String> customFields = GitBlit.getStrings(Keys.repository.customFields);
		for (String customFieldDef : customFields) {
			String[] customFieldProperty = customFieldDef.split("=");
			definedProperties.add(new AbstractMap.SimpleEntry<String, String>(customFieldProperty[0], customFieldProperty[1]));
		}
		
		final ListView<Entry<String, String>> customDefinedProperties = new ListView<Entry<String, String>>("customDefinedProperties", definedProperties) {
			@Override
			protected void populateItem(ListItem<Entry<String, String>> item) {
				String value = repositoryModel.customDefinedProperties.get(item.getModelObject().getKey());
				
				item.add(new Label(item.getModelObject().getKey(), item.getModelObject().getValue()));		// Used to get the key later
				item.add(new Label("customLabel", item.getModelObject().getValue()));
				item.add(new TextField<String>("customValue", new Model<String>(value)));
			}
		};
		customDefinedProperties.setReuseItems(true);

		CompoundPropertyModel<RepositoryModel> model = new CompoundPropertyModel<RepositoryModel>(
				repositoryModel);
		Form<RepositoryModel> form = new Form<RepositoryModel>("editForm", model) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void onSubmit() {
				try {
					// confirm a repository name was entered
					if (StringUtils.isEmpty(repositoryModel.name)) {
						error(getString("gb.pleaseSetRepositoryName"));
						return;
					}

					// automatically convert backslashes to forward slashes
					repositoryModel.name = repositoryModel.name.replace('\\', '/');
					// Automatically replace // with /
					repositoryModel.name = repositoryModel.name.replace("//", "/");

					// prohibit folder paths
					if (repositoryModel.name.startsWith("/")) {
						error(getString("gb.illegalLeadingSlash"));
						return;
					}
					if (repositoryModel.name.startsWith("../")) {
						error(getString("gb.illegalRelativeSlash"));
						return;
					}
					if (repositoryModel.name.contains("/../")) {
						error(getString("gb.illegalRelativeSlash"));
						return;
					}

					// confirm valid characters in repository name
					Character c = StringUtils.findInvalidCharacter(repositoryModel.name);
					if (c != null) {
						error(MessageFormat.format(getString("gb.illegalCharacterRepositoryName"),
								c));
						return;
					}

					// confirm access restriction selection
					if (repositoryModel.accessRestriction == null) {
						error(getString("gb.selectAccessRestriction"));
						return;
					}

					// confirm federation strategy selection
					if (repositoryModel.federationStrategy == null) {
						error(getString("gb.selectFederationStrategy"));
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
						repositoryModel.mailingLists = new ArrayList<String>(list);
					}

					// indexed branches
					List<String> indexedBranches = new ArrayList<String>();
					Iterator<String> branches = indexedBranchesPalette.getSelectedChoices();
					while (branches.hasNext()) {
						indexedBranches.add(branches.next());
					}
					repositoryModel.indexedBranches = indexedBranches;

					// pre-receive scripts
					List<String> preReceiveScripts = new ArrayList<String>();
					Iterator<String> pres = preReceivePalette.getSelectedChoices();
					while (pres.hasNext()) {
						preReceiveScripts.add(pres.next());
					}
					repositoryModel.preReceiveScripts = preReceiveScripts;

					// post-receive scripts
					List<String> postReceiveScripts = new ArrayList<String>();
					Iterator<String> post = postReceivePalette.getSelectedChoices();
					while (post.hasNext()) {
						postReceiveScripts.add(post.next());
					}
					repositoryModel.postReceiveScripts = postReceiveScripts;
					
					// Loop over each of the user defined properties
					for (int i = 0; i < customDefinedProperties.size(); i++) {
						ListItem<ListItemModel<String>> item = (ListItem<ListItemModel<String>>) customDefinedProperties.get(i);
						String key = item.get(0).getId();		// Item 0 is our 'fake' label
						String value = ((TextField<String>)item.get(2)).getValue();		// Item 2 is out text box
						
						repositoryModel.customDefinedProperties.put(key, value);
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

		// do not let the browser pre-populate these fields
		form.add(new SimpleAttributeModifier("autocomplete", "off"));

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
		
		// allow relinking HEAD to a branch or tag other than master on edit repository
		List<String> availableRefs = new ArrayList<String>();
		if (!ArrayUtils.isEmpty(repositoryModel.availableRefs)) {
			availableRefs.addAll(repositoryModel.availableRefs);
		}
		form.add(new DropDownChoice<String>("HEAD", availableRefs).setEnabled(availableRefs.size() > 0));

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
		mailingLists = new Model<String>(ArrayUtils.isEmpty(repositoryModel.mailingLists) ? ""
				: StringUtils.flattenStrings(repositoryModel.mailingLists, " "));
		form.add(new TextField<String>("mailingLists", mailingLists));
		form.add(indexedBranchesPalette);
		form.add(usersPalette);
		form.add(teamsPalette);
		form.add(federationSetsPalette);
		form.add(preReceivePalette);
		form.add(new BulletListPanel("inheritedPreReceive", "inherited", GitBlit.self()
				.getPreReceiveScriptsInherited(repositoryModel)));
		form.add(postReceivePalette);
		form.add(new BulletListPanel("inheritedPostReceive", "inherited", GitBlit.self()
				.getPostReceiveScriptsInherited(repositoryModel)));
		
		WebMarkupContainer customDefinedPropertiesSection = new WebMarkupContainer("customDefinedPropertiesSection") {
			public boolean isVisible() {
				return GitBlit.getString(Keys.repository.customFields, "").isEmpty() == false;
			};
		};
		customDefinedPropertiesSection.add(customDefinedProperties);
		form.add(customDefinedPropertiesSection);

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
