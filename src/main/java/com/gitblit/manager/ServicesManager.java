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
import com.gitblit.git.GitDaemon;
import com.gitblit.models.FederationModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.service.FederationPullService;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;

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

	private FanoutService fanoutService;

	private GitDaemon gitDaemon;

	public ServicesManager(IGitblit gitblit) {
		this.settings = gitblit.getSettings();
		this.gitblit = gitblit;
	}

	@Override
	public ServicesManager start() {
		configureFederation();
		configureFanout();
		configureGitDaemon();

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
		return this;
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
				String servername = request.getServerName();
				String url = gitDaemon.formatUrl(servername, repository.name);
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
			int mins = TimeUtils.convertFrequencyToMinutes(registration.frequency);
			registration.nextPull = new Date(System.currentTimeMillis() + (mins * 60 * 1000L));
			scheduledExecutor.schedule(new FederationPuller(registration), mins, TimeUnit.MINUTES);
			logger.info(MessageFormat.format(
					"Next pull of {0} @ {1} scheduled for {2,date,yyyy-MM-dd HH:mm}",
					registration.name, registration.url, registration.nextPull));
		}

	}
}
