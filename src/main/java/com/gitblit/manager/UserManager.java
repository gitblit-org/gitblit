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

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.ConfigUserService;
import com.gitblit.Constants;
import com.gitblit.Constants.AccountType;
import com.gitblit.IStoredSettings;
import com.gitblit.IUserService;
import com.gitblit.Keys;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.DeepCopier;
import com.gitblit.utils.StringUtils;

/**
 * The user manager manages persistence and retrieval of users and teams.
 *
 * @author James Moger
 *
 */
public class UserManager implements IUserManager {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final IStoredSettings settings;

	private final IRuntimeManager runtimeManager;

	private IUserService userService;

	public UserManager(IRuntimeManager runtimeManager) {
		this.settings = runtimeManager.getSettings();
		this.runtimeManager = runtimeManager;
	}

	/**
	 * Set the user service. The user service authenticates local users and is
	 * responsible for persisting and retrieving users and teams.
	 *
	 * @param userService
	 */
	public void setUserService(IUserService userService) {
		logger.info("Setting up user service " + userService.toString());
		this.userService = userService;
		this.userService.setup(runtimeManager);
	}

	@Override
	public IManager setup() {
		if (this.userService == null) {
			String realm = settings.getString(Keys.realm.userService, "${baseFolder}/users.properties");
			IUserService service = null;
			try {
				// check to see if this "file" is a login service class
				Class<?> realmClass = Class.forName(realm);
				service = (IUserService) realmClass.newInstance();
			} catch (Throwable t) {
				File realmFile = runtimeManager.getFileOrFolder(Keys.realm.userService, "${baseFolder}/users.conf");
				service = createUserService(realmFile);
			}
			setUserService(service);
		}
		return this;
	}

	protected IUserService createUserService(File realmFile) {
		IUserService service = null;
		if (realmFile.getName().toLowerCase().endsWith(".conf")) {
			// v0.8.0+ config-based realm file
			service = new ConfigUserService(realmFile);
		}

		assert service != null;

		if (!realmFile.exists()) {
			// Create the Administrator account for a new realm file
			try {
				realmFile.createNewFile();
			} catch (IOException x) {
				logger.error(MessageFormat.format("COULD NOT CREATE REALM FILE {0}!", realmFile), x);
			}
			UserModel admin = new UserModel("admin");
			admin.password = "admin";
			admin.canAdmin = true;
			admin.excludeFromFederation = true;
			service.updateUserModel(admin);
		}

		return service;
	}

	@Override
	public IManager stop() {
		return this;
	}

	@Override
	public boolean supportsAddUser() {
		return supportsCredentialChanges(new UserModel(""));
	}

	/**
	 * Returns true if the user's credentials can be changed.
	 *
	 * @param user
	 * @return true if the user service supports credential changes
	 */
	@Override
	public boolean supportsCredentialChanges(UserModel user) {
		if (user == null) {
			return false;
		} else if (AccountType.LOCAL.equals(user.accountType)) {
			// local account, we can change credentials
			return true;
		} else {
			// external account, ask user service
			return userService.supportsCredentialChanges();
		}
	}

	/**
	 * Returns true if the user's display name can be changed.
	 *
	 * @param user
	 * @return true if the user service supports display name changes
	 */
	@Override
	public boolean supportsDisplayNameChanges(UserModel user) {
		return (user != null && user.isLocalAccount()) || userService.supportsDisplayNameChanges();
	}

	/**
	 * Returns true if the user's email address can be changed.
	 *
	 * @param user
	 * @return true if the user service supports email address changes
	 */
	@Override
	public boolean supportsEmailAddressChanges(UserModel user) {
		return (user != null && user.isLocalAccount()) || userService.supportsEmailAddressChanges();
	}

	/**
	 * Returns true if the user's team memberships can be changed.
	 *
	 * @param user
	 * @return true if the user service supports team membership changes
	 */
	@Override
	public boolean supportsTeamMembershipChanges(UserModel user) {
		return (user != null && user.isLocalAccount()) || userService.supportsTeamMembershipChanges();
	}

	/**
	 * Allow to understand if GitBlit supports and is configured to allow
	 * cookie-based authentication.
	 *
	 * @return status of Cookie authentication enablement.
	 */
	@Override
	public boolean supportsCookies() {
		return settings.getBoolean(Keys.web.allowCookieAuthentication, true) && userService.supportsCookies();
	}

	/**
	 * Returns the cookie value for the specified user.
	 *
	 * @param model
	 * @return cookie value
	 */
	@Override
	public String getCookie(UserModel model) {
		return userService.getCookie(model);
	}

	/**
	 * Authenticate a user based on a username and password.
	 *
	 * @param username
	 * @param password
	 * @return a user object or null
	 */

	@Override
	public UserModel authenticate(String username, char[] password) {
		UserModel user = userService.authenticate(username, password);
		setAccountType(user);
		return user;
	}

	/**
	 * Authenticate a user based on their cookie.
	 *
	 * @param cookie
	 * @return a user object or null
	 */
	@Override
	public UserModel authenticate(char[] cookie) {
		UserModel user = userService.authenticate(cookie);
		setAccountType(user);
		return user;
	}

	/**
	 * Logout a user.
	 *
	 * @param user
	 */
	@Override
	public void logout(UserModel user) {
		if (userService == null) {
			return;
		}
		userService.logout(user);
	}

	/**
	 * Retrieve the user object for the specified username.
	 *
	 * @param username
	 * @return a user object or null
	 */
	@Override
	public UserModel getUserModel(String username) {
		if (StringUtils.isEmpty(username)) {
			return null;
		}
		String usernameDecoded = StringUtils.decodeUsername(username);
		UserModel user = userService.getUserModel(usernameDecoded);
		setAccountType(user);
		return user;
	}

	/**
	 * Updates/writes a complete user object.
	 *
	 * @param model
	 * @return true if update is successful
	 */
	@Override
	public boolean updateUserModel(UserModel model) {
		return userService.updateUserModel(model);
	}

	/**
	 * Updates/writes all specified user objects.
	 *
	 * @param models a list of user models
	 * @return true if update is successful
	 * @since 1.2.0
	 */
	@Override
	public boolean updateUserModels(Collection<UserModel> models) {
		return userService.updateUserModels(models);
	}

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
	@Override
	public boolean updateUserModel(String username, UserModel model) {
		if (model.isLocalAccount() || userService.supportsCredentialChanges()) {
			if (!model.isLocalAccount() && !userService.supportsTeamMembershipChanges()) {
				//  teams are externally controlled - copy from original model
				UserModel existingModel = getUserModel(username);

				model = DeepCopier.copy(model);
				model.teams.clear();
				model.teams.addAll(existingModel.teams);
			}
			return userService.updateUserModel(username, model);
		}
		if (model.username.equals(username)) {
			// passwords are not persisted by the backing user service
			model.password = null;
			if (!model.isLocalAccount() && !userService.supportsTeamMembershipChanges()) {
				//  teams are externally controlled- copy from original model
				UserModel existingModel = getUserModel(username);

				model = DeepCopier.copy(model);
				model.teams.clear();
				model.teams.addAll(existingModel.teams);
			}
			return userService.updateUserModel(username, model);
		}
		logger.error("Users can not be renamed!");
		return false;
	}

	/**
	 * Deletes the user object from the user service.
	 *
	 * @param model
	 * @return true if successful
	 */
	@Override
	public boolean deleteUserModel(UserModel model) {
		return userService.deleteUserModel(model);
	}

	/**
	 * Delete the user object with the specified username
	 *
	 * @param username
	 * @return true if successful
	 */
	@Override
	public boolean deleteUser(String username) {
		if (StringUtils.isEmpty(username)) {
			return false;
		}
		String usernameDecoded = StringUtils.decodeUsername(username);
		return userService.deleteUser(usernameDecoded);
	}

	/**
	 * Returns the list of all users available to the login service.
	 *
	 * @return list of all usernames
	 */
	@Override
	public List<String> getAllUsernames() {
		List<String> names = new ArrayList<String>(userService.getAllUsernames());
		return names;
	}

	/**
	 * Returns the list of all users available to the login service.
	 *
	 * @return list of all users
	 * @since 0.8.0
	 */
	@Override
	public List<UserModel> getAllUsers() {
		List<UserModel> users = userService.getAllUsers();
    	for (UserModel user : users) {
    		setAccountType(user);
    	}
		return users;
	}

	/**
	 * Returns the list of all teams available to the login service.
	 *
	 * @return list of all teams
	 * @since 0.8.0
	 */
	@Override
	public List<String> getAllTeamNames() {
		return userService.getAllTeamNames();
	}

	/**
	 * Returns the list of all teams available to the login service.
	 *
	 * @return list of all teams
	 * @since 0.8.0
	 */
	@Override
	public List<TeamModel> getAllTeams() {
		List<TeamModel> teams = userService.getAllTeams();
		return teams;
	}

	/**
	 * Returns the list of all teams who are allowed to bypass the access
	 * restriction placed on the specified repository.
	 *
	 * @param role
	 *            the repository name
	 * @return list of all teams that can bypass the access restriction
	 * @since 0.8.0
	 */
	@Override
	public List<String> getTeamNamesForRepositoryRole(String role) {
		return userService.getTeamNamesForRepositoryRole(role);
	}

	/**
	 * Retrieve the team object for the specified team name.
	 *
	 * @param teamname
	 * @return a team object or null
	 * @since 0.8.0
	 */
	@Override
	public TeamModel getTeamModel(String teamname) {
		return userService.getTeamModel(teamname);
	}

	/**
	 * Updates/writes a complete team object.
	 *
	 * @param model
	 * @return true if update is successful
	 * @since 0.8.0
	 */
	@Override
	public boolean updateTeamModel(TeamModel model) {
		return userService.updateTeamModel(model);
	}

	/**
	 * Updates/writes all specified team objects.
	 *
	 * @param models a list of team models
	 * @return true if update is successful
	 * @since 1.2.0
	 */
	@Override
	public boolean updateTeamModels(Collection<TeamModel> models) {
		return userService.updateTeamModels(models);
	}

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
	@Override
	public boolean updateTeamModel(String teamname, TeamModel model) {
		if (!userService.supportsTeamMembershipChanges()) {
			// teams are externally controlled - copy from original model
			TeamModel existingModel = getTeamModel(teamname);

			model = DeepCopier.copy(model);
			model.users.clear();
			model.users.addAll(existingModel.users);
		}
		return userService.updateTeamModel(teamname, model);
	}

	/**
	 * Deletes the team object from the user service.
	 *
	 * @param model
	 * @return true if successful
	 * @since 0.8.0
	 */
	@Override
	public boolean deleteTeamModel(TeamModel model) {
		return userService.deleteTeamModel(model);
	}

	/**
	 * Delete the team object with the specified teamname
	 *
	 * @param teamname
	 * @return true if successful
	 * @since 0.8.0
	 */
	@Override
	public boolean deleteTeam(String teamname) {
		return userService.deleteTeam(teamname);
	}

	/**
	 * Returns the list of all users who are allowed to bypass the access
	 * restriction placed on the specified repository.
	 *
	 * @param role
	 *            the repository name
	 * @return list of all usernames that can bypass the access restriction
	 * @since 0.8.0
	 */
	@Override
	public List<String> getUsernamesForRepositoryRole(String role) {
		return userService.getUsernamesForRepositoryRole(role);
	}

	/**
	 * Renames a repository role.
	 *
	 * @param oldRole
	 * @param newRole
	 * @return true if successful
	 */
	@Override
	public boolean renameRepositoryRole(String oldRole, String newRole) {
		return userService.renameRepositoryRole(oldRole, newRole);
	}

	/**
	 * Removes a repository role from all users.
	 *
	 * @param role
	 * @return true if successful
	 */
	@Override
	public boolean deleteRepositoryRole(String role) {
		return userService.deleteRepositoryRole(role);
	}

	protected void setAccountType(UserModel user) {
		if (user != null) {
			if (!StringUtils.isEmpty(user.password)
					&& !Constants.EXTERNAL_ACCOUNT.equalsIgnoreCase(user.password)
					&& !"StoredInLDAP".equalsIgnoreCase(user.password)) {
				user.accountType = AccountType.LOCAL;
			} else {
				user.accountType = userService.getAccountType();
			}
		}
	}
}
