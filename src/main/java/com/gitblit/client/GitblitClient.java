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
package com.gitblit.client;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.gitblit.Constants;
import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.AuthorizationControl;
import com.gitblit.Constants.PermissionType;
import com.gitblit.Constants.RegistrantType;
import com.gitblit.Keys;
import com.gitblit.models.FederationModel;
import com.gitblit.models.FeedEntryModel;
import com.gitblit.models.FeedModel;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.ServerSettings;
import com.gitblit.models.ServerStatus;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.RpcUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.SyndicationUtils;

/**
 * GitblitClient is a object that retrieves data from a Gitblit server, caches
 * it for local operations, and allows updating or creating Gitblit objects.
 *
 * @author James Moger
 *
 */
public class GitblitClient implements Serializable {

	private static final long serialVersionUID = 1L;

	private static final Date NEVER = new Date(0);

	protected final GitblitRegistration reg;

	public final String url;

	public final String account;

	private final char[] password;

	private volatile int protocolVersion;

	private volatile boolean allowManagement;

	private volatile boolean allowAdministration;

	private volatile ServerSettings settings;

	private final List<RepositoryModel> allRepositories;

	private final List<UserModel> allUsers;

	private final List<TeamModel> allTeams;

	private final List<FederationModel> federationRegistrations;

	private final List<FeedModel> availableFeeds;

	private final List<FeedEntryModel> syndicatedEntries;

	private final Set<String> subscribedRepositories;

	private ServerStatus status;

	public GitblitClient(GitblitRegistration reg) {
		this.reg = reg;
		this.url = reg.url;
		this.account = reg.account;
		this.password = reg.password;

		this.allUsers = new ArrayList<UserModel>();
		this.allTeams = new ArrayList<TeamModel>();
		this.allRepositories = new ArrayList<RepositoryModel>();
		this.federationRegistrations = new ArrayList<FederationModel>();
		this.availableFeeds = new ArrayList<FeedModel>();
		this.syndicatedEntries = new ArrayList<FeedEntryModel>();
		this.subscribedRepositories = new HashSet<String>();
	}

	public void login() throws IOException {
		protocolVersion = RpcUtils.getProtocolVersion(url, account, password);
		refreshSettings();
		refreshAvailableFeeds();
		refreshRepositories();
		refreshSubscribedFeeds(0);

		// credentials may not have administrator access
		// or server may have disabled rpc management
		refreshUsers();
		if (protocolVersion > 1) {
			refreshTeams();
		}
		allowManagement = true;

		// credentials may not have administrator access
		// or server may have disabled rpc administration
		refreshStatus();
		allowAdministration = true;

	}

	public int getProtocolVersion() {
		return protocolVersion;
	}

	public boolean allowManagement() {
		return allowManagement;
	}

	public boolean allowAdministration() {
		return allowAdministration;
	}

	public boolean isOwner(RepositoryModel model) {
		return model.isOwner(account);
	}

	public String getURL(String action, String repository, String objectId) {
		boolean mounted = settings.get(Keys.web.mountParameters).getBoolean(true);
		StringBuilder sb = new StringBuilder();
		sb.append(url);
		sb.append('/');
		sb.append(action);
		sb.append('/');
		if (mounted) {
			// mounted url/action/repository/objectId
			sb.append(StringUtils.encodeURL(repository));
			if (!StringUtils.isEmpty(objectId)) {
				sb.append('/');
				sb.append(objectId);
			}
			return sb.toString();
		} else {
			// parameterized url/action/&r=repository&h=objectId
			sb.append("?r=");
			sb.append(repository);
			if (!StringUtils.isEmpty(objectId)) {
				sb.append("&h=");
				sb.append(objectId);
			}
			return sb.toString();
		}
	}

	public AccessRestrictionType getDefaultAccessRestriction() {
		String restriction = "PUSH";
		if (settings.hasKey(Keys.git.defaultAccessRestriction)) {
			restriction = settings.get(Keys.git.defaultAccessRestriction).currentValue;
		}
		return AccessRestrictionType.fromName(restriction);
	}

	public AuthorizationControl getDefaultAuthorizationControl() {
		String authorization = null;
		if (settings.hasKey(Keys.git.defaultAuthorizationControl)) {
			authorization = settings.get(Keys.git.defaultAuthorizationControl).currentValue;
		}
		return AuthorizationControl.fromName(authorization);
	}

	/**
	 * Returns the list of pre-receive scripts the repository inherited from the
	 * global settings and team affiliations.
	 *
	 * @param repository
	 *            if null only the globally specified scripts are returned
	 * @return a list of scripts
	 */
	public List<String> getPreReceiveScriptsInherited(RepositoryModel repository) {
		Set<String> scripts = new LinkedHashSet<String>();
		// Globals
		for (String script : settings.get(Keys.groovy.preReceiveScripts).getStrings()) {
			if (script.endsWith(".groovy")) {
				scripts.add(script.substring(0, script.lastIndexOf('.')));
			} else {
				scripts.add(script);
			}
		}

		// Team Scripts
		if (repository != null) {
			for (String teamname : getPermittedTeamnames(repository)) {
				TeamModel team = getTeamModel(teamname);
				if (!ArrayUtils.isEmpty(team.preReceiveScripts)) {
					scripts.addAll(team.preReceiveScripts);
				}
			}
		}
		return new ArrayList<String>(scripts);
	}

	/**
	 * Returns the list of all available Groovy pre-receive push hook scripts
	 * that are not already inherited by the repository. Script files must have
	 * .groovy extension
	 *
	 * @param repository
	 *            optional parameter
	 * @return list of available hook scripts
	 */
	public List<String> getPreReceiveScriptsUnused(RepositoryModel repository) {
		Set<String> inherited = new TreeSet<String>(getPreReceiveScriptsInherited(repository));

		// create list of available scripts by excluding inherited scripts
		List<String> scripts = new ArrayList<String>();
		if (!ArrayUtils.isEmpty(settings.pushScripts)) {
			for (String script : settings.pushScripts) {
				if (!inherited.contains(script)) {
					scripts.add(script);
				}
			}
		}
		return scripts;
	}

	/**
	 * Returns the list of post-receive scripts the repository inherited from
	 * the global settings and team affiliations.
	 *
	 * @param repository
	 *            if null only the globally specified scripts are returned
	 * @return a list of scripts
	 */
	public List<String> getPostReceiveScriptsInherited(RepositoryModel repository) {
		Set<String> scripts = new LinkedHashSet<String>();
		// Global Scripts
		for (String script : settings.get(Keys.groovy.postReceiveScripts).getStrings()) {
			if (script.endsWith(".groovy")) {
				scripts.add(script.substring(0, script.lastIndexOf('.')));
			} else {
				scripts.add(script);
			}
		}
		// Team Scripts
		if (repository != null) {
			for (String teamname : getPermittedTeamnames(repository)) {
				TeamModel team = getTeamModel(teamname);
				if (!ArrayUtils.isEmpty(team.postReceiveScripts)) {
					scripts.addAll(team.postReceiveScripts);
				}
			}
		}
		return new ArrayList<String>(scripts);
	}

	/**
	 * Returns the list of unused Groovy post-receive push hook scripts that are
	 * not already inherited by the repository. Script files must have .groovy
	 * extension
	 *
	 * @param repository
	 *            optional parameter
	 * @return list of available hook scripts
	 */
	public List<String> getPostReceiveScriptsUnused(RepositoryModel repository) {
		Set<String> inherited = new TreeSet<String>(getPostReceiveScriptsInherited(repository));

		// create list of available scripts by excluding inherited scripts
		List<String> scripts = new ArrayList<String>();
		if (!ArrayUtils.isEmpty(settings.pushScripts)) {
			for (String script : settings.pushScripts) {
				if (!inherited.contains(script)) {
					scripts.add(script);
				}
			}
		}
		return scripts;
	}

	public ServerSettings getSettings() {
		return settings;
	}

	public ServerStatus getStatus() {
		return status;
	}

	public String getSettingDescription(String key) {
		return settings.get(key).description;
	}

	public List<RepositoryModel> refreshRepositories() throws IOException {
		Map<String, RepositoryModel> repositories = RpcUtils
				.getRepositories(url, account, password);
		allRepositories.clear();
		allRepositories.addAll(repositories.values());
		Collections.sort(allRepositories);
		markSubscribedFeeds();
		return allRepositories;
	}

	public List<UserModel> refreshUsers() throws IOException {
		List<UserModel> users = RpcUtils.getUsers(url, account, password);
		allUsers.clear();
		allUsers.addAll(users);
		Collections.sort(users);
		return allUsers;
	}

	public List<TeamModel> refreshTeams() throws IOException {
		List<TeamModel> teams = RpcUtils.getTeams(url, account, password);
		allTeams.clear();
		allTeams.addAll(teams);
		Collections.sort(teams);
		return allTeams;
	}

	public ServerSettings refreshSettings() throws IOException {
		settings = RpcUtils.getSettings(url, account, password);
		return settings;
	}

	public ServerStatus refreshStatus() throws IOException {
		status = RpcUtils.getStatus(url, account, password);
		return status;
	}

	public List<String> getBranches(String repository) {
		List<FeedModel> feeds = getAvailableFeeds(repository);
		List<String> branches = new ArrayList<String>();
		for (FeedModel feed : feeds) {
			branches.add(feed.branch);
		}
		Collections.sort(branches);
		return branches;
	}

	public List<FeedModel> getAvailableFeeds() {
		return availableFeeds;
	}

	public List<FeedModel> getAvailableFeeds(RepositoryModel repository) {
		return getAvailableFeeds(repository.name);
	}

	public List<FeedModel> getAvailableFeeds(String repository) {
		List<FeedModel> repositoryFeeds = new ArrayList<FeedModel>();
		if (repository == null) {
			return repositoryFeeds;
		}
		for (FeedModel feed : availableFeeds) {
			if (feed.repository.equalsIgnoreCase(repository)) {
				repositoryFeeds.add(feed);
			}
		}
		return repositoryFeeds;
	}

	public List<FeedModel> refreshAvailableFeeds() throws IOException {
		List<FeedModel> feeds = RpcUtils.getBranchFeeds(url, account, password);
		availableFeeds.clear();
		availableFeeds.addAll(feeds);
		markSubscribedFeeds();
		return availableFeeds;
	}

	public List<FeedEntryModel> refreshSubscribedFeeds(int page) throws IOException {
		Set<FeedEntryModel> allEntries = new HashSet<FeedEntryModel>();
		if (reg.feeds.size() > 0) {
			for (FeedModel feed : reg.feeds) {
				feed.lastRefreshDate = feed.currentRefreshDate;
				feed.currentRefreshDate = new Date();
				List<FeedEntryModel> entries = SyndicationUtils.readFeed(url, feed.repository,
						feed.branch, -1, page, account, password);
				allEntries.addAll(entries);
			}
		}
		reg.cacheFeeds();
		syndicatedEntries.clear();
		syndicatedEntries.addAll(allEntries);
		Collections.sort(syndicatedEntries);
		return syndicatedEntries;
	}

	public void updateSubscribedFeeds(List<FeedModel> list) {
		reg.updateSubscribedFeeds(list);
		markSubscribedFeeds();
	}

	private void markSubscribedFeeds() {
		subscribedRepositories.clear();
		for (FeedModel feed : availableFeeds) {
			// mark feed in the available list as subscribed
			feed.subscribed = reg.feeds.contains(feed);
			if (feed.subscribed) {
				subscribedRepositories.add(feed.repository.toLowerCase());
			}
		}
	}

	public Date getLastFeedRefresh(String repository, String branch) {
		FeedModel feed = new FeedModel();
		feed.repository = repository;
		feed.branch = branch;
		if (reg.feeds.contains(feed)) {
			int idx = reg.feeds.indexOf(feed);
			feed = reg.feeds.get(idx);
			return feed.lastRefreshDate;
		}
		return NEVER;
	}

	public boolean isSubscribed(RepositoryModel repository) {
		return subscribedRepositories.contains(repository.name.toLowerCase());
	}

	public List<FeedEntryModel> getSyndicatedEntries() {
		return syndicatedEntries;
	}

	public List<FeedEntryModel> log(String repository, String branch, int numberOfEntries, int page)
			throws IOException {
		return SyndicationUtils.readFeed(url, repository, branch, numberOfEntries, page, account,
				password);
	}

	public List<FeedEntryModel> search(String repository, String branch, String fragment,
			Constants.SearchType type, int numberOfEntries, int page) throws IOException {
		return SyndicationUtils.readSearchFeed(url, repository, branch, fragment, type,
				numberOfEntries, page, account, password);
	}

	public List<FederationModel> refreshFederationRegistrations() throws IOException {
		List<FederationModel> list = RpcUtils.getFederationRegistrations(url, account, password);
		federationRegistrations.clear();
		federationRegistrations.addAll(list);
		return federationRegistrations;
	}

	public List<UserModel> getUsers() {
		return allUsers;
	}

	public UserModel getUser(String username) {
		for (UserModel user : getUsers()) {
			if (user.username.equalsIgnoreCase(username)) {
				return user;
			}
		}
		return null;
	}

	public List<String> getUsernames() {
		List<String> usernames = new ArrayList<String>();
		for (UserModel user : this.allUsers) {
			usernames.add(user.username);
		}
		Collections.sort(usernames);
		return usernames;
	}

	public List<String> getPermittedUsernames(RepositoryModel repository) {
		List<String> usernames = new ArrayList<String>();
		for (UserModel user : this.allUsers) {
			if (user.hasRepositoryPermission(repository.name)) {
				usernames.add(user.username);
			}
		}
		return usernames;
	}

	/**
	 * Returns the effective list of permissions for this user, taking into account
	 * team memberships, ownerships.
	 *
	 * @param user
	 * @return the effective list of permissions for the user
	 */
	public List<RegistrantAccessPermission> getUserAccessPermissions(UserModel user) {
		Set<RegistrantAccessPermission> set = new LinkedHashSet<RegistrantAccessPermission>();
		set.addAll(user.getRepositoryPermissions());
		// Flag missing repositories
		for (RegistrantAccessPermission permission : set) {
			if (permission.mutable && PermissionType.EXPLICIT.equals(permission.permissionType)) {
				RepositoryModel rm = getRepository(permission.registrant);
				if (rm == null) {
					permission.permissionType = PermissionType.MISSING;
					permission.mutable = false;
					continue;
				}
			}
		}

		// TODO reconsider ownership as a user property
		// manually specify personal repository ownerships
		for (RepositoryModel rm : allRepositories) {
			if (rm.isUsersPersonalRepository(user.username) || rm.isOwner(user.username)) {
				RegistrantAccessPermission rp = new RegistrantAccessPermission(rm.name, AccessPermission.REWIND,
						PermissionType.OWNER, RegistrantType.REPOSITORY, null, false);
				// user may be owner of a repository to which they've inherited
				// a team permission, replace any existing perm with owner perm
				set.remove(rp);
				set.add(rp);
			}
		}

		List<RegistrantAccessPermission> list = new ArrayList<RegistrantAccessPermission>(set);
		Collections.sort(list);
		return list;
	}

	public List<RegistrantAccessPermission> getUserAccessPermissions(RepositoryModel repository) {
		List<RegistrantAccessPermission> list = new ArrayList<RegistrantAccessPermission>();
		if (AccessRestrictionType.NONE.equals(repository.accessRestriction)) {
			// no permissions needed, REWIND for everyone!
			return list;
		}
		if (AuthorizationControl.AUTHENTICATED.equals(repository.authorizationControl)) {
			// no permissions needed, REWIND for authenticated!
			return list;
		}
		// NAMED users and teams
		for (UserModel user : allUsers) {
			RegistrantAccessPermission ap = user.getRepositoryPermission(repository);
			if (ap.permission.exceeds(AccessPermission.NONE)) {
				list.add(ap);
			}
		}
		return list;
	}

	public boolean setUserAccessPermissions(RepositoryModel repository, List<RegistrantAccessPermission> permissions) throws IOException {
		return RpcUtils.setRepositoryMemberPermissions(repository, permissions, url, account, password);
	}

	public List<TeamModel> getTeams() {
		return allTeams;
	}

	public List<String> getTeamnames() {
		List<String> teamnames = new ArrayList<String>();
		for (TeamModel team : this.allTeams) {
			teamnames.add(team.name);
		}
		Collections.sort(teamnames);
		return teamnames;
	}

	public List<String> getPermittedTeamnames(RepositoryModel repository) {
		List<String> teamnames = new ArrayList<String>();
		for (TeamModel team : this.allTeams) {
			if (team.hasRepositoryPermission(repository.name)) {
				teamnames.add(team.name);
			}
		}
		return teamnames;
	}

	public List<RegistrantAccessPermission> getTeamAccessPermissions(RepositoryModel repository) {
		List<RegistrantAccessPermission> list = new ArrayList<RegistrantAccessPermission>();
		for (TeamModel team : allTeams) {
			RegistrantAccessPermission ap = team.getRepositoryPermission(repository);
			if (ap.permission.exceeds(AccessPermission.NONE)) {
				list.add(ap);
			}
		}
		Collections.sort(list);
		return list;
	}

	public boolean setTeamAccessPermissions(RepositoryModel repository, List<RegistrantAccessPermission> permissions) throws IOException {
		return RpcUtils.setRepositoryTeamPermissions(repository, permissions, url, account, password);
	}

	public TeamModel getTeamModel(String name) {
		for (TeamModel team : allTeams) {
			if (team.name.equalsIgnoreCase(name)) {
				return team;
			}
		}
		return null;
	}

	public List<String> getFederationSets() {
		return settings.get(Keys.federation.sets).getStrings();
	}

	public List<RepositoryModel> getRepositories() {
		return allRepositories;
	}

	public RepositoryModel getRepository(String name) {
		for (RepositoryModel repository : allRepositories) {
			if (repository.name.equalsIgnoreCase(name)) {
				return repository;
			}
		}
		return null;
	}

	public boolean createRepository(RepositoryModel repository, List<RegistrantAccessPermission> userPermissions)
			throws IOException {
		return createRepository(repository, userPermissions, null);
	}

	public boolean createRepository(RepositoryModel repository, List<RegistrantAccessPermission> userPermissions,
			List<RegistrantAccessPermission> teamPermissions) throws IOException {
		boolean success = true;
		success &= RpcUtils.createRepository(repository, url, account, password);
		if (userPermissions != null && userPermissions.size() > 0) {
			// if new repository has named members, set them
			success &= RpcUtils.setRepositoryMemberPermissions(repository, userPermissions, url, account,
					password);
		}
		if (teamPermissions != null && teamPermissions.size() > 0) {
			// if new repository has named teams, set them
			success &= RpcUtils.setRepositoryTeamPermissions(repository, teamPermissions, url, account,
					password);
		}
		return success;
	}

	public boolean updateRepository(String name, RepositoryModel repository,
			List<RegistrantAccessPermission> userPermissions) throws IOException {
		return updateRepository(name, repository, userPermissions, null);
	}

	public boolean updateRepository(String name, RepositoryModel repository,
			List<RegistrantAccessPermission> userPermissions,	List<RegistrantAccessPermission> teamPermissions) throws IOException {
		boolean success = true;
		success &= RpcUtils.updateRepository(name, repository, url, account, password);
		// set the repository members
		if (userPermissions != null) {
			success &= RpcUtils.setRepositoryMemberPermissions(repository, userPermissions, url, account,
					password);
		}
		if (teamPermissions != null) {
			success &= RpcUtils.setRepositoryTeamPermissions(repository, teamPermissions, url, account,
					password);
		}
		return success;
	}

	public boolean deleteRepository(RepositoryModel repository) throws IOException {
		return RpcUtils.deleteRepository(repository, url, account, password);
	}

	public boolean clearRepositoryCache() throws IOException {
		return RpcUtils.clearRepositoryCache(url, account, password);
	}

	public boolean createUser(UserModel user) throws IOException {
		return RpcUtils.createUser(user, url, account, password);
	}

	public boolean updateUser(String name, UserModel user) throws IOException {
		return RpcUtils.updateUser(name, user, url, account, password);
	}

	public boolean deleteUser(UserModel user) throws IOException {
		return RpcUtils.deleteUser(user, url, account, password);
	}

	public boolean createTeam(TeamModel team) throws IOException {
		return RpcUtils.createTeam(team, url, account, password);
	}

	public boolean updateTeam(String name, TeamModel team) throws IOException {
		return RpcUtils.updateTeam(name, team, url, account, password);
	}

	public boolean deleteTeam(TeamModel team) throws IOException {
		return RpcUtils.deleteTeam(team, url, account, password);
	}

	public boolean updateSettings(Map<String, String> newSettings) throws IOException {
		return RpcUtils.updateSettings(newSettings, url, account, password);
	}
}
