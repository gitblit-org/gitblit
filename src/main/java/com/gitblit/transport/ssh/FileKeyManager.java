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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.codec.binary.Base64;
import org.apache.sshd.common.util.Buffer;
import org.eclipse.jgit.lib.Constants;

import com.gitblit.Keys;
import com.gitblit.manager.IRuntimeManager;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.Files;

/**
 * Manages public keys on the filesystem.
 *
 * @author James Moger
 *
 */
public class FileKeyManager extends IPublicKeyManager {

	protected final IRuntimeManager runtimeManager;

	protected final Map<File, Long> lastModifieds;

	public FileKeyManager(IRuntimeManager runtimeManager) {
		this.runtimeManager = runtimeManager;
		this.lastModifieds = new ConcurrentHashMap<File, Long>();
	}

	@Override
	public String toString() {
		File dir = runtimeManager.getFileOrFolder(Keys.git.sshKeysFolder, "${baseFolder}/ssh");
		return MessageFormat.format("{0} ({1})", getClass().getSimpleName(), dir);
	}

	@Override
	public FileKeyManager start() {
		log.info(toString());
		return this;
	}

	@Override
	public boolean isReady() {
		return true;
	}

	@Override
	public FileKeyManager stop() {
		return this;
	}

	@Override
	protected boolean isStale(String username) {
		File keystore = getKeystore(username);
		if (!keystore.exists()) {
			// keystore may have been deleted
			return true;
		}

		if (lastModifieds.containsKey(keystore)) {
			// compare modification times
			long lastModified = lastModifieds.get(keystore);
			return lastModified != keystore.lastModified();
		}

		// assume stale
		return true;
	}

	@Override
	protected List<PublicKey> getKeysImpl(String username) {
		try {
			log.info("loading keystore for {}", username);
			File keystore = getKeystore(username);
			if (!keystore.exists()) {
				return null;
			}
			if (keystore.exists()) {
				List<PublicKey> list = new ArrayList<PublicKey>();
				for (String entry : Files.readLines(keystore, Charsets.ISO_8859_1)) {
					if (entry.trim().length() == 0) {
						// skip blanks
						continue;
					}
					if (entry.charAt(0) == '#') {
						// skip comments
						continue;
					}
					final String[] parts = entry.split(" ");
					final byte[] bin = Base64.decodeBase64(Constants.encodeASCII(parts[1]));
					list.add(new Buffer(bin).getRawPublicKey());
				}

				if (list.isEmpty()) {
					return null;
				}

				lastModifieds.put(keystore, keystore.lastModified());
				return list;
			}
		} catch (IOException e) {
			throw new RuntimeException("Canot read ssh keys", e);
		}
		return null;
	}

	/**
	 * Adds a unique key to the keystore.  This function determines uniqueness
	 * by disregarding the comment/description field during key comparisons.
	 */
	@Override
	public boolean addKey(String username, String data) {
		try {
			String newKey = stripCommentFromKey(data);

			List<String> lines = new ArrayList<String>();
			File keystore = getKeystore(username);
			if (keystore.exists()) {
				for (String entry : Files.readLines(keystore, Charsets.ISO_8859_1)) {
					String line = entry.trim();
					if (line.length() == 0) {
						// keep blanks
						lines.add(entry);
						continue;
					}
					if (line.charAt(0) == '#') {
						// keep comments
						lines.add(entry);
						continue;
					}

					// only add keys that do not match the new key
					String oldKey = stripCommentFromKey(line);
					if (!newKey.equals(oldKey)) {
						lines.add(entry);
					}
				}
			}

			// add new key
			lines.add(data);

			// write keystore
			String content = Joiner.on("\n").join(lines).trim().concat("\n");
			Files.write(content, keystore, Charsets.ISO_8859_1);

			lastModifieds.remove(keystore);
			keyCache.invalidate(username);
			return true;
		} catch (IOException e) {
			throw new RuntimeException("Cannot add ssh key", e);
		}
	}

	/**
	 * Removes a key from the keystore.
	 */
	@Override
	public boolean removeKey(String username, String data) {
		try {
			String rmKey = stripCommentFromKey(data);

			File keystore = getKeystore(username);
			if (keystore.exists()) {
				List<String> lines = new ArrayList<String>();
				for (String entry : Files.readLines(keystore, Charsets.ISO_8859_1)) {
					String line = entry.trim();
					if (line.length() == 0) {
						// keep blanks
						lines.add(entry);
						continue;
					}
					if (line.charAt(0) == '#') {
						// keep comments
						lines.add(entry);
						continue;
					}

					// only include keys that are NOT rmKey
					String oldKey = stripCommentFromKey(line);
					if (!rmKey.equals(oldKey)) {
						lines.add(entry);
					}
				}
				if (lines.isEmpty()) {
					keystore.delete();
				} else {
					// write keystore
					String content = Joiner.on("\n").join(lines).trim().concat("\n");
					Files.write(content, keystore, Charsets.ISO_8859_1);
				}

				lastModifieds.remove(keystore);
				keyCache.invalidate(username);
				return true;
			}
		} catch (IOException e) {
			throw new RuntimeException("Cannot remove ssh key", e);
		}
		return false;
	}

	@Override
	public boolean removeAllKeys(String username) {
		File keystore = getKeystore(username);
		if (keystore.delete()) {
			lastModifieds.remove(keystore);
			keyCache.invalidate(username);
			return true;
		}
		return false;
	}

	protected File getKeystore(String username) {
		File dir = runtimeManager.getFileOrFolder(Keys.git.sshKeysFolder, "${baseFolder}/ssh");
		dir.mkdirs();
		File keys = new File(dir, username + ".keys");
		return keys;
	}

	/* Strips the comment from the key data and eliminates whitespace diffs */
	protected String stripCommentFromKey(String data) {
		String [] cols = data.split(" ");
		String key = Joiner.on(" ").join(cols[0], cols[1]);
		return key;
	}
}
