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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lib.Repository;

import com.gitblit.Constants;
import com.gitblit.Constants.RpcRequest;
import com.gitblit.GitBlitException;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.IGitblit;
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
 */
@Singleton
public class RpcServlet extends JsonServlet {

	private static final long serialVersionUID = 1L;

	public static final int PROTOCOL_VERSION = 8;

	private IStoredSettings settings;

	private IGitblit gitblit;

	@Inject
	public RpcServlet(IStoredSettings settings, IGitblit gitblit) {
		this.settings = settings;
		this.gitblit = gitblit;
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
	protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException,
			IOException {
		RpcRequest reqType = RpcRequest.fromName(request.getParameter("req"));
		String objectName = request.getParameter("name");
		logger.info(MessageFormat.format("Rpc {0} request from {1}", reqType, request.getRemoteAddr()));

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
			String gitblitUrl = settings.getString(Keys.web.canonicalUrl, null);
			if (StringUtils.isEmpty(gitblitUrl)) {
				gitblitUrl = HttpUtils.getGitblitURL(request);
			}
			StringBuilder sb = new StringBuilder();
			sb.append(gitblitUrl);
			sb.append(Constants.R_PATH);
			sb.append("{0}");
			String cloneUrl = sb.toString();

			// list repositories
			List<RepositoryModel> list = gitblit.getRepositoryModels(user);
			Map<String, RepositoryModel> repositories = new HashMap<String, RepositoryModel>();
			for (RepositoryModel model : list) {
				String url = MessageFormat.format(cloneUrl, model.name);
				repositories.put(url, model);
			}
			result = repositories;
		} else if (RpcRequest.LIST_BRANCHES.equals(reqType)) {
			// list all local branches in all repositories accessible to user
			Map<String, List<String>> localBranches = new HashMap<String, List<String>>();
			List<RepositoryModel> models = gitblit.getRepositoryModels(user);
			for (RepositoryModel model : models) {
				if (!model.hasCommits) {
					// skip empty repository
					continue;
				}
				if (model.isCollectingGarbage) {
					// skip garbage collecting repository
					logger.warn(MessageFormat.format("Temporarily excluding {0} from RPC, busy collecting garbage",
							model.name));
					continue;
				}
				// get local branches
				Repository repository = gitblit.getRepository(model.name);
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
					UserModel requestedUser = gitblit.getUserModel(objectName);
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
			List<String> names = gitblit.getAllUsernames();
			List<UserModel> users = new ArrayList<UserModel>();
			for (String name : names) {
				users.add(gitblit.getUserModel(name));
			}
			result = users;
		} else if (RpcRequest.LIST_TEAMS.equals(reqType)) {
			// list teams
			List<String> names = gitblit.getAllTeamNames();
			List<TeamModel> teams = new ArrayList<TeamModel>();
			for (String name : names) {
				teams.add(gitblit.getTeamModel(name));
			}
			result = teams;
		} else if (RpcRequest.CREATE_REPOSITORY.equals(reqType)) {
			// create repository
			RepositoryModel model = deserialize(request, response, RepositoryModel.class);
			try {
				gitblit.updateRepositoryModel(model.name, model, true);
			} catch (GitBlitException e) {
				response.setStatus(failureCode);
			}
		} else if (RpcRequest.FORK_REPOSITORY.equals(reqType)) {
			// fork repository
			RepositoryModel origin = gitblit.getRepositoryModel(objectName);
			if (origin == null) {
				// failed to find repository, error is logged by the repository
				// manager
				response.setStatus(failureCode);
			} else {
				if (user == null || !user.canFork(origin)) {
					logger.error("User {} is not permitted to fork '{}'!", user == null ? "anonymous" : user.username,
							objectName);
					response.setStatus(failureCode);
				} else {
					try {
						// fork the origin
						RepositoryModel fork = gitblit.fork(origin, user);
						if (fork == null) {
							logger.error("Failed to fork repository '{}'!", objectName);
							response.setStatus(failureCode);
						} else {
							logger.info("User {} has forked '{}'!", user.username, objectName);
						}
					} catch (GitBlitException e) {
						response.setStatus(failureCode);
					}
				}
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
				gitblit.updateRepositoryModel(repoName, model, false);
			} catch (GitBlitException e) {
				response.setStatus(failureCode);
			}
		} else if (RpcRequest.DELETE_REPOSITORY.equals(reqType)) {
			// delete repository
			RepositoryModel model = deserialize(request, response, RepositoryModel.class);
			gitblit.deleteRepositoryModel(model);
		} else if (RpcRequest.CREATE_USER.equals(reqType)) {
			// create user
			UserModel model = deserialize(request, response, UserModel.class);
			try {
				gitblit.addUser(model);
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
				gitblit.reviseUser(username, model);
			} catch (GitBlitException e) {
				response.setStatus(failureCode);
			}
		} else if (RpcRequest.DELETE_USER.equals(reqType)) {
			// delete user
			UserModel model = deserialize(request, response, UserModel.class);
			if (!gitblit.deleteUser(model.username)) {
				response.setStatus(failureCode);
			}
		} else if (RpcRequest.CREATE_TEAM.equals(reqType)) {
			// create team
			TeamModel model = deserialize(request, response, TeamModel.class);
			try {
				gitblit.addTeam(model);
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
				gitblit.reviseTeam(teamname, model);
			} catch (GitBlitException e) {
				response.setStatus(failureCode);
			}
		} else if (RpcRequest.DELETE_TEAM.equals(reqType)) {
			// delete team
			TeamModel model = deserialize(request, response, TeamModel.class);
			if (!gitblit.deleteTeam(model.name)) {
				response.setStatus(failureCode);
			}
		} else if (RpcRequest.LIST_REPOSITORY_MEMBERS.equals(reqType)) {
			// get repository members
			RepositoryModel model = gitblit.getRepositoryModel(objectName);
			result = gitblit.getRepositoryUsers(model);
		} else if (RpcRequest.SET_REPOSITORY_MEMBERS.equals(reqType)) {
			// rejected since 1.2.0
			response.setStatus(failureCode);
		} else if (RpcRequest.LIST_REPOSITORY_MEMBER_PERMISSIONS.equals(reqType)) {
			// get repository member permissions
			RepositoryModel model = gitblit.getRepositoryModel(objectName);
			result = gitblit.getUserAccessPermissions(model);
		} else if (RpcRequest.SET_REPOSITORY_MEMBER_PERMISSIONS.equals(reqType)) {
			// set the repository permissions for the specified users
			RepositoryModel model = gitblit.getRepositoryModel(objectName);
			Collection<RegistrantAccessPermission> permissions = deserialize(request, response,
					RpcUtils.REGISTRANT_PERMISSIONS_TYPE);
			result = gitblit.setUserAccessPermissions(model, permissions);
		} else if (RpcRequest.LIST_REPOSITORY_TEAMS.equals(reqType)) {
			// get repository teams
			RepositoryModel model = gitblit.getRepositoryModel(objectName);
			result = gitblit.getRepositoryTeams(model);
		} else if (RpcRequest.SET_REPOSITORY_TEAMS.equals(reqType)) {
			// rejected since 1.2.0
			response.setStatus(failureCode);
		} else if (RpcRequest.LIST_REPOSITORY_TEAM_PERMISSIONS.equals(reqType)) {
			// get repository team permissions
			RepositoryModel model = gitblit.getRepositoryModel(objectName);
			result = gitblit.getTeamAccessPermissions(model);
		} else if (RpcRequest.SET_REPOSITORY_TEAM_PERMISSIONS.equals(reqType)) {
			// set the repository permissions for the specified teams
			RepositoryModel model = gitblit.getRepositoryModel(objectName);
			Collection<RegistrantAccessPermission> permissions = deserialize(request, response,
					RpcUtils.REGISTRANT_PERMISSIONS_TYPE);
			result = gitblit.setTeamAccessPermissions(model, permissions);
		} else if (RpcRequest.LIST_FEDERATION_REGISTRATIONS.equals(reqType)) {
			// return the list of federation registrations
			if (allowAdmin) {
				result = gitblit.getFederationRegistrations();
			} else {
				response.sendError(notAllowedCode);
			}
		} else if (RpcRequest.LIST_FEDERATION_RESULTS.equals(reqType)) {
			// return the list of federation result registrations
			if (allowAdmin && gitblit.canFederate()) {
				result = gitblit.getFederationResultRegistrations();
			} else {
				response.sendError(notAllowedCode);
			}
		} else if (RpcRequest.LIST_FEDERATION_PROPOSALS.equals(reqType)) {
			// return the list of federation proposals
			if (allowAdmin && gitblit.canFederate()) {
				result = gitblit.getPendingFederationProposals();
			} else {
				response.sendError(notAllowedCode);
			}
		} else if (RpcRequest.LIST_FEDERATION_SETS.equals(reqType)) {
			// return the list of federation sets
			if (allowAdmin && gitblit.canFederate()) {
				String gitblitUrl = settings.getString(Keys.web.canonicalUrl, null);
				if (StringUtils.isEmpty(gitblitUrl)) {
					gitblitUrl = HttpUtils.getGitblitURL(request);
				}
				result = gitblit.getFederationSets(gitblitUrl);
			} else {
				response.sendError(notAllowedCode);
			}
		} else if (RpcRequest.LIST_SETTINGS.equals(reqType)) {
			// return the server's settings
			ServerSettings serverSettings = gitblit.getSettingsModel();
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
				Map<String, String> map = deserialize(request, response, RpcUtils.SETTINGS_TYPE);
				gitblit.updateSettings(map);
			} else {
				response.sendError(notAllowedCode);
			}
		} else if (RpcRequest.LIST_STATUS.equals(reqType)) {
			// return the server's status information
			if (allowAdmin) {
				result = gitblit.getStatus();
			} else {
				response.sendError(notAllowedCode);
			}
		} else if (RpcRequest.CLEAR_REPOSITORY_CACHE.equals(reqType)) {
			// clear the repository list cache
			if (allowManagement) {
				gitblit.resetRepositoryListCache();
			} else {
				response.sendError(notAllowedCode);
			}
		} else if (RpcRequest.REINDEX_TICKETS.equals(reqType)) {
			if (allowManagement) {
				if (StringUtils.isEmpty(objectName)) {
					// reindex all tickets
					gitblit.getTicketService().reindex();
				} else {
					// reindex tickets in a specific repository
					RepositoryModel model = gitblit.getRepositoryModel(objectName);
					gitblit.getTicketService().reindex(model);
				}
			} else {
				response.sendError(notAllowedCode);
			}
		}

		// send the result of the request
		serialize(response, result);
	}
}
