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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.gitblit.Keys;
import com.gitblit.service.LdapSyncService;
import com.gitblit.tests.mock.MemorySettings;

/**
 * A behavior driven test for the LdapSyncService with in-memory Settings.
 *
 * @author Alfred Schmid
 *
 */
public class LdapSyncServiceTest {

	private MemorySettings settings;

	@Before
	public void init() throws Exception {
		settings = getSettings();
	}

	@Test
	public void defaultOfUnAvailableLdapSynchronizeKeyIsLdapServiceNotReady() {
		LdapSyncService ldapSyncService = new LdapSyncService(settings, null);
		assertFalse("When key " + Keys.realm.ldap.synchronize + " is not configured ldap sync is not ready." , ldapSyncService.isReady());
	}

	@Test
	public void whenLdapSynchronizeKeyIsFalseLdapServiceNotReady() {
		LdapSyncService ldapSyncService = new LdapSyncService(settings, null);
		settings.put(Keys.realm.ldap.synchronize, "false");
		assertFalse("When key " + Keys.realm.ldap.synchronize + " is configured with value false ldap sync is not ready." , ldapSyncService.isReady());
	}

	@Test
	public void whenLdapSynchronizeKeyIsTrueLdapServiceIsReady() {
		LdapSyncService ldapSyncService = new LdapSyncService(settings, null);
		settings.put(Keys.realm.ldap.synchronize, "true");
		assertTrue("When key " + Keys.realm.ldap.synchronize + " is configured with value true ldap sync is not ready." , ldapSyncService.isReady());
	}

	private MemorySettings getSettings() {
		Map<String, Object> backingMap = new HashMap<String, Object>();
		MemorySettings ms = new MemorySettings(backingMap);
		return ms;
	}

}
