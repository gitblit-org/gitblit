/*
 * Copyright 2014 gitblit.com.
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

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.behavior.SimpleAttributeModifier;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.Radio;
import org.apache.wicket.markup.html.form.RadioGroup;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import com.gitblit.Constants;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.AuthorizationControl;
import com.gitblit.GitBlitException;
import com.gitblit.Keys;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.FileUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;

public class NewRepositoryPage extends RootSubPage {

	private final RepositoryModel repositoryModel;
	private RadioGroup<Permission> permissionGroup;
	private IModel<Boolean> addReadmeModel;
	private Model<String> gitignoreModel;
	private IModel<Boolean> addGitflowModel;
	private IModel<Boolean> addGitignoreModel;

	public NewRepositoryPage() {
		// create constructor
		super();
		repositoryModel = new RepositoryModel();

		setupPage(getString("gb.newRepository"), "");

		setStatelessHint(false);
		setOutputMarkupId(true);
	}

	@Override
	protected boolean requiresPageMap() {
		return true;
	}

	@Override
	protected Class<? extends BasePage> getRootNavPageClass() {
		return RepositoriesPage.class;
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();

		CompoundPropertyModel<RepositoryModel> rModel = new CompoundPropertyModel<>(repositoryModel);
		Form<RepositoryModel> form = new Form<RepositoryModel>("editForm", rModel) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void onSubmit() {

				// confirm a repository name was entered
				if (StringUtils.isEmpty(repositoryModel.name)) {
					error(getString("gb.pleaseSetRepositoryName"));
					return;
				}

				String project = repositoryModel.projectPath;
				String fullName = (project + "/" + repositoryModel.name).trim();
				fullName = fullName.replace('\\', '/');
				fullName = fullName.replace("//", "/");
				if (fullName.charAt(0) == '/') {
					fullName = fullName.substring(1);
				}
				if (fullName.endsWith("/")) {
					fullName = fullName.substring(0, fullName.length() - 1);
				}

				try {
					if (fullName.contains("../")) {
						error(getString("gb.illegalRelativeSlash"));
						return;
					}
					if (fullName.contains("/../")) {
						error(getString("gb.illegalRelativeSlash"));
						return;
					}

					// confirm valid characters in repository name
					Character c = StringUtils.findInvalidCharacter(fullName);
					if (c != null) {
						error(MessageFormat.format(getString("gb.illegalCharacterRepositoryName"),
								c));
						return;
					}

					repositoryModel.name = fullName;
					repositoryModel.projectPath = null;

					Permission permission = permissionGroup.getModelObject();
					repositoryModel.authorizationControl = permission.control;
					repositoryModel.accessRestriction = permission.type;

					repositoryModel.owners = new ArrayList<String>();
					repositoryModel.owners.add(GitBlitWebSession.get().getUsername());

					// setup branch defaults
					boolean useGitFlow = addGitflowModel.getObject();

					repositoryModel.HEAD = Constants.R_MASTER;
					repositoryModel.mergeTo = Constants.MASTER;
					if (useGitFlow) {
						// tickets normally merge to develop unless they are hotfixes
						repositoryModel.mergeTo = Constants.DEVELOP;
					}

					repositoryModel.allowForks = app().settings().getBoolean(Keys.web.allowForking, true);

					// optionally generate an initial commit
					boolean addReadme = addReadmeModel.getObject();
					String gitignore = null;
					boolean addGitignore = addGitignoreModel.getObject();
					if (addGitignore) {
						gitignore = gitignoreModel.getObject();
						if (StringUtils.isEmpty(gitignore)) {
							throw new GitBlitException("Please select a .gitignore file");
						}
					}

					// init the repository
					app().gitblit().updateRepositoryModel(repositoryModel.name, repositoryModel, true);

					// optionally create an initial commit
					initialCommit(repositoryModel, addReadme, gitignore, useGitFlow);

				} catch (GitBlitException e) {
					error(e.getMessage());

					// restore project and name fields on error condition
					repositoryModel.projectPath = StringUtils.getFirstPathElement(fullName);
					if (!StringUtils.isEmpty(repositoryModel.projectPath)) {
						repositoryModel.name = fullName.substring(repositoryModel.projectPath.length() + 1);
					}
					return;
				}
				setRedirect(true);
				setResponsePage(SummaryPage.class, WicketUtils.newRepositoryParameter(fullName));
			}
		};

		GitBlitWebSession session = GitBlitWebSession.get();
		UserModel user = session.getUser();

		// build project list for repository destination
		String defaultProject = null;
		List<String> projects = new ArrayList<String>();

		if (user.canAdmin()) {
			String main = app().settings().getString(Keys.web.repositoryRootGroupName, "main");
			projects.add(main);
			defaultProject = main;
		}

		if (user.canCreate()) {
			projects.add(user.getPersonalPath());
			if (defaultProject == null) {
				// only prefer personal namespace if default is not already set
				defaultProject = user.getPersonalPath();
			}
		}

		repositoryModel.projectPath = defaultProject;

		// do not let the browser pre-populate these fields
		form.add(new SimpleAttributeModifier("autocomplete", "off"));

		form.add(new DropDownChoice<String>("projectPath", projects));
		form.add(new TextField<String>("name"));
		form.add(new TextField<String>("description"));

		Permission anonymousPermission = new Permission(getString("gb.anonymousPush"),
				getString("gb.anonymousPushDescription"),
				"blank.png",
				AuthorizationControl.AUTHENTICATED,
				AccessRestrictionType.NONE);

		Permission authenticatedPermission = new Permission(getString("gb.pushRestrictedAuthenticated"),
				getString("gb.pushRestrictedAuthenticatedDescription"),
				"lock_go_16x16.png",
				AuthorizationControl.AUTHENTICATED,
				AccessRestrictionType.PUSH);

		Permission publicPermission = new Permission(getString("gb.pushRestrictedNamed"),
				getString("gb.pushRestrictedNamedDescription"),
				"lock_go_16x16.png",
				AuthorizationControl.NAMED,
				AccessRestrictionType.PUSH);

		Permission protectedPermission = new Permission(getString("gb.cloneRestricted"),
				getString("gb.cloneRestrictedDescription"),
				"lock_pull_16x16.png",
				AuthorizationControl.NAMED,
				AccessRestrictionType.CLONE);

		Permission privatePermission = new Permission(getString("gb.private"),
				getString("gb.privateRepoDescription"),
				"shield_16x16.png",
				AuthorizationControl.NAMED,
				AccessRestrictionType.VIEW);

		List<Permission> permissions = new ArrayList<Permission>();
		if (app().settings().getBoolean(Keys.git.allowAnonymousPushes, false)) {
			permissions.add(anonymousPermission);
		}
		permissions.add(authenticatedPermission);
		permissions.add(publicPermission);
		permissions.add(protectedPermission);
		permissions.add(privatePermission);

		// determine default permission selection
		AccessRestrictionType defaultRestriction = AccessRestrictionType.fromName(
				app().settings().getString(Keys.git.defaultAccessRestriction, AccessRestrictionType.PUSH.name()));
		if (AccessRestrictionType.NONE == defaultRestriction) {
			defaultRestriction = AccessRestrictionType.PUSH;
		}
		AuthorizationControl defaultControl = AuthorizationControl.fromName(
				app().settings().getString(Keys.git.defaultAuthorizationControl, AuthorizationControl.NAMED.name()));

		if (AuthorizationControl.AUTHENTICATED == defaultControl) {
			defaultRestriction = AccessRestrictionType.PUSH;
		}

		Permission defaultPermission = publicPermission;
		for (Permission permission : permissions) {
			if (permission.type == defaultRestriction
					&& permission.control == defaultControl) {
				defaultPermission = permission;
			}
		}

		permissionGroup = new RadioGroup<>("permissionsGroup", new Model<Permission>(defaultPermission));
		form.add(permissionGroup);

		ListView<Permission> permissionsList = new ListView<Permission>("permissions", permissions) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void populateItem(ListItem<Permission> item) {
				Permission p = item.getModelObject();
				item.add(new Radio<Permission>("radio", item.getModel()));
				item.add(WicketUtils.newImage("image",  p.image));
				item.add(new Label("name", p.name));
				item.add(new Label("description", p.description));
			}
		};
		permissionGroup.add(permissionsList);

		//
		// initial commit options
		//

		// add README
		addReadmeModel = Model.of(false);
		form.add(new CheckBox("addReadme", addReadmeModel));

		// add .gitignore
		File gitignoreDir = app().runtime().getFileOrFolder(Keys.git.gitignoreFolder, "${baseFolder}/gitignore");
		File [] files = gitignoreDir.listFiles();
		if (files == null) {
			files = new File[0];
		}
		List<String> gitignores = new ArrayList<String>();
		for (File file : files) {
			if (file.isFile() && file.getName().endsWith(".gitignore")) {
				gitignores.add(StringUtils.stripFileExtension(file.getName()));
			}
		}
		Collections.sort(gitignores);
		gitignoreModel = Model.of("");
		final DropDownChoice<String> gitignoreChoice = new DropDownChoice<String>("gitignore", gitignoreModel, gitignores);
		gitignoreChoice.setOutputMarkupId(true);
		form.add(gitignoreChoice.setEnabled(false));

		addGitignoreModel = Model.of(false);
		final CheckBox gitignoreCheckbox = new CheckBox("addGitignore", addGitignoreModel);
		form.add(gitignoreCheckbox);

		gitignoreCheckbox.add(new AjaxFormComponentUpdatingBehavior("onchange") {

			private static final long serialVersionUID = 1L;

			@Override
			protected void onUpdate(AjaxRequestTarget target) {
				gitignoreChoice.setEnabled(addGitignoreModel.getObject());
				target.addComponent(gitignoreChoice);
			}
		});

		// TODO add .gitflow
		addGitflowModel = Model.of(false);
		form.add(new CheckBox("addGitflow", addGitflowModel));

		form.add(new Button("create"));

		add(form);
	}

	/**
	 * Prepare the initial commit for the repository.
	 *
	 * @param repository
	 * @param addReadme
	 * @param gitignore
	 * @param addGitFlow
	 * @return true if an initial commit was created
	 */
	protected boolean initialCommit(RepositoryModel repository, boolean addReadme, String gitignore,
			boolean addGitFlow) {
		boolean initialCommit = addReadme || !StringUtils.isEmpty(gitignore) || addGitFlow;
		if (!initialCommit) {
			return false;
		}

		// build an initial commit
		boolean success = false;
		Repository db = app().repositories().getRepository(repositoryModel.name);
		ObjectInserter odi = db.newObjectInserter();
		try {

			UserModel user = GitBlitWebSession.get().getUser();
			PersonIdent author = new PersonIdent(user.getDisplayName(), user.emailAddress);

			DirCache newIndex = DirCache.newInCore();
			DirCacheBuilder indexBuilder = newIndex.builder();

			if (addReadme) {
				// insert a README
				String title = StringUtils.stripDotGit(StringUtils.getLastPathElement(repositoryModel.name));
				String description = repositoryModel.description == null ? "" : repositoryModel.description;
				String readme = String.format("## %s\n\n%s\n\n", title, description);
				byte [] bytes = readme.getBytes(Constants.ENCODING);

				DirCacheEntry entry = new DirCacheEntry("README.md");
				entry.setLength(bytes.length);
				entry.setLastModified(System.currentTimeMillis());
				entry.setFileMode(FileMode.REGULAR_FILE);
				entry.setObjectId(odi.insert(org.eclipse.jgit.lib.Constants.OBJ_BLOB, bytes));

				indexBuilder.add(entry);
			}

			if (!StringUtils.isEmpty(gitignore)) {
				// insert a .gitignore file
				File dir = app().runtime().getFileOrFolder(Keys.git.gitignoreFolder, "${baseFolder}/gitignore");
				File file = new File(dir, gitignore + ".gitignore");
				if (file.exists() && file.length() > 0) {
					byte [] bytes = FileUtils.readContent(file);
					if (!ArrayUtils.isEmpty(bytes)) {
						DirCacheEntry entry = new DirCacheEntry(".gitignore");
						entry.setLength(bytes.length);
						entry.setLastModified(System.currentTimeMillis());
						entry.setFileMode(FileMode.REGULAR_FILE);
						entry.setObjectId(odi.insert(org.eclipse.jgit.lib.Constants.OBJ_BLOB, bytes));

						indexBuilder.add(entry);
					}
				}
			}

			if (addGitFlow) {
				// insert a .gitflow file
				Config config = new Config();
				config.setString("gitflow", null, "masterBranch", Constants.MASTER);
				config.setString("gitflow", null, "developBranch", Constants.DEVELOP);
				config.setString("gitflow", null, "featureBranchPrefix", "feature/");
				config.setString("gitflow", null, "releaseBranchPrefix", "release/");
				config.setString("gitflow", null, "hotfixBranchPrefix", "hotfix/");
				config.setString("gitflow", null, "supportBranchPrefix", "support/");
				config.setString("gitflow", null, "versionTagPrefix", "");

				byte [] bytes = config.toText().getBytes(Constants.ENCODING);

				DirCacheEntry entry = new DirCacheEntry(".gitflow");
				entry.setLength(bytes.length);
				entry.setLastModified(System.currentTimeMillis());
				entry.setFileMode(FileMode.REGULAR_FILE);
				entry.setObjectId(odi.insert(org.eclipse.jgit.lib.Constants.OBJ_BLOB, bytes));

				indexBuilder.add(entry);
			}

			indexBuilder.finish();

			if (newIndex.getEntryCount() == 0) {
				// nothing to commit
				return false;
			}

			ObjectId treeId = newIndex.writeTree(odi);

			// Create a commit object
			CommitBuilder commit = new CommitBuilder();
			commit.setAuthor(author);
			commit.setCommitter(author);
			commit.setEncoding(Constants.ENCODING);
			commit.setMessage("Initial commit");
			commit.setTreeId(treeId);

			// Insert the commit into the repository
			ObjectId commitId = odi.insert(commit);
			odi.flush();

			// set the branch refs
			RevWalk revWalk = new RevWalk(db);
			try {
				// set the master branch
				RevCommit revCommit = revWalk.parseCommit(commitId);
				RefUpdate masterRef = db.updateRef(Constants.R_MASTER);
				masterRef.setNewObjectId(commitId);
				masterRef.setRefLogMessage("commit: " + revCommit.getShortMessage(), false);
				Result masterRC = masterRef.update();
				switch (masterRC) {
				case NEW:
					success = true;
					break;
				default:
					success = false;
				}

				if (addGitFlow) {
					// set the develop branch for git-flow
					RefUpdate developRef = db.updateRef(Constants.R_DEVELOP);
					developRef.setNewObjectId(commitId);
					developRef.setRefLogMessage("commit: " + revCommit.getShortMessage(), false);
					Result developRC = developRef.update();
					switch (developRC) {
					case NEW:
						success = true;
						break;
					default:
						success = false;
					}
				}
			} finally {
				revWalk.release();
			}
		} catch (UnsupportedEncodingException e) {
			logger().error(null, e);
		} catch (IOException e) {
			logger().error(null, e);
		} finally {
			odi.release();
			db.close();
		}
		return success;
	}

	private static class Permission implements Serializable {

		private static final long serialVersionUID = 1L;

		final String name;
		final String description;
		final String image;
		final AuthorizationControl control;
		final AccessRestrictionType type;

		Permission(String name, String description, String img, AuthorizationControl control, AccessRestrictionType type) {
			this.name = name;
			this.description = description;
			this.image = img;
			this.control = control;
			this.type = type;
		}
	}
}
