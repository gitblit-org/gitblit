/*
 * Copyright 2014 gitblit.com.
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

import com.gitblit.Constants;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.TeamModel;
import org.junit.Test;

import com.gitblit.models.UserModel;

import java.util.List;

/**
 * @author Alfred Schmid
 *
 */
public class UserModelTest extends GitblitUnitTest {

	@Test
	public void whenDisplayNameIsEmptyUsernameIsUsed() {
		String username = "test";
		UserModel userModel = new UserModel(username);
		userModel.displayName = "";
		assertEquals("When displayName is empty the username has to be returnd from getDisplayName().", username, userModel.getDisplayName());
	}

	@Test
	public void whenDisplayNameIsNullUsernameIsUsed() {
		String username = "test";
		UserModel userModel = new UserModel(username);
		userModel.displayName = null;
		assertEquals("When displayName is null the username has to be returnd from getDisplayName().", username, userModel.getDisplayName());
	}

	@Test
	public void whenDisplayNameIsNotEmptyDisplayNameIsUsed() {
		String displayName = "Test User";
		UserModel userModel = new UserModel("test");
		userModel.displayName = displayName;
		assertEquals("When displayName is not empty its value has to be returnd from getDisplayName().", displayName, userModel.getDisplayName());
	}


	@Test
	public void getRepositoryPermissionsMultipleTeams()
	{

		TeamModel aTeam = new TeamModel("A team");
		aTeam.addRepositoryPermission("RW+:acerepo.git");
		aTeam.addRepositoryPermission("V:boobrepo.git");

		TeamModel bTeam = new TeamModel("Team B");
		bTeam.addRepositoryPermission("R:acerepo.git");
		bTeam.addRepositoryPermission("RWC:boobrepo.git");

		UserModel user = new UserModel("tessiur");
		user.teams.add(aTeam);
		user.teams.add(bTeam);
		user.addRepositoryPermission("RW+:myrepo.git");

		List<RegistrantAccessPermission> repoPerms = user.getRepositoryPermissions();
		int found = 0;
		for (RegistrantAccessPermission p : repoPerms) {
			switch (p.registrant) {
				case "acerepo.git":
					assertEquals("Expected REWIND(RW+) permission for " + p.registrant, Constants.AccessPermission.REWIND, p.permission);
					found++;
					break;
				case "boobrepo.git":
					assertEquals("Expected CREATE(RWC) permission for " + p.registrant, Constants.AccessPermission.CREATE, p.permission);
					found++;
					break;
				case "myrepo.git":
					assertEquals("Expected REWIND(RW+) permission for " + p.registrant, Constants.AccessPermission.REWIND, p.permission);
					found++;
					break;
				default:
					fail("Unknown repository registrant " + p.registrant);
					break;
			}
		}

		assertEquals("Repostory permissions missing in list.", 3, found);

	}

}
