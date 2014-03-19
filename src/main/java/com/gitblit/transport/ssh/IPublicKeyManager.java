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
import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.manager.IManager;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheLoader.InvalidCacheLoadException;
import com.google.common.cache.LoadingCache;

/**
 * Parent class for public key managers.
 *
 * @author James Moger
 *
 */
public abstract class IPublicKeyManager implements IManager {

	protected final Logger log = LoggerFactory.getLogger(getClass());

	protected final LoadingCache<String, List<PublicKey>> keyCache = CacheBuilder
			.newBuilder().
			expireAfterAccess(15, TimeUnit.MINUTES).
			maximumSize(100)
			.build(new CacheLoader<String, List<PublicKey>>() {
				@Override
				public List<PublicKey> load(String username) {
					return getKeysImpl(username);
				}
			});

	@Override
	public abstract IPublicKeyManager start();

	public abstract boolean isReady();

	@Override
	public abstract IPublicKeyManager stop();

	public final List<PublicKey> getKeys(String username) {
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

	protected abstract boolean isStale(String username);

	protected abstract List<PublicKey> getKeysImpl(String username);

	public abstract boolean addKey(String username, String data);

	public abstract boolean removeKey(String username, String data);

	public abstract boolean removeAllKeys(String username);
}
