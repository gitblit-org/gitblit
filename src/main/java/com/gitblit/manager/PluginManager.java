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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.fortsoft.pf4j.DefaultPluginManager;
import ro.fortsoft.pf4j.PluginVersion;
import ro.fortsoft.pf4j.PluginWrapper;

import com.gitblit.Keys;
import com.gitblit.models.PluginRegistry;
import com.gitblit.models.PluginRegistry.PluginRegistration;
import com.gitblit.models.PluginRegistry.PluginRelease;
import com.gitblit.utils.Base64;
import com.gitblit.utils.FileUtils;
import com.gitblit.utils.JsonUtils;
import com.gitblit.utils.StringUtils;
import com.google.common.io.Files;
import com.google.common.io.InputSupplier;

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

	// timeout defaults of Maven 3.0.4 in seconds
	private int connectTimeout = 20;

	private int readTimeout = 12800;

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

	@Override
	public boolean refreshRegistry() {
		String dr = "http://gitblit.github.io/gitblit-registry/plugins.json";
		String url = runtimeManager.getSettings().getString(Keys.plugins.registry, dr);
		try {
			return download(url);
		} catch (Exception e) {
			logger.error(String.format("Failed to retrieve plugins.json from %s", url), e);
		}
		return false;
	}

	protected List<PluginRegistry> getRegistries() {
		List<PluginRegistry> list = new ArrayList<PluginRegistry>();
		File folder = runtimeManager.getFileOrFolder(Keys.plugins.folder, "${baseFolder}/plugins");
		FileFilter jsonFilter = new FileFilter() {
			@Override
			public boolean accept(File file) {
				return !file.isDirectory() && file.getName().toLowerCase().endsWith(".json");
			}
		};

		File [] files = folder.listFiles(jsonFilter);
		if (files == null || files.length == 0) {
			// automatically retrieve the registry if we don't have a local copy
			refreshRegistry();
			files = folder.listFiles(jsonFilter);
		}

		if (files == null || files.length == 0) {
			return list;
		}

		for (File file : files) {
			PluginRegistry registry = null;
			try {
				String json = FileUtils.readContent(file, "\n");
				registry = JsonUtils.fromJsonString(json, PluginRegistry.class);
			} catch (Exception e) {
				logger.error("Failed to deserialize " + file, e);
			}
			if (registry != null) {
				list.add(registry);
			}
		}
		return list;
	}

	@Override
	public List<PluginRegistration> getRegisteredPlugins() {
		List<PluginRegistration> list = new ArrayList<PluginRegistration>();
		Map<String, PluginRegistration> map = new TreeMap<String, PluginRegistration>();
		for (PluginRegistry registry : getRegistries()) {
			List<PluginRegistration> registrations = registry.registrations;
			list.addAll(registrations);
			for (PluginRegistration reg : registrations) {
				reg.installedRelease = null;
				map.put(reg.id, reg);
			}
		}
		for (PluginWrapper pw : getPlugins()) {
			String id = pw.getDescriptor().getPluginId();
			PluginVersion pv = pw.getDescriptor().getVersion();
			PluginRegistration reg = map.get(id);
			if (reg != null) {
				reg.installedRelease = pv.toString();
			}
		}
		return list;
	}

	@Override
	public PluginRegistration lookupPlugin(String idOrName) {
		for (PluginRegistry registry : getRegistries()) {
			PluginRegistration reg = registry.lookup(idOrName);
			if (reg != null) {
				return reg;
			}
		}
		return null;
	}

	@Override
	public PluginRelease lookupRelease(String idOrName, String version) {
		for (PluginRegistry registry : getRegistries()) {
			PluginRegistration reg = registry.lookup(idOrName);
			if (reg != null) {
				PluginRelease pv;
				if (StringUtils.isEmpty(version)) {
					pv = reg.getCurrentRelease();
				} else {
					pv = reg.getRelease(version);
				}
				if (pv != null) {
					return pv;
				}
			}
		}
		return null;
	}


	/**
	 * Installs the plugin from the plugin version.
	 *
	 * @param pv
	 * @throws IOException
	 * @return true if successful
	 */
	@Override
	public boolean installPlugin(PluginRelease pv) {
		return installPlugin(pv.url);
	}

	/**
	 * Installs the plugin from the url.
	 *
	 * @param url
	 * @return true if successful
	 */
	@Override
	public boolean installPlugin(String url) {
		try {
			if (!download(url)) {
				return false;
			}
			// TODO stop, unload, load
		} catch (IOException e) {
			logger.error("Failed to install plugin from " + url, e);
		}
		return true;
	}

	/**
	 * Download a file to the plugins folder.
	 *
	 * @param url
	 * @return
	 * @throws IOException
	 */
	protected boolean download(String url) throws IOException {
		File pFolder = runtimeManager.getFileOrFolder(Keys.plugins.folder, "${baseFolder}/plugins");
		File tmpFile = new File(pFolder, StringUtils.getSHA1(url) + ".tmp");
		if (tmpFile.exists()) {
			tmpFile.delete();
		}

		URL u = new URL(url);
		final URLConnection conn = getConnection(u);

		// try to get the server-specified last-modified date of this artifact
		long lastModified = conn.getHeaderFieldDate("Last-Modified", System.currentTimeMillis());

		Files.copy(new InputSupplier<InputStream>() {
			 @Override
			public InputStream getInput() throws IOException {
				 return new BufferedInputStream(conn.getInputStream());
			}
		}, tmpFile);

		File destFile = new File(pFolder, StringUtils.getLastPathElement(u.getPath()));
		if (destFile.exists()) {
			destFile.delete();
		}
		tmpFile.renameTo(destFile);
		destFile.setLastModified(lastModified);

		return true;
	}

	protected URLConnection getConnection(URL url) throws IOException {
		java.net.Proxy proxy = getProxy(url);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);
		if (java.net.Proxy.Type.DIRECT != proxy.type()) {
			String auth = getProxyAuthorization(url);
			conn.setRequestProperty("Proxy-Authorization", auth);
		}

		String username = null;
		String password = null;
		if (!StringUtils.isEmpty(username) && !StringUtils.isEmpty(password)) {
			// set basic authentication header
			String auth = Base64.encodeBytes((username + ":" + password).getBytes());
			conn.setRequestProperty("Authorization", "Basic " + auth);
		}

		// configure timeouts
		conn.setConnectTimeout(connectTimeout * 1000);
		conn.setReadTimeout(readTimeout * 1000);

		switch (conn.getResponseCode()) {
		case HttpURLConnection.HTTP_MOVED_TEMP:
		case HttpURLConnection.HTTP_MOVED_PERM:
			// handle redirects by closing this connection and opening a new
			// one to the new location of the requested resource
			String newLocation = conn.getHeaderField("Location");
			if (!StringUtils.isEmpty(newLocation)) {
				logger.info("following redirect to {0}", newLocation);
				conn.disconnect();
				return getConnection(new URL(newLocation));
			}
		}

		return conn;
	}

	protected Proxy getProxy(URL url) {
		return java.net.Proxy.NO_PROXY;
	}

	protected String getProxyAuthorization(URL url) {
		return "";
	}
}
