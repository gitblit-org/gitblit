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

import java.io.IOException;
import java.util.List;

import ro.fortsoft.pf4j.PluginState;
import ro.fortsoft.pf4j.PluginWrapper;

import com.gitblit.models.PluginRegistry.InstallState;
import com.gitblit.models.PluginRegistry.PluginRegistration;
import com.gitblit.models.PluginRegistry.PluginRelease;

public interface IPluginManager extends IManager {

	/**
	 * Starts all plugins.
	 */
	void startPlugins();

	/**
	 * Stops all plugins.
	 */
	void stopPlugins();

	/**
	 * Starts the specified plugin.
	 *
	 * @param pluginId
	 * @return the state of the plugin
	 */
	PluginState startPlugin(String pluginId);

	/**
	 * Stops the specified plugin.
	 *
	 * @param pluginId
	 * @return the state of the plugin
	 */
	PluginState stopPlugin(String pluginId);

	/**
	 * Returns the list of extensions the plugin provides.
	 *
	 * @param type
	 * @return a list of extensions the plugin provides
	 */
	List<Class<?>> getExtensionClasses(String pluginId);

	/**
	 * Returns the list of extension instances for a given extension point.
	 *
	 * @param type
	 * @return a list of extension instances
	 */
	<T> List<T> getExtensions(Class<T> type);

	/**
	 * Returns the list of all resolved plugins.
	 *
	 * @return a list of resolved plugins
	 */
	List<PluginWrapper> getPlugins();

	/**
	 * Retrieves the {@link PluginWrapper} for the specified plugin id.
	 *
	 * @param pluginId
	 * @return the plugin wrapper
	 */
	PluginWrapper getPlugin(String pluginId);

	/**
     * Retrieves the {@link PluginWrapper} that loaded the given class 'clazz'.
     *
     * @param clazz extension point class to retrieve extension for
     * @return PluginWrapper that loaded the given class
     */
    PluginWrapper whichPlugin(Class<?> clazz);

    /**
     * Disable the plugin represented by pluginId.
     *
     * @param pluginId
     * @return true if successful
     */
    boolean disablePlugin(String pluginId);

    /**
     * Enable the plugin represented by pluginId.
     *
     * @param pluginId
     * @return true if successful
     */
    boolean enablePlugin(String pluginId);

    /**
     * Delete the plugin represented by pluginId.
     *
     * @param pluginId
     * @return true if successful
     */
    boolean deletePlugin(String pluginId);

    /**
     * Refresh the plugin registry.
     */
    boolean refreshRegistry();

    /**
     * Install the plugin from the specified url.
     *
     * @param url
     * @param verifyChecksum
     */
    boolean installPlugin(String url, boolean verifyChecksum) throws IOException;

    /**
     * The list of all registered plugins.
     *
     * @return a list of registered plugins
     */
    List<PluginRegistration> getRegisteredPlugins();

    /**
     * Return a list of registered plugins that match the install state.
     *
     * @param state
     * @return the list of plugins that match the install state
     */
    List<PluginRegistration> getRegisteredPlugins(InstallState state);

    /**
     * Lookup a plugin registration from the plugin registries.
     *
     * @param idOrName
     * @return a plugin registration or null
     */
    PluginRegistration lookupPlugin(String idOrName);

    /**
     * Lookup a plugin release.
     *
     * @param idOrName
     * @param version (use null for the current version)
     * @return the identified plugin version or null
     */
    PluginRelease lookupRelease(String idOrName, String version);
}
