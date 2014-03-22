package com.gitblit.transport.ssh;

import java.io.Serializable;
import java.security.PublicKey;

import org.apache.commons.codec.binary.Base64;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.util.Buffer;
import org.eclipse.jgit.lib.Constants;

import com.gitblit.utils.StringUtils;

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

	public SshKey(String data) {
		this.rawData = data;
	}

	public SshKey(PublicKey key) {
		this.publicKey = key;
		this.comment = "";
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
				e.printStackTrace();
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
			// TODO append the keysize
			int keySize = 0;
			if (keySize > 0) {
				sb.append(keySize).append(' ');
			}

			// append the key hash as colon-separated pairs
			String hash;
			if (rawData != null) {
				final String[] parts = rawData.split(" ", 3);
				final byte [] bin = Base64.decodeBase64(Constants.encodeASCII(parts[1]));
				hash = StringUtils.getMD5(bin);
			} else {
				// TODO get hash from publickey
				hash = "todo";
			}
			for (int i = 0; i < hash.length(); i += 2) {
				sb.append(hash.charAt(i)).append(hash.charAt(i + 1)).append(':');
			}
			sb.setLength(sb.length() - 1);

			// append the comment
			String c = getComment();
			if (!StringUtils.isEmpty(c)) {
				sb.append(' ');
				sb.append(c);
			}

			// append the algorithm
			String alg = getAlgorithm();
			if (!StringUtils.isEmpty(alg)) {
				sb.append(" (").append(alg).append(")");
			}
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
		return getFingerprint();
	}
}
