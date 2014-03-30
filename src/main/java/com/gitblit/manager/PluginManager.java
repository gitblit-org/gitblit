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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.fortsoft.pf4j.DefaultPluginManager;
import ro.fortsoft.pf4j.PluginWrapper;

import com.gitblit.Keys;
import com.gitblit.utils.FileUtils;

/**
 * The plugin manager maintains the lifecycle of plugins. It is exposed as
 * Dagger bean. The extension consumers supposed to retrieve plugin  manager
 * from the Dagger DI and retrieve extensions provided by active plugins.
 * 
 * @author David Ostrovsky
 * 
 */
public class PluginManager extends DefaultPluginManager implements IPluginManager {

	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	private final IRuntimeManager runtimeManager;

	public PluginManager(IRuntimeManager runtimeManager) {
		super(runtimeManager.getFileOrFolder(Keys.plugins.folder, "${baseFolder}/plugins"));
		this.runtimeManager = runtimeManager;
	}

	@Override
	public PluginManager start() {
		logger.info("Loading plugins...");
		loadPlugins();
		logger.info("Starting loaded plugins...");
		startPlugins();
		return this;
	}

	@Override
	public PluginManager stop() {
		logger.info("Stopping loaded plugins...");
		stopPlugins();
		return null;
	}
	
	@Override
	public boolean deletePlugin(PluginWrapper pw) {
		File folder = runtimeManager.getFileOrFolder(Keys.plugins.folder, "${baseFolder}/plugins");
		File pluginFolder = new File(folder, pw.getPluginPath());
		File pluginZip = new File(folder, pw.getPluginPath() + ".zip");
		
		if (pluginFolder.exists()) {
			FileUtils.delete(pluginFolder);
		}
		if (pluginZip.exists()) {
			FileUtils.delete(pluginZip);
		}
		return true;
	}
}
