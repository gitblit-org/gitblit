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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads GitBlit settings file.
 * 
 */
public class FileSettings implements IStoredSettings {

	private final Logger logger = LoggerFactory.getLogger(FileSettings.class);

	private final File propertiesFile;

	private Properties properties = new Properties();

	private long lastread;

	public FileSettings(String file) {
		this.propertiesFile = new File(file);
	}

	@Override
	public List<String> getAllKeys(String startingWith) {
		startingWith = startingWith.toLowerCase();
		List<String> keys = new ArrayList<String>();
		Properties props = read();
		for (Object o : props.keySet()) {
			String key = o.toString().toLowerCase();
			if (key.startsWith(startingWith)) {
				keys.add(key);
			}
		}
		return keys;
	}

	@Override
	public boolean getBoolean(String name, boolean defaultValue) {
		Properties props = read();
		if (props.containsKey(name)) {
			try {
				String value = props.getProperty(name);
				if (value != null && value.trim().length() > 0) {
					return Boolean.parseBoolean(value);
				}
			} catch (Exception e) {
				logger.warn("No override setting for " + name + " using default of " + defaultValue);
			}
		}
		return defaultValue;
	}

	@Override
	public int getInteger(String name, int defaultValue) {
		Properties props = read();
		if (props.containsKey(name)) {
			try {
				String value = props.getProperty(name);
				if (value != null && value.trim().length() > 0) {
					return Integer.parseInt(value);
				}
			} catch (Exception e) {
				logger.warn("No override setting for " + name + " using default of " + defaultValue);
			}
		}
		return defaultValue;
	}

	@Override
	public String getString(String name, String defaultValue) {
		Properties props = read();
		if (props.containsKey(name)) {
			try {
				String value = props.getProperty(name);
				if (value != null) {
					return value;
				}
			} catch (Exception e) {
				logger.warn("No override setting for " + name + " using default of " + defaultValue);
			}
		}
		return defaultValue;
	}

	@Override
	public List<String> getStrings(String name) {
		return getStrings(name, " ");
	}

	@Override
	public List<String> getStringsFromValue(String value) {
		return getStringsFromValue(value, " ");
	}

	@Override
	public List<String> getStrings(String name, String separator) {
		List<String> strings = new ArrayList<String>();
		Properties props = read();
		if (props.containsKey(name)) {
			String value = props.getProperty(name);
			strings = getStringsFromValue(value, separator);
		}
		return strings;
	}

	@Override
	public List<String> getStringsFromValue(String value, String separator) {
		List<String> strings = new ArrayList<String>();
		try {
			String[] chunks = value.split(separator);
			for (String chunk : chunks) {
				chunk = chunk.trim();
				if (chunk.length() > 0) {
					strings.add(chunk);
				}
			}
		} catch (PatternSyntaxException e) {
			logger.error("Failed to parse " + value, e);
		}
		return strings;
	}

	private synchronized Properties read() {
		if (propertiesFile.exists() && (propertiesFile.lastModified() > lastread)) {
			FileInputStream is = null;
			try {
				properties = new Properties();
				is = new FileInputStream(propertiesFile);
				properties.load(is);
				lastread = propertiesFile.lastModified();
			} catch (FileNotFoundException f) {
				// IGNORE - won't happen because file.exists() check above
			} catch (Throwable t) {
				t.printStackTrace();
			} finally {
				if (is != null) {
					try {
						is.close();
					} catch (Throwable t) {
						// IGNORE
					}
				}
			}
		}
		return properties;
	}

	@Override
	public String toString() {
		return propertiesFile.getAbsolutePath();
	}
}
