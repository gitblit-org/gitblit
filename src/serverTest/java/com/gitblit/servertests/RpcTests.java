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
package com.gitblit.servertests;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.AuthorizationControl;
import com.gitblit.Constants.PermissionType;
import com.gitblit.Constants.RegistrantType;
import com.gitblit.GitBlitException.ForbiddenException;
import com.gitblit.GitBlitException.UnauthorizedException;
import com.gitblit.Keys;
import com.gitblit.models.FederationModel;
import com.gitblit.models.FederationProposal;
import com.gitblit.models.FederationSet;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.ServerSettings;
import com.gitblit.models.ServerStatus;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.servlet.RpcServlet;
import com.gitblit.utils.RpcUtils;

/**
 * Tests all the rpc client utility methods, the rpc filter and rpc servlet.
 *
 * @author James Moger
 *
 */
public class RpcTests extends GitblitUnitTest {

	String url = GitBlitSuite.url;
	String account = GitBlitSuite.account;
	String password = GitBlitSuite.password;

	private static final AtomicBoolean started = new AtomicBoolean(false);

	@BeforeClass
	public static void startGitblit() throws Exception {
		started.set(GitBlitSuite.startGitblit());
	}

	@AfterClass
	public static void stopGitblit() throws Exception {
		//clean up the "A-Team" if left over
		TeamModel aTeam = new TeamModel("A-Team");
		aTeam.addRepositoryPermission("helloworld.git");
		RpcUtils.deleteTeam(aTeam, GitBlitSuite.url, GitBlitSuite.account, GitBlitSuite.password.toCharArray());

		if (started.get()) {
			GitBlitSuite.stopGitblit();
		}
	}

	@Test
	public void testGetProtocolVersion() throws IOException {
		int protocol = RpcUtils.getProtocolVersion(url, null, null);
		assertEquals(RpcServlet.PROTOCOL_VERSION, protocol);
	}

	@Test
	public void testListRepositories() throws IOException {
		Map<String, RepositoryModel> map = RpcUtils.getRepositories(url, null, null);
		assertNotNull("Repository list is null!", map);
		assertTrue("Repository list is empty!", map.size() > 0);
	}

	@Test
	public void testListUsers() throws IOException {
		List<UserModel> list = null;
		try {
			list = RpcUtils.getUsers(url, null, null);
		} catch (UnauthorizedException e) {
		}
		assertNull("Server allows anyone to admin!", list);

		list = RpcUtils.getUsers(url, "admin", "admin".toCharArray());
		assertTrue("User list is empty!", list.size() > 0);
	}

	@Test
	public void testGetUser() throws IOException {
		UserModel user = null;
		try {
			user = RpcUtils.getUser("admin", url, null, null);
		} catch (ForbiddenException e) {
		}
		assertNull("Server allows anyone to get user!", user);

		user = RpcUtils.getUser("admin", url, "admin", "admin".toCharArray());
		assertEquals("User is not the admin!", "admin", user.username);
		assertTrue("User is not an administrator!", user.canAdmin());
	}

	@Test
	public void testListTeams() throws IOException {
		List<TeamModel> list = null;
		try {
			list = RpcUtils.getTeams(url, null, null);
		} catch (UnauthorizedException e) {
		}
		assertNull("Server allows anyone to admin!", list);

		list = RpcUtils.getTeams(url, "admin", "admin".toCharArray());
		assertTrue("Team list is empty!", list.size() > 0);
		assertEquals("admins", list.get(0).name);
	}

	@Test
	public void testUserAdministration() throws IOException {
		UserModel user = new UserModel("garbage");
		user.canAdmin = true;
		user.password = "whocares";

		// create
		assertTrue("Failed to create user!",
				RpcUtils.createUser(user, url, account, password.toCharArray()));

		UserModel retrievedUser = findUser(user.username);
		assertNotNull("Failed to find " + user.username, retrievedUser);
		assertTrue("Retrieved user can not administer Gitblit", retrievedUser.canAdmin);

		// rename and toggle admin permission
		String originalName = user.username;
		user.username = "garbage2";
		user.canAdmin = false;
		assertTrue("Failed to update user!",
				RpcUtils.updateUser(originalName, user, url, account, password.toCharArray()));

		retrievedUser = findUser(user.username);
		assertNotNull("Failed to find " + user.username, retrievedUser);
		assertTrue("Retrieved user did not update", !retrievedUser.canAdmin);

		// delete
		assertTrue("Failed to delete " + user.username,
				RpcUtils.deleteUser(retrievedUser, url, account, password.toCharArray()));

		retrievedUser = findUser(user.username);
		assertNull("Failed to delete " + user.username, retrievedUser);
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

	@Test
	public void testRepositoryAdministration() throws IOException {
		RepositoryModel model = new RepositoryModel();
		model.name = "garbagerepo.git";
		model.description = "created by RpcUtils";
		model.addOwner("garbage");
		model.accessRestriction = AccessRestrictionType.VIEW;
		model.authorizationControl = AuthorizationControl.AUTHENTICATED;

		// create
		RpcUtils.deleteRepository(model, url, account, password.toCharArray());
		assertTrue("Failed to create repository!",
				RpcUtils.createRepository(model, url, account, password.toCharArray()));

		RepositoryModel retrievedRepository = findRepository(model.name);
		assertNotNull("Failed to find " + model.name, retrievedRepository);
		assertEquals(AccessRestrictionType.VIEW, retrievedRepository.accessRestriction);
		assertEquals(AuthorizationControl.AUTHENTICATED, retrievedRepository.authorizationControl);

		// rename and change access restriciton
		String originalName = model.name;
		model.name = "garbagerepo2.git";
		model.accessRestriction = AccessRestrictionType.CLONE;
		model.authorizationControl = AuthorizationControl.NAMED;
		RpcUtils.deleteRepository(model, url, account, password.toCharArray());
		assertTrue("Failed to update repository!", RpcUtils.updateRepository(originalName, model,
				url, account, password.toCharArray()));

		retrievedRepository = findRepository(model.name);
		assertNotNull("Failed to find " + model.name, retrievedRepository);
		assertTrue("Access retriction type is wrong",
				AccessRestrictionType.CLONE.equals(retrievedRepository.accessRestriction));

		// restore VIEW restriction
		retrievedRepository.accessRestriction = AccessRestrictionType.VIEW;
		assertTrue("Failed to update repository!", RpcUtils.updateRepository(retrievedRepository.name, retrievedRepository,
				url, account, password.toCharArray()));
		retrievedRepository = findRepository(retrievedRepository.name);

		// memberships
		UserModel testMember = new UserModel("justadded");
		assertTrue(RpcUtils.createUser(testMember, url, account, password.toCharArray()));

		List<RegistrantAccessPermission> permissions = RpcUtils.getRepositoryMemberPermissions(retrievedRepository, url, account,
				password.toCharArray());
		assertEquals("Unexpected permissions! " + permissions.toString(), 1, permissions.size());
		permissions.add(new RegistrantAccessPermission(testMember.username, AccessPermission.VIEW, PermissionType.EXPLICIT, RegistrantType.USER, null, true));
		assertTrue(
				"Failed to set member permissions!",
				RpcUtils.setRepositoryMemberPermissions(retrievedRepository, permissions, url, account,
						password.toCharArray()));
		permissions = RpcUtils.getRepositoryMemberPermissions(retrievedRepository, url, account,
				password.toCharArray());
		boolean foundMember = false;
		for (RegistrantAccessPermission permission : permissions) {
			if (permission.registrant.equalsIgnoreCase(testMember.username)) {
				foundMember = true;
				assertEquals(AccessPermission.VIEW, permission.permission);
				break;
			}
		}
		assertTrue("Failed to find member!", foundMember);

		// delete
		assertTrue("Failed to delete " + model.name, RpcUtils.deleteRepository(retrievedRepository,
				url, account, password.toCharArray()));

		retrievedRepository = findRepository(model.name);
		assertNull("Failed to delete " + model.name, retrievedRepository);

		for (UserModel u : RpcUtils.getUsers(url, account, password.toCharArray())) {
			if (u.username.equals(testMember.username)) {
				assertTrue(RpcUtils.deleteUser(u, url, account, password.toCharArray()));
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

	@Test
	public void testTeamAdministration() throws IOException {
		//clean up the "A-Team" left over from previous run, if any
		TeamModel aTeam = new TeamModel("A-Team");
		aTeam.addRepositoryPermission("helloworld.git");
		RpcUtils.deleteTeam(aTeam, url, account, password.toCharArray());

		List<TeamModel> teams = RpcUtils.getTeams(url, account, password.toCharArray());
		//should be just the admins team
		assertEquals("In addition to 'admins', too many left-over team(s) in Gitblit server: " + teams, 1, teams.size());

		// Create the A-Team
		aTeam = new TeamModel("A-Team");
		aTeam.users.add("admin");
		aTeam.addRepositoryPermission("helloworld.git");
		assertTrue(RpcUtils.createTeam(aTeam, url, account, password.toCharArray()));

		aTeam = null;
		teams = RpcUtils.getTeams(url, account, password.toCharArray());
		assertEquals(2, teams.size());
		for (TeamModel team : teams) {
			if (team.name.equals("A-Team")) {
				aTeam = team;
				break;
			}
		}
		assertNotNull(aTeam);
		assertTrue(aTeam.hasUser("admin"));
		assertTrue(aTeam.hasRepositoryPermission("helloworld.git"));

		RepositoryModel helloworld = null;
		Map<String, RepositoryModel> repositories = RpcUtils.getRepositories(url, account,
				password.toCharArray());
		for (RepositoryModel repository : repositories.values()) {
			if (repository.name.equals("helloworld.git")) {
				helloworld = repository;
				break;
			}
		}
		assertNotNull(helloworld);

		// Confirm that we have added the team
		List<String> helloworldTeams = RpcUtils.getRepositoryTeams(helloworld, url, account,
				password.toCharArray());
		assertEquals(1, helloworldTeams.size());
		assertTrue(helloworldTeams.contains(aTeam.name));

		// set no teams
		List<RegistrantAccessPermission> permissions = new ArrayList<RegistrantAccessPermission>();
		for (String team : helloworldTeams) {
			permissions.add(new RegistrantAccessPermission(team, AccessPermission.NONE, PermissionType.EXPLICIT, RegistrantType.TEAM, null, true));
		}
		assertTrue(RpcUtils.setRepositoryTeamPermissions(helloworld, permissions, url, account,
				password.toCharArray()));
		helloworldTeams = RpcUtils.getRepositoryTeams(helloworld, url, account,
				password.toCharArray());
		assertEquals(0, helloworldTeams.size());

		// delete the A-Team
		assertTrue(RpcUtils.deleteTeam(aTeam, url, account, password.toCharArray()));

		teams = RpcUtils.getTeams(url, account, password.toCharArray());
		assertEquals(1, teams.size());
	}

	@Test
	public void testFederationRegistrations() throws Exception {
		List<FederationModel> registrations = RpcUtils.getFederationRegistrations(url, account,
				password.toCharArray());
		assertTrue("No federation registrations were retrieved!", registrations.size() >= 0);
	}

	@Test
	public void testFederationResultRegistrations() throws Exception {
		List<FederationModel> registrations = RpcUtils.getFederationResultRegistrations(url,
				account, password.toCharArray());
		assertTrue("No federation result registrations were retrieved!", registrations.size() >= 0);
	}

	@Test
	public void testFederationProposals() throws Exception {
		List<FederationProposal> proposals = RpcUtils.getFederationProposals(url, account,
				password.toCharArray());
		assertTrue("No federation proposals were retrieved!", proposals.size() >= 0);
	}

	@Test
	public void testFederationSets() throws Exception {
		List<FederationSet> sets = RpcUtils.getFederationSets(url, account, password.toCharArray());
		assertTrue("No federation sets were retrieved!", sets.size() >= 0);
	}

	@Test
	public void testSettings() throws Exception {
		ServerSettings settings = RpcUtils.getSettings(url, account, password.toCharArray());
		assertNotNull("No settings were retrieved!", settings);
	}

	@Test
	public void testServerStatus() throws Exception {
		ServerStatus status = RpcUtils.getStatus(url, account, password.toCharArray());
		assertNotNull("No status was retrieved!", status);
	}

	@Test
	public void testUpdateSettings() throws Exception {
		Map<String, String> updated = new HashMap<String, String>();

		// grab current setting
		ServerSettings settings = RpcUtils.getSettings(url, account, password.toCharArray());
		boolean showSizes = settings.get(Keys.web.showRepositorySizes).getBoolean(true);
		showSizes = !showSizes;

		// update setting
		updated.put(Keys.web.showRepositorySizes, String.valueOf(showSizes));
		boolean success = RpcUtils.updateSettings(updated, url, account, password.toCharArray());
		assertTrue("Failed to update server settings", success);

		// confirm setting change
		settings = RpcUtils.getSettings(url, account, password.toCharArray());
		boolean newValue = settings.get(Keys.web.showRepositorySizes).getBoolean(false);
		assertEquals(newValue, showSizes);

		// restore setting
		newValue = !newValue;
		updated.put(Keys.web.showRepositorySizes, String.valueOf(newValue));
		success = RpcUtils.updateSettings(updated, url, account, password.toCharArray());
		assertTrue("Failed to update server settings", success);
		settings = RpcUtils.getSettings(url, account, password.toCharArray());
		showSizes = settings.get(Keys.web.showRepositorySizes).getBoolean(true);
		assertEquals(newValue, showSizes);
	}

	@Test
	public void testBranches() throws Exception {
		Map<String, Collection<String>> branches = RpcUtils.getBranches(url, account,
				password.toCharArray());
		assertNotNull(branches);
		assertTrue(branches.size() > 0);
	}

	@Test
	public void testFork() throws Exception {
		// test forking by an administrator
		// admins are all-powerful and can fork the unforakable :)
		testFork(account, password, true, true);
		testFork(account, password, false, true);

		// test forking by a permitted normal user
		UserModel forkUser = new UserModel("forkuser");
		forkUser.password = forkUser.username;
		forkUser.canFork = true;
		RpcUtils.deleteUser(forkUser, url, account, password.toCharArray());
		RpcUtils.createUser(forkUser, url, account, password.toCharArray());
		testFork(forkUser.username, forkUser.password, true, true);
		testFork(forkUser.username, forkUser.password, false, false);
		RpcUtils.deleteUser(forkUser, url, account, password.toCharArray());

		// test forking by a non-permitted normal user
		UserModel noForkUser = new UserModel("noforkuser");
		noForkUser.password = noForkUser.username;
		noForkUser.canFork = false;
		RpcUtils.deleteUser(noForkUser, url, account, password.toCharArray());
		RpcUtils.createUser(noForkUser, url, account, password.toCharArray());
		testFork(forkUser.username, forkUser.password, true, false);
		testFork(forkUser.username, forkUser.password, false, false);
		RpcUtils.deleteUser(noForkUser, url, account, password.toCharArray());
	}

	private void testFork(String forkAcct, String forkAcctPassword, boolean allowForks, boolean expectSuccess) throws Exception {
		// test does not exist
		RepositoryModel dne = new RepositoryModel();
		dne.name = "doesNotExist.git";
        assertFalse(String.format("Successfully forked %s!", dne.name),
                RpcUtils.forkRepository(dne, url, forkAcct, forkAcctPassword.toCharArray()));

		// delete any previous fork
		RepositoryModel fork = findRepository(String.format("~%s/helloworld.git", forkAcct));
		if (fork != null) {
			RpcUtils.deleteRepository(fork, url, account, password.toCharArray());
		}

		// update the origin to allow forks or not
		RepositoryModel origin = findRepository("helloworld.git");
		origin.allowForks = allowForks;
		RpcUtils.updateRepository(origin.name, origin, url, account, password.toCharArray());

		// fork the repository
		if (expectSuccess) {
			assertTrue(String.format("Failed to fork %s!", origin.name),
                RpcUtils.forkRepository(origin, url, forkAcct, forkAcctPassword.toCharArray()));
		} else {
			assertFalse(String.format("Successfully forked %s!", origin.name),
	                RpcUtils.forkRepository(origin, url, forkAcct, forkAcctPassword.toCharArray()));
		}

        // attempt another fork
        assertFalse(String.format("Successfully forked %s!", origin.name),
                RpcUtils.forkRepository(origin, url, forkAcct, forkAcctPassword.toCharArray()));

        // delete the fork repository
        RpcUtils.deleteRepository(fork, url, account, password.toCharArray());
	}
}
