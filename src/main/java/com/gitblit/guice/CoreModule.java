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
package com.gitblit.guice;

import com.google.inject.Singleton;

import com.gitblit.FileSettings;
import com.gitblit.GitBlit;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.AuthenticationManager;
import com.gitblit.manager.FederationManager;
import com.gitblit.manager.IAuthenticationManager;
import com.gitblit.manager.IFederationManager;
import com.gitblit.manager.IGitblit;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IPluginManager;
import com.gitblit.manager.IProjectManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.manager.NotificationManager;
import com.gitblit.manager.PluginManager;
import com.gitblit.manager.ProjectManager;
import com.gitblit.manager.RepositoryManager;
import com.gitblit.manager.RuntimeManager;
import com.gitblit.manager.UserManager;
import com.gitblit.transport.ssh.FileKeyManager;
import com.gitblit.transport.ssh.IPublicKeyManager;
import com.gitblit.transport.ssh.MemoryKeyManager;
import com.gitblit.transport.ssh.NullKeyManager;
import com.gitblit.utils.StringUtils;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;

/**
 * CoreModule references all the core business objects.
 *
 * @author James Moger
 *
 */
public class CoreModule extends AbstractModule {

	@Override
	protected void configure() {

		bind(IStoredSettings.class).toInstance(new FileSettings());

		// core managers
		bind(IRuntimeManager.class).to(RuntimeManager.class).in(Singleton.class);
		bind(IPluginManager.class).to(PluginManager.class).in(Singleton.class);
		bind(INotificationManager.class).to(NotificationManager.class).in(Singleton.class);
		bind(IUserManager.class).to(UserManager.class).in(Singleton.class);
		bind(IAuthenticationManager.class).to(AuthenticationManager.class).in(Singleton.class);
		bind(IRepositoryManager.class).to(RepositoryManager.class).in(Singleton.class);
		bind(IProjectManager.class).to(ProjectManager.class).in(Singleton.class);
		bind(IFederationManager.class).to(FederationManager.class).in(Singleton.class);

		// the monolithic manager
		bind(IGitblit.class).to(GitBlit.class).in(Singleton.class);
	}

	@Provides
	@Singleton
	IPublicKeyManager providePublicKeyManager(IStoredSettings settings, IRuntimeManager runtimeManager) {

		String clazz = settings.getString(Keys.git.sshKeysManager, FileKeyManager.class.getName());
		if (StringUtils.isEmpty(clazz)) {
			clazz = FileKeyManager.class.getName();
		}
		if (FileKeyManager.class.getName().equals(clazz)) {
			return new FileKeyManager(runtimeManager);
		} else if (NullKeyManager.class.getName().equals(clazz)) {
			return new NullKeyManager();
		} else if (MemoryKeyManager.class.getName().equals(clazz)) {
			return new MemoryKeyManager();
		} else {
			try {
				Class<?> mgrClass = Class.forName(clazz);
				return (IPublicKeyManager) mgrClass.newInstance();
			} catch (Exception e) {

			}
			return null;
		}
	}
}