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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.fortsoft.pf4j.DefaultPluginManager;

import com.gitblit.Keys;

/**
 * The plugin manager maintains the lifecycle of plugins. It is exposed as
 * Dagger bean. The extension consumers supposed to retrieve plugin  manager
 * from the Dagger DI and retrieve extensions provided by active plugins.
 * 
 * @author David Ostrovsky
 * 
 */
public class GitblitPluginManager extends DefaultPluginManager implements
		IGitblitPluginManager {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public GitblitPluginManager(IRuntimeManager runtimeManager) {
		super(runtimeManager.getFileOrFolder(Keys.plugins.folder,
				"${baseFolder}/plugins"));
	}

	@Override
	public GitblitPluginManager start() {
		logger.info("Plugin manager started");
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
