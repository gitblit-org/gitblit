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
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RefSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.fortsoft.pf4j.PluginState;
import ro.fortsoft.pf4j.PluginWrapper;
import ro.fortsoft.pf4j.Version;

import com.gitblit.Constants;
import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.FederationRequest;
import com.gitblit.Constants.FederationToken;
import com.gitblit.Constants.Role;
import com.gitblit.GitBlitException;
import com.gitblit.IStoredSettings;
import com.gitblit.extensions.RepositoryLifeCycleListener;
import com.gitblit.models.FederationModel;
import com.gitblit.models.FederationProposal;
import com.gitblit.models.FederationSet;
import com.gitblit.models.FilestoreModel;
import com.gitblit.models.ForkModel;
import com.gitblit.models.GitClientApplication;
import com.gitblit.models.Mailing;
import com.gitblit.models.Metric;
import com.gitblit.models.PluginRegistry.InstallState;
import com.gitblit.models.PluginRegistry.PluginRegistration;
import com.gitblit.models.PluginRegistry.PluginRelease;
import com.gitblit.models.ProjectModel;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.SearchResult;
import com.gitblit.models.ServerSettings;
import com.gitblit.models.ServerStatus;
import com.gitblit.models.SettingModel;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.tickets.ITicketService;
import com.gitblit.transport.ssh.IPublicKeyManager;
import com.gitblit.transport.ssh.SshKey;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.JsonUtils;
import com.gitblit.utils.ObjectCache;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.XssFilter;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.Singleton;

/**
 * GitblitManager is an aggregate interface delegate.  It implements all the manager
 * interfaces and delegates most methods calls to the proper manager implementation.
 * It's primary purpose is to provide complete management control to the git
 * upload and receive pack functions.
 *
 * GitblitManager also implements several integration methods when it is required
 * to manipulate several manages for one operation.
 *
 * @author James Moger
 *
 */
@Singleton
public class GitblitManager implements IGitblit {

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	protected final ObjectCache<Collection<GitClientApplication>> clientApplications = new ObjectCache<Collection<GitClientApplication>>();

	protected final Provider<IPublicKeyManager> publicKeyManagerProvider;

	protected final Provider<ITicketService> ticketServiceProvider;

	protected final IStoredSettings settings;

	protected final IRuntimeManager runtimeManager;

	protected final IPluginManager pluginManager;

	protected final INotificationManager notificationManager;

	protected final IUserManager userManager;

	protected final IAuthenticationManager authenticationManager;

	protected final IRepositoryManager repositoryManager;

	protected final IProjectManager projectManager;

	protected final IFederationManager federationManager;

	protected final IFilestoreManager filestoreManager;

	@Inject
	public GitblitManager(
			Provider<IPublicKeyManager> publicKeyManagerProvider,
			Provider<ITicketService> ticketServiceProvider,
			IRuntimeManager runtimeManager,
			IPluginManager pluginManager,
			INotificationManager notificationManager,
			IUserManager userManager,
			IAuthenticationManager authenticationManager,
			IRepositoryManager repositoryManager,
			IProjectManager projectManager,
			IFederationManager federationManager,
			IFilestoreManager filestoreManager) {

		this.publicKeyManagerProvider = publicKeyManagerProvider;
		this.ticketServiceProvider = ticketServiceProvider;

		this.settings = runtimeManager.getSettings();
		this.runtimeManager = runtimeManager;
		this.pluginManager = pluginManager;
		this.notificationManager = notificationManager;
		this.userManager = userManager;
		this.authenticationManager = authenticationManager;
		this.repositoryManager = repositoryManager;
		this.projectManager = projectManager;
		this.federationManager = federationManager;
		this.filestoreManager = filestoreManager;
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

	/*
	 * IGITBLIT
	 */

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
			Repository canonical = getRepository(repository.name);
			File folder = new File(repositoryManager.getRepositoriesFolder(), cloneName);
			CloneCommand clone = new CloneCommand();
			clone.setBare(true);

			// fetch branches with exclusions
			Collection<Ref> branches = canonical.getRefDatabase().getRefs(Constants.R_HEADS).values();
			List<String> branchesToClone = new ArrayList<String>();
			for (Ref branch : branches) {
				String name = branch.getName();
				if (name.startsWith(Constants.R_TICKET)) {
					// exclude ticket branches
					continue;
				}
				branchesToClone.add(name);
			}
			clone.setBranchesToClone(branchesToClone);
			clone.setURI(fromUrl);
			clone.setDirectory(folder);
			Git git = clone.call();

			// fetch tags
			FetchCommand fetch  = git.fetch();
			fetch.setRefSpecs(new RefSpec("+refs/tags/*:refs/tags/*"));
			fetch.call();

			git.getRepository().close();
		} catch (Exception e) {
			throw new GitBlitException(e);
		}

		// create a Gitblit repository model for the clone
		RepositoryModel cloneModel = repository.cloneAs(cloneName);
		// owner has REWIND/RW+ permissions
		cloneModel.addOwner(user.username);

		// ensure initial access restriction of the fork
		// is not lower than the source repository  (issue-495/ticket-167)
		if (repository.accessRestriction.exceeds(cloneModel.accessRestriction)) {
			cloneModel.accessRestriction = repository.accessRestriction;
		}

		repositoryManager.updateRepositoryModel(cloneName, cloneModel, false);

		// add the owner of the source repository to the clone's access list
		if (!ArrayUtils.isEmpty(repository.owners)) {
			for (String owner : repository.owners) {
				UserModel originOwner = userManager.getUserModel(owner);
				if (originOwner != null && !originOwner.canClone(cloneModel)) {
					// origin owner can't yet clone fork, grant explicit clone access
					originOwner.setRepositoryPermission(cloneName, AccessPermission.CLONE);
					reviseUser(originOwner.username, originOwner);
				}
			}
		}

		// grant origin's user list clone permission to fork
		List<String> users = repositoryManager.getRepositoryUsers(repository);
		List<UserModel> cloneUsers = new ArrayList<UserModel>();
		for (String name : users) {
			if (!name.equalsIgnoreCase(user.username)) {
				UserModel cloneUser = userManager.getUserModel(name);
				if (cloneUser.canClone(repository) && !cloneUser.canClone(cloneModel)) {
					// origin user can't yet clone fork, grant explicit clone access
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
			if (cloneTeam.canClone(repository) && !cloneTeam.canClone(cloneModel)) {
				// origin team can't yet clone fork, grant explicit clone access
				cloneTeam.setRepositoryPermission(cloneName, AccessPermission.CLONE);
			}
			cloneTeams.add(cloneTeam);
		}
		userManager.updateTeamModels(cloneTeams);

		// add this clone to the cached model
		repositoryManager.addToCachedRepositoryList(cloneModel);

		if (pluginManager != null) {
			for (RepositoryLifeCycleListener listener : pluginManager.getExtensions(RepositoryLifeCycleListener.class)) {
				try {
					listener.onFork(repository, cloneModel);
				} catch (Throwable t) {
					logger.error(String.format("failed to call plugin onFork %s", repository.name), t);
				}
			}
		}
		return cloneModel;
	}

	/**
	 * Adds a TeamModel object.
	 *
	 * @param team
	 */
	@Override
	public void addTeam(TeamModel team) throws GitBlitException {
		if (!userManager.updateTeamModel(team)) {
			throw new GitBlitException("Failed to add team!");
		}
	}

	/**
	 * Updates the TeamModel object for the specified name.
	 *
	 * @param teamname
	 * @param team
	 */
	@Override
	public void reviseTeam(String teamname, TeamModel team) throws GitBlitException {
		if (!teamname.equalsIgnoreCase(team.name)) {
			if (userManager.getTeamModel(team.name) != null) {
				throw new GitBlitException(MessageFormat.format(
						"Failed to rename ''{0}'' because ''{1}'' already exists.", teamname,
						team.name));
			}
		}
		if (!userManager.updateTeamModel(teamname, team)) {
			throw new GitBlitException("Failed to update team!");
		}
	}

	/**
	 * Adds a user object.
	 *
	 * @param user
	 * @throws GitBlitException
	 */
	@Override
	public void addUser(UserModel user) throws GitBlitException {
		if (!userManager.updateUserModel(user)) {
			throw new GitBlitException("Failed to add user!");
		}
	}

	/**
	 * Updates a user object keyed by username. This method allows
	 * for renaming a user.
	 *
	 * @param username
	 * @param user
	 * @throws GitBlitException
	 */
	@Override
	public void reviseUser(String username, UserModel user) throws GitBlitException {
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

			// rename the user's ssh public keystore
			getPublicKeyManager().renameUser(username, user.username);
		}
		if (!userManager.updateUserModel(username, user)) {
			throw new GitBlitException("Failed to update user!");
		}
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
				InputStream is = GitblitManager.class.getResourceAsStream("/clientapps.json");
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
	 * Parse the properties file and aggregate all the comments by the setting
	 * key. A setting model tracks the current value, the default value, the
	 * description of the setting and and directives about the setting.
	 *
	 * @return Map<String, SettingModel>
	 */
	private void loadSettingModels(ServerSettings settingsModel) {
		try {
			// Read bundled Gitblit properties to extract setting descriptions.
			// This copy is pristine and only used for populating the setting
			// models map.
			InputStream is = GitblitManager.class.getResourceAsStream("/defaults.properties");
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
			logger.error("Failed to find classpath resource 'defaults.properties'");
		} catch (IOException e) {
			logger.error("Failed to load classpath resource 'defaults.properties'");
		}
	}

	@Override
	public ITicketService getTicketService() {
		return ticketServiceProvider.get();
	}

	@Override
	public IPublicKeyManager getPublicKeyManager() {
		return publicKeyManagerProvider.get();
	}

	/*
	 * ISTOREDSETTINGS
	 *
	 * these methods are necessary for (nearly) seamless Groovy hook operation
	 * after the massive refactor.
	 */

	public boolean getBoolean(String key, boolean defaultValue) {
		return runtimeManager.getSettings().getBoolean(key, defaultValue);
	}

	public String getString(String key, String defaultValue) {
		return runtimeManager.getSettings().getString(key, defaultValue);
	}

	public int getInteger(String key, int defaultValue) {
		return runtimeManager.getSettings().getInteger(key, defaultValue);
	}

	public List<String> getStrings(String key) {
		return runtimeManager.getSettings().getStrings(key);
	}

	/*
	 * RUNTIME MANAGER
	 */

	@Override
	public File getBaseFolder() {
		return runtimeManager.getBaseFolder();
	}

	@Override
	public void setBaseFolder(File folder) {
		runtimeManager.setBaseFolder(folder);
	}

	@Override
	public Date getBootDate() {
		return runtimeManager.getBootDate();
	}

	@Override
	public ServerSettings getSettingsModel() {
		return runtimeManager.getSettingsModel();
	}

	@Override
	public TimeZone getTimezone() {
		return runtimeManager.getTimezone();
	}

	@Override
	public Locale getLocale() {
		return runtimeManager.getLocale();
	}

	@Override
	public boolean isDebugMode() {
		return runtimeManager.isDebugMode();
	}

	@Override
	public File getFileOrFolder(String key, String defaultFileOrFolder) {
		return runtimeManager.getFileOrFolder(key, defaultFileOrFolder);
	}

	@Override
	public File getFileOrFolder(String fileOrFolder) {
		return runtimeManager.getFileOrFolder(fileOrFolder);
	}

	@Override
	public IStoredSettings getSettings() {
		return runtimeManager.getSettings();
	}

	@Override
	public boolean updateSettings(Map<String, String> updatedSettings) {
		return runtimeManager.updateSettings(updatedSettings);
	}

	@Override
	public ServerStatus getStatus() {
		return runtimeManager.getStatus();
	}

	@Override
	public Injector getInjector() {
		return runtimeManager.getInjector();
	}

	@Override
	public XssFilter getXssFilter() {
		return runtimeManager.getXssFilter();
	}

	/*
	 * NOTIFICATION MANAGER
	 */

	@Override
	public boolean isSendingMail() {
		return notificationManager.isSendingMail();
	}

	@Override
	public void sendMailToAdministrators(String subject, String message) {
		notificationManager.sendMailToAdministrators(subject, message);
	}

	@Override
	public void sendMail(String subject, String message, Collection<String> toAddresses) {
		notificationManager.sendMail(subject, message, toAddresses);
	}

	@Override
	public void sendHtmlMail(String subject, String message, Collection<String> toAddresses) {
		notificationManager.sendHtmlMail(subject, message, toAddresses);
	}

	@Override
	public void send(Mailing mail) {
		notificationManager.send(mail);
	}

	/*
	 * SESSION MANAGER
	 */

	@Override
	public UserModel authenticate(String username, char[] password, String remoteIP) {
		return authenticationManager.authenticate(username, password, remoteIP);
	}

	@Override
	public UserModel authenticate(HttpServletRequest httpRequest) {
		UserModel user = authenticationManager.authenticate(httpRequest, false);
		if (user == null) {
			user = federationManager.authenticate(httpRequest);
		}
		return user;
	}

	@Override
	public UserModel authenticate(String username, SshKey key) {
		return authenticationManager.authenticate(username, key);
	}

	@Override
	public UserModel authenticate(String username) {
		return authenticationManager.authenticate(username);
	}

	@Override
	public UserModel authenticate(HttpServletRequest httpRequest, boolean requiresCertificate) {
		UserModel user = authenticationManager.authenticate(httpRequest, requiresCertificate);
		if (user == null) {
			user = federationManager.authenticate(httpRequest);
		}
		return user;
	}

	@Override
	public String getCookie(HttpServletRequest request) {
		return authenticationManager.getCookie(request);
	}

	@Override
	@Deprecated
	public void setCookie(HttpServletResponse response, UserModel user) {
		authenticationManager.setCookie(response, user);
	}

	@Override
	public void setCookie(HttpServletRequest request, HttpServletResponse response, UserModel user) {
		authenticationManager.setCookie(request, response, user);
	}

	@Override
	@Deprecated
	public void logout(HttpServletResponse response, UserModel user) {
		authenticationManager.logout(response, user);
	}

	@Override
	public void logout(HttpServletRequest request, HttpServletResponse response, UserModel user) {
		authenticationManager.logout(request, response, user);
	}

	@Override
	public boolean supportsCredentialChanges(UserModel user) {
		return authenticationManager.supportsCredentialChanges(user);
	}

	@Override
	public boolean supportsDisplayNameChanges(UserModel user) {
		return authenticationManager.supportsDisplayNameChanges(user);
	}

	@Override
	public boolean supportsEmailAddressChanges(UserModel user) {
		return authenticationManager.supportsEmailAddressChanges(user);
	}

	@Override
	public boolean supportsTeamMembershipChanges(UserModel user) {
		return authenticationManager.supportsTeamMembershipChanges(user);
	}

	@Override
	public boolean supportsTeamMembershipChanges(TeamModel team) {
		return authenticationManager.supportsTeamMembershipChanges(team);
	}

	@Override
	public boolean supportsRoleChanges(UserModel user, Role role) {
		return authenticationManager.supportsRoleChanges(user, role);
	}

	@Override
	public boolean supportsRoleChanges(TeamModel team, Role role) {
		return authenticationManager.supportsRoleChanges(team, role);
	}

	/*
	 * USER MANAGER
	 */

	@Override
	public void setup(IRuntimeManager runtimeManager) {
	}

	@Override
	public boolean isInternalAccount(String username) {
		return userManager.isInternalAccount(username);
	}

	@Override
	public List<String> getAllUsernames() {
		return userManager.getAllUsernames();
	}

	@Override
	public List<UserModel> getAllUsers() {
		return userManager.getAllUsers();
	}

	@Override
	public UserModel getUserModel(String username) {
		return userManager.getUserModel(username);
	}

	@Override
	public List<TeamModel> getAllTeams() {
		return userManager.getAllTeams();
	}

	@Override
	public TeamModel getTeamModel(String teamname) {
		return userManager.getTeamModel(teamname);
	}

	@Override
	public String getCookie(UserModel model) {
		return userManager.getCookie(model);
	}

	@Override
	public UserModel getUserModel(char[] cookie) {
		return userManager.getUserModel(cookie);
	}

	@Override
	public boolean updateUserModel(UserModel model) {
		return userManager.updateUserModel(model);
	}

	@Override
	public boolean updateUserModels(Collection<UserModel> models) {
		return userManager.updateUserModels(models);
	}

	@Override
	public boolean updateUserModel(String username, UserModel model) {
		return userManager.updateUserModel(username, model);
	}

	@Override
	public boolean deleteUser(String username) {
		// delegate to deleteUserModel() to delete public ssh keys
		UserModel user = userManager.getUserModel(username);
		return deleteUserModel(user);
	}

	/**
	 * Delete the user and all associated public ssh keys.
	 */
	@Override
	public boolean deleteUserModel(UserModel model) {
		boolean success = userManager.deleteUserModel(model);
		if (success) {
			getPublicKeyManager().removeAllKeys(model.username);
		}
		return success;
	}

	@Override
	public List<String> getAllTeamNames() {
		return userManager.getAllTeamNames();
	}

	@Override
	public List<String> getTeamNamesForRepositoryRole(String role) {
		return userManager.getTeamNamesForRepositoryRole(role);
	}

	@Override
	public boolean updateTeamModel(TeamModel model) {
		return userManager.updateTeamModel(model);
	}

	@Override
	public boolean updateTeamModels(Collection<TeamModel> models) {
		return userManager.updateTeamModels(models);
	}

	@Override
	public boolean updateTeamModel(String teamname, TeamModel model) {
		return userManager.updateTeamModel(teamname, model);
	}

	@Override
	public boolean deleteTeamModel(TeamModel model) {
		return userManager.deleteTeamModel(model);
	}

	@Override
	public List<String> getUsernamesForRepositoryRole(String role) {
		return userManager.getUsernamesForRepositoryRole(role);
	}

	@Override
	public boolean renameRepositoryRole(String oldRole, String newRole) {
		return userManager.renameRepositoryRole(oldRole, newRole);
	}

	@Override
	public boolean deleteRepositoryRole(String role) {
		return userManager.deleteRepositoryRole(role);
	}

	@Override
	public boolean deleteTeam(String teamname) {
		return userManager.deleteTeam(teamname);
	}

	/*
	 * REPOSITORY MANAGER
	 */

	@Override
	public Date getLastActivityDate() {
		return repositoryManager.getLastActivityDate();
	}

	@Override
	public File getRepositoriesFolder() {
		return repositoryManager.getRepositoriesFolder();
	}

	@Override
	public File getHooksFolder() {
		return repositoryManager.getHooksFolder();
	}

	@Override
	public File getGrapesFolder() {
		return repositoryManager.getGrapesFolder();
	}

	@Override
	public List<RegistrantAccessPermission> getUserAccessPermissions(UserModel user) {
		return repositoryManager.getUserAccessPermissions(user);
	}

	@Override
	public List<RegistrantAccessPermission> getUserAccessPermissions(RepositoryModel repository) {
		return repositoryManager.getUserAccessPermissions(repository);
	}

	@Override
	public boolean setUserAccessPermissions(RepositoryModel repository, Collection<RegistrantAccessPermission> permissions) {
		return repositoryManager.setUserAccessPermissions(repository, permissions);
	}

	@Override
	public List<String> getRepositoryUsers(RepositoryModel repository) {
		return repositoryManager.getRepositoryUsers(repository);
	}

	@Override
	public List<RegistrantAccessPermission> getTeamAccessPermissions(RepositoryModel repository) {
		return repositoryManager.getTeamAccessPermissions(repository);
	}

	@Override
	public boolean setTeamAccessPermissions(RepositoryModel repository, Collection<RegistrantAccessPermission> permissions) {
		return repositoryManager.setTeamAccessPermissions(repository, permissions);
	}

	@Override
	public List<String> getRepositoryTeams(RepositoryModel repository) {
		return repositoryManager.getRepositoryTeams(repository);
	}
	@Override
	public void addToCachedRepositoryList(RepositoryModel model) {
		repositoryManager.addToCachedRepositoryList(model);
	}

	@Override
	public void resetRepositoryListCache() {
		repositoryManager.resetRepositoryListCache();
	}

	@Override
	public void resetRepositoryCache(String repositoryName) {
		repositoryManager.resetRepositoryCache(repositoryName);
	}

	@Override
	public List<String> getRepositoryList() {
		return repositoryManager.getRepositoryList();
	}

	@Override
	public Repository getRepository(String repositoryName) {
		return repositoryManager.getRepository(repositoryName);
	}

	@Override
	public Repository getRepository(String repositoryName, boolean logError) {
		return repositoryManager.getRepository(repositoryName, logError);
	}

	@Override
	public List<RepositoryModel> getRepositoryModels() {
		return repositoryManager.getRepositoryModels();
	}

	@Override
	public List<RepositoryModel> getRepositoryModels(UserModel user) {
		return repositoryManager.getRepositoryModels(user);
	}

	@Override
	public RepositoryModel getRepositoryModel(UserModel user, String repositoryName) {
		return repositoryManager.getRepositoryModel(repositoryName);
	}

	@Override
	public RepositoryModel getRepositoryModel(String repositoryName) {
		return repositoryManager.getRepositoryModel(repositoryName);
	}

	@Override
	public long getStarCount(RepositoryModel repository) {
		return repositoryManager.getStarCount(repository);
	}

	@Override
	public boolean hasRepository(String repositoryName) {
		return repositoryManager.hasRepository(repositoryName);
	}

	@Override
	public boolean hasRepository(String repositoryName, boolean caseSensitiveCheck) {
		return repositoryManager.hasRepository(repositoryName, caseSensitiveCheck);
	}

	@Override
	public boolean hasFork(String username, String origin) {
		return repositoryManager.hasFork(username, origin);
	}

	@Override
	public String getFork(String username, String origin) {
		return repositoryManager.getFork(username, origin);
	}

	@Override
	public ForkModel getForkNetwork(String repository) {
		return repositoryManager.getForkNetwork(repository);
	}

	@Override
	public long updateLastChangeFields(Repository r, RepositoryModel model) {
		return repositoryManager.updateLastChangeFields(r, model);
	}

	@Override
	public List<Metric> getRepositoryDefaultMetrics(RepositoryModel model, Repository repository) {
		return repositoryManager.getRepositoryDefaultMetrics(model, repository);
	}

	/**
	 * Detect renames and reindex as appropriate.
	 */
	@Override
	public void updateRepositoryModel(String repositoryName, RepositoryModel repository,
			boolean isCreate) throws GitBlitException {
		RepositoryModel oldModel = null;
		boolean isRename = !isCreate && !repositoryName.equalsIgnoreCase(repository.name);
		if (isRename) {
			oldModel = repositoryManager.getRepositoryModel(repositoryName);
		}

		repositoryManager.updateRepositoryModel(repositoryName, repository, isCreate);

		if (isRename && ticketServiceProvider.get() != null) {
			ticketServiceProvider.get().rename(oldModel, repository);
		}
	}

	@Override
	public void updateConfiguration(Repository r, RepositoryModel repository) {
		repositoryManager.updateConfiguration(r, repository);
	}

	@Override
	public boolean canDelete(RepositoryModel model) {
		return repositoryManager.canDelete(model);
	}

	/**
	 * Delete the repository and all associated tickets.
	 */
	@Override
	public boolean deleteRepositoryModel(RepositoryModel model) {
		boolean success = repositoryManager.deleteRepositoryModel(model);
		if (success && ticketServiceProvider.get() != null) {
			ticketServiceProvider.get().deleteAll(model);
		}
		return success;
	}

	@Override
	public boolean deleteRepository(String repositoryName) {
		// delegate to deleteRepositoryModel() to destroy indexed tickets
		RepositoryModel repository = repositoryManager.getRepositoryModel(repositoryName);
		return deleteRepositoryModel(repository);
	}

	@Override
	public List<String> getAllScripts() {
		return repositoryManager.getAllScripts();
	}

	@Override
	public List<String> getPreReceiveScriptsInherited(RepositoryModel repository) {
		return repositoryManager.getPreReceiveScriptsInherited(repository);
	}

	@Override
	public List<String> getPreReceiveScriptsUnused(RepositoryModel repository) {
		return repositoryManager.getPreReceiveScriptsUnused(repository);
	}

	@Override
	public List<String> getPostReceiveScriptsInherited(RepositoryModel repository) {
		return repositoryManager.getPostReceiveScriptsInherited(repository);
	}

	@Override
	public List<String> getPostReceiveScriptsUnused(RepositoryModel repository) {
		return repositoryManager.getPostReceiveScriptsUnused(repository);
	}

	@Override
	public List<SearchResult> search(String query, int page, int pageSize, List<String> repositories) {
		return repositoryManager.search(query, page, pageSize, repositories);
	}

	@Override
	public boolean isCollectingGarbage() {
		return repositoryManager.isCollectingGarbage();
	}

	@Override
	public boolean isCollectingGarbage(String repositoryName) {
		return repositoryManager.isCollectingGarbage(repositoryName);
	}

	/*
	 * PROJECT MANAGER
	 */

	@Override
	public List<ProjectModel> getProjectModels(UserModel user, boolean includeUsers) {
		return projectManager.getProjectModels(user, includeUsers);
	}

	@Override
	public ProjectModel getProjectModel(String name, UserModel user) {
		return projectManager.getProjectModel(name, user);
	}

	@Override
	public ProjectModel getProjectModel(String name) {
		return projectManager.getProjectModel(name);
	}

	@Override
	public List<ProjectModel> getProjectModels(List<RepositoryModel> repositoryModels, boolean includeUsers) {
		return projectManager.getProjectModels(repositoryModels, includeUsers);
	}

	/*
	 * FEDERATION MANAGER
	 */

	@Override
	public File getProposalsFolder() {
		return federationManager.getProposalsFolder();
	}

	@Override
	public boolean canFederate() {
		return federationManager.canFederate();
	}

	@Override
	public UserModel getFederationUser() {
		return federationManager.getFederationUser();
	}

	@Override
	public List<FederationModel> getFederationRegistrations() {
		return federationManager.getFederationRegistrations();
	}

	@Override
	public FederationModel getFederationRegistration(String url, String name) {
		return federationManager.getFederationRegistration(url, name);
	}

	@Override
	public List<FederationSet> getFederationSets(String gitblitUrl) {
		return federationManager.getFederationSets(gitblitUrl);
	}

	@Override
	public List<String> getFederationTokens() {
		return federationManager.getFederationTokens();
	}

	@Override
	public String getFederationToken(FederationToken type) {
		return federationManager.getFederationToken(type);
	}

	@Override
	public String getFederationToken(String value) {
		return federationManager.getFederationToken(value);
	}

	@Override
	public boolean validateFederationRequest(FederationRequest req, String token) {
		return federationManager.validateFederationRequest(req, token);
	}

	@Override
	public boolean acknowledgeFederationStatus(String identification, FederationModel registration) {
		return federationManager.acknowledgeFederationStatus(identification, registration);
	}

	@Override
	public List<FederationModel> getFederationResultRegistrations() {
		return federationManager.getFederationResultRegistrations();
	}

	@Override
	public boolean submitFederationProposal(FederationProposal proposal, String gitblitUrl) {
		return federationManager.submitFederationProposal(proposal, gitblitUrl);
	}

	@Override
	public List<FederationProposal> getPendingFederationProposals() {
		return federationManager.getPendingFederationProposals();
	}

	@Override
	public Map<String, RepositoryModel> getRepositories(String gitblitUrl, String token) {
		return federationManager.getRepositories(gitblitUrl, token);
	}

	@Override
	public FederationProposal createFederationProposal(String gitblitUrl, String token) {
		return federationManager.createFederationProposal(gitblitUrl, token);
	}

	@Override
	public FederationProposal getPendingFederationProposal(String token) {
		return federationManager.getPendingFederationProposal(token);
	}

	@Override
	public boolean deletePendingFederationProposal(FederationProposal proposal) {
		return federationManager.deletePendingFederationProposal(proposal);
	}

	@Override
	public void closeAll() {
		repositoryManager.closeAll();
	}

	@Override
	public void close(String repository) {
		repositoryManager.close(repository);
	}

	@Override
	public boolean isIdle(Repository repository) {
		return repositoryManager.isIdle(repository);
	}

	/*
	 * FILE STORAGE MANAGER
	 */
	
	@Override
	public boolean isValidOid(String oid) {
		return filestoreManager.isValidOid(oid);
	}
	
	@Override
	public FilestoreModel.Status addObject(String oid, long size, UserModel user, RepositoryModel repo) {
		return filestoreManager.addObject(oid, size, user, repo);
	}
	
	@Override
	public FilestoreModel getObject(String oid, UserModel user, RepositoryModel repo) {
		return filestoreManager.getObject(oid, user, repo);
	};
	
	@Override
	public FilestoreModel.Status uploadBlob(String oid, long size, UserModel user, RepositoryModel repo, InputStream streamIn ) {
		return filestoreManager.uploadBlob(oid, size, user, repo, streamIn);
	}
	
	@Override
	public FilestoreModel.Status downloadBlob(String oid, UserModel user, RepositoryModel repo, OutputStream streamOut ) {
		return filestoreManager.downloadBlob(oid, user, repo, streamOut);
	}
	
	@Override
	public List<FilestoreModel> getAllObjects(UserModel user) {
		return filestoreManager.getAllObjects(user);
	}
	
	@Override
	public File getStorageFolder() {
		return filestoreManager.getStorageFolder();
	}
	
	@Override
	public File getStoragePath(String oid) {
		return filestoreManager.getStoragePath(oid);
	}
	
	@Override
	public long getMaxUploadSize() {
		return filestoreManager.getMaxUploadSize();
	};
	
	@Override
	public void clearFilestoreCache() {
		filestoreManager.clearFilestoreCache();
	};

	@Override
	public long getFilestoreUsedByteCount() {
		return filestoreManager.getFilestoreUsedByteCount();
	};
	
	@Override
	public long getFilestoreAvailableByteCount() {
		return filestoreManager.getFilestoreAvailableByteCount();
	};
	
	/*
	 * PLUGIN MANAGER
	 */

	@Override
	public Version getSystemVersion() {
		return pluginManager.getSystemVersion();
	}

	@Override
	public void startPlugins() {
		pluginManager.startPlugins();
	}

	@Override
	public void stopPlugins() {
		pluginManager.stopPlugins();
	}

	@Override
	public List<PluginWrapper> getPlugins() {
		return pluginManager.getPlugins();
	}

	@Override
	public PluginWrapper getPlugin(String pluginId) {
		return pluginManager.getPlugin(pluginId);
	}

	@Override
	public List<Class<?>> getExtensionClasses(String pluginId) {
		return pluginManager.getExtensionClasses(pluginId);
	}

	@Override
	public <T> List<T> getExtensions(Class<T> clazz) {
		return pluginManager.getExtensions(clazz);
	}

	@Override
	public PluginWrapper whichPlugin(Class<?> clazz) {
		return pluginManager.whichPlugin(clazz);
	}

	@Override
	public PluginState startPlugin(String pluginId) {
		return pluginManager.startPlugin(pluginId);
	}

	@Override
	public PluginState stopPlugin(String pluginId) {
		return pluginManager.stopPlugin(pluginId);
	}

	@Override
	public boolean disablePlugin(String pluginId) {
		return pluginManager.disablePlugin(pluginId);
	}

	@Override
	public boolean enablePlugin(String pluginId) {
		return pluginManager.enablePlugin(pluginId);
	}

	@Override
	public boolean uninstallPlugin(String pluginId) {
		return pluginManager.uninstallPlugin(pluginId);
	}

	@Override
	public boolean refreshRegistry(boolean verifyChecksum) {
		return pluginManager.refreshRegistry(verifyChecksum);
	}

	@Override
	public boolean installPlugin(String url, boolean verifyChecksum) throws IOException {
		return pluginManager.installPlugin(url, verifyChecksum);
	}

	@Override
	public boolean upgradePlugin(String pluginId, String url, boolean verifyChecksum) throws IOException {
		return pluginManager.upgradePlugin(pluginId, url, verifyChecksum);
	}

	@Override
	public List<PluginRegistration> getRegisteredPlugins() {
		return pluginManager.getRegisteredPlugins();
	}

	@Override
	public List<PluginRegistration> getRegisteredPlugins(InstallState state) {
		return pluginManager.getRegisteredPlugins(state);
	}

	@Override
	public PluginRegistration lookupPlugin(String pluginId) {
		return pluginManager.lookupPlugin(pluginId);
	}

	@Override
	public PluginRelease lookupRelease(String pluginId, String version) {
		return pluginManager.lookupRelease(pluginId, version);
	}
	
}
