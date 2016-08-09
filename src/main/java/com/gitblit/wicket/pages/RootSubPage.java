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

import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.Session;
import org.apache.wicket.markup.html.basic.Label;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.AuthorizationControl;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ModelUtils;
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
		createPageMapIfNeeded();
	}

	public RootSubPage(PageParameters params) {
		super(params);
		createPageMapIfNeeded();
	}

	protected boolean requiresPageMap() {
		return false;
	}

	protected void createPageMapIfNeeded() {
		if (requiresPageMap()) {
			// because Gitblit strives for page-statelessness
			// Wicket seems to get confused as to when it really should
			// generate a page map for complex pages.  Conditionally ensure we
			// have a page map for complex AJAX pages like the EditNNN pages.
			//TODO: check if no longer needed
//			Session.get().pageMapForName(null, true);
			setVersioned(true);
		}
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

	protected List<String> getAccessRestrictedRepositoryList(boolean includeWildcards, UserModel user) {
		// build list of access-restricted projects
		String lastProject = null;
		List<String> repos = new ArrayList<String>();
		if (includeWildcards) {
			// all repositories
			repos.add(".*");
			// all repositories excluding personal repositories
			if (ModelUtils.getUserRepoPrefix().length() == 1) {
				repos.add("[^" + ModelUtils.getUserRepoPrefix() + "].*");
			}
		}

		for (String repo : app().repositories().getRepositoryList()) {
			RepositoryModel repositoryModel = app().repositories().getRepositoryModel(repo);
			if (repositoryModel.accessRestriction.exceeds(AccessRestrictionType.NONE)
					&& repositoryModel.authorizationControl.equals(AuthorizationControl.NAMED)) {
				if (user != null &&
						(repositoryModel.isOwner(user.username) || repositoryModel.isUsersPersonalRepository(user.username))) {
					// exclude Owner or personal repositories
					continue;
				}
				if (includeWildcards) {
					if (lastProject == null || !lastProject.equalsIgnoreCase(repositoryModel.projectPath)) {
						lastProject = repositoryModel.projectPath.toLowerCase();
						if (!StringUtils.isEmpty(repositoryModel.projectPath)) {
							// regex for all repositories within a project
							repos.add(repositoryModel.projectPath + "/.*");
						}
					}
				}
				repos.add(repo.toLowerCase());
			}
		}
		return repos;
	}
}
