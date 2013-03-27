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
package com.gitblit.utils;

import java.lang.reflect.Type;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.Constants.FederationProposalResult;
import com.gitblit.Constants.FederationRequest;
import com.gitblit.Constants.FederationToken;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.models.FederationModel;
import com.gitblit.models.FederationProposal;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.google.gson.reflect.TypeToken;

/**
 * Utility methods for federation functions.
 * 
 * @author James Moger
 * 
 */
public class FederationUtils {

	private static final Type REPOSITORIES_TYPE = new TypeToken<Map<String, RepositoryModel>>() {
	}.getType();

	private static final Type SETTINGS_TYPE = new TypeToken<Map<String, String>>() {
	}.getType();

	private static final Type USERS_TYPE = new TypeToken<Collection<UserModel>>() {
	}.getType();

	private static final Type TEAMS_TYPE = new TypeToken<Collection<TeamModel>>() {
	}.getType();

	private static final Logger LOGGER = LoggerFactory.getLogger(FederationUtils.class);

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
	public static String asLink(String sourceURL, String token, FederationRequest req) {
		return asLink(sourceURL, null, token, req, null);
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
	public static String asLink(String remoteURL, FederationToken tokenType, String token,
			FederationRequest req, String myURL) {
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
	 * Returns the list of federated gitblit instances that this instance will
	 * try to pull.
	 * 
	 * @return list of registered gitblit instances
	 */
	public static List<FederationModel> getFederationRegistrations(IStoredSettings settings) {
		List<FederationModel> federationRegistrations = new ArrayList<FederationModel>();
		List<String> keys = settings.getAllKeys(Keys.federation._ROOT);
		keys.remove(Keys.federation.name);
		keys.remove(Keys.federation.passphrase);
		keys.remove(Keys.federation.allowProposals);
		keys.remove(Keys.federation.proposalsFolder);
		keys.remove(Keys.federation.defaultFrequency);
		keys.remove(Keys.federation.sets);
		Collections.sort(keys);
		Map<String, FederationModel> federatedModels = new HashMap<String, FederationModel>();
		for (String key : keys) {
			String value = key.substring(Keys.federation._ROOT.length() + 1);
			List<String> values = StringUtils.getStringsFromValue(value, "\\.");
			String server = values.get(0);
			if (!federatedModels.containsKey(server)) {
				federatedModels.put(server, new FederationModel(server));
			}
			String setting = values.get(1);
			if (setting.equals("url")) {
				// url of the origin Gitblit instance
				federatedModels.get(server).url = settings.getString(key, "");
			} else if (setting.equals("token")) {
				// token for the origin Gitblit instance
				federatedModels.get(server).token = settings.getString(key, "");
			} else if (setting.equals("frequency")) {
				// frequency of the pull operation
				federatedModels.get(server).frequency = settings.getString(key, "");
			} else if (setting.equals("folder")) {
				// destination folder of the pull operation
				federatedModels.get(server).folder = settings.getString(key, "");
			} else if (setting.equals("bare")) {
				// whether pulled repositories should be bare
				federatedModels.get(server).bare = settings.getBoolean(key, true);
			} else if (setting.equals("mirror")) {
				// are the repositories to be true mirrors of the origin
				federatedModels.get(server).mirror = settings.getBoolean(key, true);
			} else if (setting.equals("mergeAccounts")) {
				// merge remote accounts into local accounts
				federatedModels.get(server).mergeAccounts = settings.getBoolean(key, false);
			} else if (setting.equals("sendStatus")) {
				// send a status acknowledgment to source Gitblit instance
				// at end of git pull
				federatedModels.get(server).sendStatus = settings.getBoolean(key, false);
			} else if (setting.equals("notifyOnError")) {
				// notify administrators on federation pull failures
				federatedModels.get(server).notifyOnError = settings.getBoolean(key, false);
			} else if (setting.equals("exclude")) {
				// excluded repositories
				federatedModels.get(server).exclusions = settings.getStrings(key);
			} else if (setting.equals("include")) {
				// included repositories
				federatedModels.get(server).inclusions = settings.getStrings(key);
			}
		}

		// verify that registrations have a url and a token
		for (FederationModel model : federatedModels.values()) {
			if (StringUtils.isEmpty(model.url)) {
				LOGGER.warn(MessageFormat.format(
						"Dropping federation registration {0}. Missing url.", model.name));
				continue;
			}
			if (StringUtils.isEmpty(model.token)) {
				LOGGER.warn(MessageFormat.format(
						"Dropping federation registration {0}. Missing token.", model.name));
				continue;
			}
			// set default frequency if unspecified
			if (StringUtils.isEmpty(model.frequency)) {
				model.frequency = settings.getString(Keys.federation.defaultFrequency, "60 mins");
			}
			federationRegistrations.add(model);
		}
		return federationRegistrations;
	}

	/**
	 * Sends a federation poke to the Gitblit instance at remoteUrl. Pokes are
	 * sent by an pulling Gitblit instance to an origin Gitblit instance as part
	 * of the proposal process. This is to ensure that the pulling Gitblit
	 * instance has an IP route to the origin instance.
	 * 
	 * @param remoteUrl
	 *            the remote Gitblit instance to send a federation proposal to
	 * @param proposal
	 *            a complete federation proposal
	 * @return true if there is a route to the remoteUrl
	 */
	public static boolean poke(String remoteUrl) throws Exception {
		String url = asLink(remoteUrl, null, FederationRequest.POKE);
		String json = JsonUtils.toJsonString("POKE");
		int status = JsonUtils.sendJsonString(url, json);
		return status == HttpServletResponse.SC_OK;
	}

	/**
	 * Sends a federation proposal to the Gitblit instance at remoteUrl
	 * 
	 * @param remoteUrl
	 *            the remote Gitblit instance to send a federation proposal to
	 * @param proposal
	 *            a complete federation proposal
	 * @return the federation proposal result code
	 */
	public static FederationProposalResult propose(String remoteUrl, FederationProposal proposal)
			throws Exception {
		String url = asLink(remoteUrl, null, FederationRequest.PROPOSAL);
		String json = JsonUtils.toJsonString(proposal);
		int status = JsonUtils.sendJsonString(url, json);
		switch (status) {
		case HttpServletResponse.SC_FORBIDDEN:
			// remote Gitblit Federation disabled
			return FederationProposalResult.FEDERATION_DISABLED;
		case HttpServletResponse.SC_BAD_REQUEST:
			// remote Gitblit did not receive any JSON data
			return FederationProposalResult.MISSING_DATA;
		case HttpServletResponse.SC_METHOD_NOT_ALLOWED:
			// remote Gitblit not accepting proposals
			return FederationProposalResult.NO_PROPOSALS;
		case HttpServletResponse.SC_NOT_ACCEPTABLE:
			// remote Gitblit failed to poke this Gitblit instance
			return FederationProposalResult.NO_POKE;
		case HttpServletResponse.SC_OK:
			// received
			return FederationProposalResult.ACCEPTED;
		default:
			return FederationProposalResult.ERROR;
		}
	}

	/**
	 * Retrieves a map of the repositories at the remote gitblit instance keyed
	 * by the repository clone url.
	 * 
	 * @param registration
	 * @param checkExclusions
	 *            should returned repositories remove registration exclusions
	 * @return a map of cloneable repositories
	 * @throws Exception
	 */
	public static Map<String, RepositoryModel> getRepositories(FederationModel registration,
			boolean checkExclusions) throws Exception {
		String url = asLink(registration.url, registration.token,
				FederationRequest.PULL_REPOSITORIES);
		Map<String, RepositoryModel> models = JsonUtils.retrieveJson(url, REPOSITORIES_TYPE);
		if (checkExclusions) {
			Map<String, RepositoryModel> includedModels = new HashMap<String, RepositoryModel>();
			for (Map.Entry<String, RepositoryModel> entry : models.entrySet()) {
				if (registration.isIncluded(entry.getValue())) {
					includedModels.put(entry.getKey(), entry.getValue());
				}
			}
			return includedModels;
		}
		return models;
	}

	/**
	 * Tries to pull the gitblit user accounts from the remote gitblit instance.
	 * 
	 * @param registration
	 * @return a collection of UserModel objects
	 * @throws Exception
	 */
	public static List<UserModel> getUsers(FederationModel registration) throws Exception {
		String url = asLink(registration.url, registration.token, FederationRequest.PULL_USERS);
		Collection<UserModel> models = JsonUtils.retrieveJson(url, USERS_TYPE);
		List<UserModel> list = new ArrayList<UserModel>(models);
		return list;
	}

	/**
	 * Tries to pull the gitblit team definitions from the remote gitblit
	 * instance.
	 * 
	 * @param registration
	 * @return a collection of TeamModel objects
	 * @throws Exception
	 */
	public static List<TeamModel> getTeams(FederationModel registration) throws Exception {
		String url = asLink(registration.url, registration.token, FederationRequest.PULL_TEAMS);
		Collection<TeamModel> models = JsonUtils.retrieveJson(url, TEAMS_TYPE);
		List<TeamModel> list = new ArrayList<TeamModel>(models);
		return list;
	}

	/**
	 * Tries to pull the gitblit server settings from the remote gitblit
	 * instance.
	 * 
	 * @param registration
	 * @return a map of the remote gitblit settings
	 * @throws Exception
	 */
	public static Map<String, String> getSettings(FederationModel registration) throws Exception {
		String url = asLink(registration.url, registration.token, FederationRequest.PULL_SETTINGS);
		Map<String, String> settings = JsonUtils.retrieveJson(url, SETTINGS_TYPE);
		return settings;
	}

	/**
	 * Tries to pull the referenced scripts from the remote gitblit instance.
	 * 
	 * @param registration
	 * @return a map of the remote gitblit scripts by script name
	 * @throws Exception
	 */
	public static Map<String, String> getScripts(FederationModel registration) throws Exception {
		String url = asLink(registration.url, registration.token, FederationRequest.PULL_SCRIPTS);
		Map<String, String> scripts = JsonUtils.retrieveJson(url, SETTINGS_TYPE);
		return scripts;
	}

	/**
	 * Send an status acknowledgment to the remote Gitblit server.
	 * 
	 * @param identification
	 *            identification of this pulling instance
	 * @param registration
	 *            the source Gitblit instance to receive an acknowledgment
	 * @param results
	 *            the results of your pull operation
	 * @return true, if the remote Gitblit instance acknowledged your results
	 * @throws Exception
	 */
	public static boolean acknowledgeStatus(String identification, FederationModel registration)
			throws Exception {
		String url = asLink(registration.url, null, registration.token, FederationRequest.STATUS,
				identification);
		String json = JsonUtils.toJsonString(registration);
		int status = JsonUtils.sendJsonString(url, json);
		return status == HttpServletResponse.SC_OK;
	}
}
