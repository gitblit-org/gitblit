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

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
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
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedRequest;
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedResult;
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedSearchEntry;
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedSearchRequest;
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedSearchResult;
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedSimpleBindResult;
import com.unboundid.ldap.listener.interceptor.InMemoryOperationInterceptor;
import com.unboundid.ldap.sdk.BindRequest;
import com.unboundid.ldap.sdk.BindResult;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPResult;
import com.unboundid.ldap.sdk.OperationType;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldap.sdk.SimpleBindRequest;
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

	private static final String RESOURCE_DIR = "src/test/resources/ldap/";
	private static final String DIRECTORY_MANAGER = "cn=Directory Manager";
	private static final String USER_MANAGER = "cn=UserManager";
	private static final String ACCOUNT_BASE = "OU=Users,OU=UserControl,OU=MyOrganization,DC=MyDomain";
	private static final String GROUP_BASE  = "OU=Groups,OU=UserControl,OU=MyOrganization,DC=MyDomain";


	/**
	 * Enumeration of different test modes, representing different use scenarios.
	 * With ANONYMOUS anonymous binds are used to search LDAP.
	 * DS_MANAGER will use a DIRECTORY_MANAGER to search LDAP. Normal users are prohibited to search the DS.
	 * With USR_MANAGER, a USER_MANAGER account is used to search in LDAP. This account can only search users
	 * but not groups. Normal users can search groups, though.
	 *
	 */
	enum AuthMode {
		ANONYMOUS(1389),
		DS_MANAGER(2389),
		USR_MANAGER(3389);


		private int ldapPort;
		private InMemoryDirectoryServer ds;
		private InMemoryDirectoryServerSnapshot dsSnapshot;

		AuthMode(int port) {
			this.ldapPort = port;
		}

		int ldapPort() {
			return this.ldapPort;
		}

		void setDS(InMemoryDirectoryServer ds) {
			if (this.ds == null) {
				this.ds = ds;
				this.dsSnapshot = ds.createSnapshot();
			};
		}

		InMemoryDirectoryServer getDS() {
			return ds;
		}

		void restoreSnapshot() {
			ds.restoreSnapshot(dsSnapshot);
		}
	};



	@Parameter
	public AuthMode authMode;

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private File usersConf;



	private LdapAuthProvider ldap;

	private IUserManager userManager;

	private AuthenticationManager auth;

	private MemorySettings settings;


	/**
	 * Run the tests with each authentication scenario once.
	 */
	@Parameters(name = "{0}")
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][] { {AuthMode.ANONYMOUS}, {AuthMode.DS_MANAGER}, {AuthMode.USR_MANAGER} });
	}



	/**
	 * Create three different in memory DS.
	 *
	 * Each DS has a different configuration:
	 * The first allows anonymous binds.
	 * The second requires authentication for all operations. It will only allow the DIRECTORY_MANAGER account
	 * to search for users and groups.
	 * The third one is like the second, but it allows users to search for users and groups, and restricts the
	 * USER_MANAGER from searching for groups.
	 */
	@BeforeClass
	public static void init() throws Exception {
		InMemoryDirectoryServer ds;
		InMemoryDirectoryServerConfig config = createInMemoryLdapServerConfig(AuthMode.ANONYMOUS);
		config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("default", AuthMode.ANONYMOUS.ldapPort()));
		ds = createInMemoryLdapServer(config);
		AuthMode.ANONYMOUS.setDS(ds);


		config = createInMemoryLdapServerConfig(AuthMode.DS_MANAGER);
		config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("default", AuthMode.DS_MANAGER.ldapPort()));
		config.setAuthenticationRequiredOperationTypes(EnumSet.allOf(OperationType.class));
		ds = createInMemoryLdapServer(config);
		AuthMode.DS_MANAGER.setDS(ds);


		config = createInMemoryLdapServerConfig(AuthMode.USR_MANAGER);
		config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("default", AuthMode.USR_MANAGER.ldapPort()));
		config.setAuthenticationRequiredOperationTypes(EnumSet.allOf(OperationType.class));
		ds = createInMemoryLdapServer(config);
		AuthMode.USR_MANAGER.setDS(ds);

	}

	@AfterClass
	public static void destroy() throws Exception {
		for (AuthMode am : AuthMode.values()) {
			am.getDS().shutDown(true);
		}
	}

	public static InMemoryDirectoryServer createInMemoryLdapServer(InMemoryDirectoryServerConfig config) throws Exception {
		InMemoryDirectoryServer imds = new InMemoryDirectoryServer(config);
		imds.importFromLDIF(true, RESOURCE_DIR + "sampledata.ldif");
		imds.startListening();
		return imds;
	}

	public static InMemoryDirectoryServerConfig createInMemoryLdapServerConfig(AuthMode authMode) throws Exception {
		InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=MyDomain");
		config.addAdditionalBindCredentials(DIRECTORY_MANAGER, "password");
		config.addAdditionalBindCredentials(USER_MANAGER, "passwd");
		config.setSchema(null);

		config.addInMemoryOperationInterceptor(new AccessInterceptor(authMode));

		return config;
	}



	@Before
	public void setup() throws Exception {
		authMode.restoreSnapshot();

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
		switch(authMode) {
		case ANONYMOUS:
			backingMap.put(Keys.realm.ldap.server, "ldap://localhost:" + authMode.ldapPort());
			backingMap.put(Keys.realm.ldap.username, "");
			backingMap.put(Keys.realm.ldap.password, "");
			break;
		case DS_MANAGER:
			backingMap.put(Keys.realm.ldap.server, "ldap://localhost:" + authMode.ldapPort());
			backingMap.put(Keys.realm.ldap.username, DIRECTORY_MANAGER);
			backingMap.put(Keys.realm.ldap.password, "password");
			break;
		case USR_MANAGER:
			backingMap.put(Keys.realm.ldap.server, "ldap://localhost:" + authMode.ldapPort());
			backingMap.put(Keys.realm.ldap.username, USER_MANAGER);
			backingMap.put(Keys.realm.ldap.password, "passwd");
			break;
		default:
			throw new RuntimeException("Unimplemented AuthMode case!");

		}
		backingMap.put(Keys.realm.ldap.maintainTeams, "true");
		backingMap.put(Keys.realm.ldap.accountBase, ACCOUNT_BASE);
		backingMap.put(Keys.realm.ldap.accountPattern, "(&(objectClass=person)(sAMAccountName=${username}))");
		backingMap.put(Keys.realm.ldap.groupBase, GROUP_BASE);
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


	private InMemoryDirectoryServer getDS()
	{
		return authMode.getDS();
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




	/**
	 * Operation interceptor for the in memory DS. This interceptor
	 * implements access restrictions for certain user/DN combinations.
	 *
	 * The USER_MANAGER is only allowed to search for users, but not for groups.
	 * This is to test the original behaviour where the teams were searched under
	 * the user binding.
	 * When running in a DIRECTORY_MANAGER scenario, only the manager account
	 * is allowed to search for users and groups, while a normal user may not do so.
	 * This tests the scenario where a normal user cannot read teams and thus the
	 * manager account needs to be used for all searches.
	 *
	 */
	private static class AccessInterceptor extends InMemoryOperationInterceptor {
		AuthMode authMode;
		Map<Long,String> lastSuccessfulBindDN = new HashMap<>();
		Map<Long,Boolean> resultProhibited = new HashMap<>();

		public AccessInterceptor(AuthMode authMode) {
			this.authMode = authMode;
		}


		@Override
		public void processSimpleBindResult(InMemoryInterceptedSimpleBindResult bind) {
			BindResult result = bind.getResult();
			if (result.getResultCode() == ResultCode.SUCCESS) {
				 BindRequest bindRequest = bind.getRequest();
				 lastSuccessfulBindDN.put(bind.getConnectionID(), ((SimpleBindRequest)bindRequest).getBindDN());
				 resultProhibited.remove(bind.getConnectionID());
			}
		}



		@Override
		public void processSearchRequest(InMemoryInterceptedSearchRequest request) throws LDAPException {
			String bindDN = getLastBindDN(request);

			if (USER_MANAGER.equals(bindDN)) {
				if (request.getRequest().getBaseDN().endsWith(GROUP_BASE)) {
					throw new LDAPException(ResultCode.NO_SUCH_OBJECT);
				}
			}
			else if(authMode == AuthMode.DS_MANAGER && !DIRECTORY_MANAGER.equals(bindDN)) {
				throw new LDAPException(ResultCode.NO_SUCH_OBJECT);
			}
		}


		@Override
		public void processSearchEntry(InMemoryInterceptedSearchEntry entry) {
			String bindDN = getLastBindDN(entry);

			boolean prohibited = false;

			if (USER_MANAGER.equals(bindDN)) {
				if (entry.getSearchEntry().getDN().endsWith(GROUP_BASE)) {
					prohibited = true;
				}
			}
			else if(authMode == AuthMode.DS_MANAGER && !DIRECTORY_MANAGER.equals(bindDN)) {
				prohibited = true;
			}

			if (prohibited) {
				// Found entry prohibited for bound user. Setting entry to null.
				entry.setSearchEntry(null);
				resultProhibited.put(entry.getConnectionID(), Boolean.TRUE);
			}
		}

		@Override
		public void processSearchResult(InMemoryInterceptedSearchResult result) {
			String bindDN = getLastBindDN(result);

			boolean prohibited = false;

			Boolean rspb = resultProhibited.get(result.getConnectionID());
			if (USER_MANAGER.equals(bindDN)) {
				if (rspb != null && rspb) {
					prohibited = true;
				}
			}
			else if(authMode == AuthMode.DS_MANAGER && !DIRECTORY_MANAGER.equals(bindDN)) {
				if (rspb != null && rspb) {
					prohibited = true;
				}
			}

			if (prohibited) {
				// Result prohibited for bound user. Returning error
				result.setResult(new LDAPResult(result.getMessageID(), ResultCode.INSUFFICIENT_ACCESS_RIGHTS));
				resultProhibited.remove(result.getConnectionID());
			}
		}

		private String getLastBindDN(InMemoryInterceptedResult result) {
			String bindDN = lastSuccessfulBindDN.get(result.getConnectionID());
			if (bindDN == null) {
				return "UNKNOWN";
			}
			return bindDN;
		}
		private String getLastBindDN(InMemoryInterceptedRequest request) {
			String bindDN = lastSuccessfulBindDN.get(request.getConnectionID());
			if (bindDN == null) {
				return "UNKNOWN";
			}
			return bindDN;
		}
	}

}
