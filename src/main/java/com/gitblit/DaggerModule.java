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
package com.gitblit;

import javax.inject.Singleton;

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
import com.gitblit.utils.JSoupXssFilter;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.XssFilter;
import com.gitblit.wicket.GitBlitWebApp;

import dagger.Module;
import dagger.Provides;

/**
 * DaggerModule references all injectable objects.
 *
 * @author James Moger
 *
 */
@Module(
	library = true,
	injects = {
			IStoredSettings.class,
			XssFilter.class,

			// core managers
			IRuntimeManager.class,
			IPluginManager.class,
			INotificationManager.class,
			IUserManager.class,
			IAuthenticationManager.class,
			IPublicKeyManager.class,
			IRepositoryManager.class,
			IProjectManager.class,
			IFederationManager.class,

			// the monolithic manager
			IGitblit.class,

			// the Gitblit Wicket app
			GitBlitWebApp.class
		}
)
public class DaggerModule {

	@Provides @Singleton IStoredSettings provideSettings() {
		return new FileSettings();
	}

	@Provides @Singleton XssFilter provideXssFilter() {
		return new JSoupXssFilter();
	}

	@Provides @Singleton IRuntimeManager provideRuntimeManager(IStoredSettings settings, XssFilter xssFilter) {
		return new RuntimeManager(settings, xssFilter);
	}

	@Provides @Singleton IPluginManager providePluginManager(IRuntimeManager runtimeManager) {
		return new PluginManager(runtimeManager);
	}

	@Provides @Singleton INotificationManager provideNotificationManager(IStoredSettings settings) {
		return new NotificationManager(settings);
	}

	@Provides @Singleton IUserManager provideUserManager(
			IRuntimeManager runtimeManager,
			IPluginManager pluginManager) {

		return new UserManager(runtimeManager, pluginManager);
	}

	@Provides @Singleton IAuthenticationManager provideAuthenticationManager(
			IRuntimeManager runtimeManager,
			IUserManager userManager) {

		return new AuthenticationManager(
				runtimeManager,
				userManager);
	}

	@Provides @Singleton IPublicKeyManager providePublicKeyManager(
			IStoredSettings settings,
			IRuntimeManager runtimeManager) {

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

	@Provides @Singleton IRepositoryManager provideRepositoryManager(
			IRuntimeManager runtimeManager,
			IPluginManager pluginManager,
			IUserManager userManager) {

		return new RepositoryManager(
				runtimeManager,
				pluginManager,
				userManager);
	}

	@Provides @Singleton IProjectManager provideProjectManager(
			IRuntimeManager runtimeManager,
			IUserManager userManager,
			IRepositoryManager repositoryManager) {

		return new ProjectManager(
				runtimeManager,
				userManager,
				repositoryManager);
	}

	@Provides @Singleton IFederationManager provideFederationManager(
			IRuntimeManager runtimeManager,
			INotificationManager notificationManager,
			IRepositoryManager repositoryManager) {

		return new FederationManager(
				runtimeManager,
				notificationManager,
				repositoryManager);
	}

	@Provides @Singleton IGitblit provideGitblit(
			IRuntimeManager runtimeManager,
			IPluginManager pluginManager,
			INotificationManager notificationManager,
			IUserManager userManager,
			IAuthenticationManager authenticationManager,
			IPublicKeyManager publicKeyManager,
			IRepositoryManager repositoryManager,
			IProjectManager projectManager,
			IFederationManager federationManager) {

		return new GitBlit(
				runtimeManager,
				pluginManager,
				notificationManager,
				userManager,
				authenticationManager,
				publicKeyManager,
				repositoryManager,
				projectManager,
				federationManager);
	}

	@Provides @Singleton GitBlitWebApp provideWebApplication(
			IRuntimeManager runtimeManager,
			IPluginManager pluginManager,
			INotificationManager notificationManager,
			IUserManager userManager,
			IAuthenticationManager authenticationManager,
			IPublicKeyManager publicKeyManager,
			IRepositoryManager repositoryManager,
			IProjectManager projectManager,
			IFederationManager federationManager,
			IGitblit gitblit) {

		return new GitBlitWebApp(
				runtimeManager,
				pluginManager,
				notificationManager,
				userManager,
				authenticationManager,
				publicKeyManager,
				repositoryManager,
				projectManager,
				federationManager,
				gitblit);
	}
}