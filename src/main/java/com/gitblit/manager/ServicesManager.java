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
package com.gitblit.manager;

import java.io.IOException;
import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.FederationToken;
import com.gitblit.Constants.Transport;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.fanout.FanoutNioService;
import com.gitblit.fanout.FanoutService;
import com.gitblit.fanout.FanoutSocketService;
import com.gitblit.models.FederationModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.RepositoryUrl;
import com.gitblit.models.UserModel;
import com.gitblit.service.FederationPullService;
import com.gitblit.transport.git.GitDaemon;
import com.gitblit.transport.ssh.SshDaemon;
import com.gitblit.utils.HttpUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;
import com.gitblit.utils.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

/**
 * Services manager manages long-running services/processes that either have no
 * direct relation to other managers OR require really high-level manager
 * integration (i.e. a Gitblit instance).
 *
 * @author James Moger
 *
 */
@Singleton
public class ServicesManager implements IServicesManager {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(5);

	private final Provider<WorkQueue> workQueueProvider;

	private final IStoredSettings settings;

	private final IGitblit gitblit;

	private FanoutService fanoutService;

	private GitDaemon gitDaemon;

	private SshDaemon sshDaemon;

	@Inject
	public ServicesManager(
			Provider<WorkQueue> workQueueProvider,
			IStoredSettings settings,
			IGitblit gitblit) {

		this.workQueueProvider = workQueueProvider;

		this.settings = settings;
		this.gitblit = gitblit;
	}

	@Override
	public ServicesManager start() {
		configureFederation();
		configureFanout();
		configureGitDaemon();
		configureSshDaemon();

		return this;
	}

	@Override
	public ServicesManager stop() {
		scheduledExecutor.shutdownNow();
		if (fanoutService != null) {
			fanoutService.stop();
		}
		if (gitDaemon != null) {
			gitDaemon.stop();
		}
		if (sshDaemon != null) {
			sshDaemon.stop();
		}
		workQueueProvider.get().stop();
		return this;
	}

	protected String getRepositoryUrl(HttpServletRequest request, String username, RepositoryModel repository) {
		String gitblitUrl = settings.getString(Keys.web.canonicalUrl, null);
		if (StringUtils.isEmpty(gitblitUrl)) {
			gitblitUrl = HttpUtils.getGitblitURL(request);
		}
		StringBuilder sb = new StringBuilder();
		sb.append(gitblitUrl);
		sb.append(Constants.R_PATH);
		sb.append(repository.name);

		// inject username into repository url if authentication is required
		if (repository.accessRestriction.exceeds(AccessRestrictionType.NONE)
				&& !StringUtils.isEmpty(username)) {
			sb.insert(sb.indexOf("://") + 3, username + "@");
		}
		return sb.toString();
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
		if (settings.getBoolean(Keys.git.enableGitServlet, true) &&
			settings.getBoolean(Keys.web.showHttpServletUrls, true)) {
			AccessPermission permission = user.getRepositoryPermission(repository).permission;
			if (permission.exceeds(AccessPermission.NONE)) {
				String repoUrl = getRepositoryUrl(request, username, repository);
				Transport transport = Transport.fromUrl(repoUrl);
				if (permission.atLeast(AccessPermission.PUSH) && !acceptsPush(transport)) {
					// downgrade the repo permission for this transport
					// because it is not an acceptable PUSH transport
					permission = AccessPermission.CLONE;
				}
				list.add(new RepositoryUrl(repoUrl, permission));
			}
		}

		// ssh daemon url
		String sshDaemonUrl = getSshDaemonUrl(request, user, repository);
		if (!StringUtils.isEmpty(sshDaemonUrl) &&
			settings.getBoolean(Keys.web.showSshDaemonUrls, true)) {
			AccessPermission permission = user.getRepositoryPermission(repository).permission;
			if (permission.exceeds(AccessPermission.NONE)) {
				if (permission.atLeast(AccessPermission.PUSH) && !acceptsPush(Transport.SSH)) {
					// downgrade the repo permission for this transport
					// because it is not an acceptable PUSH transport
					permission = AccessPermission.CLONE;
				}

				list.add(new RepositoryUrl(sshDaemonUrl, permission));
			}
		}

		// git daemon url
		String gitDaemonUrl = getGitDaemonUrl(request, user, repository);
		if (!StringUtils.isEmpty(gitDaemonUrl) &&
				settings.getBoolean(Keys.web.showGitDaemonUrls, true)) {
			AccessPermission permission = getGitDaemonAccessPermission(user, repository);
			if (permission.exceeds(AccessPermission.NONE)) {
				if (permission.atLeast(AccessPermission.PUSH) && !acceptsPush(Transport.GIT)) {
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
		boolean advertisePermsForOther = settings.getBoolean(Keys.web.advertiseAccessPermissionForOtherUrls, false);
		for (String url : settings.getStrings(Keys.web.otherUrls)) {
			String externalUrl = null;

			if (url.contains("{1}")) {
				// external url requires username, only add url IF we have one
				if (StringUtils.isEmpty(username)) {
					continue;
				} else {
					externalUrl = MessageFormat.format(url, repository.name, username);
				}
			} else {
				// external url does not require username, just do repo name formatting
				externalUrl = MessageFormat.format(url, repository.name);
			}

			AccessPermission permission = null;
			if (advertisePermsForOther) {
				permission = user.getRepositoryPermission(repository).permission;
				if (permission.exceeds(AccessPermission.NONE)) {
					Transport transport = Transport.fromUrl(externalUrl);
					if (permission.atLeast(AccessPermission.PUSH) && !acceptsPush(transport)) {
						// downgrade the repo permission for this transport
						// because it is not an acceptable PUSH transport
						permission = AccessPermission.CLONE;
					}
				}
			}
			list.add(new RepositoryUrl(externalUrl, permission));
		}

		// sort transports by highest permission and then by transport security
		Collections.sort(list, new Comparator<RepositoryUrl>() {

			@Override
			public int compare(RepositoryUrl o1, RepositoryUrl o2) {
				if (o1.hasPermission() && !o2.hasPermission()) {
					// prefer known permission items over unknown
					return -1;
				} else if (!o1.hasPermission() && o2.hasPermission()) {
					// prefer known permission items over unknown
					return 1;
				} else if (!o1.hasPermission() && !o2.hasPermission()) {
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
				if (url.transport != null && url.transport.equals(preferredTransport)) {
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

	/* (non-Javadoc)
	 * @see com.gitblit.manager.IServicesManager#isServingRepositories()
	 */
	@Override
	public boolean isServingRepositories() {
		return isServingHTTPS()
				|| isServingHTTP()
				|| isServingGIT()
				|| isServingSSH();
	}

	/* (non-Javadoc)
	 * @see com.gitblit.manager.IServicesManager#isServingHTTP()
	 */
	@Override
	public boolean isServingHTTP() {
		return settings.getBoolean(Keys.git.enableGitServlet, true)
				&& ((gitblit.getStatus().isGO && settings.getInteger(Keys.server.httpPort, 0) > 0)
						|| !gitblit.getStatus().isGO);
	}

	/* (non-Javadoc)
	 * @see com.gitblit.manager.IServicesManager#isServingHTTPS()
	 */
	@Override
	public boolean isServingHTTPS() {
		return settings.getBoolean(Keys.git.enableGitServlet, true)
				&& ((gitblit.getStatus().isGO && settings.getInteger(Keys.server.httpsPort, 0) > 0)
						|| !gitblit.getStatus().isGO);
	}

	/* (non-Javadoc)
	 * @see com.gitblit.manager.IServicesManager#isServingGIT()
	 */
	@Override
	public boolean isServingGIT() {
		return gitDaemon != null && gitDaemon.isRunning();
	}

	/* (non-Javadoc)
	 * @see com.gitblit.manager.IServicesManager#isServingSSH()
	 */
	@Override
	public boolean isServingSSH() {
		return sshDaemon != null && sshDaemon.isRunning();
	}

	protected void configureFederation() {
		boolean validPassphrase = true;
		String passphrase = settings.getString(Keys.federation.passphrase, "");
		if (StringUtils.isEmpty(passphrase)) {
			logger.info("Federation passphrase is blank! This server can not be PULLED from.");
			validPassphrase = false;
		}
		if (validPassphrase) {
			// standard tokens
			for (FederationToken tokenType : FederationToken.values()) {
				logger.info(MessageFormat.format("Federation {0} token = {1}", tokenType.name(),
						gitblit.getFederationToken(tokenType)));
			}

			// federation set tokens
			for (String set : settings.getStrings(Keys.federation.sets)) {
				logger.info(MessageFormat.format("Federation Set {0} token = {1}", set,
						gitblit.getFederationToken(set)));
			}
		}

		// Schedule or run the federation executor
		List<FederationModel> registrations = gitblit.getFederationRegistrations();
		if (registrations.size() > 0) {
			FederationPuller executor = new FederationPuller(registrations);
			scheduledExecutor.schedule(executor, 1, TimeUnit.MINUTES);
		}
	}

	@Override
	public boolean acceptsPush(Transport byTransport) {
		if (byTransport == null) {
			logger.info("Unknown transport, push rejected!");
			return false;
		}

		Set<Transport> transports = new HashSet<Transport>();
		for (String value : settings.getStrings(Keys.git.acceptedPushTransports)) {
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

	protected void configureGitDaemon() {
		int port = settings.getInteger(Keys.git.daemonPort, 0);
		String bindInterface = settings.getString(Keys.git.daemonBindInterface, "localhost");
		if (port > 0) {
			try {
				gitDaemon = new GitDaemon(gitblit);
				gitDaemon.start();
			} catch (IOException e) {
				gitDaemon = null;
				logger.error(MessageFormat.format("Failed to start Git Daemon on {0}:{1,number,0}", bindInterface, port), e);
			}
		} else {
			logger.info("Git Daemon is disabled.");
		}
	}

	protected void configureSshDaemon() {
		int port = settings.getInteger(Keys.git.sshPort, 0);
		String bindInterface = settings.getString(Keys.git.sshBindInterface, "localhost");
		if (port > 0) {
			try {
				sshDaemon = new SshDaemon(gitblit, workQueueProvider.get());
				sshDaemon.start();
			} catch (IOException e) {
				sshDaemon = null;
				logger.error(MessageFormat.format("Failed to start SSH daemon on {0}:{1,number,0}", bindInterface, port), e);
			}
		}
	}

	protected void configureFanout() {
		// startup Fanout PubSub service
		if (settings.getInteger(Keys.fanout.port, 0) > 0) {
			String bindInterface = settings.getString(Keys.fanout.bindInterface, null);
			int port = settings.getInteger(Keys.fanout.port, FanoutService.DEFAULT_PORT);
			boolean useNio = settings.getBoolean(Keys.fanout.useNio, true);
			int limit = settings.getInteger(Keys.fanout.connectionLimit, 0);

			if (useNio) {
				if (StringUtils.isEmpty(bindInterface)) {
					fanoutService = new FanoutNioService(port);
				} else {
					fanoutService = new FanoutNioService(bindInterface, port);
				}
			} else {
				if (StringUtils.isEmpty(bindInterface)) {
					fanoutService = new FanoutSocketService(port);
				} else {
					fanoutService = new FanoutSocketService(bindInterface, port);
				}
			}

			fanoutService.setConcurrentConnectionLimit(limit);
			fanoutService.setAllowAllChannelAnnouncements(false);
			fanoutService.start();
		} else {
			logger.info("Fanout PubSub service is disabled.");
		}
	}

	public String getGitDaemonUrl(HttpServletRequest request, UserModel user, RepositoryModel repository) {
		if (gitDaemon != null) {
			String bindInterface = settings.getString(Keys.git.daemonBindInterface, "localhost");
			if (bindInterface.equals("localhost")
					&& (!request.getServerName().equals("localhost") && !request.getServerName().equals("127.0.0.1"))) {
				// git daemon is bound to localhost and the request is from elsewhere
				return null;
			}
			if (user.canClone(repository)) {
				String hostname = getHostname(request);
				String url = gitDaemon.formatUrl(hostname, repository.name);
				return url;
			}
		}
		return null;
	}

	public AccessPermission getGitDaemonAccessPermission(UserModel user, RepositoryModel repository) {
		if (gitDaemon != null && user.canClone(repository)) {
			AccessPermission gitDaemonPermission = user.getRepositoryPermission(repository).permission;
			if (gitDaemonPermission.atLeast(AccessPermission.CLONE)) {
				if (repository.accessRestriction.atLeast(AccessRestrictionType.CLONE)) {
					// can not authenticate clone via anonymous git protocol
					gitDaemonPermission = AccessPermission.NONE;
				} else if (repository.accessRestriction.atLeast(AccessRestrictionType.PUSH)) {
					// can not authenticate push via anonymous git protocol
					gitDaemonPermission = AccessPermission.CLONE;
				} else {
					// normal user permission
				}
			}
			return gitDaemonPermission;
		}
		return AccessPermission.NONE;
	}

	public String getSshDaemonUrl(HttpServletRequest request, UserModel user, RepositoryModel repository) {
		if (user == null || UserModel.ANONYMOUS.equals(user)) {
			// SSH always requires authentication - anonymous access prohibited
			return null;
		}
		if (sshDaemon != null) {
			String bindInterface = settings.getString(Keys.git.sshBindInterface, "localhost");
			if (bindInterface.equals("localhost")
					&& (!request.getServerName().equals("localhost") && !request.getServerName().equals("127.0.0.1"))) {
				// ssh daemon is bound to localhost and the request is from elsewhere
				return null;
			}
			if (user.canClone(repository)) {
				String hostname = getHostname(request);
				String url = sshDaemon.formatUrl(user.username, hostname, repository.name);
				return url;
			}
		}
		return null;
	}


	/**
	 * Extract the hostname from the canonical url or return the
	 * hostname from the servlet request.
	 *
	 * @param request
	 * @return
	 */
	protected String getHostname(HttpServletRequest request) {
		String hostname = request.getServerName();
		String canonicalUrl = settings.getString(Keys.web.canonicalUrl, null);
		if (!StringUtils.isEmpty(canonicalUrl)) {
			try {
				URI uri = new URI(canonicalUrl);
				String host = uri.getHost();
				if (!StringUtils.isEmpty(host) && !"localhost".equals(host)) {
					hostname = host;
				}
			} catch (Exception e) {
			}
		}
		return hostname;
	}

	private class FederationPuller extends FederationPullService {

		public FederationPuller(FederationModel registration) {
			super(gitblit, Arrays.asList(registration));
		}

		public FederationPuller(List<FederationModel> registrations) {
			super(gitblit, registrations);
		}

		@Override
		public void reschedule(FederationModel registration) {
			// schedule the next pull
			int mins = TimeUtils.convertFrequencyToMinutes(registration.frequency, 5);
			registration.nextPull = new Date(System.currentTimeMillis() + (mins * 60 * 1000L));
			scheduledExecutor.schedule(new FederationPuller(registration), mins, TimeUnit.MINUTES);
			logger.info(MessageFormat.format(
					"Next pull of {0} @ {1} scheduled for {2,date,yyyy-MM-dd HH:mm}",
					registration.name, registration.url, registration.nextPull));
		}
	}
}
