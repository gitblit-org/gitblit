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
package com.gitblit.servlet;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import javax.servlet.http.HttpServletRequest;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Keys;
import com.gitblit.manager.IAuthenticationManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;

/**
 * The DownloadZipFilter is an AccessRestrictionFilter which ensures that zip
 * requests for view-restricted repositories have proper authentication
 * credentials and are authorized.
 *
 * @author James Moger
 *
 */
@Singleton
public class DownloadZipFilter extends AccessRestrictionFilter {

	@Inject
	public DownloadZipFilter(
			IRuntimeManager runtimeManager,
			IAuthenticationManager authenticationManager,
			IRepositoryManager repositoryManager) {

		super(runtimeManager, authenticationManager, repositoryManager);
	}

	/**
	 * Extract the repository name from the url.
	 *
	 * @param url
	 * @return repository name
	 */
	@Override
	protected String extractRepositoryName(HttpServletRequest request) {
		// Mimic the wicket page mount parameters, key off of same config value
		if (runtimeManager.getSettings().getBoolean(Keys.web.mountParameters, true)) {
			String repository = request.getPathInfo();
			if (!StringUtils.isEmpty(repository)) {
				//Remove leading slash from path info
				repository = repository.substring(1);
				char c = runtimeManager.getSettings().getChar(Keys.web.forwardSlashCharacter, '/');
				repository = repository.replace('!', '/').replace(c, '/');
				return repository;
			}
		} else {
			return request.getParameter("r");
		}
		return null;
	}

	/**
	 * Analyze the url and returns the action of the request.
	 *
	 * @param url
	 * @return action of the request
	 */
	@Override
	protected String getUrlRequestAction(String url) {
		return "DOWNLOAD";
	}

	/**
	 * Determine if a non-existing repository can be created using this filter.
	 *
	 * @return true if the filter allows repository creation
	 */
	@Override
	protected boolean isCreationAllowed() {
		return false;
	}

	/**
	 * Determine if the action may be executed on the repository.
	 *
	 * @param repository
	 * @param action
	 * @return true if the action may be performed
	 */
	@Override
	protected boolean isActionAllowed(RepositoryModel repository, String action) {
		return true;
	}

	/**
	 * Determine if the repository requires authentication.
	 *
	 * @param repository
	 * @param action
	 * @return true if authentication required
	 */
	@Override
	protected boolean requiresAuthentication(RepositoryModel repository, String action) {
		return repository.accessRestriction.atLeast(AccessRestrictionType.VIEW);
	}

	/**
	 * Determine if the user can access the repository and perform the specified
	 * action.
	 *
	 * @param repository
	 * @param user
	 * @param action
	 * @return true if user may execute the action on the repository
	 */
	@Override
	protected boolean canAccess(RepositoryModel repository, UserModel user, String action) {
		return user.canView(repository);
	}

}
