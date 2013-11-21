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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.GitBlitException;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.models.GitClientApplication;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.RepositoryUrl;
import com.gitblit.models.ServerSettings;
import com.gitblit.models.SettingModel;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.HttpUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.JsonUtils;
import com.gitblit.utils.ObjectCache;
import com.gitblit.utils.StringUtils;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

public class GitblitManager implements IGitblitManager {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final ObjectCache<Collection<GitClientApplication>> clientApplications = new ObjectCache<Collection<GitClientApplication>>();

	private final IStoredSettings settings;

	private final IRuntimeManager runtimeManager;

	private final IUserManager userManager;

	private final IRepositoryManager repositoryManager;

	public GitblitManager(
			IRuntimeManager runtimeManager,
			IUserManager userManager,
			IRepositoryManager repositoryManager) {

		this.settings = runtimeManager.getSettings();
		this.runtimeManager = runtimeManager;
		this.userManager = userManager;
		this.repositoryManager = repositoryManager;
	}

	@Override
	public GitblitManager start() {
		loadSettingModels(runtimeManager.getSettingsModel());
		return this;
	}

	@Override
	public GitblitManager stop() {
		return this;
	}

	/**
	 * Parse the properties file and aggregate all the comments by the setting
	 * key. A setting model tracks the current value, the default value, the
	 * description of the setting and and directives about the setting.
	 *
	 * @return Map<String, SettingModel>
	 */
	private void loadSettingModels(ServerSettings settingsModel) {
		// this entire "supports" concept will go away with user service refactoring
		UserModel externalUser = new UserModel(Constants.EXTERNAL_ACCOUNT);
		externalUser.password = Constants.EXTERNAL_ACCOUNT;
		settingsModel.supportsCredentialChanges = userManager.supportsCredentialChanges(externalUser);
		settingsModel.supportsDisplayNameChanges = userManager.supportsDisplayNameChanges(externalUser);
		settingsModel.supportsEmailAddressChanges = userManager.supportsEmailAddressChanges(externalUser);
		settingsModel.supportsTeamMembershipChanges = userManager.supportsTeamMembershipChanges(externalUser);
		try {
			// Read bundled Gitblit properties to extract setting descriptions.
			// This copy is pristine and only used for populating the setting
			// models map.
			InputStream is = getClass().getResourceAsStream("/reference.properties");
			BufferedReader propertiesReader = new BufferedReader(new InputStreamReader(is));
			StringBuilder description = new StringBuilder();
			SettingModel setting = new SettingModel();
			String line = null;
			while ((line = propertiesReader.readLine()) != null) {
				if (line.length() == 0) {
					description.setLength(0);
					setting = new SettingModel();
				} else {
					if (line.charAt(0) == '#') {
						if (line.length() > 1) {
							String text = line.substring(1).trim();
							if (SettingModel.CASE_SENSITIVE.equals(text)) {
								setting.caseSensitive = true;
							} else if (SettingModel.RESTART_REQUIRED.equals(text)) {
								setting.restartRequired = true;
							} else if (SettingModel.SPACE_DELIMITED.equals(text)) {
								setting.spaceDelimited = true;
							} else if (text.startsWith(SettingModel.SINCE)) {
								try {
									setting.since = text.split(" ")[1];
								} catch (Exception e) {
									setting.since = text;
								}
							} else {
								description.append(text);
								description.append('\n');
							}
						}
					} else {
						String[] kvp = line.split("=", 2);
						String key = kvp[0].trim();
						setting.name = key;
						setting.defaultValue = kvp[1].trim();
						setting.currentValue = setting.defaultValue;
						setting.description = description.toString().trim();
						settingsModel.add(setting);
						description.setLength(0);
						setting = new SettingModel();
					}
				}
			}
			propertiesReader.close();
		} catch (NullPointerException e) {
			logger.error("Failed to find resource copy of gitblit.properties");
		} catch (IOException e) {
			logger.error("Failed to load resource copy of gitblit.properties");
		}
	}

	/**
	 * Returns a list of repository URLs and the user access permission.
	 *
	 * @param request
	 * @param user
	 * @param repository
	 * @return a list of repository urls
	 */
	@Override
	public List<RepositoryUrl> getRepositoryUrls(HttpServletRequest request, UserModel user, RepositoryModel repository) {
		if (user == null) {
			user = UserModel.ANONYMOUS;
		}
		String username = StringUtils.encodeUsername(UserModel.ANONYMOUS.equals(user) ? "" : user.username);

		List<RepositoryUrl> list = new ArrayList<RepositoryUrl>();
		// http/https url
		if (settings.getBoolean(Keys.git.enableGitServlet, true)) {
			AccessPermission permission = user.getRepositoryPermission(repository).permission;
			if (permission.exceeds(AccessPermission.NONE)) {
				list.add(new RepositoryUrl(getRepositoryUrl(request, username, repository), permission));
			}
		}

		// git daemon url
		String gitDaemonUrl = getGitDaemonUrl(request, user, repository);
		if (!StringUtils.isEmpty(gitDaemonUrl)) {
			AccessPermission permission = getGitDaemonAccessPermission(user, repository);
			if (permission.exceeds(AccessPermission.NONE)) {
				list.add(new RepositoryUrl(gitDaemonUrl, permission));
			}
		}

		// add all other urls
		// {0} = repository
		// {1} = username
		for (String url : settings.getStrings(Keys.web.otherUrls)) {
			if (url.contains("{1}")) {
				// external url requires username, only add url IF we have one
				if (!StringUtils.isEmpty(username)) {
					list.add(new RepositoryUrl(MessageFormat.format(url, repository.name, username), null));
				}
			} else {
				// external url does not require username
				list.add(new RepositoryUrl(MessageFormat.format(url, repository.name), null));
			}
		}
		return list;
	}

	protected String getRepositoryUrl(HttpServletRequest request, String username, RepositoryModel repository) {
		StringBuilder sb = new StringBuilder();
		sb.append(HttpUtils.getGitblitURL(request));
		sb.append(Constants.GIT_PATH);
		sb.append(repository.name);

		// inject username into repository url if authentication is required
		if (repository.accessRestriction.exceeds(AccessRestrictionType.NONE)
				&& !StringUtils.isEmpty(username)) {
			sb.insert(sb.indexOf("://") + 3, username + "@");
		}
		return sb.toString();
	}

	protected String getGitDaemonUrl(HttpServletRequest request, UserModel user, RepositoryModel repository) {
//		if (gitDaemon != null) {
//			String bindInterface = settings.getString(Keys.git.daemonBindInterface, "localhost");
//			if (bindInterface.equals("localhost")
//					&& (!request.getServerName().equals("localhost") && !request.getServerName().equals("127.0.0.1"))) {
//				// git daemon is bound to localhost and the request is from elsewhere
//				return null;
//			}
//			if (user.canClone(repository)) {
//				String servername = request.getServerName();
//				String url = gitDaemon.formatUrl(servername, repository.name);
//				return url;
//			}
//		}
		return null;
	}

	protected AccessPermission getGitDaemonAccessPermission(UserModel user, RepositoryModel repository) {
//		if (gitDaemon != null && user.canClone(repository)) {
//			AccessPermission gitDaemonPermission = user.getRepositoryPermission(repository).permission;
//			if (gitDaemonPermission.atLeast(AccessPermission.CLONE)) {
//				if (repository.accessRestriction.atLeast(AccessRestrictionType.CLONE)) {
//					// can not authenticate clone via anonymous git protocol
//					gitDaemonPermission = AccessPermission.NONE;
//				} else if (repository.accessRestriction.atLeast(AccessRestrictionType.PUSH)) {
//					// can not authenticate push via anonymous git protocol
//					gitDaemonPermission = AccessPermission.CLONE;
//				} else {
//					// normal user permission
//				}
//			}
//			return gitDaemonPermission;
//		}
		return AccessPermission.NONE;
	}

	/**
	 * Returns the list of custom client applications to be used for the
	 * repository url panel;
	 *
	 * @return a collection of client applications
	 */
	@Override
	public Collection<GitClientApplication> getClientApplications() {
		// prefer user definitions, if they exist
		File userDefs = new File(runtimeManager.getBaseFolder(), "clientapps.json");
		if (userDefs.exists()) {
			Date lastModified = new Date(userDefs.lastModified());
			if (clientApplications.hasCurrent("user", lastModified)) {
				return clientApplications.getObject("user");
			} else {
				// (re)load user definitions
				try {
					InputStream is = new FileInputStream(userDefs);
					Collection<GitClientApplication> clients = readClientApplications(is);
					is.close();
					if (clients != null) {
						clientApplications.updateObject("user", lastModified, clients);
						return clients;
					}
				} catch (IOException e) {
					logger.error("Failed to deserialize " + userDefs.getAbsolutePath(), e);
				}
			}
		}

		// no user definitions, use system definitions
		if (!clientApplications.hasCurrent("system", new Date(0))) {
			try {
				InputStream is = getClass().getResourceAsStream("/clientapps.json");
				Collection<GitClientApplication> clients = readClientApplications(is);
				is.close();
				if (clients != null) {
					clientApplications.updateObject("system", new Date(0), clients);
				}
			} catch (IOException e) {
				logger.error("Failed to deserialize clientapps.json resource!", e);
			}
		}

		return clientApplications.getObject("system");
	}

	private Collection<GitClientApplication> readClientApplications(InputStream is) {
		try {
			Type type = new TypeToken<Collection<GitClientApplication>>() {
			}.getType();
			InputStreamReader reader = new InputStreamReader(is);
			Gson gson = JsonUtils.gson();
			Collection<GitClientApplication> links = gson.fromJson(reader, type);
			return links;
		} catch (JsonIOException e) {
			logger.error("Error deserializing client applications!", e);
		} catch (JsonSyntaxException e) {
			logger.error("Error deserializing client applications!", e);
		}
		return null;
	}

	/**
	 * Creates a personal fork of the specified repository. The clone is view
	 * restricted by default and the owner of the source repository is given
	 * access to the clone.
	 *
	 * @param repository
	 * @param user
	 * @return the repository model of the fork, if successful
	 * @throws GitBlitException
	 */
	@Override
	public RepositoryModel fork(RepositoryModel repository, UserModel user) throws GitBlitException {
		String cloneName = MessageFormat.format("{0}/{1}.git", user.getPersonalPath(), StringUtils.stripDotGit(StringUtils.getLastPathElement(repository.name)));
		String fromUrl = MessageFormat.format("file://{0}/{1}", repositoryManager.getRepositoriesFolder().getAbsolutePath(), repository.name);

		// clone the repository
		try {
			JGitUtils.cloneRepository(repositoryManager.getRepositoriesFolder(), cloneName, fromUrl, true, null);
		} catch (Exception e) {
			throw new GitBlitException(e);
		}

		// create a Gitblit repository model for the clone
		RepositoryModel cloneModel = repository.cloneAs(cloneName);
		// owner has REWIND/RW+ permissions
		cloneModel.addOwner(user.username);
		repositoryManager.updateRepositoryModel(cloneName, cloneModel, false);

		// add the owner of the source repository to the clone's access list
		if (!ArrayUtils.isEmpty(repository.owners)) {
			for (String owner : repository.owners) {
				UserModel originOwner = userManager.getUserModel(owner);
				if (originOwner != null) {
					originOwner.setRepositoryPermission(cloneName, AccessPermission.CLONE);
					updateUserModel(originOwner.username, originOwner, false);
				}
			}
		}

		// grant origin's user list clone permission to fork
		List<String> users = repositoryManager.getRepositoryUsers(repository);
		List<UserModel> cloneUsers = new ArrayList<UserModel>();
		for (String name : users) {
			if (!name.equalsIgnoreCase(user.username)) {
				UserModel cloneUser = userManager.getUserModel(name);
				if (cloneUser.canClone(repository)) {
					// origin user can clone origin, grant clone access to fork
					cloneUser.setRepositoryPermission(cloneName, AccessPermission.CLONE);
				}
				cloneUsers.add(cloneUser);
			}
		}
		userManager.updateUserModels(cloneUsers);

		// grant origin's team list clone permission to fork
		List<String> teams = repositoryManager.getRepositoryTeams(repository);
		List<TeamModel> cloneTeams = new ArrayList<TeamModel>();
		for (String name : teams) {
			TeamModel cloneTeam = userManager.getTeamModel(name);
			if (cloneTeam.canClone(repository)) {
				// origin team can clone origin, grant clone access to fork
				cloneTeam.setRepositoryPermission(cloneName, AccessPermission.CLONE);
			}
			cloneTeams.add(cloneTeam);
		}
		userManager.updateTeamModels(cloneTeams);

		// add this clone to the cached model
		repositoryManager.addToCachedRepositoryList(cloneModel);
		return cloneModel;
	}

	/**
	 * Adds/updates a complete user object keyed by username. This method allows
	 * for renaming a user.
	 *
	 * @see IUserService.updateUserModel(String, UserModel)
	 * @param username
	 * @param user
	 * @param isCreate
	 * @throws GitBlitException
	 */
	@Override
	public void updateUserModel(String username, UserModel user, boolean isCreate)
			throws GitBlitException {
		if (!username.equalsIgnoreCase(user.username)) {
			if (userManager.getUserModel(user.username) != null) {
				throw new GitBlitException(MessageFormat.format(
						"Failed to rename ''{0}'' because ''{1}'' already exists.", username,
						user.username));
			}

			// rename repositories and owner fields for all repositories
			for (RepositoryModel model : repositoryManager.getRepositoryModels(user)) {
				if (model.isUsersPersonalRepository(username)) {
					// personal repository
					model.addOwner(user.username);
					String oldRepositoryName = model.name;
					model.name = user.getPersonalPath() + model.name.substring(model.projectPath.length());
					model.projectPath = user.getPersonalPath();
					repositoryManager.updateRepositoryModel(oldRepositoryName, model, false);
				} else if (model.isOwner(username)) {
					// common/shared repo
					model.addOwner(user.username);
					repositoryManager.updateRepositoryModel(model.name, model, false);
				}
			}
		}
		if (!userManager.updateUserModel(username, user)) {
			throw new GitBlitException(isCreate ? "Failed to add user!" : "Failed to update user!");
		}
	}

	/**
	 * Updates the TeamModel object for the specified name.
	 *
	 * @param teamname
	 * @param team
	 * @param isCreate
	 */
	@Override
	public void updateTeamModel(String teamname, TeamModel team, boolean isCreate)
			throws GitBlitException {
		if (!teamname.equalsIgnoreCase(team.name)) {
			if (userManager.getTeamModel(team.name) != null) {
				throw new GitBlitException(MessageFormat.format(
						"Failed to rename ''{0}'' because ''{1}'' already exists.", teamname,
						team.name));
			}
		}
		if (!userManager.updateTeamModel(teamname, team)) {
			throw new GitBlitException(isCreate ? "Failed to add team!" : "Failed to update team!");
		}
	}
}
