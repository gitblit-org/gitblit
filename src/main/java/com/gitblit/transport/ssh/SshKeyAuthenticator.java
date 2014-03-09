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

import java.io.File;
import java.io.IOException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.codec.binary.Base64;
import org.apache.sshd.common.util.Buffer;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.lib.Constants;

import com.gitblit.Keys;
import com.gitblit.manager.IGitblit;
import com.gitblit.models.UserModel;
import com.google.common.base.Charsets;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.io.Files;

/**
 *
 * @author Eric Myrhe
 *
 */
public class SshKeyAuthenticator implements PublickeyAuthenticator {

	protected final IGitblit gitblit;

	LoadingCache<String, List<PublicKey>> sshKeyCache = CacheBuilder
			.newBuilder().
			expireAfterAccess(15, TimeUnit.MINUTES).
			maximumSize(100)
			.build(new CacheLoader<String, List<PublicKey>>() {
				public List<PublicKey> load(String username) {
					try {
						File dir = gitblit.getFileOrFolder(Keys.git.sshKeysFolder, "${baseFolder}/ssh");
						dir.mkdirs();
						File keys = new File(dir, username + ".keys");
						if (!keys.exists()) {
							return null;
						}
						if (keys.exists()) {
							String str = Files.toString(keys, Charsets.ISO_8859_1);
							String [] entries = str.split("\n");
							List<PublicKey> list = new ArrayList<PublicKey>();
							for (String entry : entries) {
								final String[] parts = entry.split(" ");
								final byte[] bin = Base64.decodeBase64(Constants.encodeASCII(parts[1]));
								list.add(new Buffer(bin).getRawPublicKey());
							}
							
							if (list.isEmpty()) {
								return null;
							}
							return list;
						}
					} catch (IOException e) {
						throw new RuntimeException("Canot read public key", e);
					}
					return null;
				}
			});

	public SshKeyAuthenticator(IGitblit gitblit) {
		this.gitblit = gitblit;
	}

	@Override
	public boolean authenticate(String username, final PublicKey suppliedKey,
			final ServerSession session) {
		final SshSession sd = session.getAttribute(SshSession.KEY);

		username = username.toLowerCase(Locale.US);
		try {
			List<PublicKey> keys = sshKeyCache.get(username);
			if (keys == null || keys.isEmpty()) {
				sd.authenticationError(username, "no-matching-key");
				return false;
			}
			for (PublicKey key : keys) {
				if (key.equals(suppliedKey)) {
					return validate(username, sd);
				}
			}
			return false;
		} catch (ExecutionException e) {
			sd.authenticationError(username, "user-not-found");
			return false;
		}
	}

	boolean validate(String username, SshSession sd) {
		// now that the key has been validated, check with the authentication
		// manager to ensure that this user exists and can authenticate
		sd.authenticationSuccess(username);
		UserModel user = gitblit.authenticate(sd);
		if (user != null) {
			return true;
		}
		sd.authenticationError(username, "user-not-found");
		return false;
	}
}
