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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletContext;

import com.gitblit.utils.StringUtils;

/**
 * Loads Gitblit settings from the context-parameter values of a web.xml file.
 * 
 * @author James Moger
 * 
 */
public class WebXmlSettings extends IStoredSettings {

	private final Properties properties = new Properties();

	private final ServletContext context;

	public WebXmlSettings(ServletContext context) {
		super(WebXmlSettings.class);
		this.context = context;
		Enumeration<?> keys = context.getInitParameterNames();
		while (keys.hasMoreElements()) {
			String key = keys.nextElement().toString();
			String value = context.getInitParameter(key);
			properties.put(key, decodeValue(value));
			logger.debug(key + "=" + properties.getProperty(key));
		}
		// apply any web-configured overrides
		File file = new File(context.getRealPath("/WEB-INF/web.properties"));
		if (file.exists()) {
			try {
				InputStream is = new FileInputStream(file);
				properties.load(is);
				is.close();
			} catch (Throwable t) {
				logger.error("Failed to load web.properties setting overrides", t);
			}
		}
	}

	private String decodeValue(String value) {
		// decode escaped backslashes and HTML entities
		return StringUtils.decodeFromHtml(value).replace("\\\\", "\\");
	}

	@Override
	protected Properties read() {
		return properties;
	}

	@Override
	public synchronized boolean saveSettings(Map<String, String> settings) {
		try {
			Properties props = new Properties();
			// load pre-existing web-configuration
			File file = new File(context.getRealPath("/WEB-INF/web.properties"));
			if (file.exists()) {
				InputStream is = new FileInputStream(file);
				props.load(is);
				is.close();
			}
			
			// put all new settings and persist
			props.putAll(settings);
			OutputStream os = new FileOutputStream(file);
			props.store(os, null);
			os.close();
			
			// override current runtime settings
			properties.putAll(settings);
			return true;
		} catch (Throwable t) {
			logger.error("Failed to save settings!", t);
		}
		return false;
	}

	@Override
	public String toString() {
		return "WEB.XML";
	}
}
