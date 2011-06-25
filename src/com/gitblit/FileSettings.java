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
 * Reads GitBlit settings file.
 * 
 */
public class FileSettings extends IStoredSettings {

	protected final File propertiesFile;

	private final Properties properties = new Properties();

	private volatile long lastread;

	public FileSettings(String file) {
		super(FileSettings.class);
		this.propertiesFile = new File(file);
	}

	@Override
	protected synchronized Properties read() {
		if (propertiesFile.exists() && (propertiesFile.lastModified() > lastread)) {
			FileInputStream is = null;
			try {
				Properties props = new Properties();
				is = new FileInputStream(propertiesFile);
				props.load(is);

				// load properties after we have successfully read file
				properties.clear();
				properties.putAll(props);
				lastread = propertiesFile.lastModified();
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

	protected long lastRead() {
		return lastread;
	}

	@Override
	public String toString() {
		return propertiesFile.getAbsolutePath();
	}
}
