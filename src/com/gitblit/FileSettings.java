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
import java.util.Properties;

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
		if (propertiesFile.exists() && (propertiesFile.lastModified() > lastModified)) {
			FileInputStream is = null;
			try {
				Properties props = new Properties();
				is = new FileInputStream(propertiesFile);
				props.load(is);

				// load properties after we have successfully read file
				properties.clear();
				properties.putAll(props);
				lastModified = propertiesFile.lastModified();
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
	 * @return the last modification date of the properties file
	 */
	protected long lastModified() {
		return lastModified;
	}

	@Override
	public String toString() {
		return propertiesFile.getAbsolutePath();
	}
}
