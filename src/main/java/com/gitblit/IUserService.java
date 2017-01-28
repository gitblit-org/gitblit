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
package com.gitblit;

import java.util.Collection;
import java.util.List;

import com.gitblit.manager.IRuntimeManager;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;

/**
 * Implementations of IUserService control all aspects of UserModel objects and
 * user authentication.
 *
 * Plugins implementing this interface (which are instantiated during {@link com.gitblit.manager.UserManager#start()}) can provide
 * a default constructor or might also use {@link IRuntimeManager} as a constructor argument which will be passed automatically then. 
 *
 * @author James Moger
 *
 */
public interface IUserService {

	/**
	 * Setup the user service. This method allows custom implementations to
	 * retrieve settings from gitblit.properties or the web.xml file without
	 * relying on the GitBlit static singleton.
	 *
	 * @param runtimeManager
	 * @since 1.4.0
	 */
	void setup(IRuntimeManager runtimeManager);

	/**
	 * Returns the cookie value for the specified user.
	 *
	 * @param model
	 * @return cookie value
 	 * @since 0.5.0
	 */
	String getCookie(UserModel model);

	/**
	 * Retrieve a user object for the specified cookie.
	 *
	 * @param cookie
	 * @return a user object or null
 	 * @since 0.5.0
	 */
	UserModel getUserModel(char[] cookie);

	/**
	 * Retrieve the user object for the specified username.
	 *
	 * @param username
	 * @return a user object or null
 	 * @since 0.5.0
	 */
	UserModel getUserModel(String username);

	/**
	 * Updates/writes a complete user object.
	 *
	 * @param model
	 * @return true if update is successful
 	 * @since 0.5.0
	 */
	boolean updateUserModel(UserModel model);

	/**
	 * Updates/writes all specified user objects.
	 *
	 * @param models a list of user models
	 * @return true if update is successful
	 * @since 1.2.0
	 */
	boolean updateUserModels(Collection<UserModel> models);

	/**
	 * Adds/updates a user object keyed by username. This method allows for
	 * renaming a user.
	 *
	 * @param username
	 *            the old username
	 * @param model
	 *            the user object to use for username
	 * @return true if update is successful
 	 * @since 0.5.0
	 */
	boolean updateUserModel(String username, UserModel model);

	/**
	 * Deletes the user object from the user service.
	 *
	 * @param model
	 * @return true if successful
 	 * @since 0.5.0
	 */
	boolean deleteUserModel(UserModel model);

	/**
	 * Delete the user object with the specified username
	 *
	 * @param username
	 * @return true if successful
 	 * @since 0.5.0
	 */
	boolean deleteUser(String username);

	/**
	 * Returns the list of all users available to the login service.
	 *
	 * @return list of all usernames
 	 * @since 0.5.0
	 */
	List<String> getAllUsernames();

	/**
	 * Returns the list of all users available to the login service.
	 *
	 * @return list of all users
	 * @since 0.8.0
	 */
	List<UserModel> getAllUsers();

	/**
	 * Returns the list of all teams available to the login service.
	 *
	 * @return list of all teams
	 * @since 0.8.0
	 */
	List<String> getAllTeamNames();

	/**
	 * Returns the list of all teams available to the login service.
	 *
	 * @return list of all teams
	 * @since 0.8.0
	 */
	List<TeamModel> getAllTeams();

	/**
	 * Returns the list of all users who are allowed to bypass the access
	 * restriction placed on the specified repository.
	 *
	 * @param role
	 *            the repository name
	 * @return list of all usernames that can bypass the access restriction
	 * @since 0.8.0
	 */
	List<String> getTeamNamesForRepositoryRole(String role);

	/**
	 * Retrieve the team object for the specified team name.
	 *
	 * @param teamname
	 * @return a team object or null
	 * @since 0.8.0
	 */
	TeamModel getTeamModel(String teamname);

	/**
	 * Updates/writes a complete team object.
	 *
	 * @param model
	 * @return true if update is successful
	 * @since 0.8.0
	 */
	boolean updateTeamModel(TeamModel model);

	/**
	 * Updates/writes all specified team objects.
	 *
	 * @param models a list of team models
	 * @return true if update is successful
	 * @since 1.2.0
	 */
	boolean updateTeamModels(Collection<TeamModel> models);

	/**
	 * Updates/writes and replaces a complete team object keyed by teamname.
	 * This method allows for renaming a team.
	 *
	 * @param teamname
	 *            the old teamname
	 * @param model
	 *            the team object to use for teamname
	 * @return true if update is successful
	 * @since 0.8.0
	 */
	boolean updateTeamModel(String teamname, TeamModel model);

	/**
	 * Deletes the team object from the user service.
	 *
	 * @param model
	 * @return true if successful
	 * @since 0.8.0
	 */
	boolean deleteTeamModel(TeamModel model);

	/**
	 * Delete the team object with the specified teamname
	 *
	 * @param teamname
	 * @return true if successful
	 * @since 0.8.0
	 */
	boolean deleteTeam(String teamname);

	/**
	 * Returns the list of all users who are allowed to bypass the access
	 * restriction placed on the specified repository.
	 *
	 * @param role
	 *            the repository name
	 * @return list of all usernames that can bypass the access restriction
	 * @since 0.8.0
	 */
	List<String> getUsernamesForRepositoryRole(String role);

	/**
	 * Renames a repository role.
	 *
	 * @param oldRole
	 * @param newRole
	 * @return true if successful
 	 * @since 0.5.0
	 */
	boolean renameRepositoryRole(String oldRole, String newRole);

	/**
	 * Removes a repository role from all users.
	 *
	 * @param role
	 * @return true if successful
 	 * @since 0.5.0
	 */
	boolean deleteRepositoryRole(String role);

	/**
	 * @See java.lang.Object.toString();
	 * @return string representation of the login service
	 */
	@Override
	String toString();
}
