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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.utils.StringUtils;

/**
 * Base class for stored settings implementations.
 *
 * @author James Moger
 *
 */
public abstract class IStoredSettings {

	protected final Logger logger;

	protected final Properties overrides = new Properties();

	protected final Set<String> removals = new TreeSet<String>();

	public IStoredSettings(Class<? extends IStoredSettings> clazz) {
		logger = LoggerFactory.getLogger(clazz);
	}

	protected abstract Properties read();

	private Properties getSettings() {
		Properties props = read();
		props.putAll(overrides);
		return props;
	}

	/**
	 * Returns the list of keys whose name starts with the specified prefix. If
	 * the prefix is null or empty, all key names are returned.
	 *
	 * @param startingWith
	 * @return list of keys
	 */
	public List<String> getAllKeys(String startingWith) {
		List<String> keys = new ArrayList<String>();
		Properties props = getSettings();
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

	/**
	 * Returns the boolean value for the specified key. If the key does not
	 * exist or the value for the key can not be interpreted as a boolean, the
	 * defaultValue is returned.
	 *
	 * @param key
	 * @param defaultValue
	 * @return key value or defaultValue
	 */
	public boolean getBoolean(String name, boolean defaultValue) {
		Properties props = getSettings();
		if (props.containsKey(name)) {
			String value = props.getProperty(name);
			if (!StringUtils.isEmpty(value)) {
				return Boolean.parseBoolean(value.trim());
			}
		}
		return defaultValue;
	}

	/**
	 * Returns the integer value for the specified key. If the key does not
	 * exist or the value for the key can not be interpreted as an integer, the
	 * defaultValue is returned.
	 *
	 * @param key
	 * @param defaultValue
	 * @return key value or defaultValue
	 */
	public int getInteger(String name, int defaultValue) {
		Properties props = getSettings();
		if (props.containsKey(name)) {
			try {
				String value = props.getProperty(name);
				if (!StringUtils.isEmpty(value)) {
					return Integer.parseInt(value.trim());
				}
			} catch (NumberFormatException e) {
				logger.warn("Failed to parse integer for " + name + " using default of "
						+ defaultValue);
			}
		}
		return defaultValue;
	}

	/**
	 * Returns the long value for the specified key. If the key does not
	 * exist or the value for the key can not be interpreted as an long, the
	 * defaultValue is returned.
	 *
	 * @param key
	 * @param defaultValue
	 * @return key value or defaultValue
	 */
	public long getLong(String name, long defaultValue) {
		Properties props = getSettings();
		if (props.containsKey(name)) {
			try {
				String value = props.getProperty(name);
				if (!StringUtils.isEmpty(value)) {
					return Long.parseLong(value.trim());
				}
			} catch (NumberFormatException e) {
				logger.warn("Failed to parse long for " + name + " using default of "
						+ defaultValue);
			}
		}
		return defaultValue;
	}

	/**
	 * Returns an int filesize from a string value such as 50m or 50mb
	 * @param name
	 * @param defaultValue
	 * @return an int filesize or defaultValue if the key does not exist or can
	 *         not be parsed
	 */
	public int getFilesize(String name, int defaultValue) {
		String val = getString(name, null);
		if (StringUtils.isEmpty(val)) {
			return defaultValue;
		}
		return com.gitblit.utils.FileUtils.convertSizeToInt(val, defaultValue);
	}

	/**
	 * Returns an long filesize from a string value such as 50m or 50mb
	 * @param n
	 * @param defaultValue
	 * @return a long filesize or defaultValue if the key does not exist or can
	 *         not be parsed
	 */
	public long getFilesize(String key, long defaultValue) {
		String val = getString(key, null);
		if (StringUtils.isEmpty(val)) {
			return defaultValue;
		}
		return com.gitblit.utils.FileUtils.convertSizeToLong(val, defaultValue);
	}

	/**
	 * Returns the char value for the specified key. If the key does not exist
	 * or the value for the key can not be interpreted as a char, the
	 * defaultValue is returned.
	 *
	 * @param key
	 * @param defaultValue
	 * @return key value or defaultValue
	 */
	public char getChar(String name, char defaultValue) {
		Properties props = getSettings();
		if (props.containsKey(name)) {
			String value = props.getProperty(name);
			if (!StringUtils.isEmpty(value)) {
				return value.trim().charAt(0);
			}
		}
		return defaultValue;
	}

	/**
	 * Returns the string value for the specified key. If the key does not exist
	 * or the value for the key can not be interpreted as a string, the
	 * defaultValue is returned.
	 *
	 * @param key
	 * @param defaultValue
	 * @return key value or defaultValue
	 */
	public String getString(String name, String defaultValue) {
		Properties props = getSettings();
		if (props.containsKey(name)) {
			String value = props.getProperty(name);
			if (value != null) {
				return value.trim();
			}
		}
		return defaultValue;
	}

	/**
	 * Returns the string value for the specified key.  If the key does not
	 * exist an exception is thrown.
	 *
	 * @param key
	 * @return key value
	 */
	public String getRequiredString(String name) {
		Properties props = getSettings();
		if (props.containsKey(name)) {
			String value = props.getProperty(name);
			if (value != null) {
				return value.trim();
			}
		}
		throw new RuntimeException("Property (" + name + ") does not exist");
	}

	/**
	 * Returns a list of space-separated strings from the specified key.
	 *
	 * @param name
	 * @return list of strings
	 */
	public List<String> getStrings(String name) {
		return getStrings(name, " ");
	}

	/**
	 * Returns a list of strings from the specified key using the specified
	 * string separator.
	 *
	 * @param name
	 * @param separator
	 * @return list of strings
	 */
	public List<String> getStrings(String name, String separator) {
		List<String> strings = new ArrayList<String>();
		Properties props = getSettings();
		if (props.containsKey(name)) {
			String value = props.getProperty(name);
			strings = StringUtils.getStringsFromValue(value, separator);
		}
		return strings;
	}

	/**
	 * Returns a list of space-separated integers from the specified key.
	 *
	 * @param name
	 * @return list of strings
	 */
	public List<Integer> getIntegers(String name) {
		return getIntegers(name, " ");
	}

	/**
	 * Returns a list of integers from the specified key using the specified
	 * string separator.
	 *
	 * @param name
	 * @param separator
	 * @return list of integers
	 */
	public List<Integer> getIntegers(String name, String separator) {
		List<Integer> ints = new ArrayList<Integer>();
		Properties props = getSettings();
		if (props.containsKey(name)) {
			String value = props.getProperty(name);
			List<String> strings = StringUtils.getStringsFromValue(value, separator);
			for (String str : strings) {
				try {
					int i = Integer.parseInt(str);
					ints.add(i);
				} catch (NumberFormatException e) {
				}
			}
		}
		return ints;
	}

	/**
	 * Returns a map of strings from the specified key.
	 *
	 * @param name
	 * @return map of string, string
	 */
	public Map<String, String> getMap(String name) {
		Map<String, String> map = new LinkedHashMap<String, String>();
		for (String string : getStrings(name)) {
			String[] kvp = string.split("=", 2);
			String key = kvp[0];
			String value = kvp[1];
			map.put(key,  value);
		}
		return map;
	}

	/**
	 * Override the specified key with the specified value.
	 *
	 * @param key
	 * @param value
	 */
	public void overrideSetting(String key, String value) {
		overrides.put(key, value);
	}

	/**
	 * Override the specified key with the specified value.
	 *
	 * @param key
	 * @param value
	 */
	public void overrideSetting(String key, int value) {
		overrides.put(key, "" + value);
	}

	/**
	 * Override the specified key with the specified value.
	 *
	 * @param key
	 * @param value
	 */
	public void overrideSetting(String key, boolean value) {
		overrides.put(key, "" + value);
	}

	/**
	 * Tests for the existence of a setting.
	 *
	 * @param key
	 * @return true if the setting exists
	 */
	public boolean hasSettings(String key) {
		return getString(key, null) != null;
	}

	/**
	 * Remove a setting.
	 *
	 * @param key
	 */
	public void removeSetting(String key) {
		getSettings().remove(key);
		overrides.remove(key);
		removals.add(key);
	}

	/**
	 * Saves the current settings.
	 *
	 * @param map
	 */
	public abstract boolean saveSettings();

	/**
	 * Updates the values for the specified keys and persists the entire
	 * configuration file.
	 *
	 * @param map
	 *            of key, value pairs
	 * @return true if successful
	 */
	public abstract boolean saveSettings(Map<String, String> updatedSettings);

	/**
	 * Merge all settings from the settings parameter into this instance.
	 *
	 * @param settings
	 */
	public void merge(IStoredSettings settings) {
		getSettings().putAll(settings.getSettings());
		overrides.putAll(settings.overrides);
	}
}