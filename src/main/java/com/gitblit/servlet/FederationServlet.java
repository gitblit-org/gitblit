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

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import com.gitblit.Constants.FederationRequest;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.IFederationManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.models.FederationModel;
import com.gitblit.models.FederationProposal;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.FederationUtils;
import com.gitblit.utils.FileUtils;
import com.gitblit.utils.HttpUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;

import dagger.ObjectGraph;

/**
 * Handles federation requests.
 *
 * @author James Moger
 *
 */
public class FederationServlet extends JsonServlet {

	private static final long serialVersionUID = 1L;

	private IStoredSettings settings;

	private IUserManager userManager;

	private IRepositoryManager repositoryManager;

	private IFederationManager federationManager;

	@Override
	protected void inject(ObjectGraph dagger) {
		this.settings = dagger.get(IStoredSettings.class);
		this.userManager = dagger.get(IUserManager.class);
		this.repositoryManager = dagger.get(IRepositoryManager.class);
		this.federationManager = dagger.get(IFederationManager.class);
	}

	/**
	 * Processes a federation request.
	 *
	 * @param request
	 * @param response
	 * @throws javax.servlet.ServletException
	 * @throws java.io.IOException
	 */

	@Override
	protected void processRequest(javax.servlet.http.HttpServletRequest request,
			javax.servlet.http.HttpServletResponse response) throws javax.servlet.ServletException,
			java.io.IOException {

		FederationRequest reqType = FederationRequest.fromName(request.getParameter("req"));
		logger.info(MessageFormat.format("Federation {0} request from {1}", reqType,
				request.getRemoteAddr()));

		if (FederationRequest.POKE.equals(reqType)) {
			// Gitblit always responds to POKE requests to verify a connection
			logger.info("Received federation POKE from " + request.getRemoteAddr());
			return;
		}

		if (!settings.getBoolean(Keys.git.enableGitServlet, true)) {
			logger.warn(Keys.git.enableGitServlet + " must be set TRUE for federation requests.");
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		String uuid = settings.getString(Keys.federation.passphrase, "");
		if (StringUtils.isEmpty(uuid)) {
			logger.warn(Keys.federation.passphrase
					+ " is not properly set!  Federation request denied.");
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		if (FederationRequest.PROPOSAL.equals(reqType)) {
			// Receive a gitblit federation proposal
			FederationProposal proposal = deserialize(request, response, FederationProposal.class);
			if (proposal == null) {
				return;
			}

			// reject proposal, if not receipt prohibited
			if (!settings.getBoolean(Keys.federation.allowProposals, false)) {
				logger.error(MessageFormat.format("Rejected {0} federation proposal from {1}",
						proposal.tokenType.name(), proposal.url));
				response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
				return;
			}

			// poke the origin Gitblit instance that is proposing federation
			boolean poked = false;
			try {
				poked = FederationUtils.poke(proposal.url);
			} catch (Exception e) {
				logger.error("Failed to poke origin", e);
			}
			if (!poked) {
				logger.error(MessageFormat.format("Failed to send federation poke to {0}",
						proposal.url));
				response.setStatus(HttpServletResponse.SC_NOT_ACCEPTABLE);
				return;
			}

			String gitblitUrl = settings.getString(Keys.web.canonicalUrl, null);
			if (StringUtils.isEmpty(gitblitUrl)) {
				gitblitUrl = HttpUtils.getGitblitURL(request);
			}
			federationManager.submitFederationProposal(proposal, gitblitUrl);
			logger.info(MessageFormat.format(
					"Submitted {0} federation proposal to pull {1} repositories from {2}",
					proposal.tokenType.name(), proposal.repositories.size(), proposal.url));
			response.setStatus(HttpServletResponse.SC_OK);
			return;
		}

		if (FederationRequest.STATUS.equals(reqType)) {
			// Receive a gitblit federation status acknowledgment
			String remoteId = StringUtils.decodeFromHtml(request.getParameter("url"));
			String identification = MessageFormat.format("{0} ({1})", remoteId,
					request.getRemoteAddr());

			// deserialize the status data
			FederationModel results = deserialize(request, response, FederationModel.class);
			if (results == null) {
				return;
			}

			// setup the last and netx pull dates
			results.lastPull = new Date();
			int mins = TimeUtils.convertFrequencyToMinutes(results.frequency, 5);
			results.nextPull = new Date(System.currentTimeMillis() + (mins * 60 * 1000L));

			// acknowledge the receipt of status
			federationManager.acknowledgeFederationStatus(identification, results);
			logger.info(MessageFormat.format(
					"Received status of {0} federated repositories from {1}", results
							.getStatusList().size(), identification));
			response.setStatus(HttpServletResponse.SC_OK);
			return;
		}

		// Determine the federation tokens for this gitblit instance
		String token = request.getParameter("token");
		List<String> tokens = federationManager.getFederationTokens();
		if (!tokens.contains(token)) {
			logger.warn(MessageFormat.format(
					"Received Federation token ''{0}'' does not match the server tokens", token));
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		Object result = null;
		if (FederationRequest.PULL_REPOSITORIES.equals(reqType)) {
			String gitblitUrl = settings.getString(Keys.web.canonicalUrl, null);
			if (StringUtils.isEmpty(gitblitUrl)) {
				gitblitUrl = HttpUtils.getGitblitURL(request);
			}
			result = federationManager.getRepositories(gitblitUrl, token);
		} else {
			if (FederationRequest.PULL_SETTINGS.equals(reqType)) {
				// pull settings
				if (!federationManager.validateFederationRequest(reqType, token)) {
					// invalid token to pull users or settings
					logger.warn(MessageFormat.format(
							"Federation token from {0} not authorized to pull SETTINGS",
							request.getRemoteAddr()));
					response.sendError(HttpServletResponse.SC_FORBIDDEN);
					return;
				}
				Map<String, String> map = new HashMap<String, String>();
				List<String> keys = settings.getAllKeys(null);
				for (String key : keys) {
					map.put(key, settings.getString(key, ""));
				}
				result = map;
			} else if (FederationRequest.PULL_USERS.equals(reqType)) {
				// pull users
				if (!federationManager.validateFederationRequest(reqType, token)) {
					// invalid token to pull users or settings
					logger.warn(MessageFormat.format(
							"Federation token from {0} not authorized to pull USERS",
							request.getRemoteAddr()));
					response.sendError(HttpServletResponse.SC_FORBIDDEN);
					return;
				}
				List<String> usernames = userManager.getAllUsernames();
				List<UserModel> users = new ArrayList<UserModel>();
				for (String username : usernames) {
					UserModel user = userManager.getUserModel(username);
					if (!user.excludeFromFederation) {
						users.add(user);
					}
				}
				result = users;
			} else if (FederationRequest.PULL_TEAMS.equals(reqType)) {
				// pull teams
				if (!federationManager.validateFederationRequest(reqType, token)) {
					// invalid token to pull teams
					logger.warn(MessageFormat.format(
							"Federation token from {0} not authorized to pull TEAMS",
							request.getRemoteAddr()));
					response.sendError(HttpServletResponse.SC_FORBIDDEN);
					return;
				}
				List<String> teamnames = userManager.getAllTeamNames();
				List<TeamModel> teams = new ArrayList<TeamModel>();
				for (String teamname : teamnames) {
					TeamModel user = userManager.getTeamModel(teamname);
					teams.add(user);
				}
				result = teams;
			} else if (FederationRequest.PULL_SCRIPTS.equals(reqType)) {
				// pull scripts
				if (!federationManager.validateFederationRequest(reqType, token)) {
					// invalid token to pull script
					logger.warn(MessageFormat.format(
							"Federation token from {0} not authorized to pull SCRIPTS",
							request.getRemoteAddr()));
					response.sendError(HttpServletResponse.SC_FORBIDDEN);
					return;
				}
				Map<String, String> scripts = new HashMap<String, String>();

				Set<String> names = new HashSet<String>();
				names.addAll(settings.getStrings(Keys.groovy.preReceiveScripts));
				names.addAll(settings.getStrings(Keys.groovy.postReceiveScripts));
				for (TeamModel team :  userManager.getAllTeams()) {
					names.addAll(team.preReceiveScripts);
					names.addAll(team.postReceiveScripts);
				}
				File scriptsFolder = repositoryManager.getHooksFolder();
				for (String name : names) {
					File file = new File(scriptsFolder, name);
					if (!file.exists() && !file.getName().endsWith(".groovy")) {
						file = new File(scriptsFolder, name + ".groovy");
					}
					if (file.exists()) {
						// read the script
						String content = FileUtils.readContent(file, "\n");
						scripts.put(name, content);
					} else {
						// missing script?!
						logger.warn(MessageFormat.format("Failed to find push script \"{0}\"", name));
					}
				}
				result = scripts;
			}
		}

		// send the result of the request
		serialize(response, result);
	}
}
