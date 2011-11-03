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
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gitblit.GitBlitException.ForbiddenException;
import com.gitblit.GitBlitException.NotAllowedException;
import com.gitblit.GitBlitException.UnauthorizedException;
import com.gitblit.GitBlitException.UnknownRequestException;
import com.gitblit.Keys;
import com.gitblit.models.FederationModel;
import com.gitblit.models.FeedModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.ServerSettings;
import com.gitblit.models.ServerStatus;
import com.gitblit.models.SyndicatedEntryModel;
import com.gitblit.models.UserModel;
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

	private volatile boolean allowManagement;

	private volatile boolean allowAdministration;

	private volatile ServerSettings settings;

	private final List<RepositoryModel> allRepositories;

	private final List<UserModel> allUsers;

	private final List<FederationModel> federationRegistrations;

	private final List<FeedModel> availableFeeds;

	private final List<SyndicatedEntryModel> syndicatedEntries;

	private final Set<String> subscribedRepositories;

	private ServerStatus status;

	public GitblitClient(GitblitRegistration reg) {
		this.reg = reg;
		this.url = reg.url;
		this.account = reg.account;
		this.password = reg.password;

		this.allUsers = new ArrayList<UserModel>();
		this.allRepositories = new ArrayList<RepositoryModel>();
		this.federationRegistrations = new ArrayList<FederationModel>();
		this.availableFeeds = new ArrayList<FeedModel>();
		this.syndicatedEntries = new ArrayList<SyndicatedEntryModel>();
		this.subscribedRepositories = new HashSet<String>();
	}

	public void login() throws IOException {
		refreshSettings();
		refreshAvailableFeeds();
		refreshRepositories();

		try {
			// RSS feeds may be disabled by server
			refreshSubscribedFeeds();
		} catch (IOException e) {
			e.printStackTrace();
		}

		try {
			// credentials may not have administrator access
			// or server may have disabled rpc management
			refreshUsers();
			allowManagement = true;
		} catch (UnauthorizedException e) {
		} catch (ForbiddenException e) {
		} catch (NotAllowedException e) {
		} catch (UnknownRequestException e) {
		} catch (IOException e) {
			e.printStackTrace();
		}

		try {
			// credentials may not have administrator access
			// or server may have disabled rpc administration
			refreshStatus();
			allowAdministration = true;
		} catch (UnauthorizedException e) {
		} catch (ForbiddenException e) {
		} catch (NotAllowedException e) {
		} catch (UnknownRequestException e) {
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public boolean allowManagement() {
		return allowManagement;
	}

	public boolean allowAdministration() {
		return allowAdministration;
	}

	public boolean isOwner(RepositoryModel model) {
		return account != null && account.equalsIgnoreCase(model.owner);
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
		return allUsers;
	}

	public ServerSettings refreshSettings() throws IOException {
		settings = RpcUtils.getSettings(url, account, password);
		return settings;
	}

	public ServerStatus refreshStatus() throws IOException {
		status = RpcUtils.getStatus(url, account, password);
		return status;
	}

	public List<FeedModel> getAvailableFeeds() {
		return availableFeeds;
	}

	public List<FeedModel> getAvailableFeeds(RepositoryModel repository) {
		List<FeedModel> repositoryFeeds = new ArrayList<FeedModel>();
		if (repository == null) {
			return repositoryFeeds;
		}
		for (FeedModel feed : availableFeeds) {
			if (feed.repository.equalsIgnoreCase(repository.name)) {
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

	public List<SyndicatedEntryModel> refreshSubscribedFeeds() throws IOException {
		Set<SyndicatedEntryModel> allEntries = new HashSet<SyndicatedEntryModel>();
		if (reg.feeds.size() > 0) {
			for (FeedModel feed : reg.feeds) {
				feed.lastRefreshDate = feed.currentRefreshDate;
				feed.currentRefreshDate = new Date();
				List<SyndicatedEntryModel> entries = SyndicationUtils.readFeed(url,
						feed.repository, feed.branch, -1, account, password);
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

	public List<SyndicatedEntryModel> getSyndicatedEntries() {
		return syndicatedEntries;
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
			if (user.repositories.contains(repository.name)) {
				usernames.add(user.username);
			}
		}
		return usernames;
	}

	public List<String> getFederationSets() {
		return settings.get(Keys.federation.sets).getStrings();
	}

	public List<RepositoryModel> getRepositories() {
		return allRepositories;
	}

	public boolean createRepository(RepositoryModel repository, List<String> permittedUsers)
			throws IOException {
		boolean success = true;
		success &= RpcUtils.createRepository(repository, url, account, password);
		if (permittedUsers.size() > 0) {
			// if new repository has named members, set them
			success &= RpcUtils.setRepositoryMembers(repository, permittedUsers, url, account,
					password);
		}
		return success;
	}

	public boolean updateRepository(String name, RepositoryModel repository,
			List<String> permittedUsers) throws IOException {
		boolean success = true;
		success &= RpcUtils.updateRepository(name, repository, url, account, password);
		// always set the repository members
		success &= RpcUtils
				.setRepositoryMembers(repository, permittedUsers, url, account, password);
		return success;
	}

	public boolean deleteRepository(RepositoryModel repository) throws IOException {
		return RpcUtils.deleteRepository(repository, url, account, password);
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

	public boolean updateSettings(Map<String, String> newSettings) throws IOException {
		return RpcUtils.updateSettings(newSettings, url, account, password);
	}
}
