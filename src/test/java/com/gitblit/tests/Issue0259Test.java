/*
 * Copyright 2013 gitblit.com.
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

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;

import com.gitblit.ConfigUserService;
import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;

/**
 * https://code.google.com/p/gitblit/issues/detail?id=259
 *
 * Reported Problem:
 * We have an user with RWD access rights, but he can’t push.
 *
 * @see src/test/resources/issue0259.conf
 *
 * At the next day he try again and he can push to the project.
 *
 * @author James Moger
 *
 */
public class Issue0259Test {

	RepositoryModel repo(String name, AccessRestrictionType restriction) {
		RepositoryModel repo = new RepositoryModel();
		repo.name = name;
		repo.accessRestriction = restriction;
		return repo;
	}

	/**
	 * Test the provided users.conf file for expected access permissions.
	 *
	 * @throws Exception
	 */
	@Test
	public void testFile() throws Exception {
		File realmFile = new File("src/test/resources/issue0259.conf");
		ConfigUserService service = new ConfigUserService(realmFile);

		RepositoryModel test = repo("test.git", AccessRestrictionType.VIEW);
		RepositoryModel projects_test = repo("projects/test.git", AccessRestrictionType.VIEW);

		UserModel a = service.getUserModel("a");
		UserModel b = service.getUserModel("b");
		UserModel c = service.getUserModel("c");

		// assert RWD or RW+ for projects/test.git
		assertEquals(AccessPermission.DELETE, a.getRepositoryPermission(projects_test).permission);
		assertEquals(AccessPermission.DELETE, b.getRepositoryPermission(projects_test).permission);
		assertEquals(AccessPermission.REWIND, c.getRepositoryPermission(projects_test).permission);

		assertTrue(a.canPush(projects_test));
		assertTrue(b.canPush(projects_test));
		assertTrue(c.canPush(projects_test));

		assertTrue(a.canDeleteRef(projects_test));
		assertTrue(b.canDeleteRef(projects_test));
		assertTrue(c.canDeleteRef(projects_test));

		assertFalse(a.canRewindRef(projects_test));
		assertFalse(b.canRewindRef(projects_test));
		assertTrue(c.canRewindRef(projects_test));

		// assert R for test.git
		assertEquals(AccessPermission.CLONE, a.getRepositoryPermission(test).permission);
		assertEquals(AccessPermission.CLONE, b.getRepositoryPermission(test).permission);
		assertEquals(AccessPermission.REWIND, c.getRepositoryPermission(test).permission);

		assertTrue(a.canClone(test));
		assertTrue(b.canClone(test));

		assertFalse(a.canPush(test));
		assertFalse(b.canPush(test));
		assertTrue(c.canPush(test));
	}

	@Test
	public void testTeamsOrder() throws Exception {
		testTeams(false);
	}

	@Test
	public void testTeamsReverseOrder() throws Exception {
		testTeams(true);
	}

	/**
	 * Tests multiple teams each with a regex permisson that will match.  The
	 * highest matching permission should be used.  Order should be irrelevant.
	 *
	 * @param reverseOrder
	 * @throws Exception
	 */
	private void testTeams(boolean reverseOrder) throws Exception {
		RepositoryModel test = repo("test.git", AccessRestrictionType.VIEW);
		RepositoryModel projects_test = repo("projects/test.git", AccessRestrictionType.VIEW);

		TeamModel t1 = new TeamModel("t1");
		t1.setRepositoryPermission(".*", AccessPermission.CLONE);

		TeamModel t2 = new TeamModel("t2");
		t2.setRepositoryPermission("projects/.*", AccessPermission.DELETE);

		UserModel a = new UserModel("a");
		if (reverseOrder) {
			a.teams.add(t2);
			a.teams.add(t1);
		} else {
			a.teams.add(t1);
			a.teams.add(t2);
		}

		// simulate a repository rename
		a.setRepositoryPermission("projects/renamed.git", null);
		t1.setRepositoryPermission("projects/renamed.git", null);
		t2.setRepositoryPermission("projects/renamed.git", null);

		assertEquals(AccessPermission.CLONE, a.getRepositoryPermission(test).permission);
		assertEquals(AccessPermission.DELETE, a.getRepositoryPermission(projects_test).permission);

		assertTrue(a.canClone(test));
		assertTrue(a.canClone(projects_test));

		assertFalse(a.canDeleteRef(test));
		assertTrue(a.canDeleteRef(projects_test));
	}

	@Test
	public void testTeam() throws Exception {
		testTeam(false);
	}

	@Test
	public void testTeamReverseOrder() throws Exception {
		testTeam(true);
	}

	/**
	 * Test a single team that has multiple repository permissions that all match.
	 * Here defined order IS important.  The first permission match wins so it is
	 * important to define permissions from most-specific match to least-specific
	 * match.
	 *
	 * If the defined permissions are:
	 *   R:.*
	 *   RWD:projects/.*
	 * then the expected result is R for all repositories because it is first.
	 *
	 * But if the defined permissions are:
	 *   RWD:projects/.*
	 *   R:.*
	 * then the expected result is RWD for projects/test.git and R for test.git
	 *
	 * @param reverseOrder
	 * @throws Exception
	 */
	private void testTeam(boolean reverseOrder) throws Exception {
		RepositoryModel test = repo("test.git", AccessRestrictionType.VIEW);
		RepositoryModel projects_test = repo("projects/test.git", AccessRestrictionType.VIEW);

		TeamModel t1 = new TeamModel("t1");
		if (reverseOrder) {
			t1.setRepositoryPermission("projects/.*", AccessPermission.DELETE);
			t1.setRepositoryPermission(".*", AccessPermission.CLONE);
		} else {
			t1.setRepositoryPermission(".*", AccessPermission.CLONE);
			t1.setRepositoryPermission("projects/.*", AccessPermission.DELETE);
		}
		UserModel a = new UserModel("a");
		a.teams.add(t1);

		// simulate a repository rename
		a.setRepositoryPermission("projects/renamed.git", null);
		t1.setRepositoryPermission("projects/renamed.git", null);

		assertEquals(AccessPermission.CLONE, a.getRepositoryPermission(test).permission);
		assertTrue(a.canClone(test));
		assertFalse(a.canDeleteRef(test));
		assertTrue(a.canClone(projects_test));

		if (reverseOrder) {
			// RWD permission is found first
			assertEquals(AccessPermission.DELETE, a.getRepositoryPermission(projects_test).permission);
			assertTrue(a.canDeleteRef(projects_test));
		} else {
			// R permission is found first
			assertEquals(AccessPermission.CLONE, a.getRepositoryPermission(projects_test).permission);
			assertFalse(a.canDeleteRef(projects_test));
		}
	}
}
