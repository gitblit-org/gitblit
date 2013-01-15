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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.wicket.PageParameters;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormChoiceComponentUpdatingBehavior;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.behavior.SimpleAttributeModifier;
import org.apache.wicket.extensions.markup.html.form.palette.Palette;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.html.form.RadioChoice;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.util.CollectionModel;
import org.apache.wicket.model.util.ListModel;

import com.gitblit.Constants;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.AuthorizationControl;
import com.gitblit.Constants.FederationStrategy;
import com.gitblit.Constants.RegistrantType;
import com.gitblit.GitBlit;
import com.gitblit.GitBlitException;
import com.gitblit.Keys;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.MultiConfigUtil;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.StringChoiceRenderer;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.BulletListPanel;
import com.gitblit.wicket.panels.RegistrantPermissionsPanel;

public class EditRepositoryPage extends RootSubPage {

	private MultiConfigUtil multiConfigUtil = new MultiConfigUtil();
	
	private final boolean isCreate;

	private boolean isAdmin;
	
	RepositoryModel repositoryModel;

	private IModel<String> mailingLists;

	public EditRepositoryPage() {
		// create constructor
		super();
		isCreate = true;
		RepositoryModel model = new RepositoryModel();
		String restriction = GitBlit.getString(Keys.git.defaultAccessRestriction, null);
		model.accessRestriction = AccessRestrictionType.fromName(restriction);
		String authorization = GitBlit.getString(Keys.git.defaultAuthorizationControl, null);
		model.authorizationControl = AuthorizationControl.fromName(authorization);
		
		GitBlitWebSession session = GitBlitWebSession.get();
		UserModel user = session.getUser();
		if (user != null && user.canCreate() && !user.canAdmin()) {
			// personal create permissions, inject personal repository path
			model.name = user.getPersonalPath() + "/";
			model.projectPath = user.getPersonalPath();
			model.addRepoAdministrator(user.username);
			// personal repositories are private by default
			model.accessRestriction = AccessRestrictionType.VIEW;
			model.authorizationControl = AuthorizationControl.NAMED;
		}
		
		setupPage(model);
		setStatelessHint(false);
		setOutputMarkupId(true);
	}

	public EditRepositoryPage(PageParameters params) {
		// edit constructor
		super(params);
		isCreate = false;
		String name = WicketUtils.getRepositoryName(params);
		RepositoryModel model = GitBlit.self().getRepositoryModel(name);
		setupPage(model);
		setStatelessHint(false);
		setOutputMarkupId(true);
	}
	
	@Override
	protected boolean requiresPageMap() {
		return true;
	}

	protected void setupPage(RepositoryModel model) {
		this.repositoryModel = model;
		
		// ensure this user can create or edit this repository
		checkPermissions(repositoryModel);

		List<String> indexedBranches = new ArrayList<String>();
		List<String> federationSets = new ArrayList<String>();
		final List<RegistrantAccessPermission> repositoryUsers = new ArrayList<RegistrantAccessPermission>();
		final List<RegistrantAccessPermission> repositoryTeams = new ArrayList<RegistrantAccessPermission>();
		List<String> preReceiveScripts = new ArrayList<String>();
		List<String> postReceiveScripts = new ArrayList<String>();

		GitBlitWebSession session = GitBlitWebSession.get();
		final UserModel user = session.getUser() == null ? UserModel.ANONYMOUS : session.getUser();
		final boolean allowEditName = isCreate || isAdmin || repositoryModel.isUsersPersonalRepository(user.username);
		
		if (isCreate) {
			if (user.canAdmin()) {
				super.setupPage(getString("gb.newRepository"), "");
			} else {
				super.setupPage(getString("gb.newRepository"), user.getDisplayName());
			}
		} else {
			super.setupPage(getString("gb.edit"), repositoryModel.name);
			repositoryUsers.addAll(GitBlit.self().getUserAccessPermissions(repositoryModel));
			repositoryTeams.addAll(GitBlit.self().getTeamAccessPermissions(repositoryModel));
			Collections.sort(repositoryUsers);
			Collections.sort(repositoryTeams);
			
			federationSets.addAll(repositoryModel.federationSets);
			if (!ArrayUtils.isEmpty(repositoryModel.indexedBranches)) {
				indexedBranches.addAll(repositoryModel.indexedBranches);
			}
		}

		final String oldName = repositoryModel.name;

		final RegistrantPermissionsPanel usersPalette = new RegistrantPermissionsPanel("users", 
				RegistrantType.USER, GitBlit.self().getAllUsernames(), repositoryUsers, getAccessPermissions());
		final RegistrantPermissionsPanel teamsPalette = new RegistrantPermissionsPanel("teams", 
				RegistrantType.TEAM, GitBlit.self().getAllTeamnames(), repositoryTeams, getAccessPermissions());

		// repo administrators palette
		List admins = multiConfigUtil.convertCollectionToList(repositoryModel.getRepoAdministrators());
		List persons = GitBlit.self().getAllUsernames();
		final Palette repoAdministratorsPalette = new Palette("repoAdministrators", new ListModel<String>(admins), new CollectionModel<String>(
		      persons), new StringChoiceRenderer(), 10, true);
		
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
		
		// custom fields
		final Map<String, String> customFieldsMap = GitBlit.getMap(Keys.groovy.customFields);
		List<String> customKeys = new ArrayList<String>(customFieldsMap.keySet());
		final ListView<String> customFieldsListView = new ListView<String>("customFieldsListView", customKeys) {
			
			private static final long serialVersionUID = 1L;

			@Override
			protected void populateItem(ListItem<String> item) {
				String key = item.getModelObject();
				item.add(new Label("customFieldLabel", customFieldsMap.get(key)));
				
				String value = "";
				if (repositoryModel.customFields != null && repositoryModel.customFields.containsKey(key)) {
					value = repositoryModel.customFields.get(key);
				}
				TextField<String> field = new TextField<String>("customFieldValue", new Model<String>(value));
				item.add(field);
			}
		};
		customFieldsListView.setReuseItems(true);

		CompoundPropertyModel<RepositoryModel> rModel = new CompoundPropertyModel<RepositoryModel>(
				repositoryModel);
		Form<RepositoryModel> form = new Form<RepositoryModel>("editForm", rModel) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void onSubmit() {
				try {
					// confirm a repository name was entered
					if (repositoryModel.name == null && StringUtils.isEmpty(repositoryModel.name)) {
						error(getString("gb.pleaseSetRepositoryName"));
						return;
					}
					
					// ensure name is trimmed
					repositoryModel.name = repositoryModel.name.trim();
					
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
					if (repositoryModel.name.endsWith("/")) {
						repositoryModel.name = repositoryModel.name.substring(0, repositoryModel.name.length() - 1);
					}

					// confirm valid characters in repository name
					Character c = StringUtils.findInvalidCharacter(repositoryModel.name);
					if (c != null) {
						error(MessageFormat.format(getString("gb.illegalCharacterRepositoryName"),
								c));
						return;
					}
					
					if (user.canCreate() && !user.canAdmin() && allowEditName) {
						// ensure repository name begins with the user's path
						if (!repositoryModel.name.startsWith(user.getPersonalPath())) {
							error(MessageFormat.format(getString("gb.illegalPersonalRepositoryLocation"),
									user.getPersonalPath()));
							return;
						}
						
						if (repositoryModel.name.equals(user.getPersonalPath())) {
							// reset path prefix and show error
							repositoryModel.name = user.getPersonalPath() + "/";
							error(getString("gb.pleaseSetRepositoryName"));
							return;
						}
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

					repositoryModel.removeAllRepoAdministrators();
					Iterator<String> repoAdmins = repoAdministratorsPalette.getSelectedChoices();
					while (repoAdmins.hasNext()) {
						repositoryModel.addRepoAdministrator(repoAdmins.next());
					}
					
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
					
					// custom fields
					repositoryModel.customFields = new LinkedHashMap<String, String>();
					for (int i = 0; i < customFieldsListView.size(); i++) {
						ListItem<String> child = (ListItem<String>) customFieldsListView.get(i);
						String key = child.getModelObject();

						TextField<String> field = (TextField<String>) child.get("customFieldValue");
						String value = field.getValue();
						
						repositoryModel.customFields.put(key, value);
					}
					
					// save the repository
					GitBlit.self().updateRepositoryModel(oldName, repositoryModel, isCreate);

					// repository access permissions
					if (repositoryModel.accessRestriction.exceeds(AccessRestrictionType.NONE)) {
						GitBlit.self().setUserAccessPermissions(repositoryModel, repositoryUsers);
						GitBlit.self().setTeamAccessPermissions(repositoryModel, repositoryTeams);
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
		form.add(new TextField<String>("name").setEnabled(allowEditName));
		form.add(new TextField<String>("description"));
		form.add(repoAdministratorsPalette);
		form.add(new CheckBox("allowForks").setEnabled(GitBlit.getBoolean(Keys.web.allowForking, true)));
		DropDownChoice<AccessRestrictionType> accessRestriction = new DropDownChoice<AccessRestrictionType>("accessRestriction", Arrays
				.asList(AccessRestrictionType.values()), new AccessRestrictionRenderer());
		form.add(accessRestriction);
		form.add(new CheckBox("isFrozen"));
		// TODO enable origin definition
		form.add(new TextField<String>("origin").setEnabled(false/* isCreate */));
		
		// allow relinking HEAD to a branch or tag other than master on edit repository
		List<String> availableRefs = new ArrayList<String>();
		if (!ArrayUtils.isEmpty(repositoryModel.availableRefs)) {
			availableRefs.addAll(repositoryModel.availableRefs);
		}
		form.add(new DropDownChoice<String>("HEAD", availableRefs).setEnabled(availableRefs.size() > 0));

		boolean gcEnabled = GitBlit.getBoolean(Keys.git.enableGarbageCollection, false); 
		List<Integer> gcPeriods = Arrays.asList(1, 2, 3, 4, 5, 7, 10, 14 );
		form.add(new DropDownChoice<Integer>("gcPeriod", gcPeriods, new GCPeriodRenderer()).setEnabled(gcEnabled));
		form.add(new TextField<String>("gcThreshold").setEnabled(gcEnabled));

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
		List<Integer> maxActivityCommits  = Arrays.asList(-1, 0, 25, 50, 75, 100, 150, 200, 250, 500 );
		form.add(new DropDownChoice<Integer>("maxActivityCommits", maxActivityCommits, new MaxActivityCommitsRenderer()));

		mailingLists = new Model<String>(ArrayUtils.isEmpty(repositoryModel.mailingLists) ? ""
				: StringUtils.flattenStrings(repositoryModel.mailingLists, " "));
		form.add(new TextField<String>("mailingLists", mailingLists));
		form.add(indexedBranchesPalette);
		
		List<AuthorizationControl> acList = Arrays.asList(AuthorizationControl.values());
		final RadioChoice<AuthorizationControl> authorizationControl = new RadioChoice<Constants.AuthorizationControl>(
				"authorizationControl", acList, new AuthorizationControlRenderer());
		form.add(authorizationControl);
		
		final CheckBox verifyCommitter = new CheckBox("verifyCommitter");
		verifyCommitter.setOutputMarkupId(true);
		form.add(verifyCommitter);

		form.add(usersPalette);
		form.add(teamsPalette);
		form.add(federationSetsPalette);
		form.add(preReceivePalette);
		form.add(new BulletListPanel("inheritedPreReceive", getString("gb.inherited"), GitBlit.self()
				.getPreReceiveScriptsInherited(repositoryModel)));
		form.add(postReceivePalette);
		form.add(new BulletListPanel("inheritedPostReceive", getString("gb.inherited"), GitBlit.self()
				.getPostReceiveScriptsInherited(repositoryModel)));
		
		WebMarkupContainer customFieldsSection = new WebMarkupContainer("customFieldsSection");
		customFieldsSection.add(customFieldsListView);
		form.add(customFieldsSection.setVisible(!GitBlit.getString(Keys.groovy.customFields, "").isEmpty()));
		
		// initial enable/disable of permission controls
		if (repositoryModel.accessRestriction.equals(AccessRestrictionType.NONE)) {
			// anonymous everything, disable all controls
			usersPalette.setEnabled(false);
			teamsPalette.setEnabled(false);
			authorizationControl.setEnabled(false);
			verifyCommitter.setEnabled(false);
		} else {
			// authenticated something
			// enable authorization controls
			authorizationControl.setEnabled(true);
			verifyCommitter.setEnabled(true);
			
			boolean allowFineGrainedControls = repositoryModel.authorizationControl.equals(AuthorizationControl.NAMED);
			usersPalette.setEnabled(allowFineGrainedControls);
			teamsPalette.setEnabled(allowFineGrainedControls);
		}
		
		accessRestriction.add(new AjaxFormComponentUpdatingBehavior("onchange") {
	           
			private static final long serialVersionUID = 1L;

			protected void onUpdate(AjaxRequestTarget target) {
				// enable/disable permissions panel based on access restriction
				boolean allowAuthorizationControl = repositoryModel.accessRestriction.exceeds(AccessRestrictionType.NONE);
				authorizationControl.setEnabled(allowAuthorizationControl);
				verifyCommitter.setEnabled(allowAuthorizationControl);
				
				boolean allowFineGrainedControls = allowAuthorizationControl && repositoryModel.authorizationControl.equals(AuthorizationControl.NAMED);
				usersPalette.setEnabled(allowFineGrainedControls);
				teamsPalette.setEnabled(allowFineGrainedControls);
				
				if (allowFineGrainedControls) {
					repositoryModel.authorizationControl = AuthorizationControl.NAMED;
				}
				
				target.addComponent(authorizationControl);
				target.addComponent(verifyCommitter);
				target.addComponent(usersPalette);
				target.addComponent(teamsPalette);
			}
		});
		
		authorizationControl.add(new AjaxFormChoiceComponentUpdatingBehavior() {
	           
			private static final long serialVersionUID = 1L;

			protected void onUpdate(AjaxRequestTarget target) {
				// enable/disable permissions panel based on access restriction
				boolean allowAuthorizationControl = repositoryModel.accessRestriction.exceeds(AccessRestrictionType.NONE);
				authorizationControl.setEnabled(allowAuthorizationControl);
				
				boolean allowFineGrainedControls = allowAuthorizationControl && repositoryModel.authorizationControl.equals(AuthorizationControl.NAMED);
				usersPalette.setEnabled(allowFineGrainedControls);
				teamsPalette.setEnabled(allowFineGrainedControls);
				
				if (allowFineGrainedControls) {
					repositoryModel.authorizationControl = AuthorizationControl.NAMED;
				}
				
				target.addComponent(authorizationControl);
				target.addComponent(usersPalette);
				target.addComponent(teamsPalette);
			}
		});
		
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
					error(getString("gb.errorAdminLoginRequired"), true);
				}
				if (isCreate) {
					// Create Repository
					if (!user.canCreate() && !user.canAdmin()) {
						// Only administrators or permitted users may create
						error(getString("gb.errorOnlyAdminMayCreateRepository"), true);
					}
				} else {
					// Edit Repository
					if (user.canAdmin()) {
						// Admins can edit everything
						isAdmin = true;
						return;
					} else {
						if (!model.isRepoAdministrator(user.username)) {
							// User is not an Admin nor RepoAdministrator
							error(getString("gb.errorOnlyAdminOrRepoAdminMayEditRepository"), true);
						}
					}
				}
			}
		} else {
			// No Administration Permitted
			error(getString("gb.errorAdministrationDisabled"), true);
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
	
	private class AuthorizationControlRenderer implements IChoiceRenderer<AuthorizationControl> {

		private static final long serialVersionUID = 1L;

		private final Map<AuthorizationControl, String> map;

		public AuthorizationControlRenderer() {
			map = getAuthorizationControls();
		}

		@Override
		public String getDisplayValue(AuthorizationControl type) {
			return map.get(type);
		}

		@Override
		public String getIdValue(AuthorizationControl type, int index) {
			return Integer.toString(index);
		}
	}
	
	private class GCPeriodRenderer implements IChoiceRenderer<Integer> {

		private static final long serialVersionUID = 1L;

		public GCPeriodRenderer() {
		}

		@Override
		public String getDisplayValue(Integer value) {
			if (value == 1) {
				return getString("gb.duration.oneDay");
			} else {
				return MessageFormat.format(getString("gb.duration.days"), value);
			}
		}

		@Override
		public String getIdValue(Integer value, int index) {
			return Integer.toString(index);
		}
	}
	
	private class MaxActivityCommitsRenderer implements IChoiceRenderer<Integer> {

		private static final long serialVersionUID = 1L;

		public MaxActivityCommitsRenderer() {
		}

		@Override
		public String getDisplayValue(Integer value) {
			if (value == -1) {
				return getString("gb.excludeFromActivity");
			} else if (value == 0) {
				return getString("gb.noMaximum");
			} else {
				return value + " " + getString("gb.commits");
			}
		}

		@Override
		public String getIdValue(Integer value, int index) {
			return Integer.toString(index);
		}
	}
	
}
