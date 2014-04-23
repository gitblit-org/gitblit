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

import org.apache.wicket.Application;
import org.apache.wicket.IInitializer;

import ro.fortsoft.pf4j.PluginWrapper;

import com.gitblit.wicket.GitblitWicketApp;

/**
 * A Gitblit plugin that is allowed to extend the Wicket webapp.
 *
 * @author James Moger
 * @since 1.6.0
 */
public abstract class GitblitWicketPlugin extends GitblitPlugin implements IInitializer  {

	public GitblitWicketPlugin(PluginWrapper wrapper) {
		super(wrapper);
	}

	@Override
	public final void init(Application application) {
		init((GitblitWicketApp) application);
	}

	/**
	 * Allows plugins to extend the web application.
	 *
	 * @param app
	 * @since 1.6.0
	 */
	protected abstract void init(GitblitWicketApp app);
}