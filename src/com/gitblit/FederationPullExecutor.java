/*
 * Copyright 2011 gitblit.com.
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

import java.io.File;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants.FederationPullStatus;
import com.gitblit.Constants.FederationStrategy;
import com.gitblit.models.FederationModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.FederationUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.TimeUtils;
import com.gitblit.utils.JGitUtils.CloneResult;
import com.gitblit.utils.StringUtils;

/**
 * FederationPullExecutor pulls repository updates and, optionally, user
 * accounts and server settings from registered Gitblit instances.
 */
public class FederationPullExecutor implements Runnable {

	private final Logger logger = LoggerFactory.getLogger(FederationPullExecutor.class);

	private final List<FederationModel> registrations;

	/**
	 * Constructor for specifying a single federation registration. This
	 * constructor is used to schedule the next pull execution.
	 * 
	 * @param registration
	 */
	private FederationPullExecutor(FederationModel registration) {
		this(Arrays.asList(registration));
	}

	/**
	 * Constructor to specify a group of federation registrations. This is
	 * normally used at startup to pull and then schedule the next update based
	 * on each registrations frequency setting.
	 * 
	 * @param registrations
	 */
	public FederationPullExecutor(List<FederationModel> registrations) {
		this.registrations = registrations;
	}

	/**
	 * Run method for this pull executor.
	 */
	@Override
	public void run() {
		for (FederationModel registration : registrations) {
			FederationPullStatus was = registration.getLowestStatus();
			try {
				Date now = new Date(System.currentTimeMillis());
				pull(registration);
				sendStatusAcknowledgment(registration);
				registration.lastPull = now;
				FederationPullStatus is = registration.getLowestStatus();
				if (is.ordinal() < was.ordinal()) {
					// the status for this registration has downgraded
					logger.warn("Federation pull status of {0} is now {1}", registration.name,
							is.name());
					if (registration.notifyOnError) {
						String message = "Federation pull of " + registration.name + " @ "
								+ registration.url + " is now at " + is.name();
						GitBlit.self()
								.notifyAdministrators(
										"Pull Status of " + registration.name + " is " + is.name(),
										message);
					}
				}
			} catch (Throwable t) {
				logger.error(MessageFormat.format(
						"Failed to pull from federated gitblit ({0} @ {1})", registration.name,
						registration.url), t);
			} finally {
				schedule(registration);
			}
		}
	}

	/**
	 * Mirrors a repository and, optionally, the server's users, and/or
	 * configuration settings from a remote Gitblit instance.
	 * 
	 * @param registration
	 * @throws Exception
	 */
	private void pull(FederationModel registration) throws Exception {
		Map<String, RepositoryModel> repositories = FederationUtils.getRepositories(registration,
				true);
		String registrationFolder = registration.folder.toLowerCase().trim();
		// confirm valid characters in server alias
		Character c = StringUtils.findInvalidCharacter(registrationFolder);
		if (c != null) {
			logger.error(MessageFormat
					.format("Illegal character ''{0}'' in folder name ''{1}'' of federation registration {2}!",
							c, registrationFolder, registration.name));
			return;
		}
		File repositoriesFolder = new File(GitBlit.getString(Keys.git.repositoriesFolder, "git"));
		File registrationFolderFile = new File(repositoriesFolder, registrationFolder);
		registrationFolderFile.mkdirs();

		// Clone/Pull the repository
		for (Map.Entry<String, RepositoryModel> entry : repositories.entrySet()) {
			String cloneUrl = entry.getKey();
			RepositoryModel repository = entry.getValue();
			if (!repository.hasCommits) {
				logger.warn(MessageFormat.format(
						"Skipping federated repository {0} from {1} @ {2}. Repository is EMPTY.",
						repository.name, registration.name, registration.url));
				registration.updateStatus(repository, FederationPullStatus.SKIPPED);
				continue;
			}

			String repositoryName;
			if (StringUtils.isEmpty(registrationFolder)) {
				repositoryName = repository.name;
			} else {
				repositoryName = registrationFolder + "/" + repository.name;
			}

			// confirm that the origin of any pre-existing repository matches
			// the clone url
			Repository existingRepository = GitBlit.self().getRepository(repositoryName);
			if (existingRepository != null) {
				StoredConfig config = existingRepository.getConfig();
				config.load();
				String origin = config.getString("remote", "origin", "url");
				existingRepository.close();
				if (!origin.startsWith(registration.url)) {
					logger.warn(MessageFormat
							.format("Skipping federated repository {0} from {1} @ {2}. Origin does not match, consider EXCLUDING.",
									repository.name, registration.name, registration.url));
					registration.updateStatus(repository, FederationPullStatus.SKIPPED);
					continue;
				}
			}

			// clone/pull this repository
			CredentialsProvider credentials = new UsernamePasswordCredentialsProvider(
					Constants.FEDERATION_USER, registration.token);
			logger.info(MessageFormat.format("Pulling federated repository {0} from {1} @ {2}",
					repository.name, registration.name, registration.url));
			CloneResult result = JGitUtils.cloneRepository(registrationFolderFile, repository.name,
					cloneUrl, credentials);
			Repository r = GitBlit.self().getRepository(repositoryName);
			RepositoryModel rm = GitBlit.self().getRepositoryModel(repositoryName);
			if (result.createdRepository) {
				// default local settings
				repository.federationStrategy = FederationStrategy.EXCLUDE;
				repository.isFrozen = registration.freeze;
			} else {
				// preserve local settings
				repository.isFrozen = rm.isFrozen;
				repository.federationStrategy = rm.federationStrategy;
			}
			// only repositories that are actually _cloned_ from the source
			// Gitblit repository are marked as federated. If the origin
			// is from somewhere else, these repositories are not considered
			// "federated" repositories.
			repository.isFederated = cloneUrl.startsWith(registration.url);

			GitBlit.self().updateConfiguration(r, repository);
			r.close();
			registration.updateStatus(repository, FederationPullStatus.PULLED);
		}

		try {
			// Pull USERS
			Collection<UserModel> users = FederationUtils.getUsers(registration);
			if (users != null && users.size() > 0) {
				File realmFile = new File(registrationFolderFile, registration.name
						+ "_users.properties");
				realmFile.delete();
				FileUserService userService = new FileUserService(realmFile);
				for (UserModel user : users) {
					userService.updateUserModel(user.username, user);

					// merge the remote permissions and remote accounts into
					// the user accounts of this Gitblit instance
					if (registration.mergeAccounts) {
						// reparent all repository permissions if the local
						// repositories are stored within subfolders
						if (!StringUtils.isEmpty(registrationFolder)) {
							List<String> permissions = new ArrayList<String>(user.repositories);
							user.repositories.clear();
							for (String permission : permissions) {
								user.addRepository(registrationFolder + "/" + permission);
							}
						}

						// insert new user or update local user
						UserModel localUser = GitBlit.self().getUserModel(user.username);
						if (localUser == null) {
							// create new local user
							GitBlit.self().updateUserModel(user.username, user, true);
						} else {
							// update repository permissions of local user
							for (String repository : user.repositories) {
								localUser.addRepository(repository);
							}
							localUser.password = user.password;
							localUser.canAdmin = user.canAdmin;
							GitBlit.self().updateUserModel(localUser.username, localUser, false);
						}
					}
				}
			}
		} catch (Exception e) {
			// a 403 error code is normal for a PULL_REPOSITORIES token
			if (!e.getMessage().contains("403")) {
				logger.warn(MessageFormat.format(
						"Failed to retrieve USERS from federated gitblit ({0} @ {1})",
						registration.name, registration.url), e);
			}
		}

		try {
			// Pull SETTINGS
			Map<String, String> settings = FederationUtils.getSettings(registration);
			if (settings != null && settings.size() > 0) {
				Properties properties = new Properties();
				properties.putAll(settings);
				FileOutputStream os = new FileOutputStream(new File(registrationFolderFile,
						registration.name + "_" + Constants.PROPERTIES_FILE));
				properties.store(os, null);
				os.close();
			}
		} catch (Exception e) {
			// a 403 error code is normal for a PULL_REPOSITORIES token
			if (!e.getMessage().contains("403")) {
				logger.warn(MessageFormat.format(
						"Failed to retrieve SETTINGS from federated gitblit ({0} @ {1})",
						registration.name, registration.url), e);
			}
		}
	}

	/**
	 * Sends a status acknowledgment to the source Gitblit instance. This
	 * includes the results of the federated pull.
	 * 
	 * @param registration
	 * @throws Exception
	 */
	private void sendStatusAcknowledgment(FederationModel registration) throws Exception {
		if (!registration.sendStatus) {
			// skip status acknowledgment
			return;
		}
		InetAddress addr = InetAddress.getLocalHost();
		String federationName = GitBlit.getString(Keys.federation.name, null);
		if (StringUtils.isEmpty(federationName)) {
			federationName = addr.getHostName();
		}
		FederationUtils.acknowledgeStatus(addr.getHostAddress(), registration);
	}

	/**
	 * Schedules the next check of the federated Gitblit instance.
	 * 
	 * @param registration
	 */
	private void schedule(FederationModel registration) {
		// schedule the next pull
		int mins = TimeUtils.convertFrequencyToMinutes(registration.frequency);
		registration.nextPull = new Date(System.currentTimeMillis() + (mins * 60 * 1000L));
		GitBlit.self().executor()
				.schedule(new FederationPullExecutor(registration), mins, TimeUnit.MINUTES);
		logger.info(MessageFormat.format(
				"Next pull of {0} @ {1} scheduled for {2,date,yyyy-MM-dd HH:mm}",
				registration.name, registration.url, registration.nextPull));
	}
}
