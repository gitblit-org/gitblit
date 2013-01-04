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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.DeepCopier;
import com.gitblit.utils.StringUtils;

/**
 * ConfigUserService is Gitblit's default user service implementation since
 * version 0.8.0.
 * 
 * Users and their repository memberships are stored in a git-style config file
 * which is cached and dynamically reloaded when modified. This file is
 * plain-text, human-readable, and may be edited with a text editor.
 * 
 * Additionally, this format allows for expansion of the user model without
 * bringing in the complexity of a database.
 * 
 * @author James Moger
 * 
 */
public class ConfigUserService implements IUserService {

	private static final String TEAM = "team";

	private static final String USER = "user";

	private static final String PASSWORD = "password";
	
	private static final String DISPLAYNAME = "displayName";
	
	private static final String EMAILADDRESS = "emailAddress";
	
	private static final String ORGANIZATIONALUNIT = "organizationalUnit";
	
	private static final String ORGANIZATION = "organization";
	
	private static final String LOCALITY = "locality";
	
	private static final String STATEPROVINCE = "stateProvince";
	
	private static final String COUNTRYCODE = "countryCode";
	
	private static final String COOKIE = "cookie";

	private static final String REPOSITORY = "repository";

	private static final String ROLE = "role";

	private static final String MAILINGLIST = "mailingList";

	private static final String PRERECEIVE = "preReceiveScript";

	private static final String POSTRECEIVE = "postReceiveScript";

	private final File realmFile;

	private final Logger logger = LoggerFactory.getLogger(ConfigUserService.class);

	private final Map<String, UserModel> users = new ConcurrentHashMap<String, UserModel>();

	private final Map<String, UserModel> cookies = new ConcurrentHashMap<String, UserModel>();

	private final Map<String, TeamModel> teams = new ConcurrentHashMap<String, TeamModel>();

	private volatile long lastModified;
	
	private volatile boolean forceReload;

	public ConfigUserService(File realmFile) {
		this.realmFile = realmFile;
	}

	/**
	 * Setup the user service.
	 * 
	 * @param settings
	 * @since 0.7.0
	 */
	@Override
	public void setup(IStoredSettings settings) {
	}

	/**
	 * Does the user service support changes to credentials?
	 * 
	 * @return true or false
	 * @since 1.0.0
	 */
	@Override
	public boolean supportsCredentialChanges() {
		return true;
	}

	/**
	 * Does the user service support changes to user display name?
	 * 
	 * @return true or false
	 * @since 1.0.0
	 */
	@Override
	public boolean supportsDisplayNameChanges() {
		return true;
	}

	/**
	 * Does the user service support changes to user email address?
	 * 
	 * @return true or false
	 * @since 1.0.0
	 */
	@Override
	public boolean supportsEmailAddressChanges() {
		return true;
	}

	/**
	 * Does the user service support changes to team memberships?
	 * 
	 * @return true or false
	 * @since 1.0.0
	 */	
	public boolean supportsTeamMembershipChanges() {
		return true;
	}
	
	/**
	 * Does the user service support cookie authentication?
	 * 
	 * @return true or false
	 */
	@Override
	public boolean supportsCookies() {
		return true;
	}

	/**
	 * Returns the cookie value for the specified user.
	 * 
	 * @param model
	 * @return cookie value
	 */
	@Override
	public String getCookie(UserModel model) {
		if (!StringUtils.isEmpty(model.cookie)) {
			return model.cookie;
		}
		read();
		UserModel storedModel = users.get(model.username.toLowerCase());
		return storedModel.cookie;
	}

	/**
	 * Authenticate a user based on their cookie.
	 * 
	 * @param cookie
	 * @return a user object or null
	 */
	@Override
	public UserModel authenticate(char[] cookie) {
		String hash = new String(cookie);
		if (StringUtils.isEmpty(hash)) {
			return null;
		}
		read();
		UserModel model = null;
		if (cookies.containsKey(hash)) {
			model = cookies.get(hash);
		}
		return model;
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
		read();
		UserModel returnedUser = null;
		UserModel user = getUserModel(username);
		if (user == null) {
			return null;
		}
		if (user.password.startsWith(StringUtils.MD5_TYPE)) {
			// password digest
			String md5 = StringUtils.MD5_TYPE + StringUtils.getMD5(new String(password));
			if (user.password.equalsIgnoreCase(md5)) {
				returnedUser = user;
			}
		} else if (user.password.startsWith(StringUtils.COMBINED_MD5_TYPE)) {
			// username+password digest
			String md5 = StringUtils.COMBINED_MD5_TYPE
					+ StringUtils.getMD5(username.toLowerCase() + new String(password));
			if (user.password.equalsIgnoreCase(md5)) {
				returnedUser = user;
			}
		} else if (user.password.equals(new String(password))) {
			// plain-text password
			returnedUser = user;
		}
		return returnedUser;
	}

	/**
	 * Logout a user.
	 * 
	 * @param user
	 */
	@Override
	public void logout(UserModel user) {	
	}
	
	/**
	 * Retrieve the user object for the specified username.
	 * 
	 * @param username
	 * @return a user object or null
	 */
	@Override
	public UserModel getUserModel(String username) {
		read();
		UserModel model = users.get(username.toLowerCase());
		if (model != null) {
			// clone the model, otherwise all changes to this object are
			// live and unpersisted
			model = DeepCopier.copy(model);
		}
		return model;
	}

	/**
	 * Updates/writes a complete user object.
	 * 
	 * @param model
	 * @return true if update is successful
	 */
	@Override
	public boolean updateUserModel(UserModel model) {
		return updateUserModel(model.username, model);
	}

	/**
	 * Updates/writes all specified user objects.
	 * 
	 * @param models a list of user models
	 * @return true if update is successful
	 * @since 1.2.0
	 */
	@Override
	public boolean updateUserModels(List<UserModel> models) {
		try {
			read();
			for (UserModel model : models) {
				UserModel originalUser = users.remove(model.username.toLowerCase());
				users.put(model.username.toLowerCase(), model);
				// null check on "final" teams because JSON-sourced UserModel
				// can have a null teams object
				if (model.teams != null) {
					for (TeamModel team : model.teams) {
						TeamModel t = teams.get(team.name.toLowerCase());
						if (t == null) {
							// new team
							team.addUser(model.username);
							teams.put(team.name.toLowerCase(), team);
						} else {
							// do not clobber existing team definition
							// maybe because this is a federated user
							t.addUser(model.username);							
						}
					}

					// check for implicit team removal
					if (originalUser != null) {
						for (TeamModel team : originalUser.teams) {
							if (!model.isTeamMember(team.name)) {
								team.removeUser(model.username);
							}
						}
					}
				}
			}
			write();
			return true;
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Failed to update user {0} models!", models.size()),
					t);
		}
		return false;
	}

	/**
	 * Updates/writes and replaces a complete user object keyed by username.
	 * This method allows for renaming a user.
	 * 
	 * @param username
	 *            the old username
	 * @param model
	 *            the user object to use for username
	 * @return true if update is successful
	 */
	@Override
	public boolean updateUserModel(String username, UserModel model) {
		UserModel originalUser = null;
		try {
			read();
			originalUser = users.remove(username.toLowerCase());
			users.put(model.username.toLowerCase(), model);
			// null check on "final" teams because JSON-sourced UserModel
			// can have a null teams object
			if (model.teams != null) {
				for (TeamModel team : model.teams) {
					TeamModel t = teams.get(team.name.toLowerCase());
					if (t == null) {
						// new team
						team.addUser(username);
						teams.put(team.name.toLowerCase(), team);
					} else {
						// do not clobber existing team definition
						// maybe because this is a federated user
						t.removeUser(username);
						t.addUser(model.username);
					}
				}

				// check for implicit team removal
				if (originalUser != null) {
					for (TeamModel team : originalUser.teams) {
						if (!model.isTeamMember(team.name)) {
							team.removeUser(username);
						}
					}
				}
			}
			write();
			return true;
		} catch (Throwable t) {
			if (originalUser != null) {
				// restore original user
				users.put(originalUser.username.toLowerCase(), originalUser);
			} else {
				// drop attempted add
				users.remove(model.username.toLowerCase());
			}
			logger.error(MessageFormat.format("Failed to update user model {0}!", model.username),
					t);
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
		return deleteUser(model.username);
	}

	/**
	 * Delete the user object with the specified username
	 * 
	 * @param username
	 * @return true if successful
	 */
	@Override
	public boolean deleteUser(String username) {
		try {
			// Read realm file
			read();
			UserModel model = users.remove(username.toLowerCase());
			if (model == null) {
				// user does not exist
				return false;
			}
			// remove user from team
			for (TeamModel team : model.teams) {
				TeamModel t = teams.get(team.name);
				if (t == null) {
					// new team
					team.removeUser(username);
					teams.put(team.name.toLowerCase(), team);
				} else {
					// existing team
					t.removeUser(username);
				}
			}
			write();
			return true;
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Failed to delete user {0}!", username), t);
		}
		return false;
	}

	/**
	 * Returns the list of all teams available to the login service.
	 * 
	 * @return list of all teams
	 * @since 0.8.0
	 */
	@Override
	public List<String> getAllTeamNames() {
		read();
		List<String> list = new ArrayList<String>(teams.keySet());
		Collections.sort(list);
		return list;
	}

	/**
	 * Returns the list of all teams available to the login service.
	 * 
	 * @return list of all teams
	 * @since 0.8.0
	 */
	@Override
	public List<TeamModel> getAllTeams() {
		read();
		List<TeamModel> list = new ArrayList<TeamModel>(teams.values());
		list = DeepCopier.copy(list);
		Collections.sort(list);
		return list;
	}

	/**
	 * Returns the list of all users who are allowed to bypass the access
	 * restriction placed on the specified repository.
	 * 
	 * @param role
	 *            the repository name
	 * @return list of all usernames that can bypass the access restriction
	 */
	@Override
	public List<String> getTeamnamesForRepositoryRole(String role) {
		List<String> list = new ArrayList<String>();
		try {
			read();
			for (Map.Entry<String, TeamModel> entry : teams.entrySet()) {
				TeamModel model = entry.getValue();
				if (model.hasRepositoryPermission(role)) {
					list.add(model.name);
				}
			}
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Failed to get teamnames for role {0}!", role), t);
		}
		Collections.sort(list);
		return list;
	}

	/**
	 * Sets the list of all teams who are allowed to bypass the access
	 * restriction placed on the specified repository.
	 * 
	 * @param role
	 *            the repository name
	 * @param teamnames
	 * @return true if successful
	 */
	@Override
	public boolean setTeamnamesForRepositoryRole(String role, List<String> teamnames) {
		try {
			Set<String> specifiedTeams = new HashSet<String>();
			for (String teamname : teamnames) {
				specifiedTeams.add(teamname.toLowerCase());
			}

			read();

			// identify teams which require add or remove role
			for (TeamModel team : teams.values()) {
				// team has role, check against revised team list
				if (specifiedTeams.contains(team.name.toLowerCase())) {
					team.addRepositoryPermission(role);
				} else {
					// remove role from team
					team.removeRepositoryPermission(role);
				}
			}

			// persist changes
			write();
			return true;
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Failed to set teams for role {0}!", role), t);
		}
		return false;
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
		read();
		TeamModel model = teams.get(teamname.toLowerCase());
		if (model != null) {
			// clone the model, otherwise all changes to this object are
			// live and unpersisted
			model = DeepCopier.copy(model);
		}
		return model;
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
		return updateTeamModel(model.name, model);
	}

	/**
	 * Updates/writes all specified team objects.
	 * 
	 * @param models a list of team models
	 * @return true if update is successful
	 * @since 1.2.0
	 */
	@Override
	public boolean updateTeamModels(List<TeamModel> models) {
		try {
			read();
			for (TeamModel team : models) {
				teams.put(team.name.toLowerCase(), team);
			}
			write();
			return true;
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Failed to update team {0} models!", models.size()), t);
		}
		return false;
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
		TeamModel original = null;
		try {
			read();
			original = teams.remove(teamname.toLowerCase());
			teams.put(model.name.toLowerCase(), model);
			write();
			return true;
		} catch (Throwable t) {
			if (original != null) {
				// restore original team
				teams.put(original.name.toLowerCase(), original);
			} else {
				// drop attempted add
				teams.remove(model.name.toLowerCase());
			}
			logger.error(MessageFormat.format("Failed to update team model {0}!", model.name), t);
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
		return deleteTeam(model.name);
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
		try {
			// Read realm file
			read();
			teams.remove(teamname.toLowerCase());
			write();
			return true;
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Failed to delete team {0}!", teamname), t);
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
		read();
		List<String> list = new ArrayList<String>(users.keySet());
		Collections.sort(list);
		return list;
	}
	
	/**
	 * Returns the list of all users available to the login service.
	 * 
	 * @return list of all usernames
	 */
	@Override
	public List<UserModel> getAllUsers() {
		read();
		List<UserModel> list = new ArrayList<UserModel>(users.values());
		list = DeepCopier.copy(list);
		Collections.sort(list);
		return list;
	}	

	/**
	 * Returns the list of all users who are allowed to bypass the access
	 * restriction placed on the specified repository.
	 * 
	 * @param role
	 *            the repository name
	 * @return list of all usernames that can bypass the access restriction
	 */
	@Override
	public List<String> getUsernamesForRepositoryRole(String role) {
		List<String> list = new ArrayList<String>();
		try {
			read();
			for (Map.Entry<String, UserModel> entry : users.entrySet()) {
				UserModel model = entry.getValue();
				if (model.hasRepositoryPermission(role)) {
					list.add(model.username);
				}
			}
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Failed to get usernames for role {0}!", role), t);
		}
		Collections.sort(list);
		return list;
	}

	/**
	 * Sets the list of all uses who are allowed to bypass the access
	 * restriction placed on the specified repository.
	 * 
	 * @param role
	 *            the repository name
	 * @param usernames
	 * @return true if successful
	 */
	@Override
	@Deprecated
	public boolean setUsernamesForRepositoryRole(String role, List<String> usernames) {
		try {
			Set<String> specifiedUsers = new HashSet<String>();
			for (String username : usernames) {
				specifiedUsers.add(username.toLowerCase());
			}

			read();

			// identify users which require add or remove role
			for (UserModel user : users.values()) {
				// user has role, check against revised user list
				if (specifiedUsers.contains(user.username.toLowerCase())) {
					user.addRepositoryPermission(role);
				} else {
					// remove role from user
					user.removeRepositoryPermission(role);
				}
			}

			// persist changes
			write();
			return true;
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Failed to set usernames for role {0}!", role), t);
		}
		return false;
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
		try {
			read();
			// identify users which require role rename
			for (UserModel model : users.values()) {
				if (model.hasRepositoryPermission(oldRole)) {
					AccessPermission permission = model.removeRepositoryPermission(oldRole);
					model.setRepositoryPermission(newRole, permission);
				}
			}

			// identify teams which require role rename
			for (TeamModel model : teams.values()) {
				if (model.hasRepositoryPermission(oldRole)) {
					AccessPermission permission = model.removeRepositoryPermission(oldRole);
					model.setRepositoryPermission(newRole, permission);
				}
			}
			// persist changes
			write();
			return true;
		} catch (Throwable t) {
			logger.error(
					MessageFormat.format("Failed to rename role {0} to {1}!", oldRole, newRole), t);
		}
		return false;
	}

	/**
	 * Removes a repository role from all users.
	 * 
	 * @param role
	 * @return true if successful
	 */
	@Override
	public boolean deleteRepositoryRole(String role) {
		try {
			read();

			// identify users which require role rename
			for (UserModel user : users.values()) {
				user.removeRepositoryPermission(role);
			}

			// identify teams which require role rename
			for (TeamModel team : teams.values()) {
				team.removeRepositoryPermission(role);
			}

			// persist changes
			write();
			return true;
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Failed to delete role {0}!", role), t);
		}
		return false;
	}

	/**
	 * Writes the properties file.
	 * 
	 * @param properties
	 * @throws IOException
	 */
	private synchronized void write() throws IOException {
		// Write a temporary copy of the users file
		File realmFileCopy = new File(realmFile.getAbsolutePath() + ".tmp");

		StoredConfig config = new FileBasedConfig(realmFileCopy, FS.detect());

		// write users
		for (UserModel model : users.values()) {
			if (!StringUtils.isEmpty(model.password)) {
				config.setString(USER, model.username, PASSWORD, model.password);
			}
			if (!StringUtils.isEmpty(model.cookie)) {
				config.setString(USER, model.username, COOKIE, model.cookie);
			}
			if (!StringUtils.isEmpty(model.displayName)) {
				config.setString(USER, model.username, DISPLAYNAME, model.displayName);
			}
			if (!StringUtils.isEmpty(model.emailAddress)) {
				config.setString(USER, model.username, EMAILADDRESS, model.emailAddress);
			}
			if (!StringUtils.isEmpty(model.organizationalUnit)) {
				config.setString(USER, model.username, ORGANIZATIONALUNIT, model.organizationalUnit);
			}
			if (!StringUtils.isEmpty(model.organization)) {
				config.setString(USER, model.username, ORGANIZATION, model.organization);
			}
			if (!StringUtils.isEmpty(model.locality)) {
				config.setString(USER, model.username, LOCALITY, model.locality);
			}
			if (!StringUtils.isEmpty(model.stateProvince)) {
				config.setString(USER, model.username, STATEPROVINCE, model.stateProvince);
			}
			if (!StringUtils.isEmpty(model.countryCode)) {
				config.setString(USER, model.username, COUNTRYCODE, model.countryCode);
			}

			// user roles
			List<String> roles = new ArrayList<String>();
			if (model.canAdmin) {
				roles.add(Constants.ADMIN_ROLE);
			}
			if (model.canFork) {
				roles.add(Constants.FORK_ROLE);
			}
			if (model.canCreate) {
				roles.add(Constants.CREATE_ROLE);
			}
			if (model.excludeFromFederation) {
				roles.add(Constants.NOT_FEDERATED_ROLE);
			}
			if (roles.size() == 0) {
				// we do this to ensure that user record with no password
				// is written.  otherwise, StoredConfig optimizes that account
				// away. :(
				roles.add(Constants.NO_ROLE);
			}
			config.setStringList(USER, model.username, ROLE, roles);

			// discrete repository permissions
			if (model.permissions != null && !model.canAdmin) {
				List<String> permissions = new ArrayList<String>();
				for (Map.Entry<String, AccessPermission> entry : model.permissions.entrySet()) {
					if (entry.getValue().exceeds(AccessPermission.NONE)) {
						permissions.add(entry.getValue().asRole(entry.getKey()));
					}
				}
				config.setStringList(USER, model.username, REPOSITORY, permissions);
			}
		}

		// write teams
		for (TeamModel model : teams.values()) {
			// team roles
			List<String> roles = new ArrayList<String>();
			if (model.canAdmin) {
				roles.add(Constants.ADMIN_ROLE);
			}
			if (model.canFork) {
				roles.add(Constants.FORK_ROLE);
			}
			if (model.canCreate) {
				roles.add(Constants.CREATE_ROLE);
			}
			if (roles.size() == 0) {
				// we do this to ensure that team record is written.
				// Otherwise, StoredConfig might optimizes that record away.
				roles.add(Constants.NO_ROLE);
			}
			config.setStringList(TEAM, model.name, ROLE, roles);
			
			if (!model.canAdmin) {
				// write team permission for non-admin teams
				if (model.permissions == null) {
					// null check on "final" repositories because JSON-sourced TeamModel
					// can have a null repositories object
					if (!ArrayUtils.isEmpty(model.repositories)) {
						config.setStringList(TEAM, model.name, REPOSITORY, new ArrayList<String>(
								model.repositories));
					}
				} else {
					// discrete repository permissions
					List<String> permissions = new ArrayList<String>();
					for (Map.Entry<String, AccessPermission> entry : model.permissions.entrySet()) {
						if (entry.getValue().exceeds(AccessPermission.NONE)) {
							// code:repository (e.g. RW+:~james/myrepo.git
							permissions.add(entry.getValue().asRole(entry.getKey()));
						}
					}
					config.setStringList(TEAM, model.name, REPOSITORY, permissions);
				}
			}

			// null check on "final" users because JSON-sourced TeamModel
			// can have a null users object
			if (!ArrayUtils.isEmpty(model.users)) {
				config.setStringList(TEAM, model.name, USER, new ArrayList<String>(model.users));
			}

			// null check on "final" mailing lists because JSON-sourced
			// TeamModel can have a null users object
			if (!ArrayUtils.isEmpty(model.mailingLists)) {
				config.setStringList(TEAM, model.name, MAILINGLIST, new ArrayList<String>(
						model.mailingLists));
			}

			// null check on "final" preReceiveScripts because JSON-sourced
			// TeamModel can have a null preReceiveScripts object
			if (!ArrayUtils.isEmpty(model.preReceiveScripts)) {
				config.setStringList(TEAM, model.name, PRERECEIVE, model.preReceiveScripts);
			}

			// null check on "final" postReceiveScripts because JSON-sourced
			// TeamModel can have a null postReceiveScripts object
			if (!ArrayUtils.isEmpty(model.postReceiveScripts)) {
				config.setStringList(TEAM, model.name, POSTRECEIVE, model.postReceiveScripts);
			}
		}

		config.save();
		// manually set the forceReload flag because not all JVMs support real
		// millisecond resolution of lastModified. (issue-55)
		forceReload = true;

		// If the write is successful, delete the current file and rename
		// the temporary copy to the original filename.
		if (realmFileCopy.exists() && realmFileCopy.length() > 0) {
			if (realmFile.exists()) {
				if (!realmFile.delete()) {
					throw new IOException(MessageFormat.format("Failed to delete {0}!",
							realmFile.getAbsolutePath()));
				}
			}
			if (!realmFileCopy.renameTo(realmFile)) {
				throw new IOException(MessageFormat.format("Failed to rename {0} to {1}!",
						realmFileCopy.getAbsolutePath(), realmFile.getAbsolutePath()));
			}
		} else {
			throw new IOException(MessageFormat.format("Failed to save {0}!",
					realmFileCopy.getAbsolutePath()));
		}
	}

	/**
	 * Reads the realm file and rebuilds the in-memory lookup tables.
	 */
	protected synchronized void read() {
		if (realmFile.exists() && (forceReload || (realmFile.lastModified() != lastModified))) {
			forceReload = false;
			lastModified = realmFile.lastModified();
			users.clear();
			cookies.clear();
			teams.clear();

			try {
				StoredConfig config = new FileBasedConfig(realmFile, FS.detect());
				config.load();
				Set<String> usernames = config.getSubsections(USER);
				for (String username : usernames) {
					UserModel user = new UserModel(username.toLowerCase());
					user.password = config.getString(USER, username, PASSWORD);					
					user.displayName = config.getString(USER, username, DISPLAYNAME);
					user.emailAddress = config.getString(USER, username, EMAILADDRESS);
					user.organizationalUnit = config.getString(USER, username, ORGANIZATIONALUNIT);
					user.organization = config.getString(USER, username, ORGANIZATION);
					user.locality = config.getString(USER, username, LOCALITY);
					user.stateProvince = config.getString(USER, username, STATEPROVINCE);
					user.countryCode = config.getString(USER, username, COUNTRYCODE);
					user.cookie = config.getString(USER, username, COOKIE);
					if (StringUtils.isEmpty(user.cookie) && !StringUtils.isEmpty(user.password)) {
						user.cookie = StringUtils.getSHA1(user.username + user.password);
					}

					// user roles
					Set<String> roles = new HashSet<String>(Arrays.asList(config.getStringList(
							USER, username, ROLE)));
					user.canAdmin = roles.contains(Constants.ADMIN_ROLE);
					user.canFork = roles.contains(Constants.FORK_ROLE);
					user.canCreate = roles.contains(Constants.CREATE_ROLE);
					user.excludeFromFederation = roles.contains(Constants.NOT_FEDERATED_ROLE);

					// repository memberships
					if (!user.canAdmin) {
						// non-admin, read permissions
						Set<String> repositories = new HashSet<String>(Arrays.asList(config
								.getStringList(USER, username, REPOSITORY)));
						for (String repository : repositories) {
							user.addRepositoryPermission(repository);
						}
					}

					// update cache
					users.put(user.username, user);
					if (!StringUtils.isEmpty(user.cookie)) {
						cookies.put(user.cookie, user);
					}
				}

				// load the teams
				Set<String> teamnames = config.getSubsections(TEAM);
				for (String teamname : teamnames) {
					TeamModel team = new TeamModel(teamname);
					Set<String> roles = new HashSet<String>(Arrays.asList(config.getStringList(
							TEAM, teamname, ROLE)));
					team.canAdmin = roles.contains(Constants.ADMIN_ROLE);
					team.canFork = roles.contains(Constants.FORK_ROLE);
					team.canCreate = roles.contains(Constants.CREATE_ROLE);
					
					if (!team.canAdmin) {
						// non-admin team, read permissions
						team.addRepositoryPermissions(Arrays.asList(config.getStringList(TEAM, teamname,
								REPOSITORY)));
					}
					team.addUsers(Arrays.asList(config.getStringList(TEAM, teamname, USER)));
					team.addMailingLists(Arrays.asList(config.getStringList(TEAM, teamname,
							MAILINGLIST)));
					team.preReceiveScripts.addAll(Arrays.asList(config.getStringList(TEAM,
							teamname, PRERECEIVE)));
					team.postReceiveScripts.addAll(Arrays.asList(config.getStringList(TEAM,
							teamname, POSTRECEIVE)));

					teams.put(team.name.toLowerCase(), team);

					// set the teams on the users
					for (String user : team.users) {
						UserModel model = users.get(user);
						if (model != null) {
							model.teams.add(team);
						}
					}
				}
			} catch (Exception e) {
				logger.error(MessageFormat.format("Failed to read {0}", realmFile), e);
			}
		}
	}

	protected long lastModified() {
		return lastModified;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "(" + realmFile.getAbsolutePath() + ")";
	}
}
