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
package com.gitblit.tests;

import com.gitblit.GitBlit;
import com.gitblit.IStoredSettings;
import com.gitblit.manager.IFederationManager;
import com.gitblit.manager.IGitblitManager;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IProjectManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.ISessionManager;
import com.gitblit.manager.IUserManager;


public class GitblitUnitTest extends org.junit.Assert {

	public static IStoredSettings settings() {
		return runtime().getSettings();
	}

	public static IRuntimeManager runtime() {
		return GitBlit.getManager(IRuntimeManager.class);
	}

	public static INotificationManager notifier() {
		return GitBlit.getManager(INotificationManager.class);
	}

	public static IUserManager users() {
		return GitBlit.getManager(IUserManager.class);
	}

	public static ISessionManager session() {
		return GitBlit.getManager(ISessionManager.class);
	}

	public static IRepositoryManager repositories() {
		return GitBlit.getManager(IRepositoryManager.class);
	}

	public static IProjectManager projects() {
		return GitBlit.getManager(IProjectManager.class);
	}

	public static IFederationManager federation() {
		return GitBlit.getManager(IFederationManager.class);
	}

	public static IGitblitManager gitblit() {
		return GitBlit.getManager(IGitblitManager.class);
	}
}
