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
import java.util.List;

import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;

import com.gitblit.Constants.AccessPermission;
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
import com.gitblit.manager.ServicesManager;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.RepositoryUrl;
import com.gitblit.models.UserModel;
import com.gitblit.tickets.BranchTicketService;
import com.gitblit.tickets.FileTicketService;
import com.gitblit.tickets.ITicketService;
import com.gitblit.tickets.NullTicketService;
import com.gitblit.tickets.RedisTicketService;
import com.gitblit.transport.ssh.IPublicKeyManager;
import com.gitblit.utils.StringUtils;

import dagger.Module;
import dagger.ObjectGraph;
import dagger.Provides;

/**
 * GitBlit is the aggregate manager for the Gitblit webapp.  It provides all
 * management functions and also manages some long-running services.
 *
 * @author James Moger
 *
 */
public class GitBlit extends GitblitManager {

	private final ObjectGraph injector;

	private final ServicesManager servicesManager;

	private ITicketService ticketService;

	public GitBlit(
			IRuntimeManager runtimeManager,
			INotificationManager notificationManager,
			IUserManager userManager,
			IAuthenticationManager authenticationManager,
			IPublicKeyManager publicKeyManager,
			IRepositoryManager repositoryManager,
			IProjectManager projectManager,
			IFederationManager federationManager,
			IPluginManager pluginManager) {

		super(runtimeManager,
				notificationManager,
				userManager,
				authenticationManager,
				publicKeyManager,
				repositoryManager,
				projectManager,
				federationManager,
				pluginManager);

		this.injector = ObjectGraph.create(getModules());

		this.servicesManager = new ServicesManager(this);
	}

	@Override
	public GitBlit start() {
		super.start();
		logger.info("Starting services manager...");
		servicesManager.start();
		configureTicketService();
		return this;
	}

	@Override
	public GitBlit stop() {
		super.stop();
		servicesManager.stop();
		ticketService.stop();
		return this;
	}

	protected Object [] getModules() {
		return new Object [] { new GitBlitModule()};
	}

	/**
	 * Returns a list of repository URLs and the user access permission.
	 *
	 * @param request
	 * @param user
	 * @param repository
	 * @return a list of repository urls
	 */
	@Override
	public List<RepositoryUrl> getRepositoryUrls(HttpServletRequest request, UserModel user, RepositoryModel repository) {
		if (user == null) {
			user = UserModel.ANONYMOUS;
		}
		String username = StringUtils.encodeUsername(UserModel.ANONYMOUS.equals(user) ? "" : user.username);

		List<RepositoryUrl> list = new ArrayList<RepositoryUrl>();

		// http/https url
		if (settings.getBoolean(Keys.git.enableGitServlet, true)) {
			AccessPermission permission = user.getRepositoryPermission(repository).permission;
			if (permission.exceeds(AccessPermission.NONE)) {
				list.add(new RepositoryUrl(getRepositoryUrl(request, username, repository), permission));
			}
		}

		// ssh daemon url
		String sshDaemonUrl = servicesManager.getSshDaemonUrl(request, user, repository);
		if (!StringUtils.isEmpty(sshDaemonUrl)) {
			AccessPermission permission = user.getRepositoryPermission(repository).permission;
			if (permission.exceeds(AccessPermission.NONE)) {
				list.add(new RepositoryUrl(sshDaemonUrl, permission));
			}
		}

		// git daemon url
		String gitDaemonUrl = servicesManager.getGitDaemonUrl(request, user, repository);
		if (!StringUtils.isEmpty(gitDaemonUrl)) {
			AccessPermission permission = servicesManager.getGitDaemonAccessPermission(user, repository);
			if (permission.exceeds(AccessPermission.NONE)) {
				list.add(new RepositoryUrl(gitDaemonUrl, permission));
			}
		}

		// add all other urls
		// {0} = repository
		// {1} = username
		for (String url : settings.getStrings(Keys.web.otherUrls)) {
			if (url.contains("{1}")) {
				// external url requires username, only add url IF we have one
				if (!StringUtils.isEmpty(username)) {
					list.add(new RepositoryUrl(MessageFormat.format(url, repository.name, username), null));
				}
			} else {
				// external url does not require username
				list.add(new RepositoryUrl(MessageFormat.format(url, repository.name), null));
			}
		}
		return list;
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
		boolean success = repositoryManager.deleteRepository(repositoryName);
		if (success && ticketService != null) {
			return ticketService.deleteAll(repository);
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
			ticketService = injector.get(serviceClass).start();
			if (ticketService instanceof NullTicketService) {
				logger.warn("No ticket service configured.");
			} else if (ticketService.isReady()) {
				logger.info("{} is ready.", ticketService);
			} else {
				logger.warn("{} is disabled.", ticketService);
			}
		} catch (Exception e) {
			logger.error("failed to create ticket service " + clazz, e);
			ticketService = injector.get(NullTicketService.class).start();
		}
	}

	/**
	 * A nested Dagger graph is used for constructor dependency injection of
	 * complex classes.
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

					// ticket services
					NullTicketService.class,
					FileTicketService.class,
					BranchTicketService.class,
					RedisTicketService.class
				}
			)
	class GitBlitModule {

		@Provides @Singleton IStoredSettings provideSettings() {
			return settings;
		}

		@Provides @Singleton IRuntimeManager provideRuntimeManager() {
			return runtimeManager;
		}

		@Provides @Singleton INotificationManager provideNotificationManager() {
			return notificationManager;
		}

		@Provides @Singleton IUserManager provideUserManager() {
			return userManager;
		}

		@Provides @Singleton IAuthenticationManager provideAuthenticationManager() {
			return authenticationManager;
		}

		@Provides @Singleton IRepositoryManager provideRepositoryManager() {
			return repositoryManager;
		}

		@Provides @Singleton IProjectManager provideProjectManager() {
			return projectManager;
		}

		@Provides @Singleton IFederationManager provideFederationManager() {
			return federationManager;
		}

		@Provides @Singleton IGitblit provideGitblit() {
			return GitBlit.this;
		}

		@Provides @Singleton NullTicketService provideNullTicketService() {
			return new NullTicketService(
					runtimeManager,
					notificationManager,
					userManager,
					repositoryManager);
		}

		@Provides @Singleton FileTicketService provideFileTicketService() {
			return new FileTicketService(
					runtimeManager,
					notificationManager,
					userManager,
					repositoryManager);
		}

		@Provides @Singleton BranchTicketService provideBranchTicketService() {
			return new BranchTicketService(
					runtimeManager,
					notificationManager,
					userManager,
					repositoryManager);
		}

		@Provides @Singleton RedisTicketService provideRedisTicketService() {
			return new RedisTicketService(
					runtimeManager,
					notificationManager,
					userManager,
					repositoryManager);
		}
	}
}
