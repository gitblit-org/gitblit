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

import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gitblit.IStoredSettings;
import com.gitblit.auth.LdapAuthProvider;
import com.gitblit.manager.RuntimeManager;
import com.gitblit.manager.UserManager;
import com.gitblit.models.UserModel;
import com.gitblit.tests.mock.MemorySettings;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldif.LDIFReader;

/**
 * An Integration test for LDAP that tests going against an in-memory UnboundID
 * LDAP server.
 *
 * @author jcrygier
 *
 */
public class LdapAuthenticationTest extends GitblitUnitTest {

	private static final String RESOURCE_DIR = "src/test/resources/ldap/";

	private LdapAuthProvider ldap;

	static int ldapPort = 1389;

	@BeforeClass
	public static void createInMemoryLdapServer() throws Exception {
		InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=MyDomain");
		config.addAdditionalBindCredentials("cn=Directory Manager", "password");
		config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("default", ldapPort));
		config.setSchema(null);

		InMemoryDirectoryServer ds = new InMemoryDirectoryServer(config);
		ds.importFromLDIF(true, new LDIFReader(new FileInputStream(RESOURCE_DIR + "sampledata.ldif")));
		ds.startListening();
	}

	@Before
	public void newLdapAuthentication() {
		ldap = newLdapAuthentication(getSettings());
	}

	public LdapAuthProvider newLdapAuthentication(IStoredSettings settings) {
		RuntimeManager runtime = new RuntimeManager(settings, GitBlitSuite.BASEFOLDER).start();
		UserManager users = new UserManager(runtime).start();
		LdapAuthProvider ldap = new LdapAuthProvider();
		ldap.setup(runtime, users);
		return ldap;
	}

	private MemorySettings getSettings() {
		Map<String, Object> backingMap = new HashMap<String, Object>();
		backingMap.put("realm.userService", RESOURCE_DIR + "users.conf");
		backingMap.put("realm.ldap.server", "ldap://localhost:" + ldapPort);
		backingMap.put("realm.ldap.domain", "");
		backingMap.put("realm.ldap.username", "cn=Directory Manager");
		backingMap.put("realm.ldap.password", "password");
		backingMap.put("realm.ldap.backingUserService", "users.conf");
		backingMap.put("realm.ldap.maintainTeams", "true");
		backingMap.put("realm.ldap.accountBase", "OU=Users,OU=UserControl,OU=MyOrganization,DC=MyDomain");
		backingMap.put("realm.ldap.accountPattern", "(&(objectClass=person)(sAMAccountName=${username}))");
		backingMap.put("realm.ldap.groupBase", "OU=Groups,OU=UserControl,OU=MyOrganization,DC=MyDomain");
		backingMap.put("realm.ldap.groupPattern", "(&(objectClass=group)(member=${dn}))");
		backingMap.put("realm.ldap.admins", "UserThree @Git_Admins \"@Git Admins\"");
		backingMap.put("realm.ldap.displayName", "displayName");
		backingMap.put("realm.ldap.email", "email");

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

}
