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

import com.gitblit.models.UserModel;
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

	private final File realmFile;

	private final Logger logger = LoggerFactory.getLogger(ConfigUserService.class);

	private final Map<String, UserModel> users = new ConcurrentHashMap<String, UserModel>();

	private final Map<String, UserModel> cookies = new ConcurrentHashMap<String, UserModel>();

	private final String userSection = "user";

	private final String passwordField = "password";

	private final String repositoryField = "repository";

	private final String roleField = "role";

	private volatile long lastModified;

	public ConfigUserService(File realmFile) {
		this.realmFile = realmFile;
	}

	/**
	 * Setup the user service.
	 * 
	 * @param settings
	 * @since 0.6.1
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
		read();
		UserModel storedModel = users.get(model.username.toLowerCase());
		String cookie = StringUtils.getSHA1(model.username + storedModel.password);
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
	 * Retrieve the user object for the specified username.
	 * 
	 * @param username
	 * @return a user object or null
	 */
	@Override
	public UserModel getUserModel(String username) {
		read();
		UserModel model = users.get(username.toLowerCase());
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
			read();
			users.remove(username.toLowerCase());
			users.put(model.username.toLowerCase(), model);
			write();
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
			read();
			users.remove(username.toLowerCase());
			write();
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
		read();
		List<String> list = new ArrayList<String>(users.keySet());
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
				if (model.hasRepository(role)) {
					list.add(model.username);
				}
			}
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Failed to get usernames for role {0}!", role), t);
		}
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
					user.addRepository(role);
				} else {
					// remove role from user
					user.removeRepository(role);
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
				if (model.hasRepository(oldRole)) {
					model.removeRepository(oldRole);
					model.addRepository(newRole);
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
				user.removeRepository(role);
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
		for (UserModel model : users.values()) {
			config.setString(userSection, model.username, passwordField, model.password);

			// user roles
			List<String> roles = new ArrayList<String>();
			if (model.canAdmin) {
				roles.add(Constants.ADMIN_ROLE);
			}
			if (model.excludeFromFederation) {
				roles.add(Constants.NOT_FEDERATED_ROLE);
			}
			config.setStringList(userSection, model.username, roleField, roles);

			// repository memberships
			config.setStringList(userSection, model.username, repositoryField,
					new ArrayList<String>(model.repositories));
		}
		config.save();

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
		if (realmFile.exists() && (realmFile.lastModified() > lastModified)) {
			lastModified = realmFile.lastModified();
			users.clear();
			cookies.clear();
			try {
				StoredConfig config = new FileBasedConfig(realmFile, FS.detect());
				config.load();
				Set<String> usernames = config.getSubsections(userSection);
				for (String username : usernames) {
					UserModel user = new UserModel(username);
					user.password = config.getString(userSection, username, passwordField);

					// user roles
					Set<String> roles = new HashSet<String>(Arrays.asList(config.getStringList(
							userSection, username, roleField)));
					user.canAdmin = roles.contains(Constants.ADMIN_ROLE);
					user.excludeFromFederation = roles.contains(Constants.NOT_FEDERATED_ROLE);

					// repository memberships
					Set<String> repositories = new HashSet<String>(Arrays.asList(config
							.getStringList(userSection, username, repositoryField)));
					for (String repository : repositories) {
						user.addRepository(repository);
					}

					// update cache
					users.put(username, user);
					cookies.put(StringUtils.getSHA1(username + user.password), user);
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
