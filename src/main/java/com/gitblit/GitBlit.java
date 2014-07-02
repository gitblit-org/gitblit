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

import com.google.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.Transport;
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
import com.gitblit.tickets.ITicketService;
import com.gitblit.tickets.NullTicketService;
import com.gitblit.transport.ssh.IPublicKeyManager;
import com.gitblit.utils.StringUtils;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

/**
 * GitBlit is the aggregate manager for the Gitblit webapp.  It provides all
 * management functions and also manages some long-running services.
 *
 * @author James Moger
 *
 */
public class GitBlit extends GitblitManager {

	private final Injector injector;

	private final ServicesManager servicesManager;

	private ITicketService ticketService;

	@Inject
	public GitBlit(
			IRuntimeManager runtimeManager,
			IPluginManager pluginManager,
			INotificationManager notificationManager,
			IUserManager userManager,
			IAuthenticationManager authenticationManager,
			IPublicKeyManager publicKeyManager,
			IRepositoryManager repositoryManager,
			IProjectManager projectManager,
			IFederationManager federationManager) {

		super(runtimeManager,
				pluginManager,
				notificationManager,
				userManager,
				authenticationManager,
				publicKeyManager,
				repositoryManager,
				projectManager,
				federationManager);

		this.injector = Guice.createInjector(getModules());

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

	@Override
	public boolean isServingRepositories() {
		return servicesManager.isServingRepositories();
	}

	@Override
	public boolean isServingHTTP() {
		return servicesManager.isServingHTTP();
	}

	@Override
	public boolean isServingGIT() {
		return servicesManager.isServingGIT();
	}

	@Override
	public boolean isServingSSH() {
		return servicesManager.isServingSSH();
	}

	protected AbstractModule [] getModules() {
		return new AbstractModule [] { new GitBlitModule()};
	}

	protected boolean acceptPush(Transport byTransport) {
		if (byTransport == null) {
			logger.info("Unknown transport, push rejected!");
			return false;
		}

		Set<Transport> transports = new HashSet<Transport>();
		for (String value : getSettings().getStrings(Keys.git.acceptedPushTransports)) {
			Transport transport = Transport.fromString(value);
			if (transport == null) {
				logger.info(String.format("Ignoring unknown registered transport %s", value));
				continue;
			}

			transports.add(transport);
		}

		if (transports.isEmpty()) {
			// no transports are explicitly specified, all are acceptable
			return true;
		}

		// verify that the transport is permitted
		return transports.contains(byTransport);
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
				Transport transport = Transport.fromString(request.getScheme());
				if (permission.atLeast(AccessPermission.PUSH) && !acceptPush(transport)) {
					// downgrade the repo permission for this transport
					// because it is not an acceptable PUSH transport
					permission = AccessPermission.CLONE;
				}
				list.add(new RepositoryUrl(getRepositoryUrl(request, username, repository), permission));
			}
		}

		// ssh daemon url
		String sshDaemonUrl = servicesManager.getSshDaemonUrl(request, user, repository);
		if (!StringUtils.isEmpty(sshDaemonUrl)) {
			AccessPermission permission = user.getRepositoryPermission(repository).permission;
			if (permission.exceeds(AccessPermission.NONE)) {
				if (permission.atLeast(AccessPermission.PUSH) && !acceptPush(Transport.SSH)) {
					// downgrade the repo permission for this transport
					// because it is not an acceptable PUSH transport
					permission = AccessPermission.CLONE;
				}

				list.add(new RepositoryUrl(sshDaemonUrl, permission));
			}
		}

		// git daemon url
		String gitDaemonUrl = servicesManager.getGitDaemonUrl(request, user, repository);
		if (!StringUtils.isEmpty(gitDaemonUrl)) {
			AccessPermission permission = servicesManager.getGitDaemonAccessPermission(user, repository);
			if (permission.exceeds(AccessPermission.NONE)) {
				if (permission.atLeast(AccessPermission.PUSH) && !acceptPush(Transport.GIT)) {
					// downgrade the repo permission for this transport
					// because it is not an acceptable PUSH transport
					permission = AccessPermission.CLONE;
				}
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

		// sort transports by highest permission and then by transport security
		Collections.sort(list, new Comparator<RepositoryUrl>() {

			@Override
			public int compare(RepositoryUrl o1, RepositoryUrl o2) {
				if (!o1.isExternal() && o2.isExternal()) {
					// prefer Gitblit over external
					return -1;
				} else if (o1.isExternal() && !o2.isExternal()) {
					// prefer Gitblit over external
					return 1;
				} else if (o1.isExternal() && o2.isExternal()) {
					// sort by Transport ordinal
					return o1.transport.compareTo(o2.transport);
				} else if (o1.permission.exceeds(o2.permission)) {
					// prefer highest permission
					return -1;
				} else if (o2.permission.exceeds(o1.permission)) {
					// prefer highest permission
					return 1;
				}

				// prefer more secure transports
				return o1.transport.compareTo(o2.transport);
			}
		});

		// consider the user's transport preference
		RepositoryUrl preferredUrl = null;
		Transport preferredTransport = user.getPreferences().getTransport();
		if (preferredTransport != null) {
			Iterator<RepositoryUrl> itr = list.iterator();
			while (itr.hasNext()) {
				RepositoryUrl url = itr.next();
				if (url.transport.equals(preferredTransport)) {
					itr.remove();
					preferredUrl = url;
					break;
				}
			}
		}
		if (preferredUrl != null) {
			list.add(0, preferredUrl);
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
	 * Delete the user and all associated public ssh keys.
	 */
	@Override
	public boolean deleteUser(String username) {
		UserModel user = userManager.getUserModel(username);
		return deleteUserModel(user);
	}

	@Override
	public boolean deleteUserModel(UserModel model) {
		boolean success = userManager.deleteUserModel(model);
		if (success) {
			getPublicKeyManager().removeAllKeys(model.username);
		}
		return success;
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
