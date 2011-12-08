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
import java.io.FileWriter;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.DeepCopier;
import com.gitblit.utils.StringUtils;

/**
 * FileUserService is Gitblit's original default user service implementation.
 * 
 * Users and their repository memberships are stored in a simple properties file
 * which is cached and dynamically reloaded when modified.
 * 
 * This class was deprecated in Gitblit 0.8.0 in favor of ConfigUserService
 * which is still a human-readable, editable, plain-text file but it is more
 * flexible for storing additional fields.
 * 
 * @author James Moger
 * 
 */
@Deprecated
public class FileUserService extends FileSettings implements IUserService {

	private final Logger logger = LoggerFactory.getLogger(FileUserService.class);

	private final Map<String, String> cookies = new ConcurrentHashMap<String, String>();

	private final Map<String, TeamModel> teams = new ConcurrentHashMap<String, TeamModel>();

	public FileUserService(File realmFile) {
		super(realmFile.getAbsolutePath());
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
	public char[] getCookie(UserModel model) {
		Properties allUsers = super.read();
		String value = allUsers.getProperty(model.username);
		String[] roles = value.split(",");
		String password = roles[0];
		String cookie = StringUtils.getSHA1(model.username + password);
		return cookie.toCharArray();
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
			String username = cookies.get(hash);
			model = getUserModel(username);
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
		Properties allUsers = read();
		String userInfo = allUsers.getProperty(username);
		if (StringUtils.isEmpty(userInfo)) {
			return null;
		}
		UserModel returnedUser = null;
		UserModel user = getUserModel(username);
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
	 * Retrieve the user object for the specified username.
	 * 
	 * @param username
	 * @return a user object or null
	 */
	@Override
	public UserModel getUserModel(String username) {
		Properties allUsers = read();
		String userInfo = allUsers.getProperty(username);
		if (userInfo == null) {
			return null;
		}
		UserModel model = new UserModel(username);
		String[] userValues = userInfo.split(",");
		model.password = userValues[0];
		for (int i = 1; i < userValues.length; i++) {
			String role = userValues[i];
			switch (role.charAt(0)) {
			case '#':
				// Permissions
				if (role.equalsIgnoreCase(Constants.ADMIN_ROLE)) {
					model.canAdmin = true;
				} else if (role.equalsIgnoreCase(Constants.NOT_FEDERATED_ROLE)) {
					model.excludeFromFederation = true;
				}
				break;
			default:
				model.addRepository(role);
			}
		}
		// set the teams for the user
		for (TeamModel team : teams.values()) {
			if (team.hasUser(username)) {
				model.teams.add(DeepCopier.copy(team));
			}
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
		try {
			Properties allUsers = read();
			UserModel oldUser = getUserModel(username);
			ArrayList<String> roles = new ArrayList<String>(model.repositories);

			// Permissions
			if (model.canAdmin) {
				roles.add(Constants.ADMIN_ROLE);
			}
			if (model.excludeFromFederation) {
				roles.add(Constants.NOT_FEDERATED_ROLE);
			}

			StringBuilder sb = new StringBuilder();
			sb.append(model.password);
			sb.append(',');
			for (String role : roles) {
				sb.append(role);
				sb.append(',');
			}
			// trim trailing comma
			sb.setLength(sb.length() - 1);
			allUsers.remove(username);
			allUsers.put(model.username, sb.toString());

			// null check on "final" teams because JSON-sourced UserModel
			// can have a null teams object
			if (model.teams != null) {
				// update team cache
				for (TeamModel team : model.teams) {
					TeamModel t = getTeamModel(team.name);
					if (t == null) {
						// new team
						t = team;
					}
					t.removeUser(username);
					t.addUser(model.username);
					updateTeamCache(allUsers, t.name, t);
				}

				// check for implicit team removal
				if (oldUser != null) {
					for (TeamModel team : oldUser.teams) {
						if (!model.isTeamMember(team.name)) {
							team.removeUser(username);
							updateTeamCache(allUsers, team.name, team);
						}
					}
				}
			}

			write(allUsers);
			return true;
		} catch (Throwable t) {
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
			Properties allUsers = read();
			UserModel user = getUserModel(username);
			allUsers.remove(username);
			for (TeamModel team : user.teams) {
				TeamModel t = getTeamModel(team.name);
				if (t == null) {
					// new team
					t = team;
				}
				t.removeUser(username);
				updateTeamCache(allUsers, t.name, t);
			}
			write(allUsers);
			return true;
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Failed to delete user {0}!", username), t);
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
		Properties allUsers = read();
		List<String> list = new ArrayList<String>();
		for (String user : allUsers.stringPropertyNames()) {
			if (user.charAt(0) == '@') {
				// skip team user definitions
				continue;
			}
			list.add(user);
		}
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
			Properties allUsers = read();
			for (String username : allUsers.stringPropertyNames()) {
				if (username.charAt(0) == '@') {
					continue;
				}
				String value = allUsers.getProperty(username);
				String[] values = value.split(",");
				// skip first value (password)
				for (int i = 1; i < values.length; i++) {
					String r = values[i];
					if (r.equalsIgnoreCase(role)) {
						list.add(username);
						break;
					}
				}
			}
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Failed to get usernames for role {0}!", role), t);
		}
		return list;
	}

	/**
	 * Sets the list of all users who are allowed to bypass the access
	 * restriction placed on the specified repository.
	 * 
	 * @param role
	 *            the repository name
	 * @param usernames
	 * @return true if successful
	 */
	@Override
	public boolean setUsernamesForRepositoryRole(String role, List<String> usernames) {
		try {
			Set<String> specifiedUsers = new HashSet<String>(usernames);
			Set<String> needsAddRole = new HashSet<String>(specifiedUsers);
			Set<String> needsRemoveRole = new HashSet<String>();

			// identify users which require add and remove role
			Properties allUsers = read();
			for (String username : allUsers.stringPropertyNames()) {
				String value = allUsers.getProperty(username);
				String[] values = value.split(",");
				// skip first value (password)
				for (int i = 1; i < values.length; i++) {
					String r = values[i];
					if (r.equalsIgnoreCase(role)) {
						// user has role, check against revised user list
						if (specifiedUsers.contains(username)) {
							needsAddRole.remove(username);
						} else {
							// remove role from user
							needsRemoveRole.add(username);
						}
						break;
					}
				}
			}

			// add roles to users
			for (String user : needsAddRole) {
				String userValues = allUsers.getProperty(user);
				userValues += "," + role;
				allUsers.put(user, userValues);
			}

			// remove role from user
			for (String user : needsRemoveRole) {
				String[] values = allUsers.getProperty(user).split(",");
				String password = values[0];
				StringBuilder sb = new StringBuilder();
				sb.append(password);
				sb.append(',');

				// skip first value (password)
				for (int i = 1; i < values.length; i++) {
					String value = values[i];
					if (!value.equalsIgnoreCase(role)) {
						sb.append(value);
						sb.append(',');
					}
				}
				sb.setLength(sb.length() - 1);

				// update properties
				allUsers.put(user, sb.toString());
			}

			// persist changes
			write(allUsers);
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
			Properties allUsers = read();
			Set<String> needsRenameRole = new HashSet<String>();

			// identify users which require role rename
			for (String username : allUsers.stringPropertyNames()) {
				String value = allUsers.getProperty(username);
				String[] roles = value.split(",");
				// skip first value (password)
				for (int i = 1; i < roles.length; i++) {
					String r = roles[i];
					if (r.equalsIgnoreCase(oldRole)) {
						needsRenameRole.add(username);
						break;
					}
				}
			}

			// rename role for identified users
			for (String user : needsRenameRole) {
				String userValues = allUsers.getProperty(user);
				String[] values = userValues.split(",");
				String password = values[0];
				StringBuilder sb = new StringBuilder();
				sb.append(password);
				sb.append(',');
				sb.append(newRole);
				sb.append(',');

				// skip first value (password)
				for (int i = 1; i < values.length; i++) {
					String value = values[i];
					if (!value.equalsIgnoreCase(oldRole)) {
						sb.append(value);
						sb.append(',');
					}
				}
				sb.setLength(sb.length() - 1);

				// update properties
				allUsers.put(user, sb.toString());
			}

			// persist changes
			write(allUsers);
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
			Properties allUsers = read();
			Set<String> needsDeleteRole = new HashSet<String>();

			// identify users which require role rename
			for (String username : allUsers.stringPropertyNames()) {
				String value = allUsers.getProperty(username);
				String[] roles = value.split(",");
				// skip first value (password)
				for (int i = 1; i < roles.length; i++) {
					String r = roles[i];
					if (r.equalsIgnoreCase(role)) {
						needsDeleteRole.add(username);
						break;
					}
				}
			}

			// delete role for identified users
			for (String user : needsDeleteRole) {
				String userValues = allUsers.getProperty(user);
				String[] values = userValues.split(",");
				String password = values[0];
				StringBuilder sb = new StringBuilder();
				sb.append(password);
				sb.append(',');
				// skip first value (password)
				for (int i = 1; i < values.length; i++) {
					String value = values[i];
					if (!value.equalsIgnoreCase(role)) {
						sb.append(value);
						sb.append(',');
					}
				}
				sb.setLength(sb.length() - 1);

				// update properties
				allUsers.put(user, sb.toString());
			}

			// persist changes
			write(allUsers);
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
	private void write(Properties properties) throws IOException {
		// Write a temporary copy of the users file
		File realmFileCopy = new File(propertiesFile.getAbsolutePath() + ".tmp");
		FileWriter writer = new FileWriter(realmFileCopy);
		properties
				.store(writer,
						" Gitblit realm file format:\n   username=password,\\#permission,repository1,repository2...\n   @teamname=!username1,!username2,!username3,repository1,repository2...");
		writer.close();
		// If the write is successful, delete the current file and rename
		// the temporary copy to the original filename.
		if (realmFileCopy.exists() && realmFileCopy.length() > 0) {
			if (propertiesFile.exists()) {
				if (!propertiesFile.delete()) {
					throw new IOException(MessageFormat.format("Failed to delete {0}!",
							propertiesFile.getAbsolutePath()));
				}
			}
			if (!realmFileCopy.renameTo(propertiesFile)) {
				throw new IOException(MessageFormat.format("Failed to rename {0} to {1}!",
						realmFileCopy.getAbsolutePath(), propertiesFile.getAbsolutePath()));
			}
		} else {
			throw new IOException(MessageFormat.format("Failed to save {0}!",
					realmFileCopy.getAbsolutePath()));
		}
	}

	/**
	 * Reads the properties file and rebuilds the in-memory cookie lookup table.
	 */
	@Override
	protected synchronized Properties read() {
		long lastRead = lastModified();
		Properties allUsers = super.read();
		if (lastRead != lastModified()) {
			// reload hash cache
			cookies.clear();
			teams.clear();

			for (String username : allUsers.stringPropertyNames()) {
				String value = allUsers.getProperty(username);
				String[] roles = value.split(",");
				if (username.charAt(0) == '@') {
					// team definition
					TeamModel team = new TeamModel(username.substring(1));
					List<String> repositories = new ArrayList<String>();
					List<String> users = new ArrayList<String>();
					for (String role : roles) {
						if (role.charAt(0) == '!') {
							users.add(role.substring(1));
						} else {
							repositories.add(role);
						}
					}
					team.addRepositories(repositories);
					team.addUsers(users);
					teams.put(team.name.toLowerCase(), team);
				} else {
					// user definition
					String password = roles[0];
					cookies.put(StringUtils.getSHA1(username + password), username);
				}
			}
		}
		return allUsers;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "(" + propertiesFile.getAbsolutePath() + ")";
	}

	/**
	 * Returns the list of all teams available to the login service.
	 * 
	 * @return list of all teams
	 * @since 0.8.0
	 */
	@Override
	public List<String> getAllTeamNames() {
		List<String> list = new ArrayList<String>(teams.keySet());
		return list;
	}

	/**
	 * Returns the list of all teams who are allowed to bypass the access
	 * restriction placed on the specified repository.
	 * 
	 * @param role
	 *            the repository name
	 * @return list of all teamnames that can bypass the access restriction
	 */
	@Override
	public List<String> getTeamnamesForRepositoryRole(String role) {
		List<String> list = new ArrayList<String>();
		try {
			Properties allUsers = read();
			for (String team : allUsers.stringPropertyNames()) {
				if (team.charAt(0) != '@') {
					// skip users
					continue;
				}
				String value = allUsers.getProperty(team);
				String[] values = value.split(",");
				for (int i = 0; i < values.length; i++) {
					String r = values[i];
					if (r.equalsIgnoreCase(role)) {
						// strip leading @
						list.add(team.substring(1));
						break;
					}
				}
			}
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Failed to get teamnames for role {0}!", role), t);
		}
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
			Set<String> specifiedTeams = new HashSet<String>(teamnames);
			Set<String> needsAddRole = new HashSet<String>(specifiedTeams);
			Set<String> needsRemoveRole = new HashSet<String>();

			// identify teams which require add and remove role
			Properties allUsers = read();
			for (String team : allUsers.stringPropertyNames()) {
				if (team.charAt(0) != '@') {
					// skip users
					continue;
				}
				String name = team.substring(1);
				String value = allUsers.getProperty(team);
				String[] values = value.split(",");
				for (int i = 0; i < values.length; i++) {
					String r = values[i];
					if (r.equalsIgnoreCase(role)) {
						// team has role, check against revised team list
						if (specifiedTeams.contains(name)) {
							needsAddRole.remove(name);
						} else {
							// remove role from team
							needsRemoveRole.add(name);
						}
						break;
					}
				}
			}

			// add roles to teams
			for (String name : needsAddRole) {
				String team = "@" + name;
				String teamValues = allUsers.getProperty(team);
				teamValues += "," + role;
				allUsers.put(team, teamValues);
			}

			// remove role from team
			for (String name : needsRemoveRole) {
				String team = "@" + name;
				String[] values = allUsers.getProperty(team).split(",");				
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < values.length; i++) {
					String value = values[i];
					if (!value.equalsIgnoreCase(role)) {
						sb.append(value);
						sb.append(',');
					}
				}
				sb.setLength(sb.length() - 1);

				// update properties
				allUsers.put(team, sb.toString());
			}

			// persist changes
			write(allUsers);
			return true;
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Failed to set teamnames for role {0}!", role), t);
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
		TeamModel team = teams.get(teamname.toLowerCase());
		if (team != null) {
			// clone the model, otherwise all changes to this object are
			// live and unpersisted
			team = DeepCopier.copy(team);
		}
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
		return updateTeamModel(model.name, model);
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
		try {
			Properties allUsers = read();
			updateTeamCache(allUsers, teamname, model);
			write(allUsers);
			return true;
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Failed to update team model {0}!", model.name), t);
		}
		return false;
	}

	private void updateTeamCache(Properties allUsers, String teamname, TeamModel model) {
		StringBuilder sb = new StringBuilder();
		for (String repository : model.repositories) {
			sb.append(repository);
			sb.append(',');
		}
		for (String user : model.users) {
			sb.append('!');
			sb.append(user);
			sb.append(',');
		}
		// trim trailing comma
		sb.setLength(sb.length() - 1);
		allUsers.remove("@" + teamname);
		allUsers.put("@" + model.name, sb.toString());

		// update team cache
		teams.remove(teamname.toLowerCase());
		teams.put(model.name.toLowerCase(), model);
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
		Properties allUsers = read();
		teams.remove(teamname.toLowerCase());
		allUsers.remove("@" + teamname);
		try {
			write(allUsers);
			return true;
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Failed to delete team {0}!", teamname), t);
		}
		return false;
	}
}
