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

import java.util.Enumeration;
import java.util.Properties;

import javax.servlet.ServletContext;

public class WebXmlSettings extends IStoredSettings {

	private final Properties properties = new Properties();
	
	public WebXmlSettings(ServletContext context) {
		super(WebXmlSettings.class);
		Enumeration<?> keys = context.getInitParameterNames();
		while (keys.hasMoreElements()) {
			String key = keys.nextElement().toString();
			String value = context.getInitParameter(key);
			properties.put(key, value);
		}
	}
	
	@Override
	protected Properties read() {
		return properties;
	}

	@Override
	public String toString() {
		return "WEB.XML";
	}
}
