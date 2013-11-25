/*
 * Copyright 2013 gitblit.com.
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
package com.gitblit.manager;

import java.util.Collection;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import com.gitblit.GitBlitException;
import com.gitblit.models.GitClientApplication;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.RepositoryUrl;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;

public interface IGitblit extends IManager,
									IRuntimeManager,
									INotificationManager,
									IUserManager,
									IAuthenticationManager,
									IRepositoryManager,
									IProjectManager,
									IFederationManager {

	/**
	 * Returns a list of repository URLs and the user access permission.
	 *
	 * @param request
	 * @param user
	 * @param repository
	 * @return a list of repository urls
	 */
	List<RepositoryUrl> getRepositoryUrls(HttpServletRequest request, UserModel user, RepositoryModel repository);

	/**
	 * Creates a complete user object.
	 *
	 * @param user
	 * @param isCreate
	 * @throws GitBlitException
	 */
	void addUser(UserModel user) throws GitBlitException;

	/**
	 * Updates a complete user object keyed by username. This method allows
	 * for renaming a user.
	 *
	 * @param username
	 * @param user
	 * @throws GitBlitException
	 */
	void reviseUser(String username, UserModel user) throws GitBlitException;

	/**
	 * Creates a TeamModel object.
	 *
	 * @param team
	 * @param isCreate
	 */
	void addTeam(TeamModel team) throws GitBlitException;

	/**
	 * Updates the TeamModel object for the specified name.
	 *
	 * @param teamname
	 * @param team
	 */
	void reviseTeam(String teamname, TeamModel team) throws GitBlitException;

	/**
	 * Creates a personal fork of the specified repository. The clone is view
	 * restricted by default and the owner of the source repository is given
	 * access to the clone.
	 *
	 * @param repository
	 * @param user
	 * @return the repository model of the fork, if successful
	 * @throws GitBlitException
	 */
	RepositoryModel fork(RepositoryModel repository, UserModel user) throws GitBlitException;

	/**
	 * Returns the list of custom client applications to be used for the
	 * repository url panel;
	 *
	 * @return a collection of client applications
	 */
	Collection<GitClientApplication> getClientApplications();

}