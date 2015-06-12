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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.inject.Inject;

/**
 * Memory public key manager.
 *
 * @author James Moger
 *
 */
public class MemoryKeyManager extends IPublicKeyManager {

	final Map<String, List<SshKey>> keys;

	@Inject
	public MemoryKeyManager() {
		keys = new HashMap<String, List<SshKey>>();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}

	@Override
	public MemoryKeyManager start() {
		log.info(toString());
		return this;
	}

	@Override
	public boolean isReady() {
		return true;
	}

	@Override
	public MemoryKeyManager stop() {
		return this;
	}

	@Override
	protected boolean isStale(String username) {
		// always return true so we gets keys from our hashmap
		return true;
	}

	@Override
	protected List<SshKey> getKeysImpl(String username) {
		String id = username.toLowerCase();
		if (keys.containsKey(id)) {
			return keys.get(id);
		}
		return null;
	}

	@Override
	public boolean addKey(String username, SshKey key) {
		String id = username.toLowerCase();
		if (!keys.containsKey(id)) {
			keys.put(id, new ArrayList<SshKey>());
		}
		log.info("added {} key {}", username, key.getFingerprint());
		return keys.get(id).add(key);
	}

	@Override
	public boolean removeKey(String username, SshKey key) {
		String id = username.toLowerCase();
		if (!keys.containsKey(id)) {
			log.info("can't remove keys for {}", username);
			return false;
		}
		List<SshKey> list = keys.get(id);
		boolean success = list.remove(key);
		if (success) {
			log.info("removed {} key {}", username, key.getFingerprint());
		}

		if (list.isEmpty()) {
			keys.remove(id);
			log.info("no {} keys left, removed {}", username, username);
		}
		return success;
	}

	@Override
	public boolean removeAllKeys(String username) {
		String id = username.toLowerCase();
		keys.remove(id.toLowerCase());
		log.info("removed all keys for {}", username);
		return true;
	}
}
