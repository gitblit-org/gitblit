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

import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;

import com.gitblit.GitBlit;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.StringUtils;

/**
 * RootSubPage is a non-topbar navigable RootPage. It also has a page header.
 * 
 * @author James Moger
 * 
 */
public abstract class RootSubPage extends RootPage {

	public RootSubPage() {
		super();
	}

	public RootSubPage(PageParameters params) {
		super(params);
	}

	@Override
	protected void setupPage(String pageName, String subName) {
		add(new Label("pageName", pageName));
		if (!StringUtils.isEmpty(subName)) {
			subName = "/ " + subName;
		}
		add(new Label("pageSubName", subName));
		super.setupPage("", pageName);
	}
	
	protected List<String> getAccessRestrictedRepositoryList(boolean includeWildcards) {
		// build list of access-restricted projects
		String lastProject = null;
		List<String> repos = new ArrayList<String>();
		if (includeWildcards) {
			// all repositories
			repos.add(".*");
			// all repositories excluding personal repositories
			repos.add("[^~].*");
		}
		for (String repo : GitBlit.self().getRepositoryList()) {
			RepositoryModel repositoryModel = GitBlit.self().getRepositoryModel(repo);
			if (repositoryModel.accessRestriction.exceeds(AccessRestrictionType.NONE)) {
				if (includeWildcards) {
					if (lastProject == null || !lastProject.equalsIgnoreCase(repositoryModel.projectPath)) {
						lastProject = repositoryModel.projectPath;
						if (!StringUtils.isEmpty(repositoryModel.projectPath)) {
							// regex for all repositories within a project
							repos.add(repositoryModel.projectPath + "/.*");
						}
					}
				}
				repos.add(repo);
			}
		}
		return repos;
	}
}
