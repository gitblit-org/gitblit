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

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.gitblit.Constants;
import com.gitblit.Constants.RpcRequest;
import com.gitblit.GitBlitException.UnknownRequestException;
import com.gitblit.models.FederationModel;
import com.gitblit.models.FederationProposal;
import com.gitblit.models.FederationSet;
import com.gitblit.models.FeedModel;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.ServerSettings;
import com.gitblit.models.ServerStatus;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.google.gson.reflect.TypeToken;

/**
 * Utility methods for rpc calls.
 *
 * @author James Moger
 *
 */
public class RpcUtils {

	public static final Type NAMES_TYPE = new TypeToken<Collection<String>>() {
	}.getType();

	public static final Type SETTINGS_TYPE = new TypeToken<Map<String, String>>() {
	}.getType();

	private static final Type REPOSITORIES_TYPE = new TypeToken<Map<String, RepositoryModel>>() {
	}.getType();

	private static final Type USERS_TYPE = new TypeToken<Collection<UserModel>>() {
	}.getType();

	private static final Type TEAMS_TYPE = new TypeToken<Collection<TeamModel>>() {
	}.getType();

	private static final Type REGISTRATIONS_TYPE = new TypeToken<Collection<FederationModel>>() {
	}.getType();

	private static final Type PROPOSALS_TYPE = new TypeToken<Collection<FederationProposal>>() {
	}.getType();

	private static final Type SETS_TYPE = new TypeToken<Collection<FederationSet>>() {
	}.getType();

	private static final Type BRANCHES_TYPE = new TypeToken<Map<String, Collection<String>>>() {
	}.getType();

	public static final Type REGISTRANT_PERMISSIONS_TYPE = new TypeToken<Collection<RegistrantAccessPermission>>() {
	}.getType();

	/**
	 *
	 * @param remoteURL
	 *            the url of the remote gitblit instance
	 * @param req
	 *            the rpc request type
	 * @return
	 */
	public static String asLink(String remoteURL, RpcRequest req) {
		return asLink(remoteURL, req, null);
	}

	/**
	 *
	 * @param remoteURL
	 *            the url of the remote gitblit instance
	 * @param req
	 *            the rpc request type
	 * @param name
	 *            the name of the actionable object
	 * @return
	 */
	public static String asLink(String remoteURL, RpcRequest req, String name) {
		if (remoteURL.length() > 0 && remoteURL.charAt(remoteURL.length() - 1) == '/') {
			remoteURL = remoteURL.substring(0, remoteURL.length() - 1);
		}
		if (req == null) {
			req = RpcRequest.LIST_REPOSITORIES;
		}
		return remoteURL + Constants.RPC_PATH + "?req=" + req.name().toLowerCase()
				+ (name == null ? "" : ("&name=" + StringUtils.encodeURL(name)));
	}

	/**
	 * Returns the version of the RPC protocol on the server.
	 *
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return the protocol version
	 * @throws IOException
	 */
	public static int getProtocolVersion(String serverUrl, String account, char[] password)
			throws IOException {
		String url = asLink(serverUrl, RpcRequest.GET_PROTOCOL);
		int protocol = 1;
		try {
			protocol = JsonUtils.retrieveJson(url, Integer.class, account, password);
		} catch (UnknownRequestException e) {
			// v0.7.0 (protocol 1) did not have this request type
		}
		return protocol;
	}

	/**
	 * Retrieves a map of the repositories at the remote gitblit instance keyed
	 * by the repository clone url.
	 *
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return a map of cloneable repositories
	 * @throws IOException
	 */
	public static Map<String, RepositoryModel> getRepositories(String serverUrl, String account,
			char[] password) throws IOException {
		String url = asLink(serverUrl, RpcRequest.LIST_REPOSITORIES);
		Map<String, RepositoryModel> models = JsonUtils.retrieveJson(url, REPOSITORIES_TYPE,
				account, password);
		return models;
	}

	/**
	 * Tries to pull the gitblit user accounts from the remote gitblit instance.
	 *
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return a collection of UserModel objects
	 * @throws IOException
	 */
	public static List<UserModel> getUsers(String serverUrl, String account, char[] password)
			throws IOException {
		String url = asLink(serverUrl, RpcRequest.LIST_USERS);
		Collection<UserModel> models = JsonUtils.retrieveJson(url, USERS_TYPE, account, password);
		List<UserModel> list = new ArrayList<UserModel>(models);
		return list;
	}

	/**
	 * Tries to pull the gitblit team definitions from the remote gitblit
	 * instance.
	 *
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return a collection of UserModel objects
	 * @throws IOException
	 */
	public static List<TeamModel> getTeams(String serverUrl, String account, char[] password)
			throws IOException {
		String url = asLink(serverUrl, RpcRequest.LIST_TEAMS);
		Collection<TeamModel> models = JsonUtils.retrieveJson(url, TEAMS_TYPE, account, password);
		List<TeamModel> list = new ArrayList<TeamModel>(models);
		return list;
	}

	/**
	 * Create a repository on the Gitblit server.
	 *
	 * @param repository
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return true if the action succeeded
	 * @throws IOException
	 */
	public static boolean createRepository(RepositoryModel repository, String serverUrl,
			String account, char[] password) throws IOException {
		// ensure repository name ends with .git
		if (!repository.name.endsWith(".git")) {
			repository.name += ".git";
		}
		return doAction(RpcRequest.CREATE_REPOSITORY, null, repository, serverUrl, account,
				password);

	}

	/**
	 * Send a revised version of the repository model to the Gitblit server.
	 *
	 * @param repository
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return true if the action succeeded
	 * @throws IOException
	 */
	public static boolean updateRepository(String repositoryName, RepositoryModel repository,
			String serverUrl, String account, char[] password) throws IOException {
		return doAction(RpcRequest.EDIT_REPOSITORY, repositoryName, repository, serverUrl, account,
				password);
	}

	/**
	 * Delete a repository from the Gitblit server.
	 *
	 * @param repository
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return true if the action succeeded
	 * @throws IOException
	 */
	public static boolean deleteRepository(RepositoryModel repository, String serverUrl,
			String account, char[] password) throws IOException {
		return doAction(RpcRequest.DELETE_REPOSITORY, null, repository, serverUrl, account,
				password);

	}

	/**
	 * Clears the repository cache on the Gitblit server.
	 *
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return true if the action succeeded
	 * @throws IOException
	 */
	public static boolean clearRepositoryCache(String serverUrl, String account,
			char[] password) throws IOException {
		return doAction(RpcRequest.CLEAR_REPOSITORY_CACHE, null, null, serverUrl, account,
				password);
	}

	/**
	 * Reindex all tickets on the Gitblit server.
	 *
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return true if the action succeeded
	 * @throws IOException
	 */
	public static boolean reindexTickets(String serverUrl, String account,
			char[] password) throws IOException {
		return doAction(RpcRequest.REINDEX_TICKETS, null, null, serverUrl, account,
				password);
	}

	/**
	 * Reindex tickets for the specified repository on the Gitblit server.
	 *
	 * @param serverUrl
	 * @param repositoryName
	 * @param account
	 * @param password
	 * @return true if the action succeeded
	 * @throws IOException
	 */
	public static boolean reindexTickets(String serverUrl, String repositoryName,
			String account, char[] password) throws IOException {
		return doAction(RpcRequest.REINDEX_TICKETS, repositoryName, null, serverUrl,
				account, password);
	}

	/**
	 * Create a user on the Gitblit server.
	 *
	 * @param user
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return true if the action succeeded
	 * @throws IOException
	 */
	public static boolean createUser(UserModel user, String serverUrl, String account,
			char[] password) throws IOException {
		return doAction(RpcRequest.CREATE_USER, null, user, serverUrl, account, password);

	}

	/**
	 * Send a revised version of the user model to the Gitblit server.
	 *
	 * @param user
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return true if the action succeeded
	 * @throws IOException
	 */
	public static boolean updateUser(String username, UserModel user, String serverUrl,
			String account, char[] password) throws IOException {
		return doAction(RpcRequest.EDIT_USER, username, user, serverUrl, account, password);

	}

	/**
	 * Deletes a user from the Gitblit server.
	 *
	 * @param user
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return true if the action succeeded
	 * @throws IOException
	 */
	public static boolean deleteUser(UserModel user, String serverUrl, String account,
			char[] password) throws IOException {
		return doAction(RpcRequest.DELETE_USER, null, user, serverUrl, account, password);
	}

	/**
	 * Tries to get the specified gitblit user account from the remote gitblit instance.
	 * If the username is null or empty, the current user is returned.
	 *
	 * @param username
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return a UserModel or null
	 * @throws IOException
	 */
	public static UserModel getUser(String username, String serverUrl, String account, char[] password)
			throws IOException {
		String url = asLink(serverUrl, RpcRequest.GET_USER);
		UserModel model = JsonUtils.retrieveJson(url, UserModel.class, account, password);
		return model;
	}

	/**
	 * Create a team on the Gitblit server.
	 *
	 * @param team
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return true if the action succeeded
	 * @throws IOException
	 */
	public static boolean createTeam(TeamModel team, String serverUrl, String account,
			char[] password) throws IOException {
		return doAction(RpcRequest.CREATE_TEAM, null, team, serverUrl, account, password);

	}

	/**
	 * Send a revised version of the team model to the Gitblit server.
	 *
	 * @param team
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return true if the action succeeded
	 * @throws IOException
	 */
	public static boolean updateTeam(String teamname, TeamModel team, String serverUrl,
			String account, char[] password) throws IOException {
		return doAction(RpcRequest.EDIT_TEAM, teamname, team, serverUrl, account, password);

	}

	/**
	 * Deletes a team from the Gitblit server.
	 *
	 * @param team
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return true if the action succeeded
	 * @throws IOException
	 */
	public static boolean deleteTeam(TeamModel team, String serverUrl, String account,
			char[] password) throws IOException {
		return doAction(RpcRequest.DELETE_TEAM, null, team, serverUrl, account, password);
	}

	/**
	 * Retrieves the list of users that can access the specified repository.
	 *
	 * @param repository
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return list of members
	 * @throws IOException
	 */
	public static List<String> getRepositoryMembers(RepositoryModel repository, String serverUrl,
			String account, char[] password) throws IOException {
		String url = asLink(serverUrl, RpcRequest.LIST_REPOSITORY_MEMBERS, repository.name);
		Collection<String> list = JsonUtils.retrieveJson(url, NAMES_TYPE, account, password);
		return new ArrayList<String>(list);
	}

	/**
	 * Retrieves the list of user access permissions for the specified repository.
	 *
	 * @param repository
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return list of User-AccessPermission tuples
	 * @throws IOException
	 */
	public static List<RegistrantAccessPermission> getRepositoryMemberPermissions(RepositoryModel repository,
			String serverUrl, String account, char [] password) throws IOException {
		String url = asLink(serverUrl, RpcRequest.LIST_REPOSITORY_MEMBER_PERMISSIONS, repository.name);
		Collection<RegistrantAccessPermission> list = JsonUtils.retrieveJson(url, REGISTRANT_PERMISSIONS_TYPE, account, password);
		return new ArrayList<RegistrantAccessPermission>(list);
	}

	/**
	 * Sets the repository user access permissions
	 *
	 * @param repository
	 * @param permissions
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return true if the action succeeded
	 * @throws IOException
	 */
	public static boolean setRepositoryMemberPermissions(RepositoryModel repository,
			List<RegistrantAccessPermission> permissions, String serverUrl, String account, char[] password)
			throws IOException {
		return doAction(RpcRequest.SET_REPOSITORY_MEMBER_PERMISSIONS, repository.name, permissions, serverUrl,
				account, password);
	}

	/**
	 * Retrieves the list of teams that can access the specified repository.
	 *
	 * @param repository
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return list of teams
	 * @throws IOException
	 */
	public static List<String> getRepositoryTeams(RepositoryModel repository, String serverUrl,
			String account, char[] password) throws IOException {
		String url = asLink(serverUrl, RpcRequest.LIST_REPOSITORY_TEAMS, repository.name);
		Collection<String> list = JsonUtils.retrieveJson(url, NAMES_TYPE, account, password);
		return new ArrayList<String>(list);
	}

	/**
	 * Retrieves the list of team access permissions for the specified repository.
	 *
	 * @param repository
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return list of Team-AccessPermission tuples
	 * @throws IOException
	 */
	public static List<RegistrantAccessPermission> getRepositoryTeamPermissions(RepositoryModel repository,
			String serverUrl, String account, char [] password) throws IOException {
		String url = asLink(serverUrl, RpcRequest.LIST_REPOSITORY_TEAM_PERMISSIONS, repository.name);
		Collection<RegistrantAccessPermission> list = JsonUtils.retrieveJson(url, REGISTRANT_PERMISSIONS_TYPE, account, password);
		return new ArrayList<RegistrantAccessPermission>(list);
	}

	/**
	 * Sets the repository team access permissions
	 *
	 * @param repository
	 * @param permissions
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return true if the action succeeded
	 * @throws IOException
	 */
	public static boolean setRepositoryTeamPermissions(RepositoryModel repository,
			List<RegistrantAccessPermission> permissions, String serverUrl, String account, char[] password)
			throws IOException {
		return doAction(RpcRequest.SET_REPOSITORY_TEAM_PERMISSIONS, repository.name, permissions, serverUrl,
				account, password);
	}

	/**
	 * Retrieves the list of federation registrations. These are the list of
	 * registrations that this Gitblit instance is pulling from.
	 *
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return a collection of FederationRegistration objects
	 * @throws IOException
	 */
	public static List<FederationModel> getFederationRegistrations(String serverUrl,
			String account, char[] password) throws IOException {
		String url = asLink(serverUrl, RpcRequest.LIST_FEDERATION_REGISTRATIONS);
		Collection<FederationModel> registrations = JsonUtils.retrieveJson(url, REGISTRATIONS_TYPE,
				account, password);
		List<FederationModel> list = new ArrayList<FederationModel>(registrations);
		return list;
	}

	/**
	 * Retrieves the list of federation result registrations. These are the
	 * results reported back to this Gitblit instance from a federation client.
	 *
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return a collection of FederationRegistration objects
	 * @throws IOException
	 */
	public static List<FederationModel> getFederationResultRegistrations(String serverUrl,
			String account, char[] password) throws IOException {
		String url = asLink(serverUrl, RpcRequest.LIST_FEDERATION_RESULTS);
		Collection<FederationModel> registrations = JsonUtils.retrieveJson(url, REGISTRATIONS_TYPE,
				account, password);
		List<FederationModel> list = new ArrayList<FederationModel>(registrations);
		return list;
	}

	/**
	 * Retrieves the list of federation proposals.
	 *
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return a collection of FederationProposal objects
	 * @throws IOException
	 */
	public static List<FederationProposal> getFederationProposals(String serverUrl, String account,
			char[] password) throws IOException {
		String url = asLink(serverUrl, RpcRequest.LIST_FEDERATION_PROPOSALS);
		Collection<FederationProposal> proposals = JsonUtils.retrieveJson(url, PROPOSALS_TYPE,
				account, password);
		List<FederationProposal> list = new ArrayList<FederationProposal>(proposals);
		return list;
	}

	/**
	 * Retrieves the list of federation repository sets.
	 *
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return a collection of FederationSet objects
	 * @throws IOException
	 */
	public static List<FederationSet> getFederationSets(String serverUrl, String account,
			char[] password) throws IOException {
		String url = asLink(serverUrl, RpcRequest.LIST_FEDERATION_SETS);
		Collection<FederationSet> sets = JsonUtils.retrieveJson(url, SETS_TYPE, account, password);
		List<FederationSet> list = new ArrayList<FederationSet>(sets);
		return list;
	}

	/**
	 * Retrieves the settings of the Gitblit server.
	 *
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return an Settings object
	 * @throws IOException
	 */
	public static ServerSettings getSettings(String serverUrl, String account, char[] password)
			throws IOException {
		String url = asLink(serverUrl, RpcRequest.LIST_SETTINGS);
		ServerSettings settings = JsonUtils.retrieveJson(url, ServerSettings.class, account,
				password);
		return settings;
	}

	/**
	 * Update the settings on the Gitblit server.
	 *
	 * @param settings
	 *            the settings to update
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return true if the action succeeded
	 * @throws IOException
	 */
	public static boolean updateSettings(Map<String, String> settings, String serverUrl,
			String account, char[] password) throws IOException {
		return doAction(RpcRequest.EDIT_SETTINGS, null, settings, serverUrl, account, password);

	}

	/**
	 * Retrieves the server status object.
	 *
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return an ServerStatus object
	 * @throws IOException
	 */
	public static ServerStatus getStatus(String serverUrl, String account, char[] password)
			throws IOException {
		String url = asLink(serverUrl, RpcRequest.LIST_STATUS);
		ServerStatus status = JsonUtils.retrieveJson(url, ServerStatus.class, account, password);
		return status;
	}

	/**
	 * Retrieves a map of local branches in the Gitblit server keyed by
	 * repository.
	 *
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return
	 * @throws IOException
	 */
	public static Map<String, Collection<String>> getBranches(String serverUrl, String account,
			char[] password) throws IOException {
		String url = asLink(serverUrl, RpcRequest.LIST_BRANCHES);
		Map<String, Collection<String>> branches = JsonUtils.retrieveJson(url, BRANCHES_TYPE,
				account, password);
		return branches;
	}

	/**
	 * Retrieves a list of available branch feeds in the Gitblit server.
	 *
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return
	 * @throws IOException
	 */
	public static List<FeedModel> getBranchFeeds(String serverUrl, String account, char[] password)
			throws IOException {
		List<FeedModel> feeds = new ArrayList<FeedModel>();
		Map<String, Collection<String>> allBranches = getBranches(serverUrl, account, password);
		for (Map.Entry<String, Collection<String>> entry : allBranches.entrySet()) {
			for (String branch : entry.getValue()) {
				FeedModel feed = new FeedModel();
				feed.repository = entry.getKey();
				feed.branch = branch;
				feeds.add(feed);
			}
		}
		return feeds;
	}

	/**
	 * Do the specified administrative action on the Gitblit server.
	 *
	 * @param request
	 * @param name
	 *            the name of the object (may be null)
	 * @param object
	 * @param serverUrl
	 * @param account
	 * @param password
	 * @return true if the action succeeded
	 * @throws IOException
	 */
	protected static boolean doAction(RpcRequest request, String name, Object object,
			String serverUrl, String account, char[] password) throws IOException {
		String url = asLink(serverUrl, request, name);
		String json = JsonUtils.toJsonString(object);
		int resultCode = JsonUtils.sendJsonString(url, json, account, password);
		return resultCode == 200;
	}
}
