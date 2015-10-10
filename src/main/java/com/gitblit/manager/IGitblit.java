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

import com.gitblit.GitBlitException;
import com.gitblit.models.GitClientApplication;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.tickets.ITicketService;
import com.gitblit.transport.ssh.IPublicKeyManager;

public interface IGitblit extends IManager,
									IRuntimeManager,
									IPluginManager,
									INotificationManager,
									IUserManager,
									IAuthenticationManager,
									IRepositoryManager,
									IProjectManager,
									IFederationManager,
									IFilestoreManager {

	/**
	 * Creates a complete user object.
	 *
	 * @param user
	 * @param isCreate
	 * @throws GitBlitException
	 * @since 1.4.0
	 */
	void addUser(UserModel user) throws GitBlitException;

	/**
	 * Updates a complete user object keyed by username. This method allows
	 * for renaming a user.
	 *
	 * @param username
	 * @param user
	 * @throws GitBlitException
	 * @since 1.4.0
	 */
	void reviseUser(String username, UserModel user) throws GitBlitException;

	/**
	 * Creates a TeamModel object.
	 *
	 * @param team
	 * @param isCreate
	 * @since 1.4.0
	 */
	void addTeam(TeamModel team) throws GitBlitException;

	/**
	 * Updates the TeamModel object for the specified name.
	 *
	 * @param teamname
	 * @param team
	 * @since 1.4.0
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
	 * @since 1.4.0
	 */
	RepositoryModel fork(RepositoryModel repository, UserModel user) throws GitBlitException;

	/**
	 * Returns the list of custom client applications to be used for the
	 * repository url panel;
	 *
	 * @return a collection of client applications
	 * @since 1.4.0
	 */
	Collection<GitClientApplication> getClientApplications();

	/**
	 * Returns the ticket service.
	 *
	 * @return a ticket service
	 * @since 1.4.0
	 */
	ITicketService getTicketService();

	/**
	 * Returns the SSH public key manager.
	 *
	 * @return the SSH public key manager
	 * @since 1.5.0
	 */
	IPublicKeyManager getPublicKeyManager();

}