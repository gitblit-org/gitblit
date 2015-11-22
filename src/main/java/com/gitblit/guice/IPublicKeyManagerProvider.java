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
package com.gitblit.guice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.transport.ssh.FileKeyManager;
import com.gitblit.transport.ssh.IPublicKeyManager;
import com.gitblit.transport.ssh.NullKeyManager;
import com.gitblit.utils.StringUtils;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

/**
 * Provides a lazily-instantiated IPublicKeyManager configured from IStoredSettings.
 *
 * @author James Moger
 *
 */
@Singleton
public class IPublicKeyManagerProvider implements Provider<IPublicKeyManager> {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final IRuntimeManager runtimeManager;

	private volatile IPublicKeyManager manager;

	@Inject
	public IPublicKeyManagerProvider(IRuntimeManager runtimeManager) {
		this.runtimeManager = runtimeManager;
	}

	@Override
	public synchronized IPublicKeyManager get() {
		if (manager != null) {
			return manager;
		}

		IStoredSettings settings = runtimeManager.getSettings();
		String clazz = settings.getString(Keys.git.sshKeysManager, FileKeyManager.class.getName());
		if (StringUtils.isEmpty(clazz)) {
			clazz = FileKeyManager.class.getName();
		}
		try {
			Class<? extends IPublicKeyManager> mgrClass = (Class<? extends IPublicKeyManager>) Class.forName(clazz);
			manager = runtimeManager.getInjector().getInstance(mgrClass);
		} catch (Exception e) {
			logger.error("failed to create public key manager", e);
			manager = new NullKeyManager();
		}
		return manager;
	}
}