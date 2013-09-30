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

import java.util.Date;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Config.SectionParser;

import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;
import com.gitblit.utils.X509Utils.X509Metadata;

/**
 * Certificate config file parser.
 *
 * @author James Moger
 */
public class NewCertificateConfig {
		public static final SectionParser<NewCertificateConfig> KEY = new SectionParser<NewCertificateConfig>() {
			@Override
			public NewCertificateConfig parse(final Config cfg) {
				return new NewCertificateConfig(cfg);
			}
		};

		public String OU;
		public String O;
		public String L;
		public String ST;
		public String C;

		public int duration;

		private NewCertificateConfig(final Config c) {
			duration = c.getInt("new",  null, "duration", 0);
			OU = c.getString("new", null, "organizationalUnit");
			O = c.getString("new", null, "organization");
			L = c.getString("new", null, "locality");
			ST = c.getString("new", null, "stateProvince");
			C = c.getString("new", null, "countryCode");
		}

		public void update(X509Metadata metadata) {
			update(metadata, "OU", OU);
			update(metadata, "O", O);
			update(metadata, "L", L);
			update(metadata, "ST", ST);
			update(metadata, "C", C);
			if (duration > 0) {
				metadata.notAfter = new Date(System.currentTimeMillis() + duration*TimeUtils.ONEDAY);
			}
		}

		private void update(X509Metadata metadata, String oid, String value) {
			if (!StringUtils.isEmpty(value)) {
				metadata.oids.put(oid, value);
			}
		}

		public void store(Config c, X509Metadata metadata) {
			store(c, "new", "organizationalUnit", metadata.getOID("OU", null));
			store(c, "new", "organization", metadata.getOID("O", null));
			store(c, "new", "locality", metadata.getOID("L", null));
			store(c, "new", "stateProvince", metadata.getOID("ST", null));
			store(c, "new", "countryCode", metadata.getOID("C", null));
			if (duration <= 0) {
				c.unset("new", null, "duration");
			} else {
				c.setInt("new", null, "duration", duration);
			}
		}

		private void store(Config c, String section, String name, String value) {
			if (StringUtils.isEmpty(value)) {
				c.unset(section, null, name);
			} else {
				c.setString(section, null, name, value);
			}
		}
	}