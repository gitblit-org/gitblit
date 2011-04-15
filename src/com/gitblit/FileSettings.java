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
 * Reads GitBlit settings file.
 * 
 */
public class FileSettings implements IStoredSettings {

	private Properties properties = new Properties();

	private long lastread = 0;

	private final Logger logger = LoggerFactory.getLogger(FileSettings.class);

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
		} catch (Exception e) {
		}
		return strings;
	}

	private synchronized Properties read() {
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
	
	@Override
	public String toString() {
		return getClass().getSimpleName() + ": " + new File(Constants.PROPERTIES_FILE).getAbsolutePath();
	}
}
