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
package com.gitblit.tests;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.GitBlitException.UnauthorizedException;
import com.gitblit.models.FederationModel;
import com.gitblit.models.FederationProposal;
import com.gitblit.models.FederationSet;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.ServerStatus;
import com.gitblit.models.SettingModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.RpcUtils;

/**
 * Tests all the rpc client utility methods, the rpc filter and rpc servlet.
 * 
 * @author James Moger
 * 
 */
public class RpcTests extends TestCase {

	String url = "https://localhost:8443";
	String account = "admin";
	String password = "admin";

	public void testListRepositories() throws IOException {
		Map<String, RepositoryModel> map = RpcUtils.getRepositories(url, null, null);
		assertTrue("Repository list is null!", map != null);
		assertTrue("Repository list is empty!", map.size() > 0);
	}

	public void testListUsers() throws IOException {
		List<UserModel> list = null;
		try {
			list = RpcUtils.getUsers(url, null, null);
		} catch (UnauthorizedException e) {
		}
		assertTrue("Server allows anyone to admin!", list == null);

		list = RpcUtils.getUsers(url, "admin", "admin".toCharArray());
		assertTrue("User list is empty!", list.size() > 0);
	}

	public void testUserAdministration() throws IOException {
		UserModel user = new UserModel("garbage");
		user.canAdmin = true;
		user.password = "whocares";

		// create
		assertTrue("Failed to create user!",
				RpcUtils.createUser(user, url, account, password.toCharArray()));

		UserModel retrievedUser = findUser(user.username);
		assertTrue("Failed to find " + user.username, retrievedUser != null);
		assertTrue("Retrieved user can not administer Gitblit", retrievedUser.canAdmin);

		// rename and toggle admin permission
		String originalName = user.username;
		user.username = "garbage2";
		user.canAdmin = false;
		assertTrue("Failed to update user!",
				RpcUtils.updateUser(originalName, user, url, account, password.toCharArray()));

		retrievedUser = findUser(user.username);
		assertTrue("Failed to find " + user.username, retrievedUser != null);
		assertTrue("Retrieved user did not update", !retrievedUser.canAdmin);

		// delete
		assertTrue("Failed to delete " + user.username,
				RpcUtils.deleteUser(retrievedUser, url, account, password.toCharArray()));

		retrievedUser = findUser(user.username);
		assertTrue("Failed to delete " + user.username, retrievedUser == null);
	}

	private UserModel findUser(String name) throws IOException {
		List<UserModel> users = RpcUtils.getUsers(url, account, password.toCharArray());
		UserModel retrievedUser = null;
		for (UserModel model : users) {
			if (model.username.equalsIgnoreCase(name)) {
				retrievedUser = model;
				break;
			}
		}
		return retrievedUser;
	}

	public void testRepositoryAdministration() throws IOException {
		RepositoryModel model = new RepositoryModel();
		model.name = "garbagerepo.git";
		model.description = "created by RpcUtils";
		model.owner = "garbage";
		model.accessRestriction = AccessRestrictionType.VIEW;

		// create
		assertTrue("Failed to create repository!",
				RpcUtils.createRepository(model, url, account, password.toCharArray()));

		RepositoryModel retrievedRepository = findRepository(model.name);
		assertTrue("Failed to find " + model.name, retrievedRepository != null);
		assertTrue("Access retriction type is wrong",
				AccessRestrictionType.VIEW.equals(retrievedRepository.accessRestriction));

		// rename and change access restriciton
		String originalName = model.name;
		model.name = "garbagerepo2.git";
		model.accessRestriction = AccessRestrictionType.PUSH;
		assertTrue("Failed to update repository!", RpcUtils.updateRepository(originalName, model,
				url, account, password.toCharArray()));

		retrievedRepository = findRepository(model.name);
		assertTrue("Failed to find " + model.name, retrievedRepository != null);
		assertTrue("Access retriction type is wrong",
				AccessRestrictionType.PUSH.equals(retrievedRepository.accessRestriction));

		// memberships
		String testMember = "justadded";
		List<String> members = RpcUtils.getRepositoryMembers(retrievedRepository, url, account,
				password.toCharArray());
		assertTrue("Membership roster is not empty!", members.size() == 0);
		members.add(testMember);
		assertTrue(
				"Failed to set memberships!",
				RpcUtils.setRepositoryMembers(retrievedRepository, members, url, account,
						password.toCharArray()));
		members = RpcUtils.getRepositoryMembers(retrievedRepository, url, account,
				password.toCharArray());
		boolean foundMember = false;
		for (String member : members) {
			if (member.equalsIgnoreCase(testMember)) {
				foundMember = true;
				break;
			}
		}
		assertTrue("Failed to find member!", foundMember);

		// delete
		assertTrue("Failed to delete " + model.name, RpcUtils.deleteRepository(retrievedRepository,
				url, account, password.toCharArray()));

		retrievedRepository = findRepository(model.name);
		assertTrue("Failed to delete " + model.name, retrievedRepository == null);

		for (UserModel u : RpcUtils.getUsers(url, account, password.toCharArray())) {
			if (u.username.equals(testMember)) {
				RpcUtils.deleteUser(u, url, account, password.toCharArray());
				break;
			}
		}
	}

	private RepositoryModel findRepository(String name) throws IOException {
		Map<String, RepositoryModel> repositories = RpcUtils.getRepositories(url, account,
				password.toCharArray());
		RepositoryModel retrievedRepository = null;
		for (RepositoryModel model : repositories.values()) {
			if (model.name.equalsIgnoreCase(name)) {
				retrievedRepository = model;
				break;
			}
		}
		return retrievedRepository;
	}

	public void testFederationRegistrations() throws Exception {
		List<FederationModel> registrations = RpcUtils.getFederationRegistrations(url, account,
				password.toCharArray());
		assertTrue("No federation registrations wre retrieved!", registrations.size() > 0);
	}

	public void testFederationResultRegistrations() throws Exception {
		List<FederationModel> registrations = RpcUtils.getFederationResultRegistrations(url,
				account, password.toCharArray());
		assertTrue("No federation result registrations were retrieved!", registrations.size() > 0);
	}

	public void testFederationProposals() throws Exception {
		List<FederationProposal> proposals = RpcUtils.getFederationProposals(url, account,
				password.toCharArray());
		assertTrue("No federation proposals were retrieved!", proposals.size() > 0);
	}

	public void testFederationSets() throws Exception {
		List<FederationSet> sets = RpcUtils.getFederationSets(url, account, password.toCharArray());
		assertTrue("No federation sets were retrieved!", sets.size() > 0);
	}

	public void testSettings() throws Exception {
		Map<String, SettingModel> settings = RpcUtils.getSettings(url, account, password.toCharArray());
		assertTrue("No settings were retrieved!", settings != null);
	}
	
	public void testServerStatus() throws Exception {
		ServerStatus status = RpcUtils.getStatus(url, account, password.toCharArray());
		assertTrue("No status was retrieved!", status != null);
	}
}
