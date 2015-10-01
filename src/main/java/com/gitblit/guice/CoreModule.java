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

import com.gitblit.FileSettings;
import com.gitblit.GitBlit;
import com.gitblit.IStoredSettings;
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
import com.gitblit.manager.IServicesManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.manager.NotificationManager;
import com.gitblit.manager.PluginManager;
import com.gitblit.manager.ProjectManager;
import com.gitblit.manager.RepositoryManager;
import com.gitblit.manager.RuntimeManager;
import com.gitblit.manager.ServicesManager;
import com.gitblit.manager.UserManager;
import com.gitblit.tickets.ITicketService;
import com.gitblit.transport.ssh.IPublicKeyManager;
import com.gitblit.utils.JSoupXssFilter;
import com.gitblit.utils.WorkQueue;
import com.gitblit.utils.XssFilter;
import com.google.inject.AbstractModule;

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
		bind(XssFilter.class).to(JSoupXssFilter.class);

		// bind complex providers
		bind(IPublicKeyManager.class).toProvider(IPublicKeyManagerProvider.class);
		bind(ITicketService.class).toProvider(ITicketServiceProvider.class);
		bind(WorkQueue.class).toProvider(WorkQueueProvider.class);

		// core managers
		bind(IRuntimeManager.class).to(RuntimeManager.class);
		bind(IPluginManager.class).to(PluginManager.class);
		bind(INotificationManager.class).to(NotificationManager.class);
		bind(IUserManager.class).to(UserManager.class);
		bind(IAuthenticationManager.class).to(AuthenticationManager.class);
		bind(IRepositoryManager.class).to(RepositoryManager.class);
		bind(IProjectManager.class).to(ProjectManager.class);
		bind(IFederationManager.class).to(FederationManager.class);

		// the monolithic manager
		bind(IGitblit.class).to(GitBlit.class);

		// manager for long-running daemons and services
		bind(IServicesManager.class).to(ServicesManager.class);
	}
}