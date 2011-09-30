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

import java.io.BufferedReader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants.FederationRequest;
import com.gitblit.Constants.FederationToken;
import com.gitblit.models.FederationModel;
import com.gitblit.models.FederationProposal;
import com.gitblit.models.UserModel;
import com.gitblit.utils.FederationUtils;
import com.gitblit.utils.HttpUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Handles federation requests.
 * 
 * @author James Moger
 * 
 */
public class FederationServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private transient Logger logger = LoggerFactory.getLogger(FederationServlet.class);

	public FederationServlet() {
		super();
	}

	/**
	 * Returns an url to this servlet for the specified parameters.
	 * 
	 * @param sourceURL
	 *            the url of the source gitblit instance
	 * @param token
	 *            the federation token of the source gitblit instance
	 * @param req
	 *            the pull type request
	 */
	public static String asFederationLink(String sourceURL, String token, FederationRequest req) {
		return asFederationLink(sourceURL, null, token, req, null);
	}

	/**
	 * 
	 * @param remoteURL
	 *            the url of the remote gitblit instance
	 * @param tokenType
	 *            the type of federation token of a gitblit instance
	 * @param token
	 *            the federation token of a gitblit instance
	 * @param req
	 *            the pull type request
	 * @param myURL
	 *            the url of this gitblit instance
	 * @return
	 */
	public static String asFederationLink(String remoteURL, FederationToken tokenType,
			String token, FederationRequest req, String myURL) {
		if (remoteURL.length() > 0 && remoteURL.charAt(remoteURL.length() - 1) == '/') {
			remoteURL = remoteURL.substring(0, remoteURL.length() - 1);
		}
		if (req == null) {
			req = FederationRequest.PULL_REPOSITORIES;
		}
		return remoteURL + Constants.FEDERATION_PATH + "?req=" + req.name().toLowerCase()
				+ (token == null ? "" : ("&token=" + token))
				+ (tokenType == null ? "" : ("&tokenType=" + tokenType.name().toLowerCase()))
				+ (myURL == null ? "" : ("&url=" + StringUtils.encodeURL(myURL)));
	}

	/**
	 * Returns the list of repositories for federation requests.
	 * 
	 * @param request
	 * @param response
	 * @throws javax.servlet.ServletException
	 * @throws java.io.IOException
	 */
	private void processRequest(javax.servlet.http.HttpServletRequest request,
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

		if (!GitBlit.getBoolean(Keys.git.enableGitServlet, true)) {
			logger.warn(Keys.git.enableGitServlet + " must be set TRUE for federation requests.");
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		String uuid = GitBlit.getString(Keys.federation.passphrase, "");
		if (StringUtils.isEmpty(uuid)) {
			logger.warn(Keys.federation.passphrase
					+ " is not properly set!  Federation request denied.");
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		if (FederationRequest.PROPOSAL.equals(reqType)) {
			// Receive a gitblit federation proposal
			BufferedReader reader = request.getReader();
			StringBuilder json = new StringBuilder();
			String line = null;
			while ((line = reader.readLine()) != null) {
				json.append(line);
			}
			reader.close();

			// check to see if we have proposal data
			if (json.length() == 0) {
				logger.error(MessageFormat.format("Failed to receive proposal data from {0}",
						request.getRemoteAddr()));
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				return;
			}

			// deserialize the proposal
			Gson gson = new Gson();
			FederationProposal proposal = gson.fromJson(json.toString(), FederationProposal.class);

			// reject proposal, if not receipt prohibited
			if (!GitBlit.getBoolean(Keys.federation.allowProposals, false)) {
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

			String url = HttpUtils.getGitblitURL(request);
			GitBlit.self().submitFederationProposal(proposal, url);
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
			BufferedReader reader = request.getReader();
			StringBuilder json = new StringBuilder();
			String line = null;
			while ((line = reader.readLine()) != null) {
				json.append(line);
			}
			reader.close();

			// check to see if we have repository data
			if (json.length() == 0) {
				logger.error(MessageFormat.format(
						"Failed to receive pulled repositories list from {0}", identification));
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				return;
			}

			// deserialize the status data
			Gson gson = new Gson();
			FederationModel results = gson.fromJson(json.toString(), FederationModel.class);
			// setup the last and netx pull dates
			results.lastPull = new Date();
			int mins = TimeUtils.convertFrequencyToMinutes(results.frequency);
			results.nextPull = new Date(System.currentTimeMillis() + (mins * 60 * 1000L));

			// acknowledge the receipt of status
			GitBlit.self().acknowledgeFederationStatus(identification, results);
			logger.info(MessageFormat.format(
					"Received status of {0} federated repositories from {1}", results
							.getStatusList().size(), identification));
			response.setStatus(HttpServletResponse.SC_OK);
			return;
		}

		// Determine the federation tokens for this gitblit instance
		String token = request.getParameter("token");
		List<String> tokens = GitBlit.self().getFederationTokens();
		if (!tokens.contains(token)) {
			logger.warn(MessageFormat.format(
					"Received Federation token ''{0}'' does not match the server tokens", token));
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		Object result = null;
		if (FederationRequest.PULL_REPOSITORIES.equals(reqType)) {
			String gitblitUrl = HttpUtils.getGitblitURL(request);
			result = GitBlit.self().getRepositories(gitblitUrl, token);
		} else {
			if (FederationRequest.PULL_SETTINGS.equals(reqType)) {
				// pull settings
				if (!GitBlit.self().validateFederationRequest(reqType, token)) {
					// invalid token to pull users or settings
					logger.warn(MessageFormat.format(
							"Federation token from {0} not authorized to pull SETTINGS",
							request.getRemoteAddr()));
					response.sendError(HttpServletResponse.SC_FORBIDDEN);
					return;
				}
				Map<String, String> settings = new HashMap<String, String>();
				List<String> keys = GitBlit.getAllKeys(null);
				for (String key : keys) {
					settings.put(key, GitBlit.getString(key, ""));
				}
				result = settings;
			} else if (FederationRequest.PULL_USERS.equals(reqType)) {
				// pull users
				if (!GitBlit.self().validateFederationRequest(reqType, token)) {
					// invalid token to pull users or settings
					logger.warn(MessageFormat.format(
							"Federation token from {0} not authorized to pull USERS",
							request.getRemoteAddr()));
					response.sendError(HttpServletResponse.SC_FORBIDDEN);
					return;
				}
				List<String> usernames = GitBlit.self().getAllUsernames();
				List<UserModel> users = new ArrayList<UserModel>();
				for (String username : usernames) {
					UserModel user = GitBlit.self().getUserModel(username);
					if (!user.excludeFromFederation) {
						users.add(user);
					}
				}
				result = users;
			}
		}

		if (result != null) {
			// Send JSON response
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			String json = gson.toJson(result);
			response.getWriter().append(json);
		}
	}

	@Override
	protected void doPost(javax.servlet.http.HttpServletRequest request,
			javax.servlet.http.HttpServletResponse response) throws javax.servlet.ServletException,
			java.io.IOException {
		processRequest(request, response);
	}

	@Override
	protected void doGet(javax.servlet.http.HttpServletRequest request,
			javax.servlet.http.HttpServletResponse response) throws javax.servlet.ServletException,
			java.io.IOException {
		processRequest(request, response);
	}
}
