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
package com.gitblit.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Server settings represents the settings of the Gitblit server including all
 * setting metadata such as name, current value, default value, description, and
 * directives. It is a model class for serialization and presentation, but not
 * for persistence.
 *
 * @author James Moger
 */
public class ServerSettings implements Serializable {

	private final Map<String, SettingModel> settings;

	private static final long serialVersionUID = 1L;

	public List<String> pushScripts;

	public ServerSettings() {
		settings = new TreeMap<String, SettingModel>();
	}

	public List<String> getKeys() {
		return new ArrayList<String>(settings.keySet());
	}

	public void add(SettingModel setting) {
		if (setting != null) {
			settings.put(setting.name, setting);
		}
	}

	public SettingModel get(String key) {
		return settings.get(key);
	}

	public boolean hasKey(String key) {
		return settings.containsKey(key);
	}

	public SettingModel remove(String key) {
		return settings.remove(key);
	}
}
