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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gitblit.utils.StringUtils;

/**
 * SettingModel represents a setting and all its metadata: name, current value,
 * default value, description, and directives.
 *
 * @author James Moger
 */
public class SettingModel implements Serializable {

	public static final String SPACE_DELIMITED = "SPACE-DELIMITED";

	public static final String CASE_SENSITIVE = "CASE-SENSITIVE";

	public static final String RESTART_REQUIRED = "RESTART REQUIRED";

	public static final String SINCE = "SINCE";

	public String name;
	public volatile String currentValue;
	public String defaultValue;
	public String description;
	public String since;
	public boolean caseSensitive;
	public boolean restartRequired;
	public boolean spaceDelimited;

	private static final long serialVersionUID = 1L;

	public SettingModel() {
	}

	/**
	 * Returns true if the current value is the default value.
	 *
	 * @return true if current value is the default value
	 */
	public boolean isDefaultValue() {
		return (currentValue != null && currentValue.equals(defaultValue))
				|| currentValue.trim().length() == 0;
	}

	/**
	 * Returns the boolean value for the currentValue. If the currentValue can
	 * not be interpreted as a boolean, the defaultValue is returned.
	 *
	 * @param defaultValue
	 * @return key value or defaultValue
	 */
	public boolean getBoolean(boolean defaultValue) {
		if (!StringUtils.isEmpty(currentValue)) {
			return Boolean.parseBoolean(currentValue.trim());
		}
		return defaultValue;
	}

	/**
	 * Returns the integer value for the currentValue. If the currentValue can
	 * not be interpreted as an integer, the defaultValue is returned.
	 *
	 * @param defaultValue
	 * @return key value or defaultValue
	 */
	public int getInteger(int defaultValue) {
		try {
			if (!StringUtils.isEmpty(currentValue)) {
				return Integer.parseInt(currentValue.trim());
			}
		} catch (NumberFormatException e) {
		}
		return defaultValue;
	}

	/**
	 * Returns the char value for currentValue. If the currentValue can not be
	 * interpreted as a char, the defaultValue is returned.
	 *
	 * @param defaultValue
	 * @return key value or defaultValue
	 */
	public char getChar(char defaultValue) {
		if (!StringUtils.isEmpty(currentValue)) {
			return currentValue.trim().charAt(0);
		}
		return defaultValue;
	}

	/**
	 * Returns the string value for currentValue. If the currentValue is null,
	 * the defaultValue is returned.
	 *
	 * @param defaultValue
	 * @return key value or defaultValue
	 */
	public String getString(String defaultValue) {
		if (currentValue != null) {
			return currentValue.trim();
		}
		return defaultValue;
	}

	/**
	 * Returns a list of space-separated strings from the specified key.
	 *
	 * @return list of strings
	 */
	public List<String> getStrings() {
		return getStrings(" ");
	}

	/**
	 * Returns a list of strings from the currentValue using the specified
	 * string separator.
	 *
	 * @param separator
	 * @return list of strings
	 */
	public List<String> getStrings(String separator) {
		List<String> strings = new ArrayList<String>();
		strings = StringUtils.getStringsFromValue(currentValue, separator);
		return strings;
	}

	/**
	 * Returns a map of strings from the current value.
	 *
	 * @return map of string, string
	 */
	public Map<String, String> getMap() {
		Map<String, String> map = new LinkedHashMap<String, String>();
		for (String string : getStrings()) {
			String[] kvp = string.split("=", 2);
			String key = kvp[0];
			String value = kvp[1];
			map.put(key,  value);
		}
		return map;
	}
}
