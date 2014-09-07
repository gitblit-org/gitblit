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

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

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
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
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
public class LdapAuthenticationTest extends GitblitUnitTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

	private static final String RESOURCE_DIR = "src/test/resources/ldap/";

    private File usersConf;

    private LdapAuthProvider ldap;

	static int ldapPort = 1389;

	private static InMemoryDirectoryServer ds;

	private IUserManager userManager;

	private AuthenticationManager auth;

	private MemorySettings settings;

	@BeforeClass
	public static void createInMemoryLdapServer() throws Exception {
		InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=MyDomain");
		config.addAdditionalBindCredentials("cn=Directory Manager", "password");
		config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("default", ldapPort));
		config.setSchema(null);

		ds = new InMemoryDirectoryServer(config);
		ds.startListening();
	}

	@Before
	public void init() throws Exception {
		ds.clear();
		ds.importFromLDIF(true, new LDIFReader(new FileInputStream(RESOURCE_DIR + "sampledata.ldif")));
		usersConf = folder.newFile("users.conf");
		FileUtils.copyFile(new File(RESOURCE_DIR + "users.conf"), usersConf);
		settings = getSettings();
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

	private MemorySettings getSettings() {
		Map<String, Object> backingMap = new HashMap<String, Object>();
		backingMap.put(Keys.realm.userService, usersConf.getAbsolutePath());
		backingMap.put(Keys.realm.ldap.server, "ldap://localhost:" + ldapPort);
//		backingMap.put(Keys.realm.ldap.domain, "");
		backingMap.put(Keys.realm.ldap.username, "cn=Directory Manager");
		backingMap.put(Keys.realm.ldap.password, "password");
//		backingMap.put(Keys.realm.ldap.backingUserService, "users.conf");
		backingMap.put(Keys.realm.ldap.maintainTeams, "true");
		backingMap.put(Keys.realm.ldap.accountBase, "OU=Users,OU=UserControl,OU=MyOrganization,DC=MyDomain");
		backingMap.put(Keys.realm.ldap.accountPattern, "(&(objectClass=person)(sAMAccountName=${username}))");
		backingMap.put(Keys.realm.ldap.groupBase, "OU=Groups,OU=UserControl,OU=MyOrganization,DC=MyDomain");
		backingMap.put(Keys.realm.ldap.groupMemberPattern, "(&(objectClass=group)(member=${dn}))");
		backingMap.put(Keys.realm.ldap.admins, "UserThree @Git_Admins \"@Git Admins\"");
		backingMap.put(Keys.realm.ldap.displayName, "displayName");
		backingMap.put(Keys.realm.ldap.email, "email");
		backingMap.put(Keys.realm.ldap.uid, "sAMAccountName");

		MemorySettings ms = new MemorySettings(backingMap);
		return ms;
	}

	@Test
	public void testAuthenticate() {
		UserModel userOneModel = ldap.authenticate("UserOne", "userOnePassword".toCharArray());
		assertNotNull(userOneModel);
		assertNotNull(userOneModel.getTeam("git_admins"));
		assertNotNull(userOneModel.getTeam("git_users"));
		assertTrue(userOneModel.canAdmin);

		UserModel userOneModelFailedAuth = ldap.authenticate("UserOne", "userTwoPassword".toCharArray());
		assertNull(userOneModelFailedAuth);

		UserModel userTwoModel = ldap.authenticate("UserTwo", "userTwoPassword".toCharArray());
		assertNotNull(userTwoModel);
		assertNotNull(userTwoModel.getTeam("git_users"));
		assertNull(userTwoModel.getTeam("git_admins"));
		assertNotNull(userTwoModel.getTeam("git admins"));
		assertTrue(userTwoModel.canAdmin);

		UserModel userThreeModel = ldap.authenticate("UserThree", "userThreePassword".toCharArray());
		assertNotNull(userThreeModel);
		assertNotNull(userThreeModel.getTeam("git_users"));
		assertNull(userThreeModel.getTeam("git_admins"));
		assertTrue(userThreeModel.canAdmin);
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
		SearchResult searchResult = ds.search("OU=Users,OU=UserControl,OU=MyOrganization,DC=MyDomain", SearchScope.SUB, "objectClass=person");
		assertEquals("Number of ldap users in gitblit user model", searchResult.getEntryCount(), countLdapUsersInUserManager());
	}

	@Test
	public void addingUserInLdapShouldNotUpdateGitBlitUsersAndGroups() throws Exception {
		ds.addEntries(LDIFReader.readEntries(RESOURCE_DIR + "adduser.ldif"));
		ldap.sync();
		assertEquals("Number of ldap users in gitblit user model", 5, countLdapUsersInUserManager());
	}

	@Test
	public void addingUserInLdapShouldUpdateGitBlitUsersAndGroups() throws Exception {
		settings.put(Keys.realm.ldap.synchronize, "true");
		ds.addEntries(LDIFReader.readEntries(RESOURCE_DIR + "adduser.ldif"));
		ldap.sync();
		assertEquals("Number of ldap users in gitblit user model", 6, countLdapUsersInUserManager());
	}

	@Test
	public void addingGroupsInLdapShouldNotUpdateGitBlitUsersAndGroups() throws Exception {
		ds.addEntries(LDIFReader.readEntries(RESOURCE_DIR + "addgroup.ldif"));
		ldap.sync();
		assertEquals("Number of ldap groups in gitblit team model", 0, countLdapTeamsInUserManager());
	}

	@Test
	public void addingGroupsInLdapShouldUpdateGitBlitUsersAndGroups() throws Exception {
		settings.put(Keys.realm.ldap.synchronize, "true");
		ds.addEntries(LDIFReader.readEntries(RESOURCE_DIR + "addgroup.ldif"));
		ldap.sync();
		assertEquals("Number of ldap groups in gitblit team model", 1, countLdapTeamsInUserManager());
	}

	@Test
	public void testAuthenticationManager() {
		UserModel userOneModel = auth.authenticate("UserOne", "userOnePassword".toCharArray());
		assertNotNull(userOneModel);
		assertNotNull(userOneModel.getTeam("git_admins"));
		assertNotNull(userOneModel.getTeam("git_users"));
		assertTrue(userOneModel.canAdmin);

		UserModel userOneModelFailedAuth = auth.authenticate("UserOne", "userTwoPassword".toCharArray());
		assertNull(userOneModelFailedAuth);

		UserModel userTwoModel = auth.authenticate("UserTwo", "userTwoPassword".toCharArray());
		assertNotNull(userTwoModel);
		assertNotNull(userTwoModel.getTeam("git_users"));
		assertNull(userTwoModel.getTeam("git_admins"));
		assertNotNull(userTwoModel.getTeam("git admins"));
		assertTrue(userTwoModel.canAdmin);

		UserModel userThreeModel = auth.authenticate("UserThree", "userThreePassword".toCharArray());
		assertNotNull(userThreeModel);
		assertNotNull(userThreeModel.getTeam("git_users"));
		assertNull(userThreeModel.getTeam("git_admins"));
		assertTrue(userThreeModel.canAdmin);
	}

	@Test
	public void testBindWithUser() {
		settings.put(Keys.realm.ldap.bindpattern, "CN=${username},OU=US,OU=Users,OU=UserControl,OU=MyOrganization,DC=MyDomain");
		settings.put(Keys.realm.ldap.username, "");
		settings.put(Keys.realm.ldap.password, "");

		UserModel userOneModel = auth.authenticate("UserOne", "userOnePassword".toCharArray());
		assertNotNull(userOneModel);

		UserModel userOneModelFailedAuth = auth.authenticate("UserOne", "userTwoPassword".toCharArray());
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
