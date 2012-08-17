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

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lib.Repository;

import com.gitblit.Constants.RpcRequest;
import com.gitblit.models.RefModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.ServerSettings;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.HttpUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.RpcUtils;

/**
 * Handles remote procedure calls.
 * 
 * @author James Moger
 * 
 */
public class RpcServlet extends JsonServlet {

	private static final long serialVersionUID = 1L;

	public static final int PROTOCOL_VERSION = 4;

	public RpcServlet() {
		super();
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

		boolean allowManagement = user != null && user.canAdmin
				&& GitBlit.getBoolean(Keys.web.enableRpcManagement, false);

		boolean allowAdmin = user != null && user.canAdmin
				&& GitBlit.getBoolean(Keys.web.enableRpcAdministration, false);

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
			List<RepositoryModel> list = GitBlit.self().getRepositoryModels(user);
			Map<String, RepositoryModel> repositories = new HashMap<String, RepositoryModel>();
			for (RepositoryModel model : list) {
				String url = MessageFormat.format(cloneUrl, model.name);
				repositories.put(url, model);
			}
			result = repositories;
		} else if (RpcRequest.LIST_BRANCHES.equals(reqType)) {
			// list all local branches in all repositories accessible to user
			Map<String, List<String>> localBranches = new HashMap<String, List<String>>();
			List<RepositoryModel> models = GitBlit.self().getRepositoryModels(user);
			for (RepositoryModel model : models) {
				if (!model.hasCommits) {
					// skip empty repository
					continue;
				}
				// get local branches
				Repository repository = GitBlit.self().getRepository(model.name);
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
		} else if (RpcRequest.LIST_USERS.equals(reqType)) {
			// list users
			List<String> names = GitBlit.self().getAllUsernames();
			List<UserModel> users = new ArrayList<UserModel>();
			for (String name : names) {
				users.add(GitBlit.self().getUserModel(name));
			}
			result = users;
		} else if (RpcRequest.LIST_TEAMS.equals(reqType)) {
			// list teams
			List<String> names = GitBlit.self().getAllTeamnames();
			List<TeamModel> teams = new ArrayList<TeamModel>();
			for (String name : names) {
				teams.add(GitBlit.self().getTeamModel(name));
			}
			result = teams;
		} else if (RpcRequest.CREATE_REPOSITORY.equals(reqType)) {
			// create repository
			RepositoryModel model = deserialize(request, response, RepositoryModel.class);
			try {
				GitBlit.self().updateRepositoryModel(model.name, model, true);
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
				GitBlit.self().updateRepositoryModel(repoName, model, false);
			} catch (GitBlitException e) {
				response.setStatus(failureCode);
			}
		} else if (RpcRequest.DELETE_REPOSITORY.equals(reqType)) {
			// delete repository
			RepositoryModel model = deserialize(request, response, RepositoryModel.class);
			GitBlit.self().deleteRepositoryModel(model);
		} else if (RpcRequest.CREATE_USER.equals(reqType)) {
			// create user
			UserModel model = deserialize(request, response, UserModel.class);
			try {
				GitBlit.self().updateUserModel(model.username, model, true);
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
				GitBlit.self().updateUserModel(username, model, false);
			} catch (GitBlitException e) {
				response.setStatus(failureCode);
			}
		} else if (RpcRequest.DELETE_USER.equals(reqType)) {
			// delete user
			UserModel model = deserialize(request, response, UserModel.class);
			if (!GitBlit.self().deleteUser(model.username)) {
				response.setStatus(failureCode);
			}
		} else if (RpcRequest.CREATE_TEAM.equals(reqType)) {
			// create team
			TeamModel model = deserialize(request, response, TeamModel.class);
			try {
				GitBlit.self().updateTeamModel(model.name, model, true);
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
				GitBlit.self().updateTeamModel(teamname, model, false);
			} catch (GitBlitException e) {
				response.setStatus(failureCode);
			}
		} else if (RpcRequest.DELETE_TEAM.equals(reqType)) {
			// delete team
			TeamModel model = deserialize(request, response, TeamModel.class);
			if (!GitBlit.self().deleteTeam(model.name)) {
				response.setStatus(failureCode);
			}
		} else if (RpcRequest.LIST_REPOSITORY_MEMBERS.equals(reqType)) {
			// get repository members
			RepositoryModel model = GitBlit.self().getRepositoryModel(objectName);
			result = GitBlit.self().getRepositoryUsers(model);
		} else if (RpcRequest.SET_REPOSITORY_MEMBERS.equals(reqType)) {
			// update repository access list
			RepositoryModel model = GitBlit.self().getRepositoryModel(objectName);
			Collection<String> names = deserialize(request, response, RpcUtils.NAMES_TYPE);
			List<String> users = new ArrayList<String>(names);
			if (!GitBlit.self().setRepositoryUsers(model, users)) {
				response.setStatus(failureCode);
			}
		} else if (RpcRequest.LIST_REPOSITORY_TEAMS.equals(reqType)) {
			// get repository teams
			RepositoryModel model = GitBlit.self().getRepositoryModel(objectName);
			result = GitBlit.self().getRepositoryTeams(model);
		} else if (RpcRequest.SET_REPOSITORY_TEAMS.equals(reqType)) {
			// update repository team access list
			RepositoryModel model = GitBlit.self().getRepositoryModel(objectName);
			Collection<String> names = deserialize(request, response, RpcUtils.NAMES_TYPE);
			List<String> teams = new ArrayList<String>(names);
			if (!GitBlit.self().setRepositoryTeams(model, teams)) {
				response.setStatus(failureCode);
			}
		} else if (RpcRequest.LIST_FEDERATION_REGISTRATIONS.equals(reqType)) {
			// return the list of federation registrations
			if (allowAdmin) {
				result = GitBlit.self().getFederationRegistrations();
			} else {
				response.sendError(notAllowedCode);
			}
		} else if (RpcRequest.LIST_FEDERATION_RESULTS.equals(reqType)) {
			// return the list of federation result registrations
			if (allowAdmin && GitBlit.canFederate()) {
				result = GitBlit.self().getFederationResultRegistrations();
			} else {
				response.sendError(notAllowedCode);
			}
		} else if (RpcRequest.LIST_FEDERATION_PROPOSALS.equals(reqType)) {
			// return the list of federation proposals
			if (allowAdmin && GitBlit.canFederate()) {
				result = GitBlit.self().getPendingFederationProposals();
			} else {
				response.sendError(notAllowedCode);
			}
		} else if (RpcRequest.LIST_FEDERATION_SETS.equals(reqType)) {
			// return the list of federation sets
			if (allowAdmin && GitBlit.canFederate()) {
				String gitblitUrl = HttpUtils.getGitblitURL(request);
				result = GitBlit.self().getFederationSets(gitblitUrl);
			} else {
				response.sendError(notAllowedCode);
			}
		} else if (RpcRequest.LIST_SETTINGS.equals(reqType)) {
			// return the server's settings
			ServerSettings settings = GitBlit.self().getSettingsModel();
			if (allowAdmin) {
				// return all settings
				result = settings;
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
					managementSettings.add(settings.get(key));
				}
				if (allowManagement) {
					managementSettings.pushScripts = settings.pushScripts;
				}
				result = managementSettings;
			}
		} else if (RpcRequest.EDIT_SETTINGS.equals(reqType)) {
			// update settings on the server
			if (allowAdmin) {
				Map<String, String> settings = deserialize(request, response,
						RpcUtils.SETTINGS_TYPE);
				GitBlit.self().updateSettings(settings);
			} else {
				response.sendError(notAllowedCode);
			}
		} else if (RpcRequest.LIST_STATUS.equals(reqType)) {
			// return the server's status information
			if (allowAdmin) {
				result = GitBlit.self().getStatus();
			} else {
				response.sendError(notAllowedCode);
			}
		} else if (RpcRequest.CLEAR_REPOSITORY_CACHE.equals(reqType)) {
			// clear the repository list cache
			if (allowAdmin) {
				GitBlit.self().resetRepositoryListCache();
			} else {
				response.sendError(notAllowedCode);
			}
		}

		// send the result of the request
		serialize(response, result);
	}
}
