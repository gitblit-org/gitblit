/*
 * Copyright 2014 gitblit.com.
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
package com.gitblit.servlet;

import java.util.Enumeration;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;

import com.gitblit.IStoredSettings;
import com.gitblit.manager.IRuntimeManager;

/**
 * Wraps a filter config and will prefer a setting retrieved from IStoredSettings
 * if one is available.
 *
 * @author James Moger
 * @since 1.6.0
 */
public class FilterRuntimeConfig implements FilterConfig {

	final IRuntimeManager runtime;
	final IStoredSettings settings;
	final String namespace;
	final FilterConfig config;

	public FilterRuntimeConfig(IRuntimeManager runtime, String namespace, FilterConfig config) {
		this.runtime = runtime;
		this.settings = runtime.getSettings();
		this.namespace = namespace;
		this.config = config;
	}

	@Override
	public String getFilterName() {
		return config.getFilterName();
	}

	@Override
	public ServletContext getServletContext() {
		return config.getServletContext();
	}

	@Override
	public String getInitParameter(String name) {
		String key = namespace + "." + name;
		if (settings.hasSettings(key)) {
			String value = settings.getString(key, null);
			return value;
		}
		return config.getInitParameter(name);
	}

	@Override
	public Enumeration<String> getInitParameterNames() {
		return config.getInitParameterNames();
	}
}
