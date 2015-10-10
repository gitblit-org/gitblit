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

import com.gitblit.manager.GitblitManager;
import com.gitblit.manager.IAuthenticationManager;
import com.gitblit.manager.IFederationManager;
import com.gitblit.manager.IFilestoreManager;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IPluginManager;
import com.gitblit.manager.IProjectManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.tickets.ITicketService;
import com.gitblit.transport.ssh.IPublicKeyManager;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

/**
 * GitBlit is the aggregate manager for the Gitblit webapp.  The parent class provides all
 * functionality.  This class exists to not break existing Groovy push hooks.
 *
 * @author James Moger
 *
 */
@Singleton
@Deprecated
public class GitBlit extends GitblitManager {

	@Inject
	public GitBlit(
			Provider<IPublicKeyManager> publicKeyManagerProvider,
			Provider<ITicketService> ticketServiceProvider,
			IRuntimeManager runtimeManager,
			IPluginManager pluginManager,
			INotificationManager notificationManager,
			IUserManager userManager,
			IAuthenticationManager authenticationManager,
			IRepositoryManager repositoryManager,
			IProjectManager projectManager,
			IFederationManager federationManager,
			IFilestoreManager filestoreManager) {

		super(
				publicKeyManagerProvider,
				ticketServiceProvider,
				runtimeManager,
				pluginManager,
				notificationManager,
				userManager,
				authenticationManager,
				repositoryManager,
				projectManager,
				federationManager,
				filestoreManager);
	}
}
