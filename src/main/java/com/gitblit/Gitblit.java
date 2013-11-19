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
package com.gitblit;

import java.io.File;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lib.Repository;

import com.gitblit.Constants.FederationRequest;
import com.gitblit.Constants.FederationToken;
import com.gitblit.manager.IFederationManager;
import com.gitblit.manager.IGitblitManager;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IProjectManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.ISessionManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.models.FederationModel;
import com.gitblit.models.FederationProposal;
import com.gitblit.models.FederationSet;
import com.gitblit.models.ForkModel;
import com.gitblit.models.GitClientApplication;
import com.gitblit.models.Metric;
import com.gitblit.models.ProjectModel;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.RepositoryUrl;
import com.gitblit.models.SearchResult;
import com.gitblit.models.ServerSettings;
import com.gitblit.models.ServerStatus;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;

/**
 * Gitblit is an aggregate interface delegate.  It implements all the manager
 * interfaces and delegates all methods calls to the actual manager implementations.
 * It's primary purpose is to provide complete management control to the git
 * upload and receive pack functions.
 *
 * @author James Moger
 *
 */
@Singleton
public class Gitblit implements IRuntimeManager,
								INotificationManager,
								IUserManager,
								ISessionManager,
								IRepositoryManager,
								IProjectManager,
								IGitblitManager,
								IFederationManager {

	private final IRuntimeManager runtimeManager;

	private final INotificationManager notificationManager;

	private final IUserManager userManager;

	private final ISessionManager sessionManager;

	private final IRepositoryManager repositoryManager;

	private final IProjectManager projectManager;

	private final IFederationManager federationManager;

	private final IGitblitManager gitblitManager;

	@Inject
	public Gitblit(
			IRuntimeManager runtimeManager,
			INotificationManager notificationManager,
			IUserManager userManager,
			ISessionManager sessionManager,
			IRepositoryManager repositoryManager,
			IProjectManager projectManager,
			IFederationManager federationManager,
			IGitblitManager gitblitManager) {

		this.runtimeManager = runtimeManager;
		this.notificationManager = notificationManager;
		this.userManager = userManager;
		this.sessionManager = sessionManager;
		this.repositoryManager = repositoryManager;
		this.projectManager = projectManager;
		this.federationManager = federationManager;
		this.gitblitManager = gitblitManager;
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
	public boolean isServingRepositories() {
		return runtimeManager.isServingRepositories();
	}

	@Override
	public TimeZone getTimezone() {
		return runtimeManager.getTimezone();
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

	/*
	 * NOTIFICATION MANAGER
	 */

	@Override
	public void sendMailToAdministrators(String subject, String message) {
		notificationManager.sendMailToAdministrators(subject, message);
	}

	@Override
	public void sendMail(String subject, String message, Collection<String> toAddresses) {
		notificationManager.sendMail(subject, message, toAddresses);
	}

	@Override
	public void sendMail(String subject, String message, String... toAddresses) {
		notificationManager.sendMail(subject, message, toAddresses);
	}

	@Override
	public void sendHtmlMail(String subject, String message, Collection<String> toAddresses) {
		notificationManager.sendHtmlMail(subject, message, toAddresses);
	}

	@Override
	public void sendHtmlMail(String subject, String message, String... toAddresses) {
		notificationManager.sendHtmlMail(subject, message, toAddresses);
	}

	/*
	 * SESSION MANAGER
	 */

	@Override
	public UserModel authenticate(String username, char[] password) {
		return sessionManager.authenticate(username, password);
	}

	@Override
	public UserModel authenticate(HttpServletRequest httpRequest) {
		return sessionManager.authenticate(httpRequest, false);
	}
	@Override
	public UserModel authenticate(HttpServletRequest httpRequest, boolean requiresCertificate) {
		return sessionManager.authenticate(httpRequest, requiresCertificate);
	}

	@Override
	public void setCookie(HttpServletResponse response, UserModel user) {
		sessionManager.setCookie(response, user);
	}

	@Override
	public void logout(HttpServletResponse response, UserModel user) {
		sessionManager.logout(response, user);
	}

	/*
	 * USER MANAGER
	 */

	@Override
	public boolean supportsAddUser() {
		return userManager.supportsAddUser();
	}

	@Override
	public boolean supportsCredentialChanges(UserModel user) {
		return userManager.supportsCredentialChanges(user);
	}

	@Override
	public boolean supportsDisplayNameChanges(UserModel user) {
		return userManager.supportsDisplayNameChanges(user);
	}

	@Override
	public boolean supportsEmailAddressChanges(UserModel user) {
		return userManager.supportsEmailAddressChanges(user);
	}

	@Override
	public boolean supportsTeamMembershipChanges(UserModel user) {
		return userManager.supportsTeamMembershipChanges(user);
	}

	@Override
	public void logout(UserModel user) {
		userManager.logout(user);
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
	public boolean deleteUser(String username) {
		return userManager.deleteUser(username);
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
	public boolean supportsCookies() {
		return userManager.supportsCookies();
	}

	@Override
	public String getCookie(UserModel model) {
		return userManager.getCookie(model);
	}

	@Override
	public UserModel authenticate(char[] cookie) {
		return userManager.authenticate(cookie);
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
	public boolean deleteUserModel(UserModel model) {
		return userManager.deleteUserModel(model);
	}

	@Override
	public List<String> getAllTeamNames() {
		return userManager.getAllTeamNames();
	}

	@Override
	public List<String> getTeamnamesForRepositoryRole(String role) {
		return userManager.getTeamnamesForRepositoryRole(role);
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

	@Override
	public void updateRepositoryModel(String repositoryName, RepositoryModel repository,
			boolean isCreate) throws GitBlitException {
		repositoryManager.updateRepositoryModel(repositoryName, repository, isCreate);
	}

	@Override
	public void updateConfiguration(Repository r, RepositoryModel repository) {
		repositoryManager.updateConfiguration(r, repository);
	}

	@Override
	public boolean deleteRepositoryModel(RepositoryModel model) {
		return repositoryManager.deleteRepositoryModel(model);
	}

	@Override
	public boolean deleteRepository(String repositoryName) {
		return repositoryManager.deleteRepository(repositoryName);
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
	public UserModel getFederationUser() {
		return federationManager.getFederationUser();
	}

	@Override
	public boolean canFederate() {
		return federationManager.canFederate();
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

	/*
	 * GITBLIT MANAGER
	 */

	@Override
	public RepositoryModel fork(RepositoryModel repository, UserModel user) throws GitBlitException {
		return gitblitManager.fork(repository, user);
	}

	@Override
	public void updateTeamModel(String teamname, TeamModel team, boolean isCreate)
			throws GitBlitException {
		gitblitManager.updateTeamModel(teamname, team, isCreate);
	}

	@Override
	public void updateUserModel(String username, UserModel user, boolean isCreate)
			throws GitBlitException {
		gitblitManager.updateUserModel(username, user, isCreate);
	}

	@Override
	public List<RepositoryUrl> getRepositoryUrls(HttpServletRequest request, UserModel user, RepositoryModel repository) {
		return gitblitManager.getRepositoryUrls(request, user, repository);
	}

	@Override
	public Collection<GitClientApplication> getClientApplications() {
		return gitblitManager.getClientApplications();
	}
}
