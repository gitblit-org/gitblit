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
import java.util.List;

import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.TextField;

import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;

/**
 * A radio group panel of the 5 available authorization/access restriction combinations.
 *
 * @author James Moger
 *
 */
public class RepositoryNamePanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	private final RepositoryModel repository;

	private String fullName;

	public RepositoryNamePanel(String wicketId, RepositoryModel repository) {
		super(wicketId);
		this.repository = repository;
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();

		GitBlitWebSession session = GitBlitWebSession.get();
		UserModel user = session.getUser();

		// build project list for repository destination
		String defaultProject = null;
		List<String> projects = new ArrayList<String>();

		if (user.canAdmin()) {
			projects.add("/");
			defaultProject = "/";
		}

		if (user.canCreate()) {
			String p =  user.getPersonalPath() + "/";
			projects.add(p);
			if (defaultProject == null) {
				// only prefer personal namespace if default is not already set
				defaultProject = p;
			}
		}

		repository.projectPath = defaultProject;

		add(new DropDownChoice<String>("projectPath", projects));
		add(new TextField<String>("name"));
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
