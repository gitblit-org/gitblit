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
package com.gitblit;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.utils.StringUtils;

public abstract class IStoredSettings {

	protected final Logger logger;

	public IStoredSettings(Class<? extends IStoredSettings> clazz) {
		logger = LoggerFactory.getLogger(clazz);
	}

	protected abstract Properties read();

	public List<String> getAllKeys(String startingWith) {
		List<String> keys = new ArrayList<String>();
		Properties props = read();
		if (StringUtils.isEmpty(startingWith)) {
			keys.addAll(props.stringPropertyNames());
		} else {
			startingWith = startingWith.toLowerCase();
			for (Object o : props.keySet()) {
				String key = o.toString();
				if (key.toLowerCase().startsWith(startingWith)) {
					keys.add(key);
				}
			}
		}
		return keys;
	}

	public boolean getBoolean(String name, boolean defaultValue) {
		Properties props = read();
		if (props.containsKey(name)) {
			String value = props.getProperty(name);
			if (!StringUtils.isEmpty(value)) {
				return Boolean.parseBoolean(value);
			}
		}
		return defaultValue;
	}

	public int getInteger(String name, int defaultValue) {
		Properties props = read();
		if (props.containsKey(name)) {
			try {
				String value = props.getProperty(name);
				if (!StringUtils.isEmpty(value)) {
					return Integer.parseInt(value);
				}
			} catch (NumberFormatException e) {
				logger.warn("Failed to parse integer for " + name + " using default of "
						+ defaultValue);
			}
		}
		return defaultValue;
	}

	public String getString(String name, String defaultValue) {
		Properties props = read();
		if (props.containsKey(name)) {
			String value = props.getProperty(name);
			if (value != null) {
				return value;
			}
		}
		return defaultValue;
	}

	public List<String> getStrings(String name) {
		return getStrings(name, " ");
	}

	public List<String> getStrings(String name, String separator) {
		List<String> strings = new ArrayList<String>();
		Properties props = read();
		if (props.containsKey(name)) {
			String value = props.getProperty(name);
			strings = StringUtils.getStringsFromValue(value, separator);
		}
		return strings;
	}
}