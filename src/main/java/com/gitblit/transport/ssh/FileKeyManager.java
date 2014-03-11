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
import com.gitblit.utils.FileUtils;
import com.google.common.base.Charsets;
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
				String str = Files.toString(keys, Charsets.ISO_8859_1);
				String [] entries = str.split("\n");
				List<PublicKey> list = new ArrayList<PublicKey>();
				for (String entry : entries) {
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

	@Override
	public boolean addKey(String username, String data) {
		try {
			File keys = getKeystore(username);
			Files.append(data + '\n', keys, Charsets.ISO_8859_1);
			return true;
		} catch (IOException e) {
			throw new RuntimeException("Cannot add ssh key", e);
		}
	}
	
	@Override
	public boolean removeKey(String username, String data) {
		try {
			File keystore = getKeystore(username);
			if (keystore.exists()) {
				String str = Files.toString(keystore, Charsets.ISO_8859_1);
				List<String> keep = new ArrayList<String>();
				String [] entries = str.split("\n");
				for (String entry : entries) {
					if (entry.trim().length() == 0) {
						// keep blanks
						keep.add(entry);
						continue;
					}
					if (entry.charAt(0) == '#') {
						// keep comments
						keep.add(entry);
						continue;
					}
					final String[] parts = entry.split(" ");
					if (!parts[1].equals(data)) {
						keep.add(entry);
					}
				}
				return true;
			}
		} catch (IOException e) {
			throw new RuntimeException("Cannot remove ssh key", e);
		}
		return false;
	}

	@Override
	public boolean removeAll(String username) {
		return FileUtils.delete(getKeystore(username));
	}

	protected File getKeystore(String username) {
		File dir = runtimeManager.getFileOrFolder(Keys.git.sshKeysFolder, "${baseFolder}/ssh");
		dir.mkdirs();
		File keys = new File(dir, username + ".keys");
		return keys;
	}
}
