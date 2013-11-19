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
import com.gitblit.manager.IFederationManager;
import com.gitblit.manager.IGitblitManager;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IProjectManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.ISessionManager;
import com.gitblit.manager.IUserManager;
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
	injects = {
			// core managers
			IRuntimeManager.class,
			INotificationManager.class,
			IUserManager.class,
			ISessionManager.class,
			IRepositoryManager.class,
			IProjectManager.class,
			IGitblitManager.class,
			IFederationManager.class,

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

	final GitBlit gitblit;

	// HACK but necessary for now
	public DaggerModule(GitBlit gitblit) {
		this.gitblit = gitblit;
	}

	@Provides @Singleton IRuntimeManager provideRuntimeManager() {
		return gitblit;
	}

	@Provides @Singleton INotificationManager provideNotificationManager() {
		return gitblit;
	}

	@Provides @Singleton IUserManager provideUserManager() {
		return gitblit;
	}

	@Provides @Singleton ISessionManager provideSessionManager() {
		return gitblit;
	}

	@Provides @Singleton IRepositoryManager provideRepositoryManager() {
		return gitblit;
	}

	@Provides @Singleton IProjectManager provideProjectManager() {
		return gitblit;
	}

	@Provides @Singleton IGitblitManager provideGitblitManager() {
		return gitblit;
	}

	@Provides @Singleton IFederationManager provideFederationManager() {
		return gitblit;
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