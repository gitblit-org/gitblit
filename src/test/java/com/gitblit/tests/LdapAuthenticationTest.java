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
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

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
import com.unboundid.ldap.listener.InMemoryDirectoryServerSnapshot;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.OperationType;
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
public class LdapAuthenticationTest extends GitblitUnitTest {

	public enum ServerMode { ANONYMOUS, AUTHENTICATED };

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private static final String RESOURCE_DIR = "src/test/resources/ldap/";

	@Parameter
	public ServerMode serverMode;

	private File usersConf;

	private LdapAuthProvider ldap;

	private static int ldapPort = 1389;
	private static int ldapAuthedPort = 2389;

	private static InMemoryDirectoryServer ds;
	private static InMemoryDirectoryServerSnapshot dsAnonSnapshot;

	private static InMemoryDirectoryServer dsAuthed;
	private static InMemoryDirectoryServerSnapshot dsAuthedSnapshot;

	private IUserManager userManager;

	private AuthenticationManager auth;

	private MemorySettings settings;



	@Parameters(name = "{0}")
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][] { {ServerMode.ANONYMOUS}, {ServerMode.AUTHENTICATED} });
	}



	@BeforeClass
	public static void init() throws Exception {
		InMemoryDirectoryServerConfig config = createInMemoryLdapServerConfig();
		config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("default", ldapPort));
		ds = createInMemoryLdapServer(config);
		dsAnonSnapshot = ds.createSnapshot();


		config = createInMemoryLdapServerConfig();
		config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("default", ldapAuthedPort));
		config.setAuthenticationRequiredOperationTypes(EnumSet.allOf(OperationType.class));
		dsAuthed = createInMemoryLdapServer(config);
		dsAuthedSnapshot = ds.createSnapshot();

	}

	public static InMemoryDirectoryServer createInMemoryLdapServer(InMemoryDirectoryServerConfig config) throws Exception {
		InMemoryDirectoryServer imds = new InMemoryDirectoryServer(config);
		imds.importFromLDIF(true, RESOURCE_DIR + "sampledata.ldif");
		imds.startListening();
		return imds;
	}

	public static InMemoryDirectoryServerConfig createInMemoryLdapServerConfig() throws Exception {
		InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=MyDomain");
		config.addAdditionalBindCredentials("cn=Directory Manager", "password");
		config.setSchema(null);
		return config;
	}



	@Before
	public void setup() throws Exception {
		ds.restoreSnapshot(dsAnonSnapshot);
		dsAuthed.restoreSnapshot(dsAuthedSnapshot);

		System.out.println("Before with server mode " + serverMode);

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
		if (ServerMode.ANONYMOUS == serverMode) {
			backingMap.put(Keys.realm.ldap.server, "ldap://localhost:" + ldapPort);
			backingMap.put(Keys.realm.ldap.username, "");
			backingMap.put(Keys.realm.ldap.password, "");
		} else {
			backingMap.put(Keys.realm.ldap.server, "ldap://localhost:" + ldapAuthedPort);
			backingMap.put(Keys.realm.ldap.username, "cn=Directory Manager");
			backingMap.put(Keys.realm.ldap.password, "password");
		}
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

		UserModel userFourModel = ldap.authenticate("UserFour", "userFourPassword".toCharArray());
		assertNotNull(userFourModel);
		assertNotNull(userFourModel.getTeam("git_users"));
		assertNull(userFourModel.getTeam("git_admins"));
		assertNull(userFourModel.getTeam("git admins"));
		assertFalse(userFourModel.canAdmin);
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
	public void addingGroupsInLdapShouldUpdateGitBlitUsersAndGroups() throws Exception {
		settings.put(Keys.realm.ldap.synchronize, "true");
		getDS().addEntries(LDIFReader.readEntries(RESOURCE_DIR + "addgroup.ldif"));
		ldap.sync();
		assertEquals("Number of ldap groups in gitblit team model", 1, countLdapTeamsInUserManager());
	}

	@Test
	public void testAuthenticationManager() {
		UserModel userOneModel = auth.authenticate("UserOne", "userOnePassword".toCharArray(), null);
		assertNotNull(userOneModel);
		assertNotNull(userOneModel.getTeam("git_admins"));
		assertNotNull(userOneModel.getTeam("git_users"));
		assertTrue(userOneModel.canAdmin);

		UserModel userOneModelFailedAuth = auth.authenticate("UserOne", "userTwoPassword".toCharArray(), null);
		assertNull(userOneModelFailedAuth);

		UserModel userTwoModel = auth.authenticate("UserTwo", "userTwoPassword".toCharArray(), null);
		assertNotNull(userTwoModel);
		assertNotNull(userTwoModel.getTeam("git_users"));
		assertNull(userTwoModel.getTeam("git_admins"));
		assertNotNull(userTwoModel.getTeam("git admins"));
		assertTrue(userTwoModel.canAdmin);

		UserModel userThreeModel = auth.authenticate("UserThree", "userThreePassword".toCharArray(), null);
		assertNotNull(userThreeModel);
		assertNotNull(userThreeModel.getTeam("git_users"));
		assertNull(userThreeModel.getTeam("git_admins"));
		assertTrue(userThreeModel.canAdmin);

		UserModel userFourModel = auth.authenticate("UserFour", "userFourPassword".toCharArray(), null);
		assertNotNull(userFourModel);
		assertNotNull(userFourModel.getTeam("git_users"));
		assertNull(userFourModel.getTeam("git_admins"));
		assertNull(userFourModel.getTeam("git admins"));
		assertFalse(userFourModel.canAdmin);
	}

	@Test
	public void testBindWithUser() {
		settings.put(Keys.realm.ldap.bindpattern, "CN=${username},OU=US,OU=Users,OU=UserControl,OU=MyOrganization,DC=MyDomain");
		settings.put(Keys.realm.ldap.username, "");
		settings.put(Keys.realm.ldap.password, "");

		UserModel userOneModel = auth.authenticate("UserOne", "userOnePassword".toCharArray(), null);
		assertNotNull(userOneModel);

		UserModel userOneModelFailedAuth = auth.authenticate("UserOne", "userTwoPassword".toCharArray(), null);
		assertNull(userOneModelFailedAuth);
	}



	private InMemoryDirectoryServer getDS() {
		if (ServerMode.ANONYMOUS == serverMode) {
			return ds;
		} else {
			return dsAuthed;
		}
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
