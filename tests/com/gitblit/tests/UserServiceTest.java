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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.gitblit.ConfigUserService;
import com.gitblit.FileUserService;
import com.gitblit.IUserService;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;

public class UserServiceTest {

	@Test
	public void testFileUserService() throws IOException {
		File file = new File("us-test.properties");
		file.delete();
		IUserService service = new FileUserService(file);
		testUsers(service);
		testTeams(service);
		file.delete();
	}

	@Test
	public void testConfigUserService() throws IOException {
		File file = new File("us-test.conf");
		file.delete();
		IUserService service = new ConfigUserService(file);
		testUsers(service);
		testTeams(service);
		file.delete();
	}

	protected void testUsers(IUserService service) {

		UserModel admin = service.getUserModel("admin");
		assertTrue(admin == null);

		// add admin
		admin = new UserModel("admin");
		admin.password = "password";
		admin.canAdmin = true;
		admin.excludeFromFederation = true;
		service.updateUserModel(admin);
		admin = null;

		// add new user
		UserModel newUser = new UserModel("test");
		newUser.password = "testPassword";
		newUser.addRepository("repo1");
		newUser.addRepository("repo2");
		newUser.addRepository("sub/repo3");
		service.updateUserModel(newUser);

		// add one more new user and then test reload of first new user
		newUser = new UserModel("garbage");
		newUser.password = "garbage";
		service.updateUserModel(newUser);

		// confirm all added users
		assertEquals(3, service.getAllUsernames().size());

		// confirm reloaded test user
		newUser = service.getUserModel("test");
		assertEquals("testPassword", newUser.password);
		assertEquals(3, newUser.repositories.size());
		assertTrue(newUser.hasRepository("repo1"));
		assertTrue(newUser.hasRepository("repo2"));
		assertTrue(newUser.hasRepository("sub/repo3"));

		// confirm authentication of test user
		UserModel testUser = service.authenticate("test", "testPassword".toCharArray());
		assertEquals("test", testUser.username);
		assertEquals("testPassword", testUser.password);

		// delete a repository role and confirm role removal from test user
		service.deleteRepositoryRole("repo2");
		testUser = service.getUserModel("test");
		assertEquals(2, testUser.repositories.size());

		// delete garbage user and confirm user count
		service.deleteUser("garbage");
		assertEquals(2, service.getAllUsernames().size());

		// rename repository and confirm role change for test user
		service.renameRepositoryRole("repo1", "newrepo1");
		testUser = service.getUserModel("test");
		assertTrue(testUser.hasRepository("newrepo1"));
	}

	protected void testTeams(IUserService service) {

		// confirm we have no teams
		assertEquals(0, service.getAllTeamNames().size());

		// remove newrepo1 from test user
		// now test user has no repositories
		UserModel user = service.getUserModel("test");
		user.repositories.clear();
		service.updateUserModel(user);
		user = service.getUserModel("test");
		assertEquals(0, user.repositories.size());
		assertFalse(user.canAccessRepository("newrepo1"));
		assertFalse(user.canAccessRepository("NEWREPO1"));

		// create test team and add test user and newrepo1
		TeamModel team = new TeamModel("testteam");
		team.addUser("test");
		team.addRepository("newrepo1");
		service.updateTeamModel(team);

		// confirm 1 user and 1 repo
		team = service.getTeamModel("testteam");
		assertEquals(1, team.repositories.size());
		assertEquals(1, team.users.size());

		// confirm team membership
		user = service.getUserModel("test");
		assertEquals(0, user.repositories.size());
		assertEquals(1, user.teams.size());

		// confirm team access
		assertTrue(team.hasRepository("newrepo1"));
		assertTrue(user.hasTeamAccess("newrepo1"));
		assertTrue(team.hasRepository("NEWREPO1"));
		assertTrue(user.hasTeamAccess("NEWREPO1"));

		// rename the team and add new repository
		team.addRepository("newrepo2");
		team.name = "testteam2";
		service.updateTeamModel("testteam", team);

		team = service.getTeamModel("testteam2");
		user = service.getUserModel("test");

		// confirm user and team can access newrepo2
		assertEquals(2, team.repositories.size());
		assertTrue(team.hasRepository("newrepo2"));
		assertTrue(user.hasTeamAccess("newrepo2"));
		assertTrue(team.hasRepository("NEWREPO2"));
		assertTrue(user.hasTeamAccess("NEWREPO2"));

		// delete testteam2
		service.deleteTeam("testteam2");
		team = service.getTeamModel("testteam2");
		user = service.getUserModel("test");

		// confirm team does not exist and user can not access newrepo1 and 2
		assertEquals(null, team);
		assertFalse(user.canAccessRepository("newrepo1"));
		assertFalse(user.canAccessRepository("newrepo2"));

		// create new team and add it to user
		// this tests the inverse team creation/team addition
		team = new TeamModel("testteam");
		team.addRepository("NEWREPO1");
		team.addRepository("NEWREPO2");
		user.teams.add(team);
		service.updateUserModel(user);

		// confirm the inverted team addition
		user = service.getUserModel("test");
		team = service.getTeamModel("testteam");
		assertTrue(user.hasTeamAccess("newrepo1"));
		assertTrue(user.hasTeamAccess("newrepo2"));
		assertTrue(team.hasUser("test"));

		// drop testteam from user and add nextteam to user
		team = new TeamModel("nextteam");
		team.addRepository("NEWREPO1");
		team.addRepository("NEWREPO2");
		user.teams.clear();
		user.teams.add(team);
		service.updateUserModel(user);

		// confirm implicit drop
		user = service.getUserModel("test");
		team = service.getTeamModel("testteam");
		assertTrue(user.hasTeamAccess("newrepo1"));
		assertTrue(user.hasTeamAccess("newrepo2"));
		assertFalse(team.hasUser("test"));
		team = service.getTeamModel("nextteam");
		assertTrue(team.hasUser("test"));

		// delete the user and confirm team no longer has user
		service.deleteUser("test");
		team = service.getTeamModel("testteam");
		assertFalse(team.hasUser("test"));

		// delete both teams
		service.deleteTeam("testteam");
		service.deleteTeam("nextteam");
		assertEquals(0, service.getAllTeamNames().size());
	}
}