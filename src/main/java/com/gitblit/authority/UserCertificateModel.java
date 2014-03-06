/*
 * Copyright 2012 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.authority;

import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.eclipse.jgit.lib.Config;

import com.gitblit.Constants;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;
import com.gitblit.utils.X509Utils.RevocationReason;

public class UserCertificateModel implements Comparable<UserCertificateModel> {
		public UserModel user;
		public Date expires;
		public List<X509Certificate> certs;
		public List<String> revoked;
		public String notes;

		public UserCertificateModel(UserModel user) {
			this.user = user;
		}

		public void update(Config config) {
			if (expires == null) {
				config.unset("user",  user.username, "expires");
			} else {
				SimpleDateFormat df = new SimpleDateFormat(Constants.ISO8601);
				config.setString("user", user.username, "expires", df.format(expires));
			}
			if (StringUtils.isEmpty(notes)) {
				config.unset("user",  user.username, "notes");
			} else {
				config.setString("user", user.username, "notes", notes);
			}
			if (ArrayUtils.isEmpty(revoked)) {
				config.unset("user",  user.username, "revoked");
			} else {
				config.setStringList("user", user.username, "revoked", revoked);
			}
		}

		@Override
		public int compareTo(UserCertificateModel o) {
			return user.compareTo(o.user);
		}

		public void revoke(BigInteger serial, RevocationReason reason) {
			if (revoked == null) {
				revoked = new ArrayList<String>();
			}
			revoked.add(serial.toString() + ":" + reason.ordinal());
			expires = null;
			for (X509Certificate cert : certs) {
				if (!isRevoked(cert.getSerialNumber())) {
					if (!isExpired(cert.getNotAfter())) {
						if (expires == null || cert.getNotAfter().after(expires)) {
							expires = cert.getNotAfter();
						}
					}
				}
			}
		}

		public boolean isRevoked(BigInteger serial) {
			return isRevoked(serial.toString());
		}

		public boolean isRevoked(String serial) {
			if (ArrayUtils.isEmpty(revoked)) {
				return false;
			}
			String sn = serial + ":";
			for (String s : revoked) {
				if (s.startsWith(sn)) {
					return true;
				}
			}
			return false;
		}

		public RevocationReason getRevocationReason(BigInteger serial) {
			try {
				String sn = serial + ":";
				for (String s : revoked) {
					if (s.startsWith(sn)) {
						String r = s.substring(sn.length());
						int i = Integer.parseInt(r);
						return RevocationReason.values()[i];
					}
				}
			} catch (Exception e) {
			}
			return RevocationReason.unspecified;
		}

		public CertificateStatus getStatus() {
			if (expires == null) {
				return CertificateStatus.unknown;
			} else if (isExpired(expires)) {
				return CertificateStatus.expired;
			} else if (isExpiring(expires)) {
				return CertificateStatus.expiring;
			}
			return CertificateStatus.ok;
		}

		public boolean hasExpired() {
			return expires != null && isExpiring(expires);
		}

		public CertificateStatus getStatus(X509Certificate cert) {
			if (isRevoked(cert.getSerialNumber())) {
				return CertificateStatus.revoked;
			} else if (isExpired(cert.getNotAfter())) {
				return CertificateStatus.expired;
			} else if (isExpiring(cert.getNotAfter())) {
				return CertificateStatus.expiring;
			}
			return CertificateStatus.ok;
		}

		private boolean isExpiring(Date date) {
			return (date.getTime() - System.currentTimeMillis()) <= TimeUtils.ONEDAY * 30;
		}

		private boolean isExpired(Date date) {
			return date.getTime() < System.currentTimeMillis();
		}
	}