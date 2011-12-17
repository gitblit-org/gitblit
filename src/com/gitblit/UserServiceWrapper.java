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

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;

/**
 * This class wraps the default user service and is recommended as the starting
 * point for custom user service implementations.
 * 
 * This does seem a little convoluted, but the idea is to allow IUserService to
 * evolve and be replaced without hampering custom implementations.
 * 
 * The most common need for a custom IUserService is to override authentication
 * and then delegate to one of Gitblit's user services. Subclassing this allows
 * for authentication customization without having to keep-up-with IUSerService
 * API changes.
 * 
 * @author James Moger
 * 
 */
public abstract class UserServiceWrapper implements IUserService {

	protected IUserService defaultService;

	private final Logger logger = LoggerFactory.getLogger(UserServiceWrapper.class);

	public UserServiceWrapper() {
	}

	@SuppressWarnings("deprecation")
	@Override
	public final void setup(IStoredSettings settings) {
		File realmFile = GitBlit.getFileOrFolder(Keys.realm.userService, "users.conf");
		if (realmFile.exists()) {
			// load the existing realm file
			if (realmFile.getName().toLowerCase().endsWith(".properties")) {
				// load the v0.5.0 - v0.7.0 properties-based realm file
				defaultService = new FileUserService(realmFile);

				// automatically create a users.conf realm file from the
				// original users.properties file
				File usersConfig = new File(realmFile.getParentFile(), "users.conf");
				if (!usersConfig.exists()) {
					logger.info(MessageFormat.format("Automatically creating {0} based on {1}",
							usersConfig.getAbsolutePath(), realmFile.getAbsolutePath()));
					ConfigUserService configService = new ConfigUserService(usersConfig);
					for (String username : defaultService.getAllUsernames()) {
						UserModel userModel = defaultService.getUserModel(username);
						configService.updateUserModel(userModel);
					}
				}

				// issue suggestion about switching to users.conf
				logger.warn("Please consider using \"users.conf\" instead of the deprecated \"users.properties\" file");
			} else if (realmFile.getName().toLowerCase().endsWith(".conf")) {
				// load the config-based realm file
				defaultService = new ConfigUserService(realmFile);
			}
		} else {
			// Create a new realm file and add the default admin
			// account. This is necessary for bootstrapping a dynamic
			// environment like running on a cloud service.
			// As of v0.8.0 the default realm file is ConfigUserService.
			try {
				realmFile = GitBlit.getFileOrFolder(Keys.realm.userService, "users.conf");
				realmFile.createNewFile();
				defaultService = new ConfigUserService(realmFile);
				UserModel admin = new UserModel("admin");
				admin.password = "admin";
				admin.canAdmin = true;
				admin.excludeFromFederation = true;
				defaultService.updateUserModel(admin);
			} catch (IOException x) {
				logger.error(MessageFormat.format("COULD NOT CREATE REALM FILE {0}!", realmFile), x);
			}
		}

		// call subclass setup
		setupService(settings);
	}

	/**
	 * Subclasses must implement this method.
	 * 
	 * @param settings
	 */
	public abstract void setupService(IStoredSettings settings);

	@Override
	public boolean supportsCookies() {
		return defaultService.supportsCookies();
	}

	@Override
	public char[] getCookie(UserModel model) {
		return defaultService.getCookie(model);
	}

	@Override
	public UserModel authenticate(char[] cookie) {
		return defaultService.authenticate(cookie);
	}

	@Override
	public UserModel authenticate(String username, char[] password) {
		return defaultService.authenticate(username, password);
	}

	@Override
	public UserModel getUserModel(String username) {
		return defaultService.getUserModel(username);
	}

	@Override
	public boolean updateUserModel(UserModel model) {
		return defaultService.updateUserModel(model);
	}

	@Override
	public boolean updateUserModel(String username, UserModel model) {
		return defaultService.updateUserModel(username, model);
	}

	@Override
	public boolean deleteUserModel(UserModel model) {
		return defaultService.deleteUserModel(model);
	}

	@Override
	public boolean deleteUser(String username) {
		return defaultService.deleteUser(username);
	}

	@Override
	public List<String> getAllUsernames() {
		return defaultService.getAllUsernames();
	}

	@Override
	public List<String> getAllTeamNames() {
		return defaultService.getAllTeamNames();
	}

	@Override
	public List<String> getTeamnamesForRepositoryRole(String role) {
		return defaultService.getTeamnamesForRepositoryRole(role);
	}

	@Override
	public boolean setTeamnamesForRepositoryRole(String role, List<String> teamnames) {
		return defaultService.setTeamnamesForRepositoryRole(role, teamnames);
	}

	@Override
	public TeamModel getTeamModel(String teamname) {
		return defaultService.getTeamModel(teamname);
	}

	@Override
	public boolean updateTeamModel(TeamModel model) {
		return defaultService.updateTeamModel(model);
	}

	@Override
	public boolean updateTeamModel(String teamname, TeamModel model) {
		return defaultService.updateTeamModel(teamname, model);
	}

	@Override
	public boolean deleteTeamModel(TeamModel model) {
		return defaultService.deleteTeamModel(model);
	}

	@Override
	public boolean deleteTeam(String teamname) {
		return defaultService.deleteTeam(teamname);
	}

	@Override
	public List<String> getUsernamesForRepositoryRole(String role) {
		return defaultService.getUsernamesForRepositoryRole(role);
	}

	@Override
	public boolean setUsernamesForRepositoryRole(String role, List<String> usernames) {
		return defaultService.setUsernamesForRepositoryRole(role, usernames);
	}

	@Override
	public boolean renameRepositoryRole(String oldRole, String newRole) {
		return defaultService.renameRepositoryRole(oldRole, newRole);
	}

	@Override
	public boolean deleteRepositoryRole(String role) {
		return defaultService.deleteRepositoryRole(role);
	}
}
