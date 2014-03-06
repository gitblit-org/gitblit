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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Config.SectionParser;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.models.UserModel;

/**
 * User certificate config section parser.
 *
 * @author James Moger
 */
public class UserCertificateConfig {
	public static final SectionParser<UserCertificateConfig> KEY = new SectionParser<UserCertificateConfig>() {
		@Override
		public UserCertificateConfig parse(final Config cfg) {
			return new UserCertificateConfig(cfg);
		}
	};

	public final List<UserCertificateModel> list;

	private UserCertificateConfig(final Config c) {
		SimpleDateFormat df = new SimpleDateFormat(Constants.ISO8601);
		list = new ArrayList<UserCertificateModel>();
		for (String username : c.getSubsections("user")) {
			UserCertificateModel uc = new UserCertificateModel(new UserModel(username));
			try {
				uc.expires = df.parse(c.getString("user", username, "expires"));
			} catch (ParseException e) {
				LoggerFactory.getLogger(UserCertificateConfig.class).error("Failed to parse date!", e);
			} catch (NullPointerException e) {
			}
			uc.notes = c.getString("user", username, "notes");
			uc.revoked = new ArrayList<String>(Arrays.asList(c.getStringList("user", username, "revoked")));
			list.add(uc);
		}
	}

	public UserCertificateModel getUserCertificateModel(String username) {
		for (UserCertificateModel ucm : list) {
			if (ucm.user.username.equalsIgnoreCase(username)) {
				return ucm;
			}
		}
		return null;
	}
}