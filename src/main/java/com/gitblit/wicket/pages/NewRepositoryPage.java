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
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.wicket.behavior.SimpleAttributeModifier;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
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
import com.gitblit.wicket.panels.AccessPolicyPanel;
import com.gitblit.wicket.panels.BooleanChoiceOption;
import com.gitblit.wicket.panels.BooleanOption;
import com.gitblit.wicket.panels.RepositoryNamePanel;
import com.google.common.base.Optional;

public class NewRepositoryPage extends RootSubPage {

	private final RepositoryModel repositoryModel;
	private final boolean allowAnonymousClones;
	private IModel<Boolean> addReadmeModel;
	private Model<String> gitignoreModel;
	private IModel<Boolean> addGitflowModel;
	private IModel<Boolean> addGitignoreModel;
	private AccessPolicyPanel accessPolicyPanel;
	private RepositoryNamePanel namePanel;

	public NewRepositoryPage() {
		// create constructor
		super();
		repositoryModel = new RepositoryModel();
		allowAnonymousClones = app().settings().getBoolean(Keys.git.allowAnonymousClones, true);
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
				try {
					if (!namePanel.updateModel(repositoryModel)) {
						return;
					}
					accessPolicyPanel.updateModel(repositoryModel);

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
							throw new GitBlitException(getString("gb.pleaseSelectGitIgnore"));
						}
					}

					// init the repository
					app().gitblit().updateRepositoryModel(repositoryModel.name, repositoryModel, true);

					// optionally create an initial commit
					initialCommit(repositoryModel, addReadme, gitignore, useGitFlow);

				} catch (GitBlitException e) {
					error(e.getMessage());
					return;
				}
				setRedirect(true);
				setResponsePage(SummaryPage.class, WicketUtils.newRepositoryParameter(repositoryModel.name));
			}
		};

		// do not let the browser pre-populate these fields
		form.add(new SimpleAttributeModifier("autocomplete", "off"));

		namePanel = new RepositoryNamePanel("namePanel", repositoryModel);
		form.add(namePanel);

		// prepare the default access controls
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

		repositoryModel.authorizationControl = defaultControl;
		repositoryModel.accessRestriction = defaultRestriction;

		accessPolicyPanel = new AccessPolicyPanel("accessPolicyPanel", repositoryModel, allowAnonymousClones);
		form.add(accessPolicyPanel);

		//
		// initial commit options
		//

		// add README
		addReadmeModel = Model.of(false);
		form.add(new BooleanOption("addReadme",
				getString("gb.initWithReadme"),
				getString("gb.initWithReadmeDescription"),
				addReadmeModel));

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
		addGitignoreModel = Model.of(false);
		form.add(new BooleanChoiceOption<String>("addGitIgnore",
				getString("gb.initWithGitignore"),
				getString("gb.initWithGitignoreDescription"),
				addGitignoreModel,
				gitignoreModel,
				gitignores).setVisible(gitignores.size() > 0));

		// TODO consider gitflow at creation (ticket-55)
		addGitflowModel = Model.of(false);
		form.add(new BooleanOption("addGitFlow",
				"Include a .gitflow file",
				"This will generate a config file which guides Git clients in setting up Gitflow branches.",
				addGitflowModel).setVisible(false));

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
			String email = Optional.fromNullable(user.emailAddress).or(user.username + "@" + "gitblit");
			PersonIdent author = new PersonIdent(user.getDisplayName(), email);

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
				revWalk.close();
			}
		} catch (UnsupportedEncodingException e) {
			logger().error(null, e);
		} catch (IOException e) {
			logger().error(null, e);
		} finally {
			odi.close();
			db.close();
		}
		return success;
	}
}
