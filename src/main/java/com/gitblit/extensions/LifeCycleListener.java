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

import ro.fortsoft.pf4j.ExtensionPoint;

/**
 * Extension point to allow plugins to listen to major Gitblit lifecycle events.
 *
 * @author James Moger
 * @since 1.6.0
 */
public abstract class LifeCycleListener implements ExtensionPoint {

	/**
	 * Called after all internal managers have been started.
	 * This may be useful for reporting "server is ready" to a monitoring system.
	 *
	 * @since 1.6.0
	 */
	public abstract void onStartup();

	/**
	 * Called when the servlet container is gracefully shutting-down the webapp.
	 * This is called before the internal managers are stopped.
	 *
	 *  @since 1.6.0
	 */
	public abstract void onShutdown();
}
