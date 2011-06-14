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
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;

public class FileLoginService extends FileSettings implements ILoginService {

	private final Logger logger = LoggerFactory.getLogger(FileLoginService.class);

	public FileLoginService(File realmFile) {
		super(realmFile.getAbsolutePath());
	}

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
			String md5 = StringUtils.MD5_TYPE + StringUtils.getMD5(new String(password));
			if (user.password.equalsIgnoreCase(md5)) {
				returnedUser = user;
			}
		}
		if (user.password.equals(new String(password))) {
			returnedUser = user;
		}
		return returnedUser;
	}

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
				}
				break;
			default:
				model.addRepository(role);
			}
		}
		return model;
	}

	@Override
	public boolean updateUserModel(UserModel model) {
		return updateUserModel(model.username, model);
	}

	@Override
	public boolean updateUserModel(String username, UserModel model) {
		try {
			Properties allUsers = read();
			ArrayList<String> roles = new ArrayList<String>(model.repositories);

			// Permissions
			if (model.canAdmin) {
				roles.add(Constants.ADMIN_ROLE);
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

			write(allUsers);
			return true;
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Failed to update user model {0}!", model.username),
					t);
		}
		return false;
	}

	@Override
	public boolean deleteUserModel(UserModel model) {
		return deleteUser(model.username);
	}

	@Override
	public boolean deleteUser(String username) {
		try {
			// Read realm file
			Properties allUsers = read();
			allUsers.remove(username);
			write(allUsers);
			return true;
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Failed to delete user {0}!", username), t);
		}
		return false;
	}

	@Override
	public List<String> getAllUsernames() {
		Properties allUsers = read();
		List<String> list = new ArrayList<String>(allUsers.stringPropertyNames());
		return list;
	}

	@Override
	public List<String> getUsernamesForRole(String role) {
		List<String> list = new ArrayList<String>();
		try {
			Properties allUsers = read();
			for (String username : allUsers.stringPropertyNames()) {
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

	@Override
	public boolean setUsernamesForRole(String role, List<String> usernames) {
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
				List<String> revisedRoles = new ArrayList<String>();
				// skip first value (password)
				for (int i = 1; i < values.length; i++) {
					String value = values[i];
					if (!value.equalsIgnoreCase(role)) {
						revisedRoles.add(value);
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

	@Override
	public boolean renameRole(String oldRole, String newRole) {
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
						needsRenameRole.remove(username);
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
				List<String> revisedRoles = new ArrayList<String>();
				revisedRoles.add(newRole);
				// skip first value (password)
				for (int i = 1; i < values.length; i++) {
					String value = values[i];
					if (!value.equalsIgnoreCase(oldRole)) {
						revisedRoles.add(value);
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

	@Override
	public boolean deleteRole(String role) {
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
						needsDeleteRole.remove(username);
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
				List<String> revisedRoles = new ArrayList<String>();
				// skip first value (password)
				for (int i = 1; i < values.length; i++) {
					String value = values[i];
					if (!value.equalsIgnoreCase(role)) {
						revisedRoles.add(value);
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

	private void write(Properties properties) throws IOException {
		// Update realm file
		File realmFileCopy = new File(propertiesFile.getAbsolutePath() + ".tmp");
		FileWriter writer = new FileWriter(realmFileCopy);
		properties
				.store(writer,
						"# Gitblit realm file format: username=password,\\#permission,repository1,repository2...");
		writer.close();
		if (realmFileCopy.exists() && realmFileCopy.length() > 0) {
			if (propertiesFile.delete()) {
				if (!realmFileCopy.renameTo(propertiesFile)) {
					throw new IOException(MessageFormat.format("Failed to rename {0} to {1}!",
							realmFileCopy.getAbsolutePath(), propertiesFile.getAbsolutePath()));
				}
			} else {
				throw new IOException(MessageFormat.format("Failed to delete (0)!",
						propertiesFile.getAbsolutePath()));
			}
		} else {
			throw new IOException(MessageFormat.format("Failed to save {0}!",
					realmFileCopy.getAbsolutePath()));
		}
	}
}
