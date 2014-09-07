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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.models.ServerSettings;
import com.gitblit.models.ServerStatus;
import com.gitblit.models.SettingModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.XssFilter;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;

@Singleton
public class RuntimeManager implements IRuntimeManager {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final IStoredSettings settings;

	private final XssFilter xssFilter;

	private final ServerStatus serverStatus;

	private final ServerSettings settingsModel;

	private File baseFolder;

	private TimeZone timezone;

	@Inject
	private Injector injector;

	@Inject
	public RuntimeManager(IStoredSettings settings, XssFilter xssFilter) {
		this(settings, xssFilter, null);
	}

	public RuntimeManager(IStoredSettings settings, XssFilter xssFilter, File baseFolder) {
		this.settings = settings;
		this.settingsModel = new ServerSettings();
		this.serverStatus = new ServerStatus();
		this.xssFilter = xssFilter;
		this.baseFolder = baseFolder == null ? new File("") : baseFolder;
	}

	@Override
	public RuntimeManager start() {
		logger.info("Basefolder  : " + baseFolder.getAbsolutePath());
		logger.info("Settings    : " + settings.toString());
		logTimezone("JVM timezone: ", TimeZone.getDefault());
		logTimezone("App timezone: ", getTimezone());
		logger.info("JVM locale  : " + Locale.getDefault());
		logger.info("App locale  : " +  (getLocale() == null ? "<client>" : getLocale()));
		return this;
	}

	@Override
	public RuntimeManager stop() {
		return this;
	}

	@Override
	public Injector getInjector() {
		return injector;
	}

	@Override
	public File getBaseFolder() {
		return baseFolder;
	}

	@Override
	public void setBaseFolder(File folder) {
		this.baseFolder = folder;
	}

	/**
	 * Returns the boot date of the Gitblit server.
	 *
	 * @return the boot date of Gitblit
	 */
	@Override
	public Date getBootDate() {
		return serverStatus.bootDate;
	}

	@Override
	public ServerSettings getSettingsModel() {
		// ensure that the current values are updated in the setting models
		for (String key : settings.getAllKeys(null)) {
			SettingModel setting = settingsModel.get(key);
			if (setting == null) {
				// unreferenced setting, create a setting model
				setting = new SettingModel();
				setting.name = key;
				settingsModel.add(setting);
			}
			setting.currentValue = settings.getString(key, "");
		}
//		settingsModel.pushScripts = getAllScripts();
		return settingsModel;
	}

	/**
	 * Returns the preferred timezone for the Gitblit instance.
	 *
	 * @return a timezone
	 */
	@Override
	public TimeZone getTimezone() {
		if (timezone == null) {
			String tzid = settings.getString(Keys.web.timezone, null);
			if (StringUtils.isEmpty(tzid)) {
				timezone = TimeZone.getDefault();
				return timezone;
			}
			timezone = TimeZone.getTimeZone(tzid);
		}
		return timezone;
	}

	private void logTimezone(String type, TimeZone zone) {
		SimpleDateFormat df = new SimpleDateFormat("z Z");
		df.setTimeZone(zone);
		String offset = df.format(new Date());
		logger.info("{}{} ({})", new Object [] { type, zone.getID(), offset });
	}

	@Override
	public Locale getLocale() {
		String lc = settings.getString(Keys.web.forceDefaultLocale, null);
		if (!StringUtils.isEmpty(lc)) {
			int underscore = lc.indexOf('_');
			if (underscore > 0) {
				String lang = lc.substring(0, underscore);
				String cc = lc.substring(underscore + 1);
				return new Locale(lang, cc);
			} else {
				return new Locale(lc);
			}
		}
		return null;
	}

	/**
	 * Is Gitblit running in debug mode?
	 *
	 * @return true if Gitblit is running in debug mode
	 */
	@Override
	public boolean isDebugMode() {
		return settings.getBoolean(Keys.web.debugMode, false);
	}

	/**
	 * Returns the file object for the specified configuration key.
	 *
	 * @return the file
	 */
	@Override
	public File getFileOrFolder(String key, String defaultFileOrFolder) {
		String fileOrFolder = settings.getString(key, defaultFileOrFolder);
		return getFileOrFolder(fileOrFolder);
	}

	/**
	 * Returns the file object which may have it's base-path determined by
	 * environment variables for running on a cloud hosting service. All Gitblit
	 * file or folder retrievals are (at least initially) funneled through this
	 * method so it is the correct point to globally override/alter filesystem
	 * access based on environment or some other indicator.
	 *
	 * @return the file
	 */
	@Override
	public File getFileOrFolder(String fileOrFolder) {
		return com.gitblit.utils.FileUtils.resolveParameter(Constants.baseFolder$,
				baseFolder, fileOrFolder);
	}

	/**
	 * Returns the runtime settings.
	 *
	 * @return runtime settings
	 */
	@Override
	public IStoredSettings getSettings() {
		return settings;
	}

	/**
	 * Updates the runtime settings.
	 *
	 * @param settings
	 * @return true if the update succeeded
	 */
	@Override
	public boolean updateSettings(Map<String, String> updatedSettings) {
		return settings.saveSettings(updatedSettings);
	}

	@Override
	public ServerStatus getStatus() {
		// update heap memory status
		serverStatus.heapAllocated = Runtime.getRuntime().totalMemory();
		serverStatus.heapFree = Runtime.getRuntime().freeMemory();
		return serverStatus;
	}

	/**
	 * Returns the XSS filter.
	 *
	 * @return the XSS filter
	 */
	@Override
	public XssFilter getXssFilter() {
		return xssFilter;
	}

}
