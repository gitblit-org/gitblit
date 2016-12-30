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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.AccountType;
import com.gitblit.Constants.Role;
import com.gitblit.Constants.Transport;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.models.UserRepositoryPreferences;
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

	private static final String STARRED = "starred";

	private static final String LOCALE = "locale";

	private static final String EMAILONMYTICKETCHANGES = "emailMeOnMyTicketChanges";

	private static final String TRANSPORT = "transport";

	private static final String ACCOUNTTYPE = "accountType";

	private static final String DISABLED = "disabled";

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
	 * @param runtimeManager
	 * @since 1.4.0
	 */
	@Override
	public void setup(IRuntimeManager runtimeManager) {
	}

	/**
	 * Returns the cookie value for the specified user.
	 *
	 * @param model
	 * @return cookie value
	 */
	@Override
	public synchronized String getCookie(UserModel model) {
		if (!StringUtils.isEmpty(model.cookie)) {
			return model.cookie;
		}
		UserModel storedModel = getUserModel(model.username);
		if (storedModel == null) {
			return null;
		}
		return storedModel.cookie;
	}

	/**
	 * Gets the user object for the specified cookie.
	 *
	 * @param cookie
	 * @return a user object or null
	 */
	@Override
	public synchronized UserModel getUserModel(char[] cookie) {
		String hash = new String(cookie);
		if (StringUtils.isEmpty(hash)) {
			return null;
		}
		read();
		UserModel model = null;
		if (cookies.containsKey(hash)) {
			model = cookies.get(hash);
		}

		if (model != null) {
			// clone the model, otherwise all changes to this object are
			// live and unpersisted
			model = DeepCopier.copy(model);
		}
		return model;
	}

	/**
	 * Retrieve the user object for the specified username.
	 *
	 * @param username
	 * @return a user object or null
	 */
	@Override
	public synchronized UserModel getUserModel(String username) {
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
	public synchronized boolean updateUserModel(UserModel model) {
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
	public synchronized boolean updateUserModels(Collection<UserModel> models) {
		try {
			read();
			for (UserModel model : models) {
				UserModel originalUser = users.remove(model.username.toLowerCase());
				users.put(model.username.toLowerCase(), model);
				// null check on "final" teams because JSON-sourced UserModel
				// can have a null teams object
				if (model.teams != null) {
					Set<TeamModel> userTeams = new HashSet<TeamModel>();
					for (TeamModel team : model.teams) {
						TeamModel t = teams.get(team.name.toLowerCase());
						if (t == null) {
							// new team
							t = team;
							teams.put(team.name.toLowerCase(), t);
						}
						// do not clobber existing team definition
						// maybe because this is a federated user
						t.addUser(model.username);
						userTeams.add(t);
					}
					// replace Team-Models in users by new ones.
					model.teams.clear();
					model.teams.addAll(userTeams);

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
	public synchronized boolean updateUserModel(String username, UserModel model) {
		UserModel originalUser = null;
		try {
			if (!model.isLocalAccount()) {
				// do not persist password
				model.password = Constants.EXTERNAL_ACCOUNT;
			}
			read();
			originalUser = users.remove(username.toLowerCase());
			if (originalUser != null) {
				cookies.remove(originalUser.cookie);
			}
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
	public synchronized boolean deleteUserModel(UserModel model) {
		return deleteUser(model.username);
	}

	/**
	 * Delete the user object with the specified username
	 *
	 * @param username
	 * @return true if successful
	 */
	@Override
	public synchronized boolean deleteUser(String username) {
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
	public synchronized List<String> getAllTeamNames() {
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
	public synchronized List<TeamModel> getAllTeams() {
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
	public synchronized List<String> getTeamNamesForRepositoryRole(String role) {
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
	 * Retrieve the team object for the specified team name.
	 *
	 * @param teamname
	 * @return a team object or null
	 * @since 0.8.0
	 */
	@Override
	public synchronized TeamModel getTeamModel(String teamname) {
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
	public synchronized boolean updateTeamModel(TeamModel model) {
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
	public synchronized boolean updateTeamModels(Collection<TeamModel> models) {
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
	public synchronized boolean updateTeamModel(String teamname, TeamModel model) {
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
	public synchronized boolean deleteTeamModel(TeamModel model) {
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
	public synchronized boolean deleteTeam(String teamname) {
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
	public synchronized List<String> getAllUsernames() {
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
	public synchronized List<UserModel> getAllUsers() {
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
	public synchronized List<String> getUsernamesForRepositoryRole(String role) {
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
	 * Renames a repository role.
	 *
	 * @param oldRole
	 * @param newRole
	 * @return true if successful
	 */
	@Override
	public synchronized boolean renameRepositoryRole(String oldRole, String newRole) {
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
	public synchronized boolean deleteRepositoryRole(String role) {
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
			if (model.accountType != null) {
				config.setString(USER, model.username, ACCOUNTTYPE, model.accountType.name());
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
			if (model.disabled) {
				config.setBoolean(USER, model.username, DISABLED, true);
			}
			if (model.getPreferences() != null) {
				Locale locale = model.getPreferences().getLocale();
				if (locale != null) {
					String val;
					if (StringUtils.isEmpty(locale.getCountry())) {
						val = locale.getLanguage();
					} else {
						val = locale.getLanguage() + "_" + locale.getCountry();
					}
					config.setString(USER, model.username, LOCALE, val);
				}

				config.setBoolean(USER, model.username, EMAILONMYTICKETCHANGES, model.getPreferences().isEmailMeOnMyTicketChanges());

				if (model.getPreferences().getTransport() != null) {
					config.setString(USER, model.username, TRANSPORT, model.getPreferences().getTransport().name());
				}
			}

			// user roles
			List<String> roles = new ArrayList<String>();
			if (model.canAdmin) {
				roles.add(Role.ADMIN.getRole());
			}
			if (model.canFork) {
				roles.add(Role.FORK.getRole());
			}
			if (model.canCreate) {
				roles.add(Role.CREATE.getRole());
			}
			if (model.excludeFromFederation) {
				roles.add(Role.NOT_FEDERATED.getRole());
			}
			if (roles.size() == 0) {
				// we do this to ensure that user record with no password
				// is written.  otherwise, StoredConfig optimizes that account
				// away. :(
				roles.add(Role.NONE.getRole());
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

			// user preferences
			if (model.getPreferences() != null) {
				List<String> starred =  model.getPreferences().getStarredRepositories();
				if (starred.size() > 0) {
					config.setStringList(USER, model.username, STARRED, starred);
				}
			}
		}

		// write teams
		for (TeamModel model : teams.values()) {
			// team roles
			List<String> roles = new ArrayList<String>();
			if (model.canAdmin) {
				roles.add(Role.ADMIN.getRole());
			}
			if (model.canFork) {
				roles.add(Role.FORK.getRole());
			}
			if (model.canCreate) {
				roles.add(Role.CREATE.getRole());
			}
			if (roles.size() == 0) {
				// we do this to ensure that team record is written.
				// Otherwise, StoredConfig might optimizes that record away.
				roles.add(Role.NONE.getRole());
			}
			config.setStringList(TEAM, model.name, ROLE, roles);
			if (model.accountType != null) {
				config.setString(TEAM, model.name, ACCOUNTTYPE, model.accountType.name());
			}

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
					user.accountType = AccountType.fromString(config.getString(USER, username, ACCOUNTTYPE));
					user.disabled = config.getBoolean(USER, username, DISABLED, false);
					user.organizationalUnit = config.getString(USER, username, ORGANIZATIONALUNIT);
					user.organization = config.getString(USER, username, ORGANIZATION);
					user.locality = config.getString(USER, username, LOCALITY);
					user.stateProvince = config.getString(USER, username, STATEPROVINCE);
					user.countryCode = config.getString(USER, username, COUNTRYCODE);
					user.cookie = config.getString(USER, username, COOKIE);
					if (StringUtils.isEmpty(user.cookie) && !StringUtils.isEmpty(user.password)) {
						user.cookie = user.createCookie();
					}

					// preferences
					user.getPreferences().setLocale(config.getString(USER, username, LOCALE));
					user.getPreferences().setEmailMeOnMyTicketChanges(config.getBoolean(USER, username, EMAILONMYTICKETCHANGES, true));
					user.getPreferences().setTransport(Transport.fromString(config.getString(USER, username, TRANSPORT)));

					// user roles
					Set<String> roles = new HashSet<String>(Arrays.asList(config.getStringList(
							USER, username, ROLE)));
					user.canAdmin = roles.contains(Role.ADMIN.getRole());
					user.canFork = roles.contains(Role.FORK.getRole());
					user.canCreate = roles.contains(Role.CREATE.getRole());
					user.excludeFromFederation = roles.contains(Role.NOT_FEDERATED.getRole());

					// repository memberships
					if (!user.canAdmin) {
						// non-admin, read permissions
						Set<String> repositories = new HashSet<String>(Arrays.asList(config
								.getStringList(USER, username, REPOSITORY)));
						for (String repository : repositories) {
							user.addRepositoryPermission(repository);
						}
					}

					// starred repositories
					Set<String> starred = new HashSet<String>(Arrays.asList(config
							.getStringList(USER, username, STARRED)));
					for (String repository : starred) {
						UserRepositoryPreferences prefs = user.getPreferences().getRepositoryPreferences(repository);
						prefs.starred = true;
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
					team.canAdmin = roles.contains(Role.ADMIN.getRole());
					team.canFork = roles.contains(Role.FORK.getRole());
					team.canCreate = roles.contains(Role.CREATE.getRole());
					team.accountType = AccountType.fromString(config.getString(TEAM, teamname, ACCOUNTTYPE));

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
