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
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants.AccountType;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.DeepCopier;
import com.gitblit.utils.StringUtils;

/**
 * This class wraps the default user service and is recommended as the starting
 * point for custom user service implementations.
 *
 * This does seem a little convoluted, but the idea is to allow IUserService to
 * evolve with new methods and implementations without breaking custom
 * authentication implementations.
 *
 * The most common implementation of a custom IUserService is to only override
 * authentication and then delegate all other functionality to one of Gitblit's
 * user services. This class optimizes that use-case.
 *
 * Extending GitblitUserService allows for authentication customization without
 * having to keep-up-with IUSerService API changes.
 *
 * @author James Moger
 *
 */
public class GitblitUserService implements IUserService {

	protected IUserService serviceImpl;

	private final Logger logger = LoggerFactory.getLogger(GitblitUserService.class);

	public GitblitUserService() {
	}

	@Override
	public void setup(IRuntimeManager runtimeManager) {
		File realmFile = runtimeManager.getFileOrFolder(Keys.realm.userService, "${baseFolder}/users.conf");
		serviceImpl = createUserService(realmFile);
		logger.info("GUS delegating to " + serviceImpl.toString());
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
	public String toString() {
		return getClass().getSimpleName();
	}

	@Override
	public boolean supportsCredentialChanges() {
		return serviceImpl.supportsCredentialChanges();
	}

	@Override
	public boolean supportsDisplayNameChanges() {
		return serviceImpl.supportsDisplayNameChanges();
	}

	@Override
	public boolean supportsEmailAddressChanges() {
		return serviceImpl.supportsEmailAddressChanges();
	}

	@Override
	public boolean supportsTeamMembershipChanges() {
		return serviceImpl.supportsTeamMembershipChanges();
	}

	@Override
	public boolean supportsCookies() {
		return serviceImpl.supportsCookies();
	}

	@Override
	public String getCookie(UserModel model) {
		return serviceImpl.getCookie(model);
	}

	/**
	 * Authenticate a user based on their cookie.
	 *
	 * @param cookie
	 * @return a user object or null
	 */
	@Override
	public UserModel authenticate(char[] cookie) {
		UserModel user = serviceImpl.authenticate(cookie);
		setAccountType(user);
		return user;
	}

	@Override
	public UserModel authenticate(String username, char[] password) {
		UserModel user = serviceImpl.authenticate(username, password);
		setAccountType(user);
		return user;
	}

	@Override
	public void logout(UserModel user) {
		serviceImpl.logout(user);
	}

	@Override
	public UserModel getUserModel(String username) {
		UserModel user = serviceImpl.getUserModel(username);
		setAccountType(user);
		return user;
	}

	@Override
	public boolean updateUserModel(UserModel model) {
		return serviceImpl.updateUserModel(model);
	}

	@Override
	public boolean updateUserModels(Collection<UserModel> models) {
		return serviceImpl.updateUserModels(models);
	}

	@Override
	public boolean updateUserModel(String username, UserModel model) {
		if (model.isLocalAccount() || supportsCredentialChanges()) {
			if (!model.isLocalAccount() && !supportsTeamMembershipChanges()) {
				//  teams are externally controlled - copy from original model
				UserModel existingModel = getUserModel(username);

				model = DeepCopier.copy(model);
				model.teams.clear();
				model.teams.addAll(existingModel.teams);
			}
			return serviceImpl.updateUserModel(username, model);
		}
		if (model.username.equals(username)) {
			// passwords are not persisted by the backing user service
			model.password = null;
			if (!model.isLocalAccount() && !supportsTeamMembershipChanges()) {
				//  teams are externally controlled- copy from original model
				UserModel existingModel = getUserModel(username);

				model = DeepCopier.copy(model);
				model.teams.clear();
				model.teams.addAll(existingModel.teams);
			}
			return serviceImpl.updateUserModel(username, model);
		}
		logger.error("Users can not be renamed!");
		return false;
	}
	@Override
	public boolean deleteUserModel(UserModel model) {
		return serviceImpl.deleteUserModel(model);
	}

	@Override
	public boolean deleteUser(String username) {
		return serviceImpl.deleteUser(username);
	}

	@Override
	public List<String> getAllUsernames() {
		return serviceImpl.getAllUsernames();
	}

	@Override
	public List<UserModel> getAllUsers() {
		List<UserModel> users = serviceImpl.getAllUsers();
    	for (UserModel user : users) {
    		setAccountType(user);
    	}
		return users;
	}

	@Override
	public List<String> getAllTeamNames() {
		return serviceImpl.getAllTeamNames();
	}

	@Override
	public List<TeamModel> getAllTeams() {
		return serviceImpl.getAllTeams();
	}

	@Override
	public List<String> getTeamNamesForRepositoryRole(String role) {
		return serviceImpl.getTeamNamesForRepositoryRole(role);
	}

	@Override
	@Deprecated
	public boolean setTeamnamesForRepositoryRole(String role, List<String> teamnames) {
		return serviceImpl.setTeamnamesForRepositoryRole(role, teamnames);
	}

	@Override
	public TeamModel getTeamModel(String teamname) {
		return serviceImpl.getTeamModel(teamname);
	}

	@Override
	public boolean updateTeamModel(TeamModel model) {
		return serviceImpl.updateTeamModel(model);
	}

	@Override
	public boolean updateTeamModels(Collection<TeamModel> models) {
		return serviceImpl.updateTeamModels(models);
	}

	@Override
	public boolean updateTeamModel(String teamname, TeamModel model) {
		if (!supportsTeamMembershipChanges()) {
			// teams are externally controlled - copy from original model
			TeamModel existingModel = getTeamModel(teamname);

			model = DeepCopier.copy(model);
			model.users.clear();
			model.users.addAll(existingModel.users);
		}
		return serviceImpl.updateTeamModel(teamname, model);
	}

	@Override
	public boolean deleteTeamModel(TeamModel model) {
		return serviceImpl.deleteTeamModel(model);
	}

	@Override
	public boolean deleteTeam(String teamname) {
		return serviceImpl.deleteTeam(teamname);
	}

	@Override
	public List<String> getUsernamesForRepositoryRole(String role) {
		return serviceImpl.getUsernamesForRepositoryRole(role);
	}

	@Override
	@Deprecated
	public boolean setUsernamesForRepositoryRole(String role, List<String> usernames) {
		return serviceImpl.setUsernamesForRepositoryRole(role, usernames);
	}

	@Override
	public boolean renameRepositoryRole(String oldRole, String newRole) {
		return serviceImpl.renameRepositoryRole(oldRole, newRole);
	}

	@Override
	public boolean deleteRepositoryRole(String role) {
		return serviceImpl.deleteRepositoryRole(role);
	}

	protected boolean isLocalAccount(String username) {
		UserModel user = getUserModel(username);
		return user != null && user.isLocalAccount();
	}

	protected void setAccountType(UserModel user) {
		if (user != null) {
			if (!StringUtils.isEmpty(user.password)
					&& !Constants.EXTERNAL_ACCOUNT.equalsIgnoreCase(user.password)
					&& !"StoredInLDAP".equalsIgnoreCase(user.password)) {
				user.accountType = AccountType.LOCAL;
			} else {
				user.accountType = getAccountType();
			}
		}
	}

	@Override
	public AccountType getAccountType() {
		return AccountType.LOCAL;
	}
}
