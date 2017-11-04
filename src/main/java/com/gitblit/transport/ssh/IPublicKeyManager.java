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

import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.manager.IManager;
import com.gitblit.models.UserModel;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheLoader.InvalidCacheLoadException;
import com.google.common.cache.LoadingCache;

/**
 * Parent class for ssh public key managers.
 *
 * @author James Moger
 *
 */
public abstract class IPublicKeyManager implements IManager {

	protected final Logger log = LoggerFactory.getLogger(getClass());

	protected final LoadingCache<String, List<SshKey>> keyCache = CacheBuilder
			.newBuilder().
			expireAfterAccess(15, TimeUnit.MINUTES).
			maximumSize(100)
			.build(new CacheLoader<String, List<SshKey>>() {
				@Override
				public List<SshKey> load(String username) {
					List<SshKey> keys = getKeysImpl(username);
					if (keys == null) {
						return Collections.emptyList();
					}
					return Collections.unmodifiableList(keys);
				}
			});

	@Override
	public abstract IPublicKeyManager start();

	public abstract boolean isReady();

	@Override
	public abstract IPublicKeyManager stop();

	public final List<SshKey> getKeys(String username) {
		try {
			if (isStale(username)) {
				keyCache.invalidate(username);
			}
			return keyCache.get(username);
		} catch (InvalidCacheLoadException e) {
			if (e.getMessage() == null || !e.getMessage().contains("returned null")) {
				log.error(MessageFormat.format("failed to retrieve keys for {0}", username), e);
			}
		} catch (ExecutionException e) {
			log.error(MessageFormat.format("failed to retrieve keys for {0}", username), e);
		}
		return null;
	}

	public final void renameUser(String oldName, String newName) {
		List<SshKey> keys = getKeys(oldName);
		if (keys == null || keys.isEmpty()) {
			return;
		}
		removeAllKeys(oldName);
		for (SshKey key : keys) {
			addKey(newName, key);
		}
	}

	protected abstract boolean isStale(String username);

	protected abstract List<SshKey> getKeysImpl(String username);

	public abstract boolean addKey(String username, SshKey key);

	public abstract boolean removeKey(String username, SshKey key);

	public abstract boolean removeAllKeys(String username);

	public boolean supportsWritingKeys(UserModel user) {
		return (user != null);
	}

	public boolean supportsCommentChanges(UserModel user) {
		return (user != null);
	}

	public boolean supportsPermissionChanges(UserModel user) {
		return (user != null);
	}
}
