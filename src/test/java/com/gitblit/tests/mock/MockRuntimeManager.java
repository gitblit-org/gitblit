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
package com.gitblit.tests.mock;

import java.io.File;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import com.gitblit.Constants;
import com.gitblit.IStoredSettings;
import com.gitblit.manager.IManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.models.ServerSettings;
import com.gitblit.models.ServerStatus;
import com.gitblit.models.SettingModel;
import com.gitblit.utils.XssFilter;
import com.gitblit.utils.XssFilter.AllowXssFilter;

public class MockRuntimeManager implements IRuntimeManager {

	File baseFolder;

	IStoredSettings settings;

	ServerStatus serverStatus;

	ServerSettings serverSettings;

	public MockRuntimeManager() {
		this(new MemorySettings());
	}

	public MockRuntimeManager(Map<String, Object> settings) {
		this(new MemorySettings(settings));
	}

	public MockRuntimeManager(IStoredSettings settings) {
		this.settings = settings;

		this.serverStatus = new ServerStatus();
		this.serverStatus.servletContainer = "MockServer";

		this.serverSettings = new ServerSettings();
	}

	@Override
	public void setBaseFolder(File folder) {
		this.baseFolder = folder;
	}

	@Override
	public File getBaseFolder() {
		return baseFolder;
	}

	@Override
	public TimeZone getTimezone() {
		return TimeZone.getDefault();
	}

	@Override
	public Locale getLocale() {
		return Locale.getDefault();
	}

	@Override
	public boolean isServingRepositories() {
		return true;
	}

	@Override
	public boolean isServingHTTP() {
		return true;
	}

	@Override
	public boolean isServingGIT() {
		return true;
	}

	@Override
	public boolean isServingSSH() {
		return true;
	}

	@Override
	public boolean isDebugMode() {
		return true;
	}

	@Override
	public Date getBootDate() {
		return serverStatus.bootDate;
	}

	@Override
	public ServerStatus getStatus() {
		// update heap memory status
		serverStatus.heapAllocated = Runtime.getRuntime().totalMemory();
		serverStatus.heapFree = Runtime.getRuntime().freeMemory();
		return serverStatus;
	}

	@Override
	public ServerSettings getSettingsModel() {
		// ensure that the current values are updated in the setting models
		for (String key : settings.getAllKeys(null)) {
			SettingModel setting = serverSettings.get(key);
			if (setting == null) {
				// unreferenced setting, create a setting model
				setting = new SettingModel();
				setting.name = key;
				serverSettings.add(setting);
			}
			setting.currentValue = settings.getString(key, "");
		}
		return serverSettings;
	}

	@Override
	public File getFileOrFolder(String key, String defaultFileOrFolder) {
		String fileOrFolder = settings.getString(key, defaultFileOrFolder);
		return getFileOrFolder(fileOrFolder);
	}

	@Override
	public File getFileOrFolder(String fileOrFolder) {
		return com.gitblit.utils.FileUtils.resolveParameter(Constants.baseFolder$,
				baseFolder, fileOrFolder);
	}

	@Override
	public IStoredSettings getSettings() {
		return settings;
	}

	@Override
	public XssFilter getXssFilter() {
		return new AllowXssFilter();
	}

	@Override
	public boolean updateSettings(Map<String, String> updatedSettings) {
		return settings.saveSettings(updatedSettings);
	}

	@Override
	public IManager stop() {
		return this;
	}

	@Override
	public IRuntimeManager start() {
		return this;
	}
}
