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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.gitblit.manager.GitblitManager;
import com.gitblit.manager.IAuthenticationManager;
import com.gitblit.manager.IFederationManager;
import com.gitblit.manager.IGitblit;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IPluginManager;
import com.gitblit.manager.IProjectManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.tickets.ITicketService;
import com.gitblit.tickets.NullTicketService;
import com.gitblit.transport.ssh.IPublicKeyManager;
import com.gitblit.utils.StringUtils;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.Singleton;

/**
 * GitBlit is the aggregate manager for the Gitblit webapp.  It provides all
 * management functions and also manages some long-running services.
 *
 * @author James Moger
 *
 */
@Singleton
public class GitBlit extends GitblitManager {

	private final Injector injector;

	private ITicketService ticketService;

	@Inject
	public GitBlit(
			Provider<IPublicKeyManager> publicKeyManagerProvider,
			IRuntimeManager runtimeManager,
			IPluginManager pluginManager,
			INotificationManager notificationManager,
			IUserManager userManager,
			IAuthenticationManager authenticationManager,
			IRepositoryManager repositoryManager,
			IProjectManager projectManager,
			IFederationManager federationManager) {

		super(
				publicKeyManagerProvider,
				runtimeManager,
				pluginManager,
				notificationManager,
				userManager,
				authenticationManager,
				repositoryManager,
				projectManager,
				federationManager);

		this.injector = Guice.createInjector(getModules());
	}

	@Override
	public GitBlit start() {
		super.start();
		configureTicketService();
		return this;
	}

	@Override
	public GitBlit stop() {
		super.stop();
		ticketService.stop();
		return this;
	}

	protected AbstractModule [] getModules() {
		return new AbstractModule [] { new GitBlitModule()};
	}

	/**
	 * Detect renames and reindex as appropriate.
	 */
	@Override
	public void updateRepositoryModel(String repositoryName, RepositoryModel repository,
			boolean isCreate) throws GitBlitException {
		RepositoryModel oldModel = null;
		boolean isRename = !isCreate && !repositoryName.equalsIgnoreCase(repository.name);
		if (isRename) {
			oldModel = repositoryManager.getRepositoryModel(repositoryName);
		}

		super.updateRepositoryModel(repositoryName, repository, isCreate);

		if (isRename && ticketService != null) {
			ticketService.rename(oldModel, repository);
		}
	}

	/**
	 * Delete the repository and all associated tickets.
	 */
	@Override
	public boolean deleteRepository(String repositoryName) {
		RepositoryModel repository = repositoryManager.getRepositoryModel(repositoryName);
		return deleteRepositoryModel(repository);
	}

	@Override
	public boolean deleteRepositoryModel(RepositoryModel model) {
		boolean success = repositoryManager.deleteRepositoryModel(model);
		if (success && ticketService != null) {
			ticketService.deleteAll(model);
		}
		return success;
	}

	/**
	 * Returns the configured ticket service.
	 *
	 * @return a ticket service
	 */
	@Override
	public ITicketService getTicketService() {
		return ticketService;
	}

	protected void configureTicketService() {
		String clazz = settings.getString(Keys.tickets.service, NullTicketService.class.getName());
		if (StringUtils.isEmpty(clazz)) {
			clazz = NullTicketService.class.getName();
		}
		try {
			Class<? extends ITicketService> serviceClass = (Class<? extends ITicketService>) Class.forName(clazz);
			ticketService = injector.getInstance(serviceClass).start();
			if (ticketService instanceof NullTicketService) {
				logger.warn("No ticket service configured.");
			} else if (ticketService.isReady()) {
				logger.info("{} is ready.", ticketService);
			} else {
				logger.warn("{} is disabled.", ticketService);
			}
		} catch (Exception e) {
			logger.error("failed to create ticket service " + clazz, e);
			ticketService = injector.getInstance(NullTicketService.class).start();
		}
	}

	/**
	 * A nested Guice Module is used for constructor dependency injection of
	 * complex classes.
	 *
	 * @author James Moger
	 *
	 */
	class GitBlitModule extends AbstractModule {

		@Override
		protected void configure() {
			bind(IStoredSettings.class).toInstance(settings);
			bind(IRuntimeManager.class).toInstance(runtimeManager);
			bind(IPluginManager.class).toInstance(pluginManager);
			bind(INotificationManager.class).toInstance(notificationManager);
			bind(IUserManager.class).toInstance(userManager);
			bind(IAuthenticationManager.class).toInstance(authenticationManager);
			bind(IRepositoryManager.class).toInstance(repositoryManager);
			bind(IProjectManager.class).toInstance(projectManager);
			bind(IFederationManager.class).toInstance(federationManager);
			bind(IGitblit.class).toInstance(GitBlit.this);
		}
	}
}
