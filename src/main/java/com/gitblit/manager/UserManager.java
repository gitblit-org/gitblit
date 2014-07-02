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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.ConfigUserService;
import com.gitblit.Constants;
import com.gitblit.IStoredSettings;
import com.gitblit.IUserService;
import com.gitblit.Keys;
import com.gitblit.extensions.UserTeamLifeCycleListener;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * The user manager manages persistence and retrieval of users and teams.
 *
 * @author James Moger
 *
 */
@Singleton
public class UserManager implements IUserManager {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final IStoredSettings settings;

	private final IRuntimeManager runtimeManager;

	private final IPluginManager pluginManager;

	private final Map<String, String> legacyBackingServices;

	private IUserService userService;

	@Inject
	public UserManager(IRuntimeManager runtimeManager, IPluginManager pluginManager) {
		this.settings = runtimeManager.getSettings();
		this.runtimeManager = runtimeManager;
		this.pluginManager = pluginManager;

		// map of legacy realm backing user services
		legacyBackingServices = new HashMap<String, String>();
		legacyBackingServices.put("com.gitblit.HtpasswdUserService", "realm.htpasswd.backingUserService");
		legacyBackingServices.put("com.gitblit.LdapUserService", "realm.ldap.backingUserService");
		legacyBackingServices.put("com.gitblit.PAMUserService", "realm.pam.backingUserService");
		legacyBackingServices.put("com.gitblit.RedmineUserService", "realm.redmine.backingUserService");
		legacyBackingServices.put("com.gitblit.SalesforceUserService", "realm.salesforce.backingUserService");
		legacyBackingServices.put("com.gitblit.WindowsUserService", "realm.windows.backingUserService");
	}

	/**
	 * Set the user service. The user service authenticates *local* users and is
	 * responsible for persisting and retrieving all users and all teams.
	 *
	 * @param userService
	 */
	public void setUserService(IUserService userService) {
		logger.info(userService.toString());
		this.userService = userService;
		this.userService.setup(runtimeManager);
	}

	@Override
	public void setup(IRuntimeManager runtimeManager) {
		// NOOP
	}

	@Override
	public UserManager start() {
		if (this.userService == null) {
			String realm = settings.getString(Keys.realm.userService, "${baseFolder}/users.conf");
			IUserService service = null;
			if (legacyBackingServices.containsKey(realm)) {
				// create the user service from the legacy config
				String realmKey = legacyBackingServices.get(realm);
				logger.warn("");
				logger.warn(Constants.BORDER2);
				logger.warn(" Key '{}' is obsolete!", realmKey);
				logger.warn(" Please set '{}={}'", Keys.realm.userService, settings.getString(realmKey, "${baseFolder}/users.conf"));
				logger.warn(Constants.BORDER2);
				logger.warn("");
				File realmFile = runtimeManager.getFileOrFolder(realmKey, "${baseFolder}/users.conf");
				service = createUserService(realmFile);
			} else {
				// either a file path OR a custom user service
				try {
					// check to see if this "file" is a custom user service class
					Class<?> realmClass = Class.forName(realm);
					service = (IUserService) realmClass.newInstance();
				} catch (Throwable t) {
					// typical file path configuration
					File realmFile = runtimeManager.getFileOrFolder(Keys.realm.userService, "${baseFolder}/users.conf");
					service = createUserService(realmFile);
				}
			}
			setUserService(service);
		}
		return this;
	}

	protected IUserService createUserService(File realmFile) {
		IUserService service = null;
		if (realmFile.getName().toLowerCase().endsWith(".conf")) {
			// config-based realm file
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
	public UserManager stop() {
		return this;
	}

	/**
	 * Returns true if the username represents an internal account
	 *
	 * @param username
	 * @return true if the specified username represents an internal account
	 */
	@Override
	public boolean isInternalAccount(String username) {
		return !StringUtils.isEmpty(username)
				&& (username.equalsIgnoreCase(Constants.FEDERATION_USER)
						|| username.equalsIgnoreCase(UserModel.ANONYMOUS.username));
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
	 * Retrieve the user object for the specified cookie.
	 *
	 * @param cookie
	 * @return a user object or null
	 */
	@Override
	public UserModel getUserModel(char[] cookie) {
		UserModel user = userService.getUserModel(cookie);
		return user;
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
		final boolean isCreate = null == userService.getUserModel(model.username);
		if (userService.updateUserModel(model)) {
			if (isCreate) {
				callCreateUserListeners(model);
			}
			return true;
		}
		return false;
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
		final boolean isCreate = null == userService.getUserModel(username);
		if (userService.updateUserModel(username, model)) {
			if (isCreate) {
				callCreateUserListeners(model);
			}
			return true;
		}
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
		if (userService.deleteUserModel(model)) {
			callDeleteUserListeners(model);
			return true;
		}
		return false;
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
		UserModel user = getUserModel(usernameDecoded);
		if (userService.deleteUser(usernameDecoded)) {
			callDeleteUserListeners(user);
			return true;
		}
		return false;
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
		List<String> teams = userService.getAllTeamNames();
		return teams;
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
		List<String> teams = userService.getTeamNamesForRepositoryRole(role);
		return teams;
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
		TeamModel team = userService.getTeamModel(teamname);
		return team;
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
		final boolean isCreate = null == userService.getTeamModel(model.name);
		if (userService.updateTeamModel(model)) {
			if (isCreate) {
				callCreateTeamListeners(model);
			}
			return true;
		}
		return false;
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
		final boolean isCreate = null == userService.getTeamModel(teamname);
		if (userService.updateTeamModel(teamname, model)) {
			if (isCreate) {
				callCreateTeamListeners(model);
			}
			return true;
		}
		return false;
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
		if (userService.deleteTeamModel(model)) {
			callDeleteTeamListeners(model);
			return true;
		}
		return false;
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
		TeamModel team = userService.getTeamModel(teamname);
		if (userService.deleteTeam(teamname)) {
			callDeleteTeamListeners(team);
			return true;
		}
		return false;
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

	protected void callCreateUserListeners(UserModel user) {
		if (pluginManager == null || user == null) {
			return;
		}

		for (UserTeamLifeCycleListener listener : pluginManager.getExtensions(UserTeamLifeCycleListener.class)) {
			try {
				listener.onCreation(user);
			} catch (Throwable t) {
				logger.error(String.format("failed to call plugin.onCreation%s", user.username), t);
			}
		}
	}

	protected void callCreateTeamListeners(TeamModel team) {
		if (pluginManager == null || team == null) {
			return;
		}

		for (UserTeamLifeCycleListener listener : pluginManager.getExtensions(UserTeamLifeCycleListener.class)) {
			try {
				listener.onCreation(team);
			} catch (Throwable t) {
				logger.error(String.format("failed to call plugin.onCreation %s", team.name), t);
			}
		}
	}

	protected void callDeleteUserListeners(UserModel user) {
		if (pluginManager == null || user == null) {
			return;
		}

		for (UserTeamLifeCycleListener listener : pluginManager.getExtensions(UserTeamLifeCycleListener.class)) {
			try {
				listener.onDeletion(user);
			} catch (Throwable t) {
				logger.error(String.format("failed to call plugin.onDeletion %s", user.username), t);
			}
		}
	}

	protected void callDeleteTeamListeners(TeamModel team) {
		if (pluginManager == null || team == null) {
			return;
		}

		for (UserTeamLifeCycleListener listener : pluginManager.getExtensions(UserTeamLifeCycleListener.class)) {
			try {
				listener.onDeletion(team);
			} catch (Throwable t) {
				logger.error(String.format("failed to call plugin.onDeletion %s", team.name), t);
			}
		}
	}
}
