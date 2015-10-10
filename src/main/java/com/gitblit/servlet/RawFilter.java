/*
 * Copyright 2012 gitblit.com.
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

import org.eclipse.jgit.lib.Repository;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.manager.IAuthenticationManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;

/**
 * The RawFilter is an AccessRestrictionFilter which ensures http branch
 * requests for a view-restricted repository are authenticated and authorized.
 *
 * @author James Moger
 *
 */
@Singleton
public class RawFilter extends AccessRestrictionFilter {

	@Inject
	public RawFilter(
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
	protected String extractRepositoryName(String url) {
		// get the repository name from the url by finding a known url suffix
		String repository = "";
		Repository r = null;
		int offset = 0;
		while (r == null) {
			int slash = url.indexOf('/', offset);
			if (slash == -1) {
				repository = url;
			} else {
				repository = url.substring(0, slash);
			}
			r = repositoryManager.getRepository(repository, false);
			if (r == null) {
				// try again
				offset = slash + 1;
			} else {
				// close the repo
				r.close();
			}
			if (repository.equals(url)) {
				// either only repository in url or no repository found
				break;
			}
		}
		return repository;
	}

	/**
	 * Analyze the url and returns the action of the request.
	 *
	 * @param cloneUrl
	 * @return action of the request
	 */
	@Override
	protected String getUrlRequestAction(String suffix) {
		return "VIEW";
	}

	/**
	 * Determine if a non-existing repository can be created using this filter.
	 *
	 * @return true if the filter allows repository creation
	 */
	@Override
	protected boolean isCreationAllowed(String action) {
		return false;
	}

	/**
	 * Determine if the action may be executed on the repository.
	 *
	 * @param repository
	 * @param action
	 * @param method
	 * @return true if the action may be performed
	 */
	@Override
	protected boolean isActionAllowed(RepositoryModel repository, String action, String method) {
		return true;
	}

	/**
	 * Determine if the repository requires authentication.
	 *
	 * @param repository
	 * @param action
	 * @param method
	 * @return true if authentication required
	 */
	@Override
	protected boolean requiresAuthentication(RepositoryModel repository, String action, String method) {
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
