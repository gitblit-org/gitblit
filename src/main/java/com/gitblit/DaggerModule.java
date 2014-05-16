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

	@Provides @Singleton IRuntimeManager provideRuntimeManager(RuntimeManager manager) {
		return manager;
	}

	@Provides @Singleton IPluginManager providePluginManager(PluginManager manager) {
		return manager;
	}

	@Provides @Singleton INotificationManager provideNotificationManager(NotificationManager manager) {
		return manager;
	}

	@Provides @Singleton IUserManager provideUserManager(UserManager manager) {
		return manager;
	}

	@Provides @Singleton IAuthenticationManager provideAuthenticationManager(AuthenticationManager manager) {
		return manager;
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

	@Provides @Singleton IRepositoryManager provideRepositoryManager(RepositoryManager manager) {
		return manager;
	}

	@Provides @Singleton IProjectManager provideProjectManager(ProjectManager manager) {
		return manager;
	}

	@Provides @Singleton IFederationManager provideFederationManager(FederationManager manager) {
		return manager;
	}

	@Provides @Singleton IGitblit provideGitblit(GitBlit gitblit) {
		return gitblit;
	}
}