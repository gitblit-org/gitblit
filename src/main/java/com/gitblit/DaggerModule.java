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
import com.gitblit.servlet.BranchGraphServlet;
import com.gitblit.servlet.DownloadZipFilter;
import com.gitblit.servlet.DownloadZipServlet;
import com.gitblit.servlet.EnforceAuthenticationFilter;
import com.gitblit.servlet.FederationServlet;
import com.gitblit.servlet.GitFilter;
import com.gitblit.servlet.GitServlet;
import com.gitblit.servlet.LogoServlet;
import com.gitblit.servlet.PagesFilter;
import com.gitblit.servlet.PagesServlet;
import com.gitblit.servlet.ProxyFilter;
import com.gitblit.servlet.PtServlet;
import com.gitblit.servlet.RawFilter;
import com.gitblit.servlet.RawServlet;
import com.gitblit.servlet.RobotsTxtServlet;
import com.gitblit.servlet.RpcFilter;
import com.gitblit.servlet.RpcServlet;
import com.gitblit.servlet.SparkleShareInviteServlet;
import com.gitblit.servlet.SyndicationFilter;
import com.gitblit.servlet.SyndicationServlet;
import com.gitblit.transport.ssh.FileKeyManager;
import com.gitblit.transport.ssh.IPublicKeyManager;
import com.gitblit.transport.ssh.MemoryKeyManager;
import com.gitblit.transport.ssh.NullKeyManager;
import com.gitblit.utils.StringUtils;
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
			GitBlitWebApp.class,

			// filters & servlets
			GitServlet.class,
			GitFilter.class,
			RawServlet.class,
			RawFilter.class,
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
			PtServlet.class,
			ProxyFilter.class,
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
//
//	@Provides @Singleton GitblitWicketFilter provideGitblitWicketFilter(GitBlitWebApp webapp) {
//		return new GitblitWicketFilter(webapp);
//	}
//
//	@Provides GitServlet provideGitServlet(IGitblit gitblit) {
//		return new GitServlet(gitblit);
//	}
//
//	@Provides GitFilter provideGitFilter(
//			IRuntimeManager runtimeManager,
//			IUserManager userManager,
//			IAuthenticationManager authenticationManager,
//			IRepositoryManager repositoryManager,
//			IFederationManager federationManager) {
//
//		return new GitFilter(
//				runtimeManager,
//				userManager,
//				authenticationManager,
//				repositoryManager,
//				federationManager);
//	}
//
//	@Provides @Singleton PagesServlet providePagesServlet(
//			IRuntimeManager runtimeManager,
//			IRepositoryManager repositoryManager) {
//
//		return new PagesServlet(runtimeManager, repositoryManager);
//	}
//
//	@Provides @Singleton PagesFilter providePagesFilter(
//			IRuntimeManager runtimeManager,
//			IAuthenticationManager authenticationManager,
//			IRepositoryManager repositoryManager) {
//
//		return new PagesFilter(
//				runtimeManager,
//				authenticationManager,
//				repositoryManager);
//	}
//
//	@Provides @Singleton RpcServlet provideRpcServlet(IGitblit gitblit) {
//		return new RpcServlet(gitblit);
//	}
//
//	@Provides @Singleton RpcFilter provideRpcFilter(
//			IRuntimeManager runtimeManager,
//			IAuthenticationManager authenticationManager) {
//
//		return new RpcFilter(runtimeManager, authenticationManager);
//	}
//
//	@Provides @Singleton DownloadZipServlet provideDownloadZipServlet(
//			IRuntimeManager runtimeManager,
//			IRepositoryManager repositoryManager) {
//
//		return new DownloadZipServlet(runtimeManager, repositoryManager);
//	}
//
//	@Provides @Singleton DownloadZipFilter provideDownloadZipFilter(
//			IRuntimeManager runtimeManager,
//			IAuthenticationManager authenticationManager,
//			IRepositoryManager repositoryManager) {
//
//		return new DownloadZipFilter(
//				runtimeManager,
//				authenticationManager,
//				repositoryManager);
//	}
//
//	@Provides @Singleton SyndicationServlet provideSyndicationServlet(
//			IRuntimeManager runtimeManager,
//			IRepositoryManager repositoryManager,
//			IProjectManager projectManager) {
//
//		return new SyndicationServlet(
//				runtimeManager,
//				repositoryManager,
//				projectManager);
//	}
//
//	@Provides @Singleton SyndicationFilter provideSyndicationFilter(
//			IRuntimeManager runtimeManager,
//			IAuthenticationManager authenticationManager,
//			IRepositoryManager repositoryManager,
//			IProjectManager projectManager) {
//
//		return new SyndicationFilter(
//				runtimeManager,
//				authenticationManager,
//				repositoryManager,
//				projectManager);
//	}
//
//	@Provides @Singleton FederationServlet provideFederationServlet(
//			IRuntimeManager runtimeManager,
//			IUserManager userManager,
//			IRepositoryManager repositoryManager,
//			IFederationManager federationManager) {
//
//		return new FederationServlet(
//				runtimeManager,
//				userManager,
//				repositoryManager,
//				federationManager);
//	}
//
//	@Provides @Singleton SparkleShareInviteServlet provideSparkleshareInviteServlet(
//			IRuntimeManager runtimeManager,
//			IUserManager userManager,
//			IAuthenticationManager authenticationManager,
//			IRepositoryManager repositoryManager) {
//
//		return new SparkleShareInviteServlet(
//				runtimeManager,
//				userManager,
//				authenticationManager,
//				repositoryManager);
//	}
//
//	@Provides @Singleton BranchGraphServlet provideBranchGraphServlet(
//			IRuntimeManager runtimeManager,
//			IRepositoryManager repositoryManager) {
//
//		return new BranchGraphServlet(runtimeManager, repositoryManager);
//	}
//
//	@Provides @Singleton RobotsTxtServlet provideRobotsTxtServlet(IRuntimeManager runtimeManager) {
//		return new RobotsTxtServlet(runtimeManager);
//	}
//
//	@Provides @Singleton LogoServlet provideLogoServlet(IRuntimeManager runtimeManager) {
//		return new LogoServlet(runtimeManager);
//	}
//
//	@Provides @Singleton EnforceAuthenticationFilter provideEnforceAuthenticationFilter(
//			IRuntimeManager runtimeManager,
//			IAuthenticationManager authenticationManager) {
//
//		return new EnforceAuthenticationFilter(runtimeManager, authenticationManager);
//	}


	@Provides @Singleton GitblitWicketFilter provideGitblitWicketFilter(GitBlitWebApp webapp) {
		return new GitblitWicketFilter();
	}

	@Provides GitServlet provideGitServlet(IGitblit gitblit) {
		return new GitServlet();
	}

	@Provides GitFilter provideGitFilter(
			IRuntimeManager runtimeManager,
			IUserManager userManager,
			IAuthenticationManager authenticationManager,
			IRepositoryManager repositoryManager,
			IFederationManager federationManager) {

		return new GitFilter();
	}

	@Provides @Singleton RawServlet provideRawServlet(
			IRuntimeManager runtimeManager,
			IRepositoryManager repositoryManager) {

		return new RawServlet();
	}

	@Provides @Singleton RawFilter provideRawFilter(
			IRuntimeManager runtimeManager,
			IAuthenticationManager authenticationManager,
			IRepositoryManager repositoryManager) {

		return new RawFilter();
	}

	@Provides @Singleton PagesServlet providePagesServlet(
			IRuntimeManager runtimeManager,
			IRepositoryManager repositoryManager) {

		return new PagesServlet();
	}

	@Provides @Singleton PagesFilter providePagesFilter(
			IRuntimeManager runtimeManager,
			IAuthenticationManager authenticationManager,
			IRepositoryManager repositoryManager) {

		return new PagesFilter();
	}

	@Provides @Singleton RpcServlet provideRpcServlet(IGitblit gitblit) {
		return new RpcServlet();
	}

	@Provides @Singleton RpcFilter provideRpcFilter(
			IRuntimeManager runtimeManager,
			IAuthenticationManager authenticationManager) {

		return new RpcFilter();
	}

	@Provides @Singleton DownloadZipServlet provideDownloadZipServlet(
			IRuntimeManager runtimeManager,
			IRepositoryManager repositoryManager) {

		return new DownloadZipServlet();
	}

	@Provides @Singleton DownloadZipFilter provideDownloadZipFilter(
			IRuntimeManager runtimeManager,
			IAuthenticationManager authenticationManager,
			IRepositoryManager repositoryManager) {

		return new DownloadZipFilter();
	}

	@Provides @Singleton SyndicationServlet provideSyndicationServlet(
			IRuntimeManager runtimeManager,
			IRepositoryManager repositoryManager,
			IProjectManager projectManager) {

		return new SyndicationServlet();
	}

	@Provides @Singleton SyndicationFilter provideSyndicationFilter(
			IRuntimeManager runtimeManager,
			IAuthenticationManager authenticationManager,
			IRepositoryManager repositoryManager,
			IProjectManager projectManager) {

		return new SyndicationFilter();
	}

	@Provides @Singleton FederationServlet provideFederationServlet(
			IRuntimeManager runtimeManager,
			IUserManager userManager,
			IRepositoryManager repositoryManager,
			IFederationManager federationManager) {

		return new FederationServlet();
	}

	@Provides @Singleton SparkleShareInviteServlet provideSparkleshareInviteServlet(
			IRuntimeManager runtimeManager,
			IUserManager userManager,
			IAuthenticationManager authenticationManager,
			IRepositoryManager repositoryManager) {

		return new SparkleShareInviteServlet();
	}

	@Provides @Singleton BranchGraphServlet provideBranchGraphServlet(
			IRuntimeManager runtimeManager,
			IRepositoryManager repositoryManager) {

		return new BranchGraphServlet();
	}

	@Provides @Singleton RobotsTxtServlet provideRobotsTxtServlet(IRuntimeManager runtimeManager) {
		return new RobotsTxtServlet();
	}

	@Provides @Singleton LogoServlet provideLogoServlet(IRuntimeManager runtimeManager) {
		return new LogoServlet();
	}

	@Provides @Singleton PtServlet providePtServlet(IRuntimeManager runtimeManager) {
		return new PtServlet();
	}

	@Provides @Singleton ProxyFilter provideProxyFilter(
			IRuntimeManager runtimeManager,
			IAuthenticationManager authenticationManager) {

		return new ProxyFilter();
	}

	@Provides @Singleton EnforceAuthenticationFilter provideEnforceAuthenticationFilter(
			IRuntimeManager runtimeManager,
			IAuthenticationManager authenticationManager) {

		return new EnforceAuthenticationFilter();
	}
}