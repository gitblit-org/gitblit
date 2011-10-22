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
import java.util.List;
import java.util.Map;

import com.gitblit.GitBlitException.ForbiddenException;
import com.gitblit.GitBlitException.UnauthorizedException;
import com.gitblit.Keys;
import com.gitblit.models.FederationModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.ServerSettings;
import com.gitblit.models.ServerStatus;
import com.gitblit.models.UserModel;
import com.gitblit.utils.RpcUtils;

/**
 * GitblitClient is a object that retrieves data from a Gitblit server, caches
 * it for local operations, and allows updating or creating Gitblit objects.
 * 
 * @author James Moger
 * 
 */
public class GitblitClient implements Serializable {

	private static final long serialVersionUID = 1L;

	public final String url;

	public final String account;

	private final char[] password;

	private volatile boolean isAdmin;

	private volatile ServerSettings settings;

	private final List<RepositoryModel> allRepositories;

	private final List<UserModel> allUsers;

	private final List<FederationModel> federationRegistrations;

	private ServerStatus status;

	public GitblitClient(String url, String account, char[] password) {
		this.url = url;
		this.account = account;
		this.password = password;

		this.allUsers = new ArrayList<UserModel>();
		this.allRepositories = new ArrayList<RepositoryModel>();
		this.federationRegistrations = new ArrayList<FederationModel>();
	}

	public void login() throws IOException {
		refreshRepositories();

		try {
			settings = RpcUtils.getSettings(url, account, password);
			status = RpcUtils.getStatus(url, account, password);
			refreshUsers();
			isAdmin = true;
		} catch (UnauthorizedException e) {
		} catch (ForbiddenException e) {
		} catch (IOException e) {
			System.err.println(e.getMessage());
		}
	}

	public boolean allowAdmin() {
		return isAdmin;
	}

	public boolean isOwner(RepositoryModel model) {
		return account != null && account.equalsIgnoreCase(model.owner);
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
		return allRepositories;
	}

	public List<UserModel> refreshUsers() throws IOException {
		List<UserModel> users = RpcUtils.getUsers(url, account, password);
		allUsers.clear();
		allUsers.addAll(users);
		return allUsers;
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
}
