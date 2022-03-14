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

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.gitblit.ConfigUserService;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.IUserService;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;

public class UserServiceTest extends GitblitUnitTest {

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

		// add admin and admins team
		TeamModel admins = new TeamModel("admins");
		admins.mailingLists.add("admins@localhost.com");

		admin = new UserModel("admin");
		admin.password = "password";
		admin.canAdmin = true;
		admin.excludeFromFederation = true;
		admin.teams.add(admins);

		service.updateUserModel(admin);
		admin = null;
		admins = null;

		// add new user
		UserModel newUser = new UserModel("test");
		newUser.password = "testPassword";
		newUser.addRepositoryPermission("repo1");
		newUser.addRepositoryPermission("repo2");
		newUser.addRepositoryPermission("sub/repo3");
		service.updateUserModel(newUser);

		// add one more new user and then test reload of first new user
		newUser = new UserModel("GARBAGE");
		newUser.password = "garbage";
		service.updateUserModel(newUser);

		// confirm all added users
		assertEquals(3, service.getAllUsernames().size());
		assertTrue(service.getUserModel("garbage") != null);
		assertTrue(service.getUserModel("GaRbAgE") != null);
		assertTrue(service.getUserModel("GARBAGE") != null);

		// confirm reloaded test user
		newUser = service.getUserModel("test");
		assertEquals("testPassword", newUser.password);
		assertEquals(3, newUser.permissions.size());
		assertTrue(newUser.hasRepositoryPermission("repo1"));
		assertTrue(newUser.hasRepositoryPermission("repo2"));
		assertTrue(newUser.hasRepositoryPermission("sub/repo3"));

		// delete a repository role and confirm role removal from test user
		service.deleteRepositoryRole("repo2");
		UserModel testUser = service.getUserModel("test");
		assertEquals(2, testUser.permissions.size());

		// delete garbage user and confirm user count
		service.deleteUser("garbage");
		assertEquals(2, service.getAllUsernames().size());

		// rename repository and confirm role change for test user
		service.renameRepositoryRole("repo1", "newrepo1");
		testUser = service.getUserModel("test");
		assertTrue(testUser.hasRepositoryPermission("newrepo1"));
	}

	protected void testTeams(IUserService service) {

		// confirm we have 1 team (admins)
		assertEquals(1, service.getAllTeamNames().size());
		assertEquals("admins", service.getAllTeamNames().get(0));

		RepositoryModel newrepo1 = new RepositoryModel("newrepo1", null, null, null);
		newrepo1.accessRestriction = AccessRestrictionType.VIEW;
		RepositoryModel NEWREPO1 = new RepositoryModel("NEWREPO1", null, null, null);
		NEWREPO1.accessRestriction = AccessRestrictionType.VIEW;

		// remove newrepo1 from test user
		// now test user has no repositories
		UserModel user = service.getUserModel("test");
		user.permissions.clear();
		service.updateUserModel(user);
		user = service.getUserModel("test");
		assertEquals(0, user.permissions.size());
		assertFalse(user.canView(newrepo1));
		assertFalse(user.canView(NEWREPO1));

		// create test team and add test user and newrepo1
		TeamModel team = new TeamModel("testteam");
		team.addUser("test");
		team.addRepositoryPermission(newrepo1.name);
		service.updateTeamModel(team);

		// confirm 1 user and 1 repo
		team = service.getTeamModel("testteam");
		assertEquals(1, team.permissions.size());
		assertEquals(1, team.users.size());

		// confirm team membership
		user = service.getUserModel("test");
		assertEquals(0, user.permissions.size());
		assertEquals(1, user.teams.size());

		// confirm team access
		assertTrue(team.hasRepositoryPermission(newrepo1.name));
		assertTrue(user.canView(newrepo1));
		assertTrue(team.hasRepositoryPermission(NEWREPO1.name));
		assertTrue(user.canView(NEWREPO1));

		// rename the team and add new repository
		RepositoryModel newrepo2 = new RepositoryModel("newrepo2", null, null, null);
		newrepo2.accessRestriction = AccessRestrictionType.VIEW;
		RepositoryModel NEWREPO2 = new RepositoryModel("NEWREPO2", null, null, null);
		NEWREPO2.accessRestriction = AccessRestrictionType.VIEW;

		team.addRepositoryPermission(newrepo2.name);
		team.name = "testteam2";
		service.updateTeamModel("testteam", team);

		team = service.getTeamModel("testteam2");
		user = service.getUserModel("test");

		// confirm user and team can access newrepo2
		assertEquals(2, team.permissions.size());
		assertTrue(team.hasRepositoryPermission(newrepo2.name));
		assertTrue(user.canView(newrepo2));
		assertTrue(team.hasRepositoryPermission(NEWREPO2.name));
		assertTrue(user.canView(NEWREPO2));

		// delete testteam2
		service.deleteTeam("testteam2");
		team = service.getTeamModel("testteam2");
		user = service.getUserModel("test");

		// confirm team does not exist and user can not access newrepo1 and 2
		assertEquals(null, team);
		assertFalse(user.canView(newrepo1));
		assertFalse(user.canView(newrepo2));

		// create new team and add it to user
		// this tests the inverse team creation/team addition
		team = new TeamModel("testteam");
		team.addRepositoryPermission(NEWREPO1.name);
		team.addRepositoryPermission(NEWREPO2.name);
		user.teams.add(team);
		service.updateUserModel(user);

		// confirm the inverted team addition
		user = service.getUserModel("test");
		team = service.getTeamModel("testteam");
		assertTrue(user.canView(newrepo1));
		assertTrue(user.canView(newrepo2));
		assertTrue(team.hasUser("test"));

		// drop testteam from user and add nextteam to user
		team = new TeamModel("nextteam");
		team.addRepositoryPermission(NEWREPO1.name);
		team.addRepositoryPermission(NEWREPO2.name);
		user.teams.clear();
		user.teams.add(team);
		service.updateUserModel(user);

		// confirm implicit drop
		user = service.getUserModel("test");
		team = service.getTeamModel("testteam");
		assertTrue(user.canView(newrepo1));
		assertTrue(user.canView(newrepo2));
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

		// assert we still have the admins team
		assertEquals(1, service.getAllTeamNames().size());
		assertEquals("admins", service.getAllTeamNames().get(0));

		team = service.getTeamModel("admins");
		assertEquals(1, team.mailingLists.size());
		assertTrue(team.mailingLists.contains("admins@localhost.com"));
	}


	@Test
	public void testConfigUserServiceEmailExploit() throws IOException
	{
		File file = new File("us-test.conf");
		file.delete();
		IUserService service = new ConfigUserService(file);

		try {
			UserModel admin = service.getUserModel("admin");
			assertTrue(admin == null);

			// add admin
			admin = new UserModel("admin");
			admin.password = "secret";
			admin.canAdmin = true;
			admin.excludeFromFederation = true;

			service.updateUserModel(admin);
			admin = null;

			// add new user
			UserModel newUser = new UserModel("mallory");
			newUser.password = "password";
			newUser.emailAddress = "mallory@example.com";
			newUser.addRepositoryPermission("repo1");
			service.updateUserModel(newUser);

			// confirm all added users
			assertEquals(2, service.getAllUsernames().size());
			assertTrue(service.getUserModel("admin") != null);
			assertTrue(service.getUserModel("mallory") != null);

			// confirm reloaded test user
			newUser = service.getUserModel("mallory");
			assertEquals("password", newUser.password);
			assertEquals(1, newUser.permissions.size());
			assertTrue(newUser.hasRepositoryPermission("repo1"));
			assertFalse(newUser.canAdmin);


			// Change email address trying to sneak in admin permissions
			newUser = service.getUserModel("mallory");
			newUser.emailAddress = "mallory@example.com\n\tpassword = easy\n\trole = \"#admin\"\n[user \"other\"]";
			service.updateUserModel(newUser);



			// confirm test user still cannot admin
			newUser = service.getUserModel("mallory");
			assertFalse(newUser.canAdmin);
			assertEquals("password", newUser.password);

			assertEquals(2, service.getAllUsernames().size());

		}
		finally {
			file.delete();
		}
	}


	@Test
	public void testConfigUserServiceDisplayNameExploit() throws IOException
	{
		File file = new File("us-test.conf");
		file.delete();
		IUserService service = new ConfigUserService(file);

		try {
			UserModel admin = service.getUserModel("admin");
			assertTrue(admin == null);

			// add admin
			admin = new UserModel("admin");
			admin.password = "secret";
			admin.canAdmin = true;
			admin.excludeFromFederation = true;

			service.updateUserModel(admin);
			admin = null;

			// add new user
			UserModel newUser = new UserModel("mallory");
			newUser.password = "password";
			newUser.emailAddress = "mallory@example.com";
			newUser.addRepositoryPermission("repo1");
			service.updateUserModel(newUser);

			// confirm all added users
			assertEquals(2, service.getAllUsernames().size());
			assertTrue(service.getUserModel("admin") != null);
			assertTrue(service.getUserModel("mallory") != null);

			// confirm reloaded test user
			newUser = service.getUserModel("mallory");
			assertEquals("password", newUser.password);
			assertEquals(1, newUser.permissions.size());
			assertTrue(newUser.hasRepositoryPermission("repo1"));
			assertFalse(newUser.canAdmin);


			// Change display name trying to sneak in more permissions
			newUser = service.getUserModel("mallory");
			newUser.displayName = "Attacker\n\tpassword = easy\n\trepository = RW+:repo1\n\trepository = RW+:repo2\n[user \"noone\"]";
			service.updateUserModel(newUser);


			// confirm test user still has same rights
			newUser = service.getUserModel("mallory");
			assertEquals("password", newUser.password);
			assertEquals(1, newUser.permissions.size());
			assertTrue(newUser.hasRepositoryPermission("repo1"));
			assertFalse(newUser.canAdmin);

			assertEquals(2, service.getAllUsernames().size());
		}
		finally {
			file.delete();
		}
	}


}

