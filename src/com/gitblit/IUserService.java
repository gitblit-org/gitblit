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

import java.util.List;

import com.gitblit.models.UserModel;

/**
 * Implementations of IUserService control all aspects of UserModel objects and
 * user authentication.
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
	 * @param settings
	 * @since 0.7.0
	 */
	void setup(IStoredSettings settings);

	/**
	 * Does the user service support cookie authentication?
	 * 
	 * @return true or false
	 */
	boolean supportsCookies();

	/**
	 * Returns the cookie value for the specified user.
	 * 
	 * @param model
	 * @return cookie value
	 */
	char[] getCookie(UserModel model);

	/**
	 * Authenticate a user based on their cookie.
	 * 
	 * @param cookie
	 * @return a user object or null
	 */
	UserModel authenticate(char[] cookie);

	/**
	 * Authenticate a user based on a username and password.
	 * 
	 * @param username
	 * @param password
	 * @return a user object or null
	 */
	UserModel authenticate(String username, char[] password);

	/**
	 * Retrieve the user object for the specified username.
	 * 
	 * @param username
	 * @return a user object or null
	 */
	UserModel getUserModel(String username);

	/**
	 * Updates/writes a complete user object.
	 * 
	 * @param model
	 * @return true if update is successful
	 */
	boolean updateUserModel(UserModel model);

	/**
	 * Adds/updates a user object keyed by username. This method allows for
	 * renaming a user.
	 * 
	 * @param username
	 *            the old username
	 * @param model
	 *            the user object to use for username
	 * @return true if update is successful
	 */
	boolean updateUserModel(String username, UserModel model);

	/**
	 * Deletes the user object from the user service.
	 * 
	 * @param model
	 * @return true if successful
	 */
	boolean deleteUserModel(UserModel model);

	/**
	 * Delete the user object with the specified username
	 * 
	 * @param username
	 * @return true if successful
	 */
	boolean deleteUser(String username);

	/**
	 * Returns the list of all users available to the login service.
	 * 
	 * @return list of all usernames
	 */
	List<String> getAllUsernames();

	/**
	 * Returns the list of all users who are allowed to bypass the access
	 * restriction placed on the specified repository.
	 * 
	 * @param role
	 *            the repository name
	 * @return list of all usernames that can bypass the access restriction
	 */
	List<String> getUsernamesForRepositoryRole(String role);

	/**
	 * Sets the list of all uses who are allowed to bypass the access
	 * restriction placed on the specified repository.
	 * 
	 * @param role
	 *            the repository name
	 * @param usernames
	 * @return true if successful
	 */
	boolean setUsernamesForRepositoryRole(String role, List<String> usernames);

	/**
	 * Renames a repository role.
	 * 
	 * @param oldRole
	 * @param newRole
	 * @return true if successful
	 */
	boolean renameRepositoryRole(String oldRole, String newRole);

	/**
	 * Removes a repository role from all users.
	 * 
	 * @param role
	 * @return true if successful
	 */
	boolean deleteRepositoryRole(String role);

	/**
	 * @See java.lang.Object.toString();
	 * @return string representation of the login service
	 */
	String toString();
}
