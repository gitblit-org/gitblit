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

import ro.fortsoft.pf4j.PluginManager;
import ro.fortsoft.pf4j.PluginWrapper;

public interface IPluginManager extends IManager, PluginManager {

	/**
     * Retrieves the {@link PluginWrapper} that loaded the given class 'clazz'.
     *
     * @param clazz extension point class to retrieve extension for
     * @return PluginWrapper that loaded the given class
     */
    PluginWrapper whichPlugin(Class<?> clazz);
    
    /**
     * Delete the plugin represented by {@link PluginWrapper}.
     * 
     * @param wrapper
     * @return true if successful
     */
    boolean deletePlugin(PluginWrapper wrapper);
}
