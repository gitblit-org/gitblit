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
package com.gitblit.extensions;

import ro.fortsoft.pf4j.Plugin;
import ro.fortsoft.pf4j.PluginWrapper;
import ro.fortsoft.pf4j.Version;

/**
 * Parent class of Gitblit plugins.
 *
 * @author James Moger
 * @since 1.5.0
 */
public abstract class GitblitPlugin extends Plugin {

	public GitblitPlugin(PluginWrapper wrapper) {
		super(wrapper);
	}

	/**
	 * Called after a plugin as been loaded but before it is started for the
	 * first time.  This allows the plugin to install settings or perform any
	 * other required first-time initialization.
	 *
	 * @since 1.5.0
	 */
	public abstract void onInstall();

	/**
	 * Called after an updated plugin has been installed but before the updated
	 * plugin is started.  The oldVersion is passed as a parameter in the event
	 * that special processing needs to be executed.
	 *
	 * @param oldVersion
	 * @since 1.5.0
	 */
	public abstract void onUpgrade(Version oldVersion);

	/**
	 * Called before a plugin has been unloaded and deleted from the system.
	 * This allows a plugin to remove any settings it may have created or
	 * perform and other necessary cleanup.
	 *
	 * @since 1.5.0
	 */
	public abstract void onUninstall();
}
