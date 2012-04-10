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

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.gitblit.LdapUserService;
import com.gitblit.models.UserModel;
import com.gitblit.tests.mock.MemorySettings;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldif.LDIFReader;

/**
 * An Integration test for LDAP that tests going against an in-memory UnboundID
 * LDAP server.
 * 
 * @author jcrygier
 *
 */
public class LdapUserServiceTest {
	
	private LdapUserService ldapUserService;
	
	@Before
	public void createInMemoryLdapServer() throws Exception {
		InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=MyDomain");
		config.addAdditionalBindCredentials("cn=Directory Manager", "password");
		config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("default", 389));
		config.setSchema(null);
		
		InMemoryDirectoryServer ds = new InMemoryDirectoryServer(config);
		ds.importFromLDIF(true, new LDIFReader(this.getClass().getResourceAsStream("resources/ldapUserServiceSampleData.ldif")));
		ds.startListening();
	}
	
	@Before
	public void createLdapUserService() {
		Map<Object, Object> backingMap = new HashMap<Object, Object>();
		backingMap.put("realm.ldap.server", "ldap://localhost:389");
		backingMap.put("realm.ldap.domain", "");
		backingMap.put("realm.ldap.username", "cn=Directory Manager");
		backingMap.put("realm.ldap.password", "password");
		backingMap.put("realm.ldap.backingUserService", "users.conf");
		backingMap.put("realm.ldap.maintainTeams", "true");
		backingMap.put("realm.ldap.accountBase", "OU=Users,OU=UserControl,OU=MyOrganization,DC=MyDomain");
		backingMap.put("realm.ldap.accountPattern", "(&(objectClass=person)(sAMAccountName=${username}))");
		backingMap.put("realm.ldap.groupBase", "OU=Groups,OU=UserControl,OU=MyOrganization,DC=MyDomain");
		backingMap.put("realm.ldap.groupPattern", "(&(objectClass=group)(member=${dn}))");
		backingMap.put("realm.ldap.admins", "UserThree @Git_Admins");
		
		MemorySettings ms = new MemorySettings(backingMap);
		
		ldapUserService = new LdapUserService();
		ldapUserService.setup(ms);
	}
	
	@Test
	public void testAuthenticate() {		
		UserModel userOneModel = ldapUserService.authenticate("UserOne", "userOnePassword".toCharArray());
		assertNotNull(userOneModel);
		assertNotNull(userOneModel.getTeam("git_admins"));
		assertNotNull(userOneModel.getTeam("git_users"));
		assertTrue(userOneModel.canAdmin);
		
		UserModel userOneModelFailedAuth = ldapUserService.authenticate("UserOne", "userTwoPassword".toCharArray());
		assertNull(userOneModelFailedAuth);
		
		UserModel userTwoModel = ldapUserService.authenticate("UserTwo", "userTwoPassword".toCharArray());
		assertNotNull(userTwoModel);
		assertNotNull(userTwoModel.getTeam("git_users"));
		assertNull(userTwoModel.getTeam("git_admins"));
		assertFalse(userTwoModel.canAdmin);
		
		UserModel userThreeModel = ldapUserService.authenticate("UserThree", "userThreePassword".toCharArray());
		assertNotNull(userThreeModel);
		assertNotNull(userThreeModel.getTeam("git_users"));
		assertNull(userThreeModel.getTeam("git_admins"));
		assertTrue(userThreeModel.canAdmin);
	}

}
