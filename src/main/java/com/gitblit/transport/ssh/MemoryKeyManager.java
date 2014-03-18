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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Memory public key manager.
 *
 * @author James Moger
 *
 */
public class MemoryKeyManager extends IPublicKeyManager {

	Map<String, List<PublicKey>> keys;

	public MemoryKeyManager() {
		keys = new HashMap<String, List<PublicKey>>();
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
		return false;
	}

	@Override
	protected List<PublicKey> getKeysImpl(String username) {
		String id = username.toLowerCase();
		if (keys.containsKey(id)) {
			return keys.get(id);
		}
		return null;
	}

	@Override
	public boolean addKey(String username, String data) {
		return false;
	}

	@Override
	public boolean removeKey(String username, String data) {
		return false;
	}

	@Override
	public boolean removeAllKeys(String username) {
		String id = username.toLowerCase();
		keys.remove(id.toLowerCase());
		return true;
	}

	/* Test method for populating the memory key manager */
	public void addKey(String username, PublicKey key) {
		String id = username.toLowerCase();
		if (!keys.containsKey(id)) {
			keys.put(id, new ArrayList<PublicKey>());
		}
		keys.get(id).add(key);
	}
}
