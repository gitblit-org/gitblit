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
import java.util.Map;
import java.util.Properties;

import com.gitblit.utils.FileUtils;

/**
 * Dynamically loads and reloads a properties file by keeping track of the last
 * modification date.
 * 
 * @author James Moger
 * 
 */
public class FileSettings extends IStoredSettings {

	protected final File propertiesFile;

	private final Properties properties = new Properties();

	private volatile long lastModified;
	
	private volatile boolean forceReload;

	public FileSettings(String file) {
		super(FileSettings.class);
		this.propertiesFile = new File(file);
	}

	/**
	 * Returns a properties object which contains the most recent contents of
	 * the properties file.
	 */
	@Override
	protected synchronized Properties read() {
		if (propertiesFile.exists() && (forceReload || (propertiesFile.lastModified() > lastModified))) {
			FileInputStream is = null;
			try {
				Properties props = new Properties();
				is = new FileInputStream(propertiesFile);
				props.load(is);

				// load properties after we have successfully read file
				properties.clear();
				properties.putAll(props);
				lastModified = propertiesFile.lastModified();
				forceReload = false;
			} catch (FileNotFoundException f) {
				// IGNORE - won't happen because file.exists() check above
			} catch (Throwable t) {
				logger.error("Failed to read " + propertiesFile.getName(), t);
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

	/**
	 * Updates the specified settings in the settings file.
	 */
	public synchronized boolean saveSettings(Map<String, String> settings) {
		String content = FileUtils.readContent(propertiesFile, "\n");
		for (Map.Entry<String, String> setting:settings.entrySet()) {
			String regex = "(?m)^(" + regExEscape(setting.getKey()) + "\\s*+=\\s*+)"
				    + "(?:[^\r\n\\\\]++|\\\\(?:\r?\n|\r|.))*+$";
			String oldContent = content;
			content = content.replaceAll(regex, setting.getKey() + " = " + setting.getValue());
			if (content.equals(oldContent)) {
				// did not replace value because it does not exist in the file
				// append new setting to content (issue-85)
				content += "\n" + setting.getKey() + " = " + setting.getValue();
			}
		}
		FileUtils.writeContent(propertiesFile, content);
		// manually set the forceReload flag because not all JVMs support real
		// millisecond resolution of lastModified. (issue-55)		
		forceReload = true;
		return true;
	}
	
	private String regExEscape(String input) {
		return input.replace(".", "\\.");
	}

	/**
	 * @return the last modification date of the properties file
	 */
	protected long lastModified() {
		return lastModified;
	}

	/**
	 * @return the state of the force reload flag
	 */
	protected boolean forceReload() {
		return forceReload;
	}

	@Override
	public String toString() {
		return propertiesFile.getAbsolutePath();
	}
}
