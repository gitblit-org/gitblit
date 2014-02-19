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
package com.gitblit.service;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.auth.LdapAuthProvider;

/**
 * @author Alfred Schmid
 * 
 */
public final class LdapSyncService implements Runnable {

	private final Logger logger = LoggerFactory.getLogger(LdapSyncService.class);

	private final IStoredSettings settings;

	private final LdapAuthProvider ldapAuthProvider;

	private final AtomicBoolean running = new AtomicBoolean(false);

	public LdapSyncService(IStoredSettings settings, LdapAuthProvider ldapAuthProvider) {
		this.settings = settings;
		this.ldapAuthProvider = ldapAuthProvider;
	}

	/**
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		logger.info("Starting user and group sync with ldap service");
		if (!running.getAndSet(true)) {
			ldapAuthProvider.synchronizeWithLdapService();
			running.getAndSet(false);
		}
		logger.info("Finished user and group sync with ldap service");
	}

	public boolean isReady() {
		return settings.getBoolean(Keys.realm.ldap.synchronizeUsers.enable, false);
	}

}
