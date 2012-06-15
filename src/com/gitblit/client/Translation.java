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

import java.util.MissingResourceException;
import java.util.ResourceBundle;

import com.gitblit.utils.TimeUtils;

/**
 * Loads the Gitblit language resource file.
 * 
 * @author James Moger
 * 
 */
public class Translation {

	private final static ResourceBundle translation;
	
	private final static TimeUtils timeUtils;

	static {
		ResourceBundle bundle;
		try {
			// development location
			bundle = ResourceBundle.getBundle("com/gitblit/wicket/GitBlitWebApp");
		} catch (MissingResourceException e) {
			// runtime location
			bundle = ResourceBundle.getBundle("GitBlitWebApp");
		}
		translation = bundle;
		
		timeUtils = new TimeUtils(translation);
	}

	public static String get(String key) {
		if (translation.containsKey(key)) {
			return translation.getString(key).trim();
		}
		return key;
	}
	
	public static TimeUtils getTimeUtils() {
		return timeUtils;
	}
}
