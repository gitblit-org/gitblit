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
package com.gitblit.wicket;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.apache.wicket.WicketRuntimeException;
import org.apache.wicket.application.IClassResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.fortsoft.pf4j.PluginState;
import ro.fortsoft.pf4j.PluginWrapper;

import com.gitblit.manager.IPluginManager;

/**
 * Resolves plugin classes and resources.
 */
public class PluginClassResolver implements IClassResolver {
	private static final Logger logger = LoggerFactory.getLogger(PluginClassResolver.class);

	private final IClassResolver coreResolver;
	private final IPluginManager pluginManager;

	public PluginClassResolver(IClassResolver coreResolver, IPluginManager pluginManager) {
		this.coreResolver = coreResolver;
		this.pluginManager = pluginManager;
	}

	@Override
	public Class<?> resolveClass(final String className) throws ClassNotFoundException {
		boolean debugEnabled = logger.isDebugEnabled();

		for (PluginWrapper plugin : pluginManager.getPlugins()) {
			if (PluginState.STARTED != plugin.getPluginState()) {
				// ignore this plugin
				continue;
			}

			try {
				return plugin.getPluginClassLoader().loadClass(className);
			} catch (ClassNotFoundException cnfx) {
				if (debugEnabled) {
					logger.debug("ClassResolver '{}' cannot find class: '{}'", plugin.getPluginId(), className);
				}
			}
		}

		return coreResolver.resolveClass(className);
	}

	@Override
	public Iterator<URL> getResources(final String name) {
		Set<URL> urls = new TreeSet<URL>(new UrlExternalFormComparator());

		for (PluginWrapper plugin : pluginManager.getPlugins()) {
			if (PluginState.STARTED != plugin.getPluginState()) {
				// ignore this plugin
				continue;
			}

			Iterator<URL> it = getResources(name, plugin);
			while (it.hasNext()) {
				URL url = it.next();
				urls.add(url);
			}
		}

		Iterator<URL> it = coreResolver.getResources(name);
		while (it.hasNext()) {
			URL url = it.next();
			urls.add(url);
		}
		return urls.iterator();
	}

	protected Iterator<URL> getResources(String name, PluginWrapper plugin) {
		HashSet<URL> loadedFiles = new HashSet<URL>();
		try {
			// Try the classloader for the wicket jar/bundle
			Enumeration<URL> resources = plugin.getPluginClassLoader().getResources(name);
			loadResources(resources, loadedFiles);
		} catch (IOException e) {
			throw new WicketRuntimeException(e);
		}

		return loadedFiles.iterator();
	}

	private void loadResources(Enumeration<URL> resources, Set<URL> loadedFiles) {
		if (resources != null) {
			while (resources.hasMoreElements()) {
				final URL url = resources.nextElement();
				if (!loadedFiles.contains(url)) {
					loadedFiles.add(url);
				}
			}
		}
	}

	@Override
	public ClassLoader getClassLoader() {
		return getClass().getClassLoader();	}
}