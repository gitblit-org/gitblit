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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.sshd.common.Session;
import org.apache.sshd.common.SessionListener;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.manager.IAuthenticationManager;
import com.gitblit.models.UserModel;
import com.google.common.base.Preconditions;

/**
 * Authenticates an SSH session against a public key.
 *
 */
public class CachingPublicKeyAuthenticator implements PublickeyAuthenticator, SessionListener {

	protected final Logger log = LoggerFactory.getLogger(getClass());

	protected final IPublicKeyManager keyManager;

	protected final IAuthenticationManager authManager;

	private final Map<ServerSession, Map<PublicKey, Boolean>> cache = new ConcurrentHashMap<ServerSession, Map<PublicKey, Boolean>>();

	public CachingPublicKeyAuthenticator(IPublicKeyManager keyManager, IAuthenticationManager authManager) {
		this.keyManager = keyManager;
		this.authManager = authManager;
	}

	@Override
	public boolean authenticate(String username, PublicKey key, ServerSession session) {
		Map<PublicKey, Boolean> map = cache.get(session);
		if (map == null) {
			map = new HashMap<PublicKey, Boolean>();
			cache.put(session, map);
			session.addListener(this);
		}
		if (map.containsKey(key)) {
			return map.get(key);
		}
		boolean result = doAuthenticate(username, key, session);
		map.put(key, result);
		return result;
	}

	private boolean doAuthenticate(String username, PublicKey suppliedKey, ServerSession session) {
		SshDaemonClient client = session.getAttribute(SshDaemonClient.KEY);
		Preconditions.checkState(client.getUser() == null);
		username = username.toLowerCase(Locale.US);
		List<SshKey> keys = keyManager.getKeys(username);
		if (keys.isEmpty()) {
			log.info("{} has not added any public keys for ssh authentication", username);
			return false;
		}

		SshKey pk = new SshKey(suppliedKey);
		log.debug("auth supplied {}", pk.getFingerprint());

		for (SshKey key : keys) {
			log.debug("auth compare to {}", key.getFingerprint());
			if (key.getPublicKey().equals(suppliedKey)) {
				UserModel user = authManager.authenticate(username, key);
				if (user != null) {
					client.setUser(user);
					client.setKey(key);
					return true;
				}
			}
		}

		log.warn("could not authenticate {} for SSH using the supplied public key", username);
		return false;
	}

	@Override
	public void sessionCreated(Session session) {
	}

	@Override
	public void sessionEvent(Session sesssion, Event event) {
	}

	@Override
	public void sessionClosed(Session session) {
		cache.remove(session);
	}
}
