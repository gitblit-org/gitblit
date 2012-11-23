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
			public NewCertificateConfig parse(final Config cfg) {
				return new NewCertificateConfig(cfg);
			}
		};

		public final String OU;
		public final String O;
		public final String L;
		public final String ST;
		public final String C;
		
		public final int duration;
		
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
	}