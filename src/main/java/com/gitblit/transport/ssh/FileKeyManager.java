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

import org.apache.commons.codec.binary.Base64;
import org.apache.sshd.common.util.Buffer;
import org.eclipse.jgit.lib.Constants;

import com.gitblit.Keys;
import com.gitblit.manager.IRuntimeManager;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.Files;

/**
 * Manages SSH keys on the filesystem.
 *
 * @author James Moger
 *
 */
public class FileKeyManager implements IKeyManager {

	protected final IRuntimeManager runtimeManager;

	public FileKeyManager(IRuntimeManager runtimeManager) {
		this.runtimeManager = runtimeManager;
	}

	@Override
	public String toString() {
		File dir = runtimeManager.getFileOrFolder(Keys.git.sshKeysFolder, "${baseFolder}/ssh");
		return MessageFormat.format("{0} ({1})", getClass().getSimpleName(), dir);
	}

	@Override
	public FileKeyManager start() {
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
	public List<PublicKey> getKeys(String username) {
		try {
			File keys = getKeystore(username);
			if (!keys.exists()) {
				return null;
			}
			if (keys.exists()) {
				List<PublicKey> list = new ArrayList<PublicKey>();
				for (String entry : Files.readLines(keys, Charsets.ISO_8859_1)) {
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
				return true;
			}
		} catch (IOException e) {
			throw new RuntimeException("Cannot remove ssh key", e);
		}
		return false;
	}

	@Override
	public boolean removeAllKeys(String username) {
		return getKeystore(username).delete();
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
