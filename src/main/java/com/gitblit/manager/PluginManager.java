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
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.fortsoft.pf4j.DefaultPluginFactory;
import ro.fortsoft.pf4j.DefaultPluginManager;
import ro.fortsoft.pf4j.ExtensionFactory;
import ro.fortsoft.pf4j.Plugin;
import ro.fortsoft.pf4j.PluginClassLoader;
import ro.fortsoft.pf4j.PluginFactory;
import ro.fortsoft.pf4j.PluginState;
import ro.fortsoft.pf4j.PluginStateEvent;
import ro.fortsoft.pf4j.PluginStateListener;
import ro.fortsoft.pf4j.PluginWrapper;
import ro.fortsoft.pf4j.Version;

import com.gitblit.Constants;
import com.gitblit.Keys;
import com.gitblit.extensions.GitblitPlugin;
import com.gitblit.models.PluginRegistry;
import com.gitblit.models.PluginRegistry.InstallState;
import com.gitblit.models.PluginRegistry.PluginRegistration;
import com.gitblit.models.PluginRegistry.PluginRelease;
import com.gitblit.utils.Base64;
import com.gitblit.utils.FileUtils;
import com.gitblit.utils.JsonUtils;
import com.gitblit.utils.StringUtils;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * The plugin manager maintains the lifecycle of plugins. It is exposed as
 * Dagger bean. The extension consumers supposed to retrieve plugin manager from
 * the Dagger DI and retrieve extensions provided by active plugins.
 *
 * @author David Ostrovsky
 * @author James Moger
 *
 */
@Singleton
public class PluginManager implements IPluginManager, PluginStateListener {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final IRuntimeManager runtimeManager;

	private DefaultPluginManager pf4j;

	// timeout defaults of Maven 3.0.4 in seconds
	private int connectTimeout = 20;

	private int readTimeout = 12800;

	@Inject
	public PluginManager(IRuntimeManager runtimeManager) {
		this.runtimeManager = runtimeManager;
	}

	@Override
	public Version getSystemVersion() {
		return pf4j.getSystemVersion();
	}

	@Override
	public void pluginStateChanged(PluginStateEvent event) {
		logger.debug(event.toString());
	}

	@Override
	public PluginManager start() {
		File dir = runtimeManager.getFileOrFolder(Keys.plugins.folder, "${baseFolder}/plugins");
		dir.mkdirs();

		pf4j = new DefaultPluginManager(dir) {

			@Override
			protected PluginFactory createPluginFactory() {
				return new GuicePluginFactory();
			}

			@Override
			protected ExtensionFactory createExtensionFactory() {
				return new GuiceExtensionFactory();
			}
		};

		try {
			Version systemVersion = Version.createVersion(Constants.getVersion());
			pf4j.setSystemVersion(systemVersion);
		} catch (Exception e) {
			logger.error(null, e);
		}
		pf4j.loadPlugins();
		logger.debug("Starting plugins");
		pf4j.startPlugins();
		return this;
	}

	@Override
	public PluginManager stop() {
		logger.debug("Stopping plugins");
		pf4j.stopPlugins();
		return null;
	}

	/**
	 * Installs the plugin from the url.
	 *
	 * @param url
	 * @param verifyChecksum
	 * @return true if successful
	 */
	@Override
	public synchronized boolean installPlugin(String url, boolean verifyChecksum) throws IOException {
		File file = download(url, verifyChecksum);
		if (file == null || !file.exists()) {
			logger.error("Failed to download plugin {}", url);
			return false;
		}

		String pluginId = pf4j.loadPlugin(file);
		if (StringUtils.isEmpty(pluginId)) {
			logger.error("Failed to load plugin {}", file);
			return false;
		}

		// allow the plugin to prepare for operation after installation
		PluginWrapper pluginWrapper = pf4j.getPlugin(pluginId);
		if (pluginWrapper.getPlugin() instanceof GitblitPlugin) {
			((GitblitPlugin) pluginWrapper.getPlugin()).onInstall();
		}

		PluginState state = pf4j.startPlugin(pluginId);
		return PluginState.STARTED.equals(state);
	}

	@Override
	public synchronized boolean upgradePlugin(String pluginId, String url, boolean verifyChecksum) throws IOException {
		// ensure we can download the update BEFORE we remove the existing one
		File file = download(url, verifyChecksum);
		if (file == null || !file.exists()) {
			logger.error("Failed to download plugin {}", url);
			return false;
		}

		Version oldVersion = pf4j.getPlugin(pluginId).getDescriptor().getVersion();
		if (removePlugin(pluginId, false)) {
			String newPluginId = pf4j.loadPlugin(file);
			if (StringUtils.isEmpty(newPluginId)) {
				logger.error("Failed to load plugin {}", file);
				return false;
			}

			// the plugin to handle an upgrade
			PluginWrapper pluginWrapper = pf4j.getPlugin(newPluginId);
			if (pluginWrapper.getPlugin() instanceof GitblitPlugin) {
				((GitblitPlugin) pluginWrapper.getPlugin()).onUpgrade(oldVersion);
			}

			PluginState state = pf4j.startPlugin(newPluginId);
			return PluginState.STARTED.equals(state);
		} else {
			logger.error("Failed to delete plugin {}", pluginId);
		}
		return false;
	}

	@Override
	public synchronized boolean disablePlugin(String pluginId) {
		return pf4j.disablePlugin(pluginId);
	}

	@Override
	public synchronized boolean enablePlugin(String pluginId) {
		if (pf4j.enablePlugin(pluginId)) {
			return PluginState.STARTED == pf4j.startPlugin(pluginId);
		}
		return false;
	}

	@Override
	public synchronized boolean uninstallPlugin(String pluginId) {
		return removePlugin(pluginId, true);
	}

	protected boolean removePlugin(String pluginId, boolean isUninstall) {
		PluginWrapper pluginWrapper = getPlugin(pluginId);
		final String name = pluginWrapper.getPluginPath().substring(1);

		if (isUninstall) {
			// allow the plugin to prepare for uninstallation
			if (pluginWrapper.getPlugin() instanceof GitblitPlugin) {
				((GitblitPlugin) pluginWrapper.getPlugin()).onUninstall();
			}
		}

		if (pf4j.deletePlugin(pluginId)) {

			// delete the checksums
			File pFolder = runtimeManager.getFileOrFolder(Keys.plugins.folder, "${baseFolder}/plugins");
			File [] checksums = pFolder.listFiles(new FileFilter() {
				@Override
				public boolean accept(File file) {
					if (!file.isFile()) {
						return false;
					}

					return file.getName().startsWith(name) &&
							(file.getName().toLowerCase().endsWith(".sha1")
									|| file.getName().toLowerCase().endsWith(".md5"));
				}
			});

			if (checksums != null) {
				for (File checksum : checksums) {
					checksum.delete();
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public synchronized PluginState startPlugin(String pluginId) {
		return pf4j.startPlugin(pluginId);
	}

	@Override
	public synchronized PluginState stopPlugin(String pluginId) {
		return pf4j.stopPlugin(pluginId);
	}

	@Override
	public synchronized void startPlugins() {
		pf4j.startPlugins();
	}

	@Override
	public synchronized void stopPlugins() {
		pf4j.stopPlugins();
	}

	@Override
	public synchronized List<PluginWrapper> getPlugins() {
		return pf4j.getPlugins();
	}

	@Override
	public synchronized PluginWrapper getPlugin(String pluginId) {
		return pf4j.getPlugin(pluginId);
	}

	@Override
	public synchronized List<Class<?>> getExtensionClasses(String pluginId) {
		List<Class<?>> list = new ArrayList<Class<?>>();
		PluginClassLoader loader = pf4j.getPluginClassLoader(pluginId);
		for (String className : pf4j.getExtensionClassNames(pluginId)) {
			try {
				list.add(loader.loadClass(className));
			} catch (ClassNotFoundException e) {
				logger.error(String.format("Failed to find %s in %s", className, pluginId), e);
			}
		}
		return list;
	}

	@Override
	public synchronized <T> List<T> getExtensions(Class<T> type) {
		return pf4j.getExtensions(type);
	}

	@Override
	public synchronized PluginWrapper whichPlugin(Class<?> clazz) {
		return pf4j.whichPlugin(clazz);
	}

	@Override
	public synchronized boolean refreshRegistry(boolean verifyChecksum) {
		String dr = "http://gitblit.github.io/gitblit-registry/plugins.json";
		String url = runtimeManager.getSettings().getString(Keys.plugins.registry, dr);
		try {
			File file = download(url, verifyChecksum);
			if (file != null && file.exists()) {
				URL selfUrl = new URL(url.substring(0, url.lastIndexOf('/')));
				// replace ${self} with the registry url
				String content = FileUtils.readContent(file, "\n");
				content = content.replace("${self}", selfUrl.toString());
				FileUtils.writeContent(file, content);
			}
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

		File[] files = folder.listFiles(jsonFilter);
		if (files == null || files.length == 0) {
			// automatically retrieve the registry if we don't have a local copy
			refreshRegistry(true);
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
				registry.setup();
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
	public synchronized List<PluginRegistration> getRegisteredPlugins() {
		List<PluginRegistration> list = new ArrayList<PluginRegistration>();
		Map<String, PluginRegistration> map = new TreeMap<String, PluginRegistration>();
		for (PluginRegistry registry : getRegistries()) {
			list.addAll(registry.registrations);
			for (PluginRegistration reg : list) {
				reg.installedRelease = null;
				map.put(reg.id, reg);
			}
		}

		for (PluginWrapper pw : pf4j.getPlugins()) {
			String id = pw.getDescriptor().getPluginId();
			Version pv = pw.getDescriptor().getVersion();
			PluginRegistration reg = map.get(id);
			if (reg != null) {
				reg.installedRelease = pv.toString();
			}
		}
		return list;
	}

	@Override
	public synchronized List<PluginRegistration> getRegisteredPlugins(InstallState state) {
		List<PluginRegistration> list = getRegisteredPlugins();
		Iterator<PluginRegistration> itr = list.iterator();
		while (itr.hasNext()) {
			if (state != itr.next().getInstallState(getSystemVersion())) {
				itr.remove();
			}
		}
		return list;
	}

	@Override
	public synchronized PluginRegistration lookupPlugin(String pluginId) {
		for (PluginRegistration reg : getRegisteredPlugins()) {
			if (reg.id.equalsIgnoreCase(pluginId)) {
				return reg;
			}
		}
		return null;
	}

	@Override
	public synchronized PluginRelease lookupRelease(String pluginId, String version) {
		PluginRegistration reg = lookupPlugin(pluginId);
		if (reg == null) {
			return null;
		}

		PluginRelease pv;
		if (StringUtils.isEmpty(version)) {
			pv = reg.getCurrentRelease(getSystemVersion());
		} else {
			pv = reg.getRelease(version);
		}
		return pv;
	}

	/**
	 * Downloads a file with optional checksum verification.
	 *
	 * @param url
	 * @param verifyChecksum
	 * @return
	 * @throws IOException
	 */
	protected File download(String url, boolean verifyChecksum) throws IOException {
		File file = downloadFile(url);

		if (!verifyChecksum) {
			return file;
		}

		File sha1File = null;
		try {
			sha1File = downloadFile(url + ".sha1");
		} catch (IOException e) {
		}

		File md5File = null;
		try {
			md5File = downloadFile(url + ".md5");
		} catch (IOException e) {

		}

		if (sha1File == null && md5File == null) {
			throw new IOException("Missing SHA1 and MD5 checksums for " + url);
		}

		String expected;
		MessageDigest md = null;
		if (sha1File != null && sha1File.exists()) {
			// prefer SHA1 to MD5
			expected = FileUtils.readContent(sha1File, "\n").split(" ")[0].trim();
			try {
				md = MessageDigest.getInstance("SHA-1");
			} catch (NoSuchAlgorithmException e) {
				logger.error(null, e);
			}
		} else {
			expected = FileUtils.readContent(md5File, "\n").split(" ")[0].trim();
			try {
				md = MessageDigest.getInstance("MD5");
			} catch (Exception e) {
				logger.error(null, e);
			}
		}

		// calculate the checksum
		FileInputStream is = null;
		try {
			is = new FileInputStream(file);
			DigestInputStream dis = new DigestInputStream(is, md);
			byte [] buffer = new byte[1024];
			while ((dis.read(buffer)) > -1) {
				// read
			}
			dis.close();

			byte [] digest = md.digest();
			String calculated = StringUtils.toHex(digest).trim();

			if (!expected.equals(calculated)) {
				String msg = String.format("Invalid checksum for %s\nAlgorithm:  %s\nExpected:   %s\nCalculated: %s",
						file.getAbsolutePath(),
						md.getAlgorithm(),
						expected,
						calculated);
				file.delete();
				throw new IOException(msg);
			}
		} finally {
			if (is != null) {
				is.close();
			}
		}
		return file;
	}

	/**
	 * Download a file to the plugins folder.
	 *
	 * @param url
	 * @return the downloaded file
	 * @throws IOException
	 */
	protected File downloadFile(String url) throws IOException {
		File pFolder = runtimeManager.getFileOrFolder(Keys.plugins.folder, "${baseFolder}/plugins");
		pFolder.mkdirs();
		File tmpFile = new File(pFolder, StringUtils.getSHA1(url) + ".tmp");
		if (tmpFile.exists()) {
			tmpFile.delete();
		}

		URL u = new URL(url);
		final URLConnection conn = getConnection(u);

		// try to get the server-specified last-modified date of this artifact
		long lastModified = conn.getHeaderFieldDate("Last-Modified", System.currentTimeMillis());

		try (InputStream is = conn.getInputStream(); OutputStream os = new FileOutputStream(tmpFile);) {
			ByteStreams.copy(is, os);
		}

		File destFile = new File(pFolder, StringUtils.getLastPathElement(u.getPath()));
		if (destFile.exists()) {
			destFile.delete();
		}
		tmpFile.renameTo(destFile);
		destFile.setLastModified(lastModified);

		return destFile;
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
		String proxyHost = runtimeManager.getSettings().getString(Keys.plugins.httpProxyHost, "");
		String proxyPort = runtimeManager.getSettings().getString(Keys.plugins.httpProxyPort, "");

		if (!StringUtils.isEmpty(proxyHost)  && !StringUtils.isEmpty(proxyPort)) {
			return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, Integer.parseInt(proxyPort)));
		} else {
			return java.net.Proxy.NO_PROXY;
		}
	}

	protected String getProxyAuthorization(URL url) {
		String proxyAuth = runtimeManager.getSettings().getString(Keys.plugins.httpProxyAuthorization, "");
		return proxyAuth;
	}

	/**
	 * Instantiates a plugin using pf4j but injects member fields
	 * with Guice.
	 */
	private class GuicePluginFactory extends DefaultPluginFactory {

		@Override
		public Plugin create(PluginWrapper pluginWrapper) {
			// use pf4j to create the plugin
			Plugin plugin = super.create(pluginWrapper);

			if (plugin != null) {
				// allow Guice to inject member fields
				runtimeManager.getInjector().injectMembers(plugin);
			}

			return plugin;
		}
	}

	/**
	 * Instantiates an extension using Guice.
	 */
	private class GuiceExtensionFactory implements ExtensionFactory {
		@Override
		public Object create(Class<?> extensionClass) {
			// instantiate && inject the extension
			logger.debug("Create instance for extension '{}'", extensionClass.getName());
			try {
				return runtimeManager.getInjector().getInstance(extensionClass);
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
			}
			return null;
		}
	}
}
