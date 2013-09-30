/*
 * Copyright 2013 gitblit.com.
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
package com.gitblit.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.gitblit.utils.StringUtils;

/**
 * User preferences.
 *
 * @author James Moger
 *
 */
public class UserPreferences implements Serializable {

	private static final long serialVersionUID = 1L;

	public final String username;

	public String locale;

	private final Map<String, UserRepositoryPreferences> repositoryPreferences = new TreeMap<String, UserRepositoryPreferences>();

	public UserPreferences(String username) {
		this.username = username;
	}

	public Locale getLocale() {
		if (StringUtils.isEmpty(locale)) {
			return null;
		}
		return new Locale(locale);
	}

	public UserRepositoryPreferences getRepositoryPreferences(String repositoryName) {
		String key = repositoryName.toLowerCase();
		if (!repositoryPreferences.containsKey(key)) {
			// default preferences
			UserRepositoryPreferences prefs = new UserRepositoryPreferences();
			prefs.username = username;
			prefs.repositoryName = repositoryName;
			repositoryPreferences.put(key, prefs);
		}
		return repositoryPreferences.get(key);
	}

	public void setRepositoryPreferences(UserRepositoryPreferences pref) {
		repositoryPreferences.put(pref.repositoryName.toLowerCase(), pref);
	}

	public boolean isStarredRepository(String repository) {
		if (repositoryPreferences == null) {
			return false;
		}
		String key = repository.toLowerCase();
		if (repositoryPreferences.containsKey(key)) {
			UserRepositoryPreferences pref = repositoryPreferences.get(key);
			return pref.starred;
		}
		return false;
	}

	public List<String> getStarredRepositories() {
		List<String> list = new ArrayList<String>();
		for (UserRepositoryPreferences prefs : repositoryPreferences.values()) {
			if (prefs.starred) {
				list.add(prefs.repositoryName);
			}
		}
		Collections.sort(list);
		return list;
	}
}
