/*
 * Copyright 2013 gitblit.com.
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
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

import com.gitblit.IStoredSettings;
import com.gitblit.models.ServerSettings;
import com.gitblit.models.ServerStatus;

public interface IRuntimeManager extends IManager {

	void setBaseFolder(File folder);

	File getBaseFolder();

	/**
	 * Returns the preferred timezone for the Gitblit instance.
	 *
	 * @return a timezone
	 */
	TimeZone getTimezone();

	/**
	 * Determine if this Gitblit instance is actively serving git repositories
	 * or if it is merely a repository viewer.
	 *
	 * @return true if Gitblit is serving repositories
	 */
	boolean isServingRepositories();

	/**
	 * Determine if this Gitblit instance is running in debug mode
	 *
	 * @return true if Gitblit is running in debug mode
	 */
	boolean isDebugMode();

	/**
	 * Returns the boot date of the Gitblit server.
	 *
	 * @return the boot date of Gitblit
	 */
	Date getBootDate();

	ServerStatus getStatus();

	/**
	 * Returns the descriptions/comments of the Gitblit config settings.
	 *
	 * @return SettingsModel
	 */
	ServerSettings getSettingsModel();

	/**
	 * Returns the file object for the specified configuration key.
	 *
	 * @return the file
	 */
	File getFileOrFolder(String key, String defaultFileOrFolder);

	/**
	 * Returns the file object which may have it's base-path determined by
	 * environment variables for running on a cloud hosting service. All Gitblit
	 * file or folder retrievals are (at least initially) funneled through this
	 * method so it is the correct point to globally override/alter filesystem
	 * access based on environment or some other indicator.
	 *
	 * @return the file
	 */
	File getFileOrFolder(String fileOrFolder);

	/**
	 * Returns the runtime settings.
	 *
	 * @return settings
	 */
	IStoredSettings getSettings();

	/**
	 * Updates the runtime settings.
	 *
	 * @param settings
	 * @return true if the update succeeded
	 */
	boolean updateSettings(Map<String, String> updatedSettings);
}