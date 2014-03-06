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
import com.gitblit.manager.IProjectManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.manager.NotificationManager;
import com.gitblit.manager.ProjectManager;
import com.gitblit.manager.RepositoryManager;
import com.gitblit.manager.RuntimeManager;
import com.gitblit.manager.UserManager;
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

			// core managers
			IRuntimeManager.class,
			INotificationManager.class,
			IUserManager.class,
			IAuthenticationManager.class,
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

	@Provides @Singleton IRuntimeManager provideRuntimeManager(IStoredSettings settings) {
		return new RuntimeManager(settings);
	}

	@Provides @Singleton INotificationManager provideNotificationManager(IStoredSettings settings) {
		return new NotificationManager(settings);
	}

	@Provides @Singleton IUserManager provideUserManager(IRuntimeManager runtimeManager) {
		return new UserManager(runtimeManager);
	}

	@Provides @Singleton IAuthenticationManager provideAuthenticationManager(
			IRuntimeManager runtimeManager,
			IUserManager userManager) {

		return new AuthenticationManager(
				runtimeManager,
				userManager);
	}

	@Provides @Singleton IRepositoryManager provideRepositoryManager(
			IRuntimeManager runtimeManager,
			IUserManager userManager) {

		return new RepositoryManager(
				runtimeManager,
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
			INotificationManager notificationManager,
			IUserManager userManager,
			IAuthenticationManager authenticationManager,
			IRepositoryManager repositoryManager,
			IProjectManager projectManager,
			IFederationManager federationManager) {

		return new GitBlit(
				runtimeManager,
				notificationManager,
				userManager,
				authenticationManager,
				repositoryManager,
				projectManager,
				federationManager);
	}

	@Provides @Singleton GitBlitWebApp provideWebApplication(
			IRuntimeManager runtimeManager,
			INotificationManager notificationManager,
			IUserManager userManager,
			IAuthenticationManager authenticationManager,
			IRepositoryManager repositoryManager,
			IProjectManager projectManager,
			IFederationManager federationManager,
			IGitblit gitblit) {

		return new GitBlitWebApp(
				runtimeManager,
				notificationManager,
				userManager,
				authenticationManager,
				repositoryManager,
				projectManager,
				federationManager,
				gitblit);
	}
}