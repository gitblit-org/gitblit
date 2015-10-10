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

import com.gitblit.IStoredSettings;
import com.gitblit.manager.IAuthenticationManager;
import com.gitblit.manager.IFederationManager;
import com.gitblit.manager.IFilestoreManager;
import com.gitblit.manager.IGitblit;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IProjectManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.servlet.GitblitContext;


public class GitblitUnitTest extends org.junit.Assert {

	public static IStoredSettings settings() {
		return runtime().getSettings();
	}

	public static IRuntimeManager runtime() {
		return GitblitContext.getManager(IRuntimeManager.class);
	}

	public static INotificationManager notifier() {
		return GitblitContext.getManager(INotificationManager.class);
	}

	public static IUserManager users() {
		return GitblitContext.getManager(IUserManager.class);
	}

	public static IAuthenticationManager authentication() {
		return GitblitContext.getManager(IAuthenticationManager.class);
	}

	public static IRepositoryManager repositories() {
		return GitblitContext.getManager(IRepositoryManager.class);
	}

	public static IProjectManager projects() {
		return GitblitContext.getManager(IProjectManager.class);
	}

	public static IFederationManager federation() {
		return GitblitContext.getManager(IFederationManager.class);
	}

	public static IGitblit gitblit() {
		return GitblitContext.getManager(IGitblit.class);
	}
	
	public static IFilestoreManager filestore() {
		return GitblitContext.getManager(IFilestoreManager.class); 
	}
}
