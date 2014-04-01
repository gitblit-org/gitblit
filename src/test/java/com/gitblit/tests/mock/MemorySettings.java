 /*
 * Copyright 2012 John Crygier
 * Copyright 2012 gitblit.com
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
package com.gitblit.tests.mock;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.gitblit.IStoredSettings;

public class MemorySettings extends IStoredSettings {

	private Map<String, Object> backingMap;

	public MemorySettings() {
		this(new HashMap<String, Object>());
	}

	public MemorySettings(Map<String, Object> backingMap) {
		super(MemorySettings.class);
		this.backingMap = backingMap;
	}

	@Override
	protected Properties read() {
		Properties props = new Properties();
		props.putAll(backingMap);

		return props;
	}

	public void put(String key, Object value) {
		backingMap.put(key, value);
	}

	@Override
	public boolean saveSettings() {
		return false;
	}

	@Override
	public boolean saveSettings(Map<String, String> updatedSettings) {
		return false;
	}

}
