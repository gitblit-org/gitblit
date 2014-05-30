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

	private DropDownChoice<String> projectChoice;

	private TextField<String> nameField;

	public RepositoryNamePanel(String wicketId, RepositoryModel repository) {
		super(wicketId);

		GitBlitWebSession session = GitBlitWebSession.get();
		UserModel user = session.getUser();

		// build project set for repository destination
		String defaultProject = null;
		Set<String> projectNames = new TreeSet<String>();

		// add the registered/known projects
		for (ProjectModel project : app().projects().getProjectModels(user, false)) {
			// TODO issue-351: user.canAdmin(project)
			if (user.canAdmin()) {
				if (project.isRoot) {
					projectNames.add("/");
				} else {
					projectNames.add(project.name + "/");
				}
			}
		}

		// add the user's personal project namespace
		if (user.canAdmin() || user.canCreate()) {
			projectNames.add(user.getPersonalPath() + "/");
		}

		if (!StringUtils.isEmpty(repository.name)) {
			// editing a repository name
			// set the defaultProject to the current repository project
			defaultProject = repository.projectPath;
			if (StringUtils.isEmpty(defaultProject)) {
				defaultProject = "/";
			} else {
				defaultProject += "/";
			}

			projectNames.add(defaultProject);
		}

		// if default project is not already set, set preference based on the user permissions
		if (defaultProject == null) {
			if (user.canAdmin()) {
				defaultProject = "/";
			} else if (user.canCreate()) {
				defaultProject = user.getPersonalPath() + "/";
			}
		}

		// update the model which is reflectively mapped to the Wicket fields by name
		repository.projectPath = defaultProject;
		if (repository.projectPath.length() > 1 && !StringUtils.isEmpty(repository.name)) {
			repository.name = repository.name.substring(repository.projectPath.length());
		}
		projectChoice = new DropDownChoice<String>("projectPath", new ArrayList<String>(projectNames));
		nameField = new TextField<String>("name");

		// only enable project selection if we actually have multiple choices
		add(projectChoice.setEnabled(projectNames.size() > 1));
		add(nameField);
		add(new TextField<String>("description"));
	}

	public void setEditable(boolean editable) {
		// only enable project selection if we actually have multiple choices
		projectChoice.setEnabled(projectChoice.getChoices().size() > 1 && editable);
		nameField.setEnabled(editable);
	}

	public boolean updateModel(RepositoryModel repositoryModel) {
		// confirm a project was selected
		if (StringUtils.isEmpty(repositoryModel.projectPath)) {
			error(getString("gb.pleaseSelectProject"));
			return false;
		}

		// confirm a repository name was entered
		if (StringUtils.isEmpty(repositoryModel.name)) {
			error(getString("gb.pleaseSetRepositoryName"));
			return false;
		}

		String project = repositoryModel.projectPath;

		fullName = (project + repositoryModel.name).trim();
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
		repositoryModel.projectPath = null;

		return true;
	}

	public void resetModel(RepositoryModel repositoryModel) {
		// restore project and name fields on error condition
		repositoryModel.projectPath = StringUtils.getFirstPathElement(fullName) + "/";
		if (repositoryModel.projectPath.length() > 1) {
			repositoryModel.name = fullName.substring(repositoryModel.projectPath.length());
		}
	}

	@Override
	protected boolean getStatelessHint() {
		return false;
	}
}
