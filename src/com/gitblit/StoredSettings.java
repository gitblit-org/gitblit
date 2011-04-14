package com.gitblit;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads settings file.
 * 
 */
public class StoredSettings {

	private static Properties properties = new Properties();

	private static long lastread = 0;

	private static final Logger logger = LoggerFactory.getLogger(StoredSettings.class);

	public static List<String> getAllKeys(String startingWith) {
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

	public static boolean getBoolean(String name, boolean defaultValue) {
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

	public static int getInteger(String name, int defaultValue) {
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

	public static String getString(String name, String defaultValue) {
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

	public static List<String> getStrings(String name) {
		return getStrings(name, " ");
	}

	public static List<String> getStringsFromValue(String value) {
		return getStringsFromValue(value, " ");
	}

	public static List<String> getStrings(String name, String separator) {
		List<String> strings = new ArrayList<String>();
		Properties props = read();
		if (props.containsKey(name)) {
			String value = props.getProperty(name);
			strings = getStringsFromValue(value, separator);
		}
		return strings;
	}

	public static List<String> getStringsFromValue(String value, String separator) {
		List<String> strings = new ArrayList<String>();
		try {
			String[] chunks = value.split(separator);
			for (String chunk : chunks) {
				chunk = chunk.trim();
				if (chunk.length() > 0) {
					strings.add(chunk);
				}
			}
		} catch (Exception e) {
		}
		return strings;
	}

	private static synchronized Properties read() {
		File file = new File(Constants.PROPERTIES_FILE);
		if (file.exists() && (file.lastModified() > lastread)) {
			try {
				properties = new Properties();
				properties.load(new FileInputStream(Constants.PROPERTIES_FILE));
				lastread = file.lastModified();
			} catch (FileNotFoundException f) {
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}
		return properties;
	}
}
