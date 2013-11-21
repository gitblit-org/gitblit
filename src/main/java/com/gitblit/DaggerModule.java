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

import org.apache.wicket.protocol.http.WebApplication;

import com.gitblit.git.GitServlet;
import com.gitblit.manager.FederationManager;
import com.gitblit.manager.GitblitManager;
import com.gitblit.manager.IFederationManager;
import com.gitblit.manager.IGitblitManager;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IProjectManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IServicesManager;
import com.gitblit.manager.ISessionManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.manager.NotificationManager;
import com.gitblit.manager.ProjectManager;
import com.gitblit.manager.RepositoryManager;
import com.gitblit.manager.RuntimeManager;
import com.gitblit.manager.ServicesManager;
import com.gitblit.manager.SessionManager;
import com.gitblit.manager.UserManager;
import com.gitblit.wicket.GitBlitWebApp;
import com.gitblit.wicket.GitblitWicketFilter;

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
			ISessionManager.class,
			IRepositoryManager.class,
			IProjectManager.class,
			IGitblitManager.class,
			IFederationManager.class,
			IServicesManager.class,

			// the monolithic manager
			Gitblit.class,

			// filters & servlets
			GitServlet.class,
			GitFilter.class,
			PagesServlet.class,
			PagesFilter.class,
			RpcServlet.class,
			RpcFilter.class,
			DownloadZipServlet.class,
			DownloadZipFilter.class,
			SyndicationServlet.class,
			SyndicationFilter.class,
			FederationServlet.class,
			SparkleShareInviteServlet.class,
			BranchGraphServlet.class,
			RobotsTxtServlet.class,
			LogoServlet.class,
			EnforceAuthenticationFilter.class,
			GitblitWicketFilter.class
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

	@Provides @Singleton ISessionManager provideSessionManager(
			IRuntimeManager runtimeManager,
			IUserManager userManager) {

		return new SessionManager(
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
			IUserManager userManager,
			IRepositoryManager repositoryManager) {

		return new FederationManager(
				runtimeManager,
				notificationManager,
				userManager,
				repositoryManager);
	}

	@Provides @Singleton IGitblitManager provideGitblitManager(
			IRuntimeManager runtimeManager,
			IUserManager userManager,
			IRepositoryManager repositoryManager) {

		return new GitblitManager(
				runtimeManager,
				userManager,
				repositoryManager);
	}

	@Provides @Singleton Gitblit provideGitblit(
			IRuntimeManager runtimeManager,
			INotificationManager notificationManager,
			IUserManager userManager,
			ISessionManager sessionManager,
			IRepositoryManager repositoryManager,
			IProjectManager projectManager,
			IGitblitManager gitblitManager,
			IFederationManager federationManager) {

		return new Gitblit(
				runtimeManager,
				notificationManager,
				userManager,
				sessionManager,
				repositoryManager,
				projectManager,
				gitblitManager,
				federationManager);
	}

	@Provides @Singleton IServicesManager provideServicesManager(Gitblit gitblit) {
		return new ServicesManager(gitblit);
	}

	@Provides @Singleton WebApplication provideWebApplication(
			IRuntimeManager runtimeManager,
			INotificationManager notificationManager,
			IUserManager userManager,
			ISessionManager sessionManager,
			IRepositoryManager repositoryManager,
			IProjectManager projectManager,
			IGitblitManager gitblitManager,
			IFederationManager federationManager) {

		return new GitBlitWebApp(
				runtimeManager,
				notificationManager,
				userManager,
				sessionManager,
				repositoryManager,
				projectManager,
				gitblitManager,
				federationManager);
	}
}