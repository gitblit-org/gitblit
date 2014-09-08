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
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.FederationToken;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.fanout.FanoutNioService;
import com.gitblit.fanout.FanoutService;
import com.gitblit.fanout.FanoutSocketService;
import com.gitblit.models.FederationModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.service.FederationPullService;
import com.gitblit.transport.git.GitDaemon;
import com.gitblit.transport.ssh.SshDaemon;
import com.gitblit.utils.IdGenerator;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;
import com.gitblit.utils.WorkQueue;

/**
 * Services manager manages long-running services/processes that either have no
 * direct relation to other managers OR require really high-level manager
 * integration (i.e. a Gitblit instance).
 *
 * @author James Moger
 *
 */
public class ServicesManager implements IManager {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(5);

	private final IStoredSettings settings;

	private final IGitblit gitblit;

	private final IdGenerator idGenerator;

	private final WorkQueue workQueue;

	private FanoutService fanoutService;

	private GitDaemon gitDaemon;

	private SshDaemon sshDaemon;

	public ServicesManager(IGitblit gitblit) {
		this.settings = gitblit.getSettings();
		this.gitblit = gitblit;
		int defaultThreadPoolSize = settings.getInteger(Keys.execution.defaultThreadPoolSize, 1);
		this.idGenerator = new IdGenerator();
		this.workQueue = new WorkQueue(idGenerator, defaultThreadPoolSize);
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
		workQueue.stop();
		return this;
	}

	public boolean isServingRepositories() {
		return isServingHTTP()
				|| isServingGIT()
				|| isServingSSH();
	}

	public boolean isServingHTTP() {
		return settings.getBoolean(Keys.git.enableGitServlet, true);
	}

	public boolean isServingGIT() {
		return gitDaemon != null && gitDaemon.isRunning();
	}

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
				sshDaemon = new SshDaemon(gitblit, workQueue);
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
		String canonicalUrl = gitblit.getSettings().getString(Keys.web.canonicalUrl, null);
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
