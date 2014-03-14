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

/**
 * Rejects all SSH key management requests.
 * 
 * @author James Moger
 *
 */
public class NullKeyManager implements IKeyManager {

	public NullKeyManager() {
	}
	
	@Override
	public String toString() {
		return getClass().getSimpleName();
	}

	@Override
	public NullKeyManager start() {
		return this;
	}
	
	@Override
	public boolean isReady() {
		return true;
	}
	
	@Override
	public NullKeyManager stop() {
		return this;
	}

	@Override
	public List<PublicKey> getKeys(String username) {
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
		return false;
	}
}
