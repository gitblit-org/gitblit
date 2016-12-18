/*
 * Copyright 2012 John Crygier
 * Copyright 2012 gitblit.com
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

import static org.junit.Assume.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import com.gitblit.Constants.AccountType;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.auth.LdapAuthProvider;
import com.gitblit.manager.AuthenticationManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.manager.RuntimeManager;
import com.gitblit.manager.UserManager;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.tests.mock.MemorySettings;
import com.gitblit.utils.XssFilter;
import com.gitblit.utils.XssFilter.AllowXssFilter;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldif.LDIFReader;

/**
 * An Integration test for LDAP that tests going against an in-memory UnboundID
 * LDAP server.
 *
 * @author jcrygier
 *
 */
@RunWith(Parameterized.class)
public class LdapAuthenticationTest extends LdapBasedUnitTest {

	private LdapAuthProvider ldap;

	private IUserManager userManager;

	private AuthenticationManager auth;


	@Before
	public void setup() throws Exception {
		ldap = newLdapAuthentication(settings);
		auth = newAuthenticationManager(settings);
	}

	private LdapAuthProvider newLdapAuthentication(IStoredSettings settings) {
		XssFilter xssFilter = new AllowXssFilter();
		RuntimeManager runtime = new RuntimeManager(settings, xssFilter, GitBlitSuite.BASEFOLDER).start();
		userManager = new UserManager(runtime, null).start();
		LdapAuthProvider ldap = new LdapAuthProvider();
		ldap.setup(runtime, userManager);
		return ldap;
	}

	private AuthenticationManager newAuthenticationManager(IStoredSettings settings) {
		XssFilter xssFilter = new AllowXssFilter();
		RuntimeManager runtime = new RuntimeManager(settings, xssFilter, GitBlitSuite.BASEFOLDER).start();
		AuthenticationManager auth = new AuthenticationManager(runtime, userManager);
		auth.addAuthenticationProvider(newLdapAuthentication(settings));
		return auth;
	}

	@Test
	public void testAuthenticate() {
		UserModel userOneModel = ldap.authenticate("UserOne", "userOnePassword".toCharArray());
		assertNotNull(userOneModel);
		assertNotNull(userOneModel.getTeam("git_admins"));
		assertNotNull(userOneModel.getTeam("git_users"));

		UserModel userOneModelFailedAuth = ldap.authenticate("UserOne", "userTwoPassword".toCharArray());
		assertNull(userOneModelFailedAuth);

		UserModel userTwoModel = ldap.authenticate("UserTwo", "userTwoPassword".toCharArray());
		assertNotNull(userTwoModel);
		assertNotNull(userTwoModel.getTeam("git_users"));
		assertNull(userTwoModel.getTeam("git_admins"));
		assertNotNull(userTwoModel.getTeam("git admins"));

		UserModel userThreeModel = ldap.authenticate("UserThree", "userThreePassword".toCharArray());
		assertNotNull(userThreeModel);
		assertNotNull(userThreeModel.getTeam("git_users"));
		assertNull(userThreeModel.getTeam("git_admins"));

		UserModel userFourModel = ldap.authenticate("UserFour", "userFourPassword".toCharArray());
		assertNotNull(userFourModel);
		assertNotNull(userFourModel.getTeam("git_users"));
		assertNull(userFourModel.getTeam("git_admins"));
		assertNull(userFourModel.getTeam("git admins"));
	}

	@Test
	public void testAdminPropertyTeamsInLdap() {
		UserModel userOneModel = ldap.authenticate("UserOne", "userOnePassword".toCharArray());
		assertNotNull(userOneModel);
		assertNotNull(userOneModel.getTeam("git_admins"));
		assertNull(userOneModel.getTeam("git admins"));
		assertNotNull(userOneModel.getTeam("git_users"));
		assertFalse(userOneModel.canAdmin);
		assertTrue(userOneModel.canAdmin());
		assertTrue(userOneModel.getTeam("git_admins").canAdmin);
		assertFalse(userOneModel.getTeam("git_users").canAdmin);

		UserModel userTwoModel = ldap.authenticate("UserTwo", "userTwoPassword".toCharArray());
		assertNotNull(userTwoModel);
		assertNotNull(userTwoModel.getTeam("git_users"));
		assertNull(userTwoModel.getTeam("git_admins"));
		assertNotNull(userTwoModel.getTeam("git admins"));
		assertFalse(userTwoModel.canAdmin);
		assertTrue(userTwoModel.canAdmin());
		assertTrue(userTwoModel.getTeam("git admins").canAdmin);
		assertFalse(userTwoModel.getTeam("git_users").canAdmin);

		UserModel userThreeModel = ldap.authenticate("UserThree", "userThreePassword".toCharArray());
		assertNotNull(userThreeModel);
		assertNotNull(userThreeModel.getTeam("git_users"));
		assertNull(userThreeModel.getTeam("git_admins"));
		assertNull(userThreeModel.getTeam("git admins"));
		assertTrue(userThreeModel.canAdmin);
		assertTrue(userThreeModel.canAdmin());
		assertFalse(userThreeModel.getTeam("git_users").canAdmin);

		UserModel userFourModel = ldap.authenticate("UserFour", "userFourPassword".toCharArray());
		assertNotNull(userFourModel);
		assertNotNull(userFourModel.getTeam("git_users"));
		assertNull(userFourModel.getTeam("git_admins"));
		assertNull(userFourModel.getTeam("git admins"));
		assertFalse(userFourModel.canAdmin);
		assertFalse(userFourModel.canAdmin());
		assertFalse(userFourModel.getTeam("git_users").canAdmin);
	}

	@Test
	public void testAdminPropertyTeamsNotInLdap() {
		settings.put(Keys.realm.ldap.maintainTeams, "false");

		UserModel userOneModel = ldap.authenticate("UserOne", "userOnePassword".toCharArray());
		assertNotNull(userOneModel);
		assertNotNull(userOneModel.getTeam("git_admins"));
		assertNull(userOneModel.getTeam("git admins"));
		assertNotNull(userOneModel.getTeam("git_users"));
		assertTrue(userOneModel.canAdmin);
		assertTrue(userOneModel.canAdmin());
		assertFalse(userOneModel.getTeam("git_admins").canAdmin);
		assertFalse(userOneModel.getTeam("git_users").canAdmin);

		UserModel userTwoModel = ldap.authenticate("UserTwo", "userTwoPassword".toCharArray());
		assertNotNull(userTwoModel);
		assertNotNull(userTwoModel.getTeam("git_users"));
		assertNull(userTwoModel.getTeam("git_admins"));
		assertNotNull(userTwoModel.getTeam("git admins"));
		assertFalse(userTwoModel.canAdmin);
		assertTrue(userTwoModel.canAdmin());
		assertTrue(userTwoModel.getTeam("git admins").canAdmin);
		assertFalse(userTwoModel.getTeam("git_users").canAdmin);

		UserModel userThreeModel = ldap.authenticate("UserThree", "userThreePassword".toCharArray());
		assertNotNull(userThreeModel);
		assertNotNull(userThreeModel.getTeam("git_users"));
		assertNull(userThreeModel.getTeam("git_admins"));
		assertNull(userThreeModel.getTeam("git admins"));
		assertFalse(userThreeModel.canAdmin);
		assertFalse(userThreeModel.canAdmin());
		assertFalse(userThreeModel.getTeam("git_users").canAdmin);

		UserModel userFourModel = ldap.authenticate("UserFour", "userFourPassword".toCharArray());
		assertNotNull(userFourModel);
		assertNotNull(userFourModel.getTeam("git_users"));
		assertNull(userFourModel.getTeam("git_admins"));
		assertNull(userFourModel.getTeam("git admins"));
		assertFalse(userFourModel.canAdmin);
		assertFalse(userFourModel.canAdmin());
		assertFalse(userFourModel.getTeam("git_users").canAdmin);
	}

	@Test
	public void testDisplayName() {
		UserModel userOneModel = ldap.authenticate("UserOne", "userOnePassword".toCharArray());
		assertNotNull(userOneModel);
		assertEquals("User One", userOneModel.displayName);

		// Test more complicated scenarios - concat
		MemorySettings ms = getSettings();
		ms.put("realm.ldap.displayName", "${personalTitle}. ${givenName} ${surname}");
		ldap = newLdapAuthentication(ms);

		userOneModel = ldap.authenticate("UserOne", "userOnePassword".toCharArray());
		assertNotNull(userOneModel);
		assertEquals("Mr. User One", userOneModel.displayName);
	}

	@Test
	public void testEmail() {
		UserModel userOneModel = ldap.authenticate("UserOne", "userOnePassword".toCharArray());
		assertNotNull(userOneModel);
		assertEquals("userone@gitblit.com", userOneModel.emailAddress);

		// Test more complicated scenarios - concat
		MemorySettings ms = getSettings();
		ms.put("realm.ldap.email", "${givenName}.${surname}@gitblit.com");
		ldap = newLdapAuthentication(ms);

		userOneModel = ldap.authenticate("UserOne", "userOnePassword".toCharArray());
		assertNotNull(userOneModel);
		assertEquals("User.One@gitblit.com", userOneModel.emailAddress);
	}

	@Test
	public void testLdapInjection() {
		// Inject so "(&(objectClass=person)(sAMAccountName=${username}))" becomes "(&(objectClass=person)(sAMAccountName=*)(userPassword=userOnePassword))"
		// Thus searching by password

		UserModel userOneModel = ldap.authenticate("*)(userPassword=userOnePassword", "userOnePassword".toCharArray());
		assertNull(userOneModel);
	}

	@Test
	public void checkIfUsersConfContainsAllUsersFromSampleDataLdif() throws Exception {
		SearchResult searchResult = getDS().search(ACCOUNT_BASE, SearchScope.SUB, "objectClass=person");
		assertEquals("Number of ldap users in gitblit user model", searchResult.getEntryCount(), countLdapUsersInUserManager());
	}

	@Test
	public void addingUserInLdapShouldNotUpdateGitBlitUsersAndGroups() throws Exception {
		getDS().addEntries(LDIFReader.readEntries(RESOURCE_DIR + "adduser.ldif"));
		ldap.sync();
		assertEquals("Number of ldap users in gitblit user model", 5, countLdapUsersInUserManager());
	}

	@Test
	public void addingUserInLdapShouldUpdateGitBlitUsersAndGroups() throws Exception {
		settings.put(Keys.realm.ldap.synchronize, "true");
		getDS().addEntries(LDIFReader.readEntries(RESOURCE_DIR + "adduser.ldif"));
		ldap.sync();
		assertEquals("Number of ldap users in gitblit user model", 6, countLdapUsersInUserManager());
	}

	@Test
	public void addingGroupsInLdapShouldNotUpdateGitBlitUsersAndGroups() throws Exception {
		getDS().addEntries(LDIFReader.readEntries(RESOURCE_DIR + "addgroup.ldif"));
		ldap.sync();
		assertEquals("Number of ldap groups in gitblit team model", 0, countLdapTeamsInUserManager());
	}

	@Test
	public void addingGroupsInLdapShouldUpdateGitBlitUsersNotGroups2() throws Exception {
		settings.put(Keys.realm.ldap.synchronize, "true");
		settings.put(Keys.realm.ldap.maintainTeams, "false");
		getDS().addEntries(LDIFReader.readEntries(RESOURCE_DIR + "adduser.ldif"));
		getDS().addEntries(LDIFReader.readEntries(RESOURCE_DIR + "addgroup.ldif"));
		ldap.sync();
		assertEquals("Number of ldap users in gitblit user model", 6, countLdapUsersInUserManager());
		assertEquals("Number of ldap groups in gitblit team model", 0, countLdapTeamsInUserManager());
	}

	@Test
	public void addingGroupsInLdapShouldUpdateGitBlitUsersAndGroups() throws Exception {
		// This test only makes sense if the authentication mode allows for synchronization.
		assumeTrue(authMode == AuthMode.ANONYMOUS || authMode == AuthMode.DS_MANAGER);

		settings.put(Keys.realm.ldap.synchronize, "true");
		getDS().addEntries(LDIFReader.readEntries(RESOURCE_DIR + "addgroup.ldif"));
		ldap.sync();
		assertEquals("Number of ldap groups in gitblit team model", 1, countLdapTeamsInUserManager());
	}

	@Test
	public void syncUpdateUsersAndGroupsAdminProperty() throws Exception {
		// This test only makes sense if the authentication mode allows for synchronization.
		assumeTrue(authMode == AuthMode.ANONYMOUS || authMode == AuthMode.DS_MANAGER);

		settings.put(Keys.realm.ldap.synchronize, "true");
		ldap.sync();

		UserModel user = userManager.getUserModel("UserOne");
		assertNotNull(user);
		assertFalse(user.canAdmin);
		assertTrue(user.canAdmin());

		user = userManager.getUserModel("UserTwo");
		assertNotNull(user);
		assertFalse(user.canAdmin);
		assertTrue(user.canAdmin());

		user = userManager.getUserModel("UserThree");
		assertNotNull(user);
		assertTrue(user.canAdmin);
		assertTrue(user.canAdmin());

		user = userManager.getUserModel("UserFour");
		assertNotNull(user);
		assertFalse(user.canAdmin);
		assertFalse(user.canAdmin());

		TeamModel team = userManager.getTeamModel("Git_Admins");
		assertNotNull(team);
		assertTrue(team.canAdmin);

		team = userManager.getTeamModel("Git Admins");
		assertNotNull(team);
		assertTrue(team.canAdmin);

		team = userManager.getTeamModel("Git_Users");
		assertNotNull(team);
		assertFalse(team.canAdmin);
	}

	@Test
	public void syncNotUpdateUsersAndGroupsAdminProperty() throws Exception {
		settings.put(Keys.realm.ldap.synchronize, "true");
		settings.put(Keys.realm.ldap.maintainTeams, "false");
		ldap.sync();

		UserModel user = userManager.getUserModel("UserOne");
		assertNotNull(user);
		assertTrue(user.canAdmin);
		assertTrue(user.canAdmin());

		user = userManager.getUserModel("UserTwo");
		assertNotNull(user);
		assertFalse(user.canAdmin);
		assertTrue(user.canAdmin());

		user = userManager.getUserModel("UserThree");
		assertNotNull(user);
		assertFalse(user.canAdmin);
		assertFalse(user.canAdmin());

		user = userManager.getUserModel("UserFour");
		assertNotNull(user);
		assertFalse(user.canAdmin);
		assertFalse(user.canAdmin());

		TeamModel team = userManager.getTeamModel("Git_Admins");
		assertNotNull(team);
		assertFalse(team.canAdmin);

		team = userManager.getTeamModel("Git Admins");
		assertNotNull(team);
		assertTrue(team.canAdmin);

		team = userManager.getTeamModel("Git_Users");
		assertNotNull(team);
		assertFalse(team.canAdmin);
	}

	@Test
	public void testAuthenticationManager() {
		UserModel userOneModel = auth.authenticate("UserOne", "userOnePassword".toCharArray(), null);
		assertNotNull(userOneModel);
		assertNotNull(userOneModel.getTeam("git_admins"));
		assertNotNull(userOneModel.getTeam("git_users"));

		UserModel userOneModelFailedAuth = auth.authenticate("UserOne", "userTwoPassword".toCharArray(), null);
		assertNull(userOneModelFailedAuth);

		UserModel userTwoModel = auth.authenticate("UserTwo", "userTwoPassword".toCharArray(), null);
		assertNotNull(userTwoModel);
		assertNotNull(userTwoModel.getTeam("git_users"));
		assertNull(userTwoModel.getTeam("git_admins"));
		assertNotNull(userTwoModel.getTeam("git admins"));

		UserModel userThreeModel = auth.authenticate("UserThree", "userThreePassword".toCharArray(), null);
		assertNotNull(userThreeModel);
		assertNotNull(userThreeModel.getTeam("git_users"));
		assertNull(userThreeModel.getTeam("git_admins"));

		UserModel userFourModel = auth.authenticate("UserFour", "userFourPassword".toCharArray(), null);
		assertNotNull(userFourModel);
		assertNotNull(userFourModel.getTeam("git_users"));
		assertNull(userFourModel.getTeam("git_admins"));
		assertNull(userFourModel.getTeam("git admins"));
	}

	@Test
	public void testAuthenticationManagerAdminPropertyTeamsInLdap() {
		UserModel userOneModel = auth.authenticate("UserOne", "userOnePassword".toCharArray(), null);
		assertNotNull(userOneModel);
		assertNotNull(userOneModel.getTeam("git_admins"));
		assertNull(userOneModel.getTeam("git admins"));
		assertNotNull(userOneModel.getTeam("git_users"));
		assertFalse(userOneModel.canAdmin);
		assertTrue(userOneModel.canAdmin());
		assertTrue(userOneModel.getTeam("git_admins").canAdmin);
		assertFalse(userOneModel.getTeam("git_users").canAdmin);

		UserModel userOneModelFailedAuth = auth.authenticate("UserOne", "userTwoPassword".toCharArray(), null);
		assertNull(userOneModelFailedAuth);

		UserModel userTwoModel = auth.authenticate("UserTwo", "userTwoPassword".toCharArray(), null);
		assertNotNull(userTwoModel);
		assertNotNull(userTwoModel.getTeam("git_users"));
		assertNull(userTwoModel.getTeam("git_admins"));
		assertNotNull(userTwoModel.getTeam("git admins"));
		assertFalse(userTwoModel.canAdmin);
		assertTrue(userTwoModel.canAdmin());
		assertTrue(userTwoModel.getTeam("git admins").canAdmin);
		assertFalse(userTwoModel.getTeam("git_users").canAdmin);

		UserModel userThreeModel = auth.authenticate("UserThree", "userThreePassword".toCharArray(), null);
		assertNotNull(userThreeModel);
		assertNotNull(userThreeModel.getTeam("git_users"));
		assertNull(userThreeModel.getTeam("git_admins"));
		assertNull(userThreeModel.getTeam("git admins"));
		assertTrue(userThreeModel.canAdmin);
		assertTrue(userThreeModel.canAdmin());
		assertFalse(userThreeModel.getTeam("git_users").canAdmin);

		UserModel userFourModel = auth.authenticate("UserFour", "userFourPassword".toCharArray(), null);
		assertNotNull(userFourModel);
		assertNotNull(userFourModel.getTeam("git_users"));
		assertNull(userFourModel.getTeam("git_admins"));
		assertNull(userFourModel.getTeam("git admins"));
		assertFalse(userFourModel.canAdmin);
		assertFalse(userFourModel.canAdmin());
		assertFalse(userFourModel.getTeam("git_users").canAdmin);
	}

	@Test
	public void testAuthenticationManagerAdminPropertyTeamsNotInLdap() {
		settings.put(Keys.realm.ldap.maintainTeams, "false");

		UserModel userOneModel = auth.authenticate("UserOne", "userOnePassword".toCharArray(), null);
		assertNotNull(userOneModel);
		assertNotNull(userOneModel.getTeam("git_admins"));
		assertNull(userOneModel.getTeam("git admins"));
		assertNotNull(userOneModel.getTeam("git_users"));
		assertTrue(userOneModel.canAdmin);
		assertTrue(userOneModel.canAdmin());
		assertFalse(userOneModel.getTeam("git_admins").canAdmin);
		assertFalse(userOneModel.getTeam("git_users").canAdmin);

		UserModel userOneModelFailedAuth = auth.authenticate("UserOne", "userTwoPassword".toCharArray(), null);
		assertNull(userOneModelFailedAuth);

		UserModel userTwoModel = auth.authenticate("UserTwo", "userTwoPassword".toCharArray(), null);
		assertNotNull(userTwoModel);
		assertNotNull(userTwoModel.getTeam("git_users"));
		assertNull(userTwoModel.getTeam("git_admins"));
		assertNotNull(userTwoModel.getTeam("git admins"));
		assertFalse(userTwoModel.canAdmin);
		assertTrue(userTwoModel.canAdmin());
		assertTrue(userTwoModel.getTeam("git admins").canAdmin);
		assertFalse(userTwoModel.getTeam("git_users").canAdmin);

		UserModel userThreeModel = auth.authenticate("UserThree", "userThreePassword".toCharArray(), null);
		assertNotNull(userThreeModel);
		assertNotNull(userThreeModel.getTeam("git_users"));
		assertNull(userThreeModel.getTeam("git_admins"));
		assertNull(userThreeModel.getTeam("git admins"));
		assertFalse(userThreeModel.canAdmin);
		assertFalse(userThreeModel.canAdmin());
		assertFalse(userThreeModel.getTeam("git_users").canAdmin);

		UserModel userFourModel = auth.authenticate("UserFour", "userFourPassword".toCharArray(), null);
		assertNotNull(userFourModel);
		assertNotNull(userFourModel.getTeam("git_users"));
		assertNull(userFourModel.getTeam("git_admins"));
		assertNull(userFourModel.getTeam("git admins"));
		assertFalse(userFourModel.canAdmin);
		assertFalse(userFourModel.canAdmin());
		assertFalse(userFourModel.getTeam("git_users").canAdmin);
	}

	@Test
	public void testBindWithUser() {
		// This test only makes sense if the user is not prevented from reading users and teams.
		assumeTrue(authMode != AuthMode.DS_MANAGER);

		settings.put(Keys.realm.ldap.bindpattern, "CN=${username},OU=US," + ACCOUNT_BASE);
		settings.put(Keys.realm.ldap.username, "");
		settings.put(Keys.realm.ldap.password, "");

		UserModel userOneModel = auth.authenticate("UserOne", "userOnePassword".toCharArray(), null);
		assertNotNull(userOneModel);

		UserModel userOneModelFailedAuth = auth.authenticate("UserOne", "userTwoPassword".toCharArray(), null);
		assertNull(userOneModelFailedAuth);
	}









	private int countLdapUsersInUserManager() {
		int ldapAccountCount = 0;
		for (UserModel userModel : userManager.getAllUsers()) {
			if (AccountType.LDAP.equals(userModel.accountType)) {
				ldapAccountCount++;
			}
		}
		return ldapAccountCount;
	}

	private int countLdapTeamsInUserManager() {
		int ldapAccountCount = 0;
		for (TeamModel teamModel : userManager.getAllTeams()) {
			if (AccountType.LDAP.equals(teamModel.accountType)) {
				ldapAccountCount++;
			}
		}
		return ldapAccountCount;
	}

}
