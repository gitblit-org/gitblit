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
package com.gitblit.manager;

import java.io.File;
import java.lang.reflect.Field;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.IStoredSettings;

import ro.fortsoft.pf4j.DefaultPluginManager;

/**
 * The plugin manager maintains the lifecycle of plugins. The plugin manager is exposed
 * as Dagger bean. The extension consumers supposed to retrieve plugin manager from the
 * Dagger DI and retrieve extensions provided by active plugins.
 *
 * @author David Ostrovsky
 *
 */
public class GitblitPluginManager extends DefaultPluginManager implements IGitblitPluginManager {

	private final Logger logger = LoggerFactory.getLogger(getClass());
	private IStoredSettings settings;

	public GitblitPluginManager(IStoredSettings settings) {
		this.settings = settings;
	}

	@Override
	public GitblitPluginManager start() {
		logger.info("Plugin manager started");
		// TODO(davido): add GitBlit configuation option:
		// plugins.Folder for plugin directory
		String pluginFolder = "/tmp/gitblit-plugins-folder";
		System.setProperty("pf4j.pluginsDir", pluginFolder);
		// DefaultPluginManager.pluginsDirectory is private and initialized in constructor
		// override using reflection
		try {
			Field field = DefaultPluginManager.class.getDeclaredField("pluginsDirectory");
			field.setAccessible(true);
			field.set(this, new File(pluginFolder));
		} catch (Exception e) {
			logger.error("Cannot set plugin folder to {}", pluginFolder);
			throw new IllegalStateException("Cannot set pluginDirectory", e);
		}
		loadPlugins();
		startPlugins();
		return this;
	}

	@Override
	public GitblitPluginManager stop() {
		stopPlugins();
		return null;
	}
}
