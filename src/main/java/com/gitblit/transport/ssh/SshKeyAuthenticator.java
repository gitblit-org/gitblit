/*
 * Copyright 2014 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.gitblit.transport.ssh;

import java.security.PublicKey;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.manager.IAuthenticationManager;
import com.gitblit.models.UserModel;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

/**
 *
 * @author Eric Myrhe
 *
 */
public class SshKeyAuthenticator implements PublickeyAuthenticator {

	protected final Logger log = LoggerFactory.getLogger(getClass());

	protected final IKeyManager keyManager;

	protected final IAuthenticationManager authManager;

	LoadingCache<String, List<PublicKey>> sshKeyCache = CacheBuilder
			.newBuilder().
			expireAfterAccess(15, TimeUnit.MINUTES).
			maximumSize(100)
			.build(new CacheLoader<String, List<PublicKey>>() {
				@Override
				public List<PublicKey> load(String username) {
					return keyManager.getKeys(username);
				}
			});

	public SshKeyAuthenticator(IKeyManager keyManager, IAuthenticationManager authManager) {
		this.keyManager = keyManager;
		this.authManager = authManager;
	}

	@Override
	public boolean authenticate(String username, final PublicKey suppliedKey,
			final ServerSession session) {
		final SshDaemonClient client = session.getAttribute(SshDaemonClient.KEY);

		if (client.getUser() != null) {
			// TODO why do we re-authenticate?
			log.info("{} has already authenticated!", username);
			return true;
		}

		username = username.toLowerCase(Locale.US);
		try {
			List<PublicKey> keys = sshKeyCache.get(username);
			if (keys == null || keys.isEmpty()) {
				log.info("{} has not added any public keys for ssh authentication", username);
				return false;
			}

			for (PublicKey key : keys) {
				if (key.equals(suppliedKey)) {
					UserModel user = authManager.authenticate(username, key);
					if (user != null) {
						client.setUser(user);
						return true;
					}
				}
			}
		} catch (ExecutionException e) {
		}

		log.warn("could not authenticate {} for SSH using the supplied public key", username);
		return false;
	}

	public IKeyManager getKeyManager() {
		return keyManager;
	}

	public Cache<String, List<PublicKey>> getKeyCache() {
		return sshKeyCache;
	}
}
