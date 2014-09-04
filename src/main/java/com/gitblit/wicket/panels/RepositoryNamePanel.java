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
package com.gitblit.wicket.panels;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import com.gitblit.models.ProjectModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;

/**
 * A panel for naming a repository, specifying it's project, and entering a description.
 *
 * @author James Moger
 *
 */
public class RepositoryNamePanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	private String fullName;

	private final IModel<String> projectPath;

	private DropDownChoice<String> pathChoice;

	private final IModel<String> repoName;

	private TextField<String> nameField;

	public RepositoryNamePanel(String wicketId, RepositoryModel repository) {
		super(wicketId);

		GitBlitWebSession session = GitBlitWebSession.get();
		UserModel user = session.getUser();
		if (user == null) {
			user = UserModel.ANONYMOUS;
		}

		// build project set for repository destination
		String defaultPath = null;
		String defaultName = null;
		Set<String> pathNames = new TreeSet<String>();

		// add the registered/known projects
		for (ProjectModel project : app().projects().getProjectModels(user, false)) {
			// TODO issue-351: user.canAdmin(project)
			if (user.canAdmin()) {
				if (project.isRoot) {
					pathNames.add("/");
				} else {
					pathNames.add(project.name + "/");
				}
			}
		}

		// add the user's personal project namespace
		if (user.canAdmin() || user.canCreate()) {
			pathNames.add(user.getPersonalPath() + "/");
		}

		if (!StringUtils.isEmpty(repository.name)) {
			// editing a repository name
			// set the defaultProject to the current repository project
			if (StringUtils.isEmpty(repository.projectPath)) {
				defaultPath = "/";
				defaultName = repository.name;
			} else {
				defaultPath = repository.projectPath + "/";
				defaultName = repository.name.substring(defaultPath.length());
			}
			pathNames.add(defaultPath);
		}

		// if default project is not already set, set preference based on the user permissions
		if (defaultPath == null) {
			if (user.canAdmin()) {
				defaultPath = "/";
			} else if (user.canCreate()) {
				defaultPath = user.getPersonalPath() + "/";
			}
		}

		projectPath = Model.of(defaultPath);
		pathChoice = new DropDownChoice<String>("projectPath", projectPath, new ArrayList<String>(pathNames));
		repoName = Model.of(defaultName);
		nameField = new TextField<String>("name", repoName);

		// only enable project selection if we actually have multiple choices
		add(pathChoice.setEnabled(pathNames.size() > 1));
		add(nameField);
		add(new TextField<String>("description"));
	}

	public void setEditable(boolean editable) {
		// only enable project selection if we actually have multiple choices
		pathChoice.setEnabled(pathChoice.getChoices().size() > 1 && editable);
		nameField.setEnabled(editable);
	}

	public boolean updateModel(RepositoryModel repositoryModel) {
		// confirm a project path was selected
		if (StringUtils.isEmpty(projectPath.getObject())) {
			error(getString("gb.pleaseSelectProject"));
			return false;
		}

		// confirm a repository name was entered
		if (StringUtils.isEmpty(repoName.getObject())) {
			error(getString("gb.pleaseSetRepositoryName"));
			return false;
		}

		String project = projectPath.getObject();
		String name = repoName.getObject();

		fullName = (project + name).trim();
		fullName = fullName.replace('\\', '/');
		fullName = fullName.replace("//", "/");
		if (fullName.charAt(0) == '/') {
			fullName = fullName.substring(1);
		}
		if (fullName.endsWith("/")) {
			fullName = fullName.substring(0, fullName.length() - 1);
		}

		if (fullName.contains("../")) {
			error(getString("gb.illegalRelativeSlash"));
			return false;
		}
		if (fullName.contains("/../")) {
			error(getString("gb.illegalRelativeSlash"));
			return false;
		}

		// confirm valid characters in repository name
		Character c = StringUtils.findInvalidCharacter(fullName);
		if (c != null) {
			error(MessageFormat.format(getString("gb.illegalCharacterRepositoryName"), c));
			return false;
		}

		repositoryModel.name = fullName;

		return true;
	}

	@Override
	protected boolean getStatelessHint() {
		return false;
	}
}