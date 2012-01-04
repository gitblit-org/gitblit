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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.FederationProposalResult;
import com.gitblit.Constants.FederationRequest;
import com.gitblit.Constants.FederationToken;
import com.gitblit.models.FederationModel;
import com.gitblit.models.FederationProposal;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.FederationUtils;
import com.gitblit.utils.JsonUtils;
import com.gitblit.utils.RpcUtils;

public class FederationTests {

	String url = GitBlitSuite.url;
	String account = GitBlitSuite.account;
	String password = GitBlitSuite.password;
	String token = "d7cc58921a80b37e0329a4dae2f9af38bf61ef5c";

	private static final AtomicBoolean started = new AtomicBoolean(false);

	@BeforeClass
	public static void startGitblit() throws Exception {
		started.set(GitBlitSuite.startGitblit());
	}

	@AfterClass
	public static void stopGitblit() throws Exception {
		if (started.get()) {
			GitBlitSuite.stopGitblit();
		}
	}

	@Test
	public void testProposal() throws Exception {
		// create dummy repository data
		Map<String, RepositoryModel> repositories = new HashMap<String, RepositoryModel>();
		for (int i = 0; i < 5; i++) {
			RepositoryModel model = new RepositoryModel();
			model.accessRestriction = AccessRestrictionType.VIEW;
			model.description = "cloneable repository " + i;
			model.lastChange = new Date();
			model.owner = "adminuser";
			model.name = "repo" + i + ".git";
			model.size = "5 MB";
			model.hasCommits = true;
			repositories.put(model.name, model);
		}

		FederationProposal proposal = new FederationProposal("http://testurl", FederationToken.ALL,
				"testtoken", repositories);

		// propose federation
		assertEquals("proposal refused", FederationUtils.propose(url, proposal),
				FederationProposalResult.NO_PROPOSALS);
	}

	@Test
	public void testJsonRepositories() throws Exception {
		String requrl = FederationUtils.asLink(url, token, FederationRequest.PULL_REPOSITORIES);
		String json = JsonUtils.retrieveJsonString(requrl, null, null);
		assertNotNull(json);
	}

	@Test
	public void testJsonUsers() throws Exception {
		String requrl = FederationUtils.asLink(url, token, FederationRequest.PULL_USERS);
		String json = JsonUtils.retrieveJsonString(requrl, null, null);
		assertNotNull(json);
	}

	@Test
	public void testJsonTeams() throws Exception {
		String requrl = FederationUtils.asLink(url, token, FederationRequest.PULL_TEAMS);
		String json = JsonUtils.retrieveJsonString(requrl, null, null);
		assertNotNull(json);
	}

	private FederationModel getRegistration() {
		FederationModel model = new FederationModel("localhost");
		model.url = this.url;
		model.token = this.token;
		return model;
	}

	@Test
	public void testPullRepositories() throws Exception {
		Map<String, RepositoryModel> repos = FederationUtils.getRepositories(getRegistration(),
				false);
		assertNotNull(repos);
		assertTrue(repos.size() > 0);
	}

	@Test
	public void testPullUsers() throws Exception {
		List<UserModel> users = FederationUtils.getUsers(getRegistration());
		assertNotNull(users);
		// admin is excluded
		assertEquals(0, users.size());
		
		UserModel newUser = new UserModel("test");
		newUser.password = "whocares";
		assertTrue(RpcUtils.createUser(newUser, url, account, password.toCharArray()));
		
		TeamModel team = new TeamModel("testteam");
		team.addUser("test");
		team.addRepository("helloworld.git");
		assertTrue(RpcUtils.createTeam(team, url, account, password.toCharArray()));
		
		users = FederationUtils.getUsers(getRegistration());
		assertNotNull(users);
		assertEquals(1, users.size());
		
		newUser = users.get(0);
		assertTrue(newUser.isTeamMember("testteam"));		
		
		assertTrue(RpcUtils.deleteUser(newUser, url, account, password.toCharArray()));
		assertTrue(RpcUtils.deleteTeam(team, url, account, password.toCharArray()));
	}

	@Test
	public void testPullTeams() throws Exception {
		List<TeamModel> teams = FederationUtils.getTeams(getRegistration());
		assertNotNull(teams);
		assertTrue(teams.size() > 0);
	}
	
	@Test
	public void testPullScripts() throws Exception {
		Map<String, String> scripts = FederationUtils.getScripts(getRegistration());
		assertNotNull(scripts);
		assertTrue(scripts.keySet().contains("sendmail"));
	}
}
