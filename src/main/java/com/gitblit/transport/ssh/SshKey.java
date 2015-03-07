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

import java.io.Serializable;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.util.Buffer;
import org.eclipse.jgit.lib.Constants;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.utils.StringUtils;
import com.google.common.base.Joiner;

/**
 * Class that encapsulates a public SSH key and it's metadata.
 *
 * @author James Moger
 *
 */
public class SshKey implements Serializable {

	private static final long serialVersionUID = 1L;

	private String rawData;

	private PublicKey publicKey;

	private String comment;

	private String fingerprint;

	private String toString;

	private AccessPermission permission;

	public SshKey(String data) {
		// strip out line breaks (issue-571)
		this.rawData = Joiner.on("").join(data.replace("\r\n", "\n").split("\n"));
		this.permission = AccessPermission.PUSH;
	}

	public SshKey(PublicKey key) {
		this.publicKey = key;
		this.comment = "";
		this.permission = AccessPermission.PUSH;
	}

	public PublicKey getPublicKey() {
		if (publicKey == null && rawData != null) {
			// instantiate the public key from the raw key data
			final String[] parts = rawData.split(" ", 3);
			if (comment == null && parts.length == 3) {
				comment = parts[2];
			}
			final byte[] bin = Base64.decodeBase64(Constants.encodeASCII(parts[1]));
			try {
				publicKey = new Buffer(bin).getRawPublicKey();
			} catch (SshException e) {
				throw new RuntimeException(e);
			}
		}
		return publicKey;
	}

	public String getAlgorithm() {
		return getPublicKey().getAlgorithm();
	}

	public String getComment() {
		if (comment == null && rawData != null) {
			// extract comment from the raw data
			final String[] parts = rawData.split(" ", 3);
			if (parts.length == 3) {
				comment = parts[2];
			}
		}
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
		if (rawData != null) {
			rawData = null;
		}
	}

	/**
	 * Returns true if this key may be used to clone or fetch.
	 *
	 * @return true if this key can be used to clone or fetch
	 */
	public boolean canClone() {
		return permission.atLeast(AccessPermission.CLONE);
	}

	/**
	 * Returns true if this key may be used to push changes.
	 *
	 * @return true if this key can be used to push changes
	 */
	public boolean canPush() {
		return permission.atLeast(AccessPermission.PUSH);
	}

	/**
	 * Returns the access permission for the key.
	 *
	 * @return the access permission for the key
	 */
	public AccessPermission getPermission() {
		return permission;
	}

	/**
	 * Control the access permission assigned to this key.
	 *
	 * @param value
	 */
	public void setPermission(AccessPermission value) throws IllegalArgumentException {
		List<AccessPermission> permitted = Arrays.asList(AccessPermission.SSHPERMISSIONS);
		if (!permitted.contains(value)) {
			throw new IllegalArgumentException("Illegal SSH public key permission specified: " + value);
		}
		this.permission = value;
	}

	public String getRawData() {
		if (rawData == null && publicKey != null) {
			// build the raw data manually from the public key
			Buffer buf = new Buffer();

			// 1: identify the algorithm
			buf.putRawPublicKey(publicKey);
			String alg = buf.getString();

			// 2: encode the key
			buf.clear();
			buf.putPublicKey(publicKey);
			String b64 = Base64.encodeBase64String(buf.getBytes());

			String c = getComment();
			rawData = alg + " " + b64 + (StringUtils.isEmpty(c) ? "" : (" " + c));
		}
		return rawData;
	}

	public String getFingerprint() {
		if (fingerprint == null) {
			StringBuilder sb = new StringBuilder();
			// append the key hash as colon-separated pairs
			String hash;
			if (rawData != null) {
				final String[] parts = rawData.split(" ", 3);
				final byte [] bin = Base64.decodeBase64(Constants.encodeASCII(parts[1]));
				hash = StringUtils.getMD5(bin);
			} else {
				// TODO calculate the correct hash from a PublicKey instance
				hash = StringUtils.getMD5(getPublicKey().getEncoded());
			}
			for (int i = 0; i < hash.length(); i += 2) {
				sb.append(hash.charAt(i)).append(hash.charAt(i + 1)).append(':');
			}
			sb.setLength(sb.length() - 1);
			fingerprint = sb.toString();
		}
		return fingerprint;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof PublicKey) {
			return getPublicKey().equals(o);
		} else if (o instanceof SshKey) {
			return getPublicKey().equals(((SshKey) o).getPublicKey());
		}
		return false;
	}

	@Override
	public int hashCode() {
		return getPublicKey().hashCode();
	}

	@Override
	public String toString() {
		if (toString == null) {
			StringBuilder sb = new StringBuilder();
			// TODO append the keysize
			int keySize = 0;
			if (keySize > 0) {
				sb.append(keySize).append(' ');
			}
			// append fingerprint
			sb.append(' ');
			sb.append(getFingerprint());
			// append the comment
			String c = getComment();
			if (!StringUtils.isEmpty(c)) {
				sb.append(' ');
				sb.append(c);
			}
			// append algorithm
			String alg = getAlgorithm();
			if (!StringUtils.isEmpty(alg)) {
				sb.append(" (").append(alg).append(")");
			}
			toString = sb.toString();
		}
		return toString;
	}
}
