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
package com.gitblit.servlet;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lib.Repository;

import com.gitblit.Constants;
import com.gitblit.GitBlitException;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.Constants.RpcRequest;
import com.gitblit.Keys.federation;
import com.gitblit.Keys.realm;
import com.gitblit.Keys.web;
import com.gitblit.manager.IFederationManager;
import com.gitblit.manager.IGitblitManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.models.RefModel;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.ServerSettings;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.DeepCopier;
import com.gitblit.utils.HttpUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.RpcUtils;
import com.gitblit.utils.StringUtils;

/**
 * Handles remote procedure calls.
 *
 * @author James Moger
 *
 */
@Singleton
public class RpcServlet extends JsonServlet {

	private static final long serialVersionUID = 1L;

	public static final int PROTOCOL_VERSION = 6;

	private final IStoredSettings settings;

	private final IRuntimeManager runtimeManager;

	private final IUserManager userManager;

	private final IRepositoryManager repositoryManager;

	private final IFederationManager federationManager;

	private final IGitblitManager gitblitManager;

	@Inject
	public RpcServlet(
			IRuntimeManager runtimeManager,
			IUserManager userManager,
			IRepositoryManager repositoryManager,
			IFederationManager federationManager,
			IGitblitManager gitblitManager) {

		super();

		this.settings = runtimeManager.getSettings();
		this.runtimeManager = runtimeManager;
		this.userManager = userManager;
		this.repositoryManager = repositoryManager;
		this.federationManager = federationManager;
		this.gitblitManager = gitblitManager;
	}

	/**
	 * Processes an rpc request.
	 *
	 * @param request
	 * @param response
	 * @throws javax.servlet.ServletException
	 * @throws java.io.IOException
	 */
	@Override
	protected void processRequest(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		RpcRequest reqType = RpcRequest.fromName(request.getParameter("req"));
		String objectName = request.getParameter("name");
		logger.info(MessageFormat.format("Rpc {0} request from {1}", reqType,
				request.getRemoteAddr()));

		UserModel user = (UserModel) request.getUserPrincipal();

		boolean allowManagement = user != null && user.canAdmin()
				&& settings.getBoolean(Keys.web.enableRpcManagement, false);

		boolean allowAdmin = user != null && user.canAdmin()
				&& settings.getBoolean(Keys.web.enableRpcAdministration, false);

		Object result = null;
		if (RpcRequest.GET_PROTOCOL.equals(reqType)) {
			// Return the protocol version
			result = PROTOCOL_VERSION;
		} else if (RpcRequest.LIST_REPOSITORIES.equals(reqType)) {
			// Determine the Gitblit clone url
			String gitblitUrl = HttpUtils.getGitblitURL(request);
			StringBuilder sb = new StringBuilder();
			sb.append(gitblitUrl);
			sb.append(Constants.GIT_PATH);
			sb.append("{0}");
			String cloneUrl = sb.toString();

			// list repositories
			List<RepositoryModel> list = repositoryManager.getRepositoryModels(user);
			Map<String, RepositoryModel> repositories = new HashMap<String, RepositoryModel>();
			for (RepositoryModel model : list) {
				String url = MessageFormat.format(cloneUrl, model.name);
				repositories.put(url, model);
			}
			result = repositories;
		} else if (RpcRequest.LIST_BRANCHES.equals(reqType)) {
			// list all local branches in all repositories accessible to user
			Map<String, List<String>> localBranches = new HashMap<String, List<String>>();
			List<RepositoryModel> models = repositoryManager.getRepositoryModels(user);
			for (RepositoryModel model : models) {
				if (!model.hasCommits) {
					// skip empty repository
					continue;
				}
				if (model.isCollectingGarbage) {
					// skip garbage collecting repository
					logger.warn(MessageFormat.format("Temporarily excluding {0} from RPC, busy collecting garbage", model.name));
					continue;
				}
				// get local branches
				Repository repository = repositoryManager.getRepository(model.name);
				List<RefModel> refs = JGitUtils.getLocalBranches(repository, false, -1);
				if (model.showRemoteBranches) {
					// add remote branches if repository displays them
					refs.addAll(JGitUtils.getRemoteBranches(repository, false, -1));
				}
				if (refs.size() > 0) {
					List<String> branches = new ArrayList<String>();
					for (RefModel ref : refs) {
						branches.add(ref.getName());
					}
					localBranches.put(model.name, branches);
				}
				repository.close();
			}
			result = localBranches;
		} else if (RpcRequest.GET_USER.equals(reqType)) {
			if (StringUtils.isEmpty(objectName)) {
				if (UserModel.ANONYMOUS.equals(user)) {
					response.sendError(forbiddenCode);
				} else {
					// return the current user, reset credentials
					UserModel requestedUser = DeepCopier.copy(user);
					result = requestedUser;
				}
			} else {
				if (user.canAdmin() || objectName.equals(user.username)) {
					// return the specified user
					UserModel requestedUser = userManager.getUserModel(objectName);
					if (requestedUser == null) {
						response.setStatus(failureCode);
					} else {
						result = requestedUser;
					}
				} else {
					response.sendError(forbiddenCode);
				}
			}
		} else if (RpcRequest.LIST_USERS.equals(reqType)) {
			// list users
			List<String> names = userManager.getAllUsernames();
			List<UserModel> users = new ArrayList<UserModel>();
			for (String name : names) {
				users.add(userManager.getUserModel(name));
			}
			result = users;
		} else if (RpcRequest.LIST_TEAMS.equals(reqType)) {
			// list teams
			List<String> names = userManager.getAllTeamNames();
			List<TeamModel> teams = new ArrayList<TeamModel>();
			for (String name : names) {
				teams.add(userManager.getTeamModel(name));
			}
			result = teams;
		} else if (RpcRequest.CREATE_REPOSITORY.equals(reqType)) {
			// create repository
			RepositoryModel model = deserialize(request, response, RepositoryModel.class);
			try {
				repositoryManager.updateRepositoryModel(model.name, model, true);
			} catch (GitBlitException e) {
				response.setStatus(failureCode);
			}
		} else if (RpcRequest.EDIT_REPOSITORY.equals(reqType)) {
			// edit repository
			RepositoryModel model = deserialize(request, response, RepositoryModel.class);
			// name specifies original repository name in event of rename
			String repoName = objectName;
			if (repoName == null) {
				repoName = model.name;
			}
			try {
				repositoryManager.updateRepositoryModel(repoName, model, false);
			} catch (GitBlitException e) {
				response.setStatus(failureCode);
			}
		} else if (RpcRequest.DELETE_REPOSITORY.equals(reqType)) {
			// delete repository
			RepositoryModel model = deserialize(request, response, RepositoryModel.class);
			repositoryManager.deleteRepositoryModel(model);
		} else if (RpcRequest.CREATE_USER.equals(reqType)) {
			// create user
			UserModel model = deserialize(request, response, UserModel.class);
			try {
				gitblitManager.updateUserModel(model.username, model, true);
			} catch (GitBlitException e) {
				response.setStatus(failureCode);
			}
		} else if (RpcRequest.EDIT_USER.equals(reqType)) {
			// edit user
			UserModel model = deserialize(request, response, UserModel.class);
			// name parameter specifies original user name in event of rename
			String username = objectName;
			if (username == null) {
				username = model.username;
			}
			try {
				gitblitManager.updateUserModel(username, model, false);
			} catch (GitBlitException e) {
				response.setStatus(failureCode);
			}
		} else if (RpcRequest.DELETE_USER.equals(reqType)) {
			// delete user
			UserModel model = deserialize(request, response, UserModel.class);
			if (!userManager.deleteUser(model.username)) {
				response.setStatus(failureCode);
			}
		} else if (RpcRequest.CREATE_TEAM.equals(reqType)) {
			// create team
			TeamModel model = deserialize(request, response, TeamModel.class);
			try {
				gitblitManager.updateTeamModel(model.name, model, true);
			} catch (GitBlitException e) {
				response.setStatus(failureCode);
			}
		} else if (RpcRequest.EDIT_TEAM.equals(reqType)) {
			// edit team
			TeamModel model = deserialize(request, response, TeamModel.class);
			// name parameter specifies original team name in event of rename
			String teamname = objectName;
			if (teamname == null) {
				teamname = model.name;
			}
			try {
				gitblitManager.updateTeamModel(teamname, model, false);
			} catch (GitBlitException e) {
				response.setStatus(failureCode);
			}
		} else if (RpcRequest.DELETE_TEAM.equals(reqType)) {
			// delete team
			TeamModel model = deserialize(request, response, TeamModel.class);
			if (!userManager.deleteTeam(model.name)) {
				response.setStatus(failureCode);
			}
		} else if (RpcRequest.LIST_REPOSITORY_MEMBERS.equals(reqType)) {
			// get repository members
			RepositoryModel model = repositoryManager.getRepositoryModel(objectName);
			result = repositoryManager.getRepositoryUsers(model);
		} else if (RpcRequest.SET_REPOSITORY_MEMBERS.equals(reqType)) {
			// rejected since 1.2.0
			response.setStatus(failureCode);
		} else if (RpcRequest.LIST_REPOSITORY_MEMBER_PERMISSIONS.equals(reqType)) {
			// get repository member permissions
			RepositoryModel model = repositoryManager.getRepositoryModel(objectName);
			result = repositoryManager.getUserAccessPermissions(model);
		} else if (RpcRequest.SET_REPOSITORY_MEMBER_PERMISSIONS.equals(reqType)) {
			// set the repository permissions for the specified users
			RepositoryModel model = repositoryManager.getRepositoryModel(objectName);
			Collection<RegistrantAccessPermission> permissions = deserialize(request, response, RpcUtils.REGISTRANT_PERMISSIONS_TYPE);
			result = repositoryManager.setUserAccessPermissions(model, permissions);
		} else if (RpcRequest.LIST_REPOSITORY_TEAMS.equals(reqType)) {
			// get repository teams
			RepositoryModel model = repositoryManager.getRepositoryModel(objectName);
			result = repositoryManager.getRepositoryTeams(model);
		} else if (RpcRequest.SET_REPOSITORY_TEAMS.equals(reqType)) {
			// rejected since 1.2.0
			response.setStatus(failureCode);
		} else if (RpcRequest.LIST_REPOSITORY_TEAM_PERMISSIONS.equals(reqType)) {
			// get repository team permissions
			RepositoryModel model = repositoryManager.getRepositoryModel(objectName);
			result = repositoryManager.getTeamAccessPermissions(model);
		} else if (RpcRequest.SET_REPOSITORY_TEAM_PERMISSIONS.equals(reqType)) {
			// set the repository permissions for the specified teams
			RepositoryModel model = repositoryManager.getRepositoryModel(objectName);
			Collection<RegistrantAccessPermission> permissions = deserialize(request, response, RpcUtils.REGISTRANT_PERMISSIONS_TYPE);
			result = repositoryManager.setTeamAccessPermissions(model, permissions);
		} else if (RpcRequest.LIST_FEDERATION_REGISTRATIONS.equals(reqType)) {
			// return the list of federation registrations
			if (allowAdmin) {
				result = federationManager.getFederationRegistrations();
			} else {
				response.sendError(notAllowedCode);
			}
		} else if (RpcRequest.LIST_FEDERATION_RESULTS.equals(reqType)) {
			// return the list of federation result registrations
			if (allowAdmin && federationManager.canFederate()) {
				result = federationManager.getFederationResultRegistrations();
			} else {
				response.sendError(notAllowedCode);
			}
		} else if (RpcRequest.LIST_FEDERATION_PROPOSALS.equals(reqType)) {
			// return the list of federation proposals
			if (allowAdmin && federationManager.canFederate()) {
				result = federationManager.getPendingFederationProposals();
			} else {
				response.sendError(notAllowedCode);
			}
		} else if (RpcRequest.LIST_FEDERATION_SETS.equals(reqType)) {
			// return the list of federation sets
			if (allowAdmin && federationManager.canFederate()) {
				String gitblitUrl = HttpUtils.getGitblitURL(request);
				result = federationManager.getFederationSets(gitblitUrl);
			} else {
				response.sendError(notAllowedCode);
			}
		} else if (RpcRequest.LIST_SETTINGS.equals(reqType)) {
			// return the server's settings
			ServerSettings serverSettings = runtimeManager.getSettingsModel();
			if (allowAdmin) {
				// return all settings
				result = serverSettings;
			} else {
				// anonymous users get a few settings to allow browser launching
				List<String> keys = new ArrayList<String>();
				keys.add(Keys.web.siteName);
				keys.add(Keys.web.mountParameters);
				keys.add(Keys.web.syndicationEntries);

				if (allowManagement) {
					// keys necessary for repository and/or user management
					keys.add(Keys.realm.minPasswordLength);
					keys.add(Keys.realm.passwordStorage);
					keys.add(Keys.federation.sets);
				}
				// build the settings
				ServerSettings managementSettings = new ServerSettings();
				for (String key : keys) {
					managementSettings.add(serverSettings.get(key));
				}
				if (allowManagement) {
					managementSettings.pushScripts = serverSettings.pushScripts;
				}
				result = managementSettings;
			}
		} else if (RpcRequest.EDIT_SETTINGS.equals(reqType)) {
			// update settings on the server
			if (allowAdmin) {
				Map<String, String> map = deserialize(request, response,
						RpcUtils.SETTINGS_TYPE);
				runtimeManager.updateSettings(map);
			} else {
				response.sendError(notAllowedCode);
			}
		} else if (RpcRequest.LIST_STATUS.equals(reqType)) {
			// return the server's status information
			if (allowAdmin) {
				result = runtimeManager.getStatus();
			} else {
				response.sendError(notAllowedCode);
			}
		} else if (RpcRequest.CLEAR_REPOSITORY_CACHE.equals(reqType)) {
			// clear the repository list cache
			if (allowManagement) {
				repositoryManager.resetRepositoryListCache();
			} else {
				response.sendError(notAllowedCode);
			}
		}

		// send the result of the request
		serialize(response, result);
	}
}
