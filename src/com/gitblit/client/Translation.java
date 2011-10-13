/*
 * Copyright 2011 gitblit.com.
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
package com.gitblit.client;

import java.io.InputStream;
import java.util.Properties;

public class Translation {

	private final static Properties translation;

	static {
		translation = new Properties();
		InputStream is = null;
		try {
			is = Translation.class.getResource("/com/gitblit/wicket/GitBlitWebApp.properties")
					.openStream();
		} catch (Throwable t) {
			try {
				is = Translation.class.getResource("/GitBlitWebApp.properties").openStream();
			} catch (Throwable x) {
			}
		}
		if (is != null) {
			try {
				translation.load(is);
			} catch (Throwable t) {

			} finally {
				try {
					is.close();
				} catch (Throwable t) {
				}
			}
		}
	}

	public static String get(String key) {
		if (translation.containsKey(key)) {
			return translation.getProperty(key).trim();
		}
		return key;
	}
}
