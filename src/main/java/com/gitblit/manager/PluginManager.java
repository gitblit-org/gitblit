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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
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

import ro.fortsoft.pf4j.DefaultPluginManager;
import ro.fortsoft.pf4j.PluginClassLoader;
import ro.fortsoft.pf4j.PluginState;
import ro.fortsoft.pf4j.PluginStateEvent;
import ro.fortsoft.pf4j.PluginStateListener;
import ro.fortsoft.pf4j.PluginVersion;
import ro.fortsoft.pf4j.PluginWrapper;

import com.gitblit.Keys;
import com.gitblit.models.PluginRegistry;
import com.gitblit.models.PluginRegistry.InstallState;
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
 * Dagger bean. The extension consumers supposed to retrieve plugin manager from
 * the Dagger DI and retrieve extensions provided by active plugins.
 *
 * @author David Ostrovsky
 *
 */
public class PluginManager implements IPluginManager, PluginStateListener {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final DefaultPluginManager pf4j;

	private final IRuntimeManager runtimeManager;

	// timeout defaults of Maven 3.0.4 in seconds
	private int connectTimeout = 20;

	private int readTimeout = 12800;

	public PluginManager(IRuntimeManager runtimeManager) {
		File dir = runtimeManager.getFileOrFolder(Keys.plugins.folder, "${baseFolder}/plugins");
		this.runtimeManager = runtimeManager;
		this.pf4j = new DefaultPluginManager(dir);
	}

	@Override
	public void pluginStateChanged(PluginStateEvent event) {
		logger.debug(event.toString());
	}

	@Override
	public PluginManager start() {
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

		PluginState state = pf4j.startPlugin(pluginId);
		return PluginState.STARTED.equals(state);
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
	public synchronized boolean deletePlugin(String pluginId) {
		PluginWrapper pluginWrapper = getPlugin(pluginId);
		final String name = pluginWrapper.getPluginPath().substring(1);
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
	public synchronized boolean refreshRegistry() {
		String dr = "http://gitblit.github.io/gitblit-registry/plugins.json";
		String url = runtimeManager.getSettings().getString(Keys.plugins.registry, dr);
		try {
			File file = download(url, true);
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
			PluginVersion pv = pw.getDescriptor().getVersion();
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
			if (state != itr.next().getInstallState()) {
				itr.remove();
			}
		}
		return list;
	}

	@Override
	public synchronized PluginRegistration lookupPlugin(String idOrName) {
		for (PluginRegistration reg : getRegisteredPlugins()) {
			if (reg.id.equalsIgnoreCase(idOrName) || reg.name.equalsIgnoreCase(idOrName)) {
				return reg;
			}
		}
		return null;
	}

	@Override
	public synchronized PluginRelease lookupRelease(String idOrName, String version) {
		PluginRegistration reg = lookupPlugin(idOrName);
		if (reg == null) {
			return null;
		}

		PluginRelease pv;
		if (StringUtils.isEmpty(version)) {
			pv = reg.getCurrentRelease();
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

		if (sha1File == null && md5File == null && verifyChecksum) {
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
		return java.net.Proxy.NO_PROXY;
	}

	protected String getProxyAuthorization(URL url) {
		return "";
	}
}
