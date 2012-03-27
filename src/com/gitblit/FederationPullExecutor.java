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

import static org.eclipse.jgit.lib.Constants.DOT_GIT_EXT;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants.FederationPullStatus;
import com.gitblit.Constants.FederationStrategy;
import com.gitblit.GitBlitException.ForbiddenException;
import com.gitblit.models.FederationModel;
import com.gitblit.models.RefModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.FederationUtils;
import com.gitblit.utils.FileUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.JGitUtils.CloneResult;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;

/**
 * FederationPullExecutor pulls repository updates and, optionally, user
 * accounts and server settings from registered Gitblit instances.
 */
public class FederationPullExecutor implements Runnable {

	private final Logger logger = LoggerFactory.getLogger(FederationPullExecutor.class);

	private final List<FederationModel> registrations;

	private final boolean isDaemon;

	/**
	 * Constructor for specifying a single federation registration. This
	 * constructor is used to schedule the next pull execution.
	 * 
	 * @param registration
	 */
	private FederationPullExecutor(FederationModel registration) {
		this(Arrays.asList(registration), true);
	}

	/**
	 * Constructor to specify a group of federation registrations. This is
	 * normally used at startup to pull and then schedule the next update based
	 * on each registrations frequency setting.
	 * 
	 * @param registrations
	 * @param isDaemon
	 *            if true, registrations are rescheduled in perpetuity. if
	 *            false, the federation pull operation is executed once.
	 */
	public FederationPullExecutor(List<FederationModel> registrations, boolean isDaemon) {
		this.registrations = registrations;
		this.isDaemon = isDaemon;
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
								.sendMailToAdministrators(
										"Pull Status of " + registration.name + " is " + is.name(),
										message);
					}
				}
			} catch (Throwable t) {
				logger.error(MessageFormat.format(
						"Failed to pull from federated gitblit ({0} @ {1})", registration.name,
						registration.url), t);
			} finally {
				if (isDaemon) {
					schedule(registration);
				}
			}
		}
	}

	/**
	 * Mirrors a repository and, optionally, the server's users, and/or
	 * configuration settings from a origin Gitblit instance.
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

			// Determine local repository name
			String repositoryName;
			if (StringUtils.isEmpty(registrationFolder)) {
				repositoryName = repository.name;
			} else {
				repositoryName = registrationFolder + "/" + repository.name;
			}

			if (registration.bare) {
				// bare repository, ensure .git suffix
				if (!repositoryName.toLowerCase().endsWith(DOT_GIT_EXT)) {
					repositoryName += DOT_GIT_EXT;
				}
			} else {
				// normal repository, strip .git suffix
				if (repositoryName.toLowerCase().endsWith(DOT_GIT_EXT)) {
					repositoryName = repositoryName.substring(0,
							repositoryName.indexOf(DOT_GIT_EXT));
				}
			}

			// confirm that the origin of any pre-existing repository matches
			// the clone url
			String fetchHead = null;
			Repository existingRepository = GitBlit.self().getRepository(repositoryName);
			if (existingRepository != null) {
				StoredConfig config = existingRepository.getConfig();
				config.load();
				String origin = config.getString("remote", "origin", "url");
				RevCommit commit = JGitUtils.getCommit(existingRepository,
						org.eclipse.jgit.lib.Constants.FETCH_HEAD);
				if (commit != null) {
					fetchHead = commit.getName();
				}
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
					cloneUrl, registration.bare, credentials);
			Repository r = GitBlit.self().getRepository(repositoryName);
			RepositoryModel rm = GitBlit.self().getRepositoryModel(repositoryName);
			repository.isFrozen = registration.mirror;
			if (result.createdRepository) {
				// default local settings
				repository.federationStrategy = FederationStrategy.EXCLUDE;
				repository.isFrozen = registration.mirror;
				repository.showRemoteBranches = !registration.mirror;
				logger.info(MessageFormat.format("     cloning {0}", repository.name));
				registration.updateStatus(repository, FederationPullStatus.MIRRORED);
			} else {
				// fetch and update
				boolean fetched = false;
				RevCommit commit = JGitUtils.getCommit(r, org.eclipse.jgit.lib.Constants.FETCH_HEAD);
				String newFetchHead = commit.getName();
				fetched = fetchHead == null || !fetchHead.equals(newFetchHead);

				if (registration.mirror) {
					// mirror
					if (fetched) {
						// find the first branch name that FETCH_HEAD points to
						List<RefModel> refs = JGitUtils.getAllRefs(r).get(commit.getId());
						if (!ArrayUtils.isEmpty(refs)) {
							for (RefModel ref : refs) {
								if (ref.displayName.startsWith(org.eclipse.jgit.lib.Constants.R_REMOTES)) {
									newFetchHead = ref.displayName;
									break;
								}
							}
						}
						// reset HEAD to the FETCH_HEAD branch.
						// if no branch was found, reset HEAD to the commit id.
						Git git = new Git(r);
						ResetCommand reset = git.reset();
						reset.setMode(ResetType.SOFT);
						reset.setRef(newFetchHead);
						Ref ref = reset.call();
						logger.info(MessageFormat.format("     resetting HEAD of {0} to {1}",
								repository.name, ref.getObjectId().getName()));
						registration.updateStatus(repository, FederationPullStatus.MIRRORED);
					} else {
						// indicate no commits pulled
						registration.updateStatus(repository, FederationPullStatus.NOCHANGE);
					}
				} else {
					// non-mirror
					if (fetched) {
						// indicate commits pulled to origin/master
						registration.updateStatus(repository, FederationPullStatus.PULLED);
					} else {
						// indicate no commits pulled
						registration.updateStatus(repository, FederationPullStatus.NOCHANGE);
					}
				}

				// preserve local settings
				repository.isFrozen = rm.isFrozen;
				repository.federationStrategy = rm.federationStrategy;

				// merge federation sets
				Set<String> federationSets = new HashSet<String>();
				if (rm.federationSets != null) {
					federationSets.addAll(rm.federationSets);
				}
				if (repository.federationSets != null) {
					federationSets.addAll(repository.federationSets);
				}
				repository.federationSets = new ArrayList<String>(federationSets);
				
				// merge indexed branches
				Set<String> indexedBranches = new HashSet<String>();
				if (rm.indexedBranches != null) {
					indexedBranches.addAll(rm.indexedBranches);
				}
				if (repository.indexedBranches != null) {
					indexedBranches.addAll(repository.indexedBranches);
				}
				repository.indexedBranches = new ArrayList<String>(indexedBranches);

			}
			// only repositories that are actually _cloned_ from the origin
			// Gitblit repository are marked as federated. If the origin
			// is from somewhere else, these repositories are not considered
			// "federated" repositories.
			repository.isFederated = cloneUrl.startsWith(registration.url);

			GitBlit.self().updateConfiguration(r, repository);
			r.close();
		}

		IUserService userService = null;

		try {
			// Pull USERS
			// TeamModels are automatically pulled because they are contained
			// within the UserModel. The UserService creates unknown teams
			// and updates existing teams.
			Collection<UserModel> users = FederationUtils.getUsers(registration);
			if (users != null && users.size() > 0) {
				File realmFile = new File(registrationFolderFile, registration.name + "_users.conf");
				realmFile.delete();
				userService = new ConfigUserService(realmFile);
				for (UserModel user : users) {
					userService.updateUserModel(user.username, user);

					// merge the origin permissions and origin accounts into
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

						for (String teamname : GitBlit.self().getAllTeamnames()) {
							TeamModel team = GitBlit.self().getTeamModel(teamname);
							if (user.isTeamMember(teamname) && !team.hasUser(user.username)) {
								// new team member
								team.addUser(user.username);
								GitBlit.self().updateTeamModel(teamname, team, false);
							} else if (!user.isTeamMember(teamname) && team.hasUser(user.username)) {
								// remove team member
								team.removeUser(user.username);
								GitBlit.self().updateTeamModel(teamname, team, false);
							}

							// update team repositories
							TeamModel remoteTeam = user.getTeam(teamname);
							if (remoteTeam != null && !ArrayUtils.isEmpty(remoteTeam.repositories)) {
								int before = team.repositories.size();
								team.addRepositories(remoteTeam.repositories);
								int after = team.repositories.size();
								if (after > before) {
									// repository count changed, update
									GitBlit.self().updateTeamModel(teamname, team, false);
								}
							}
						}
					}
				}
			}
		} catch (ForbiddenException e) {
			// ignore forbidden exceptions
		} catch (IOException e) {
			logger.warn(MessageFormat.format(
					"Failed to retrieve USERS from federated gitblit ({0} @ {1})",
					registration.name, registration.url), e);
		}

		try {
			// Pull TEAMS
			// We explicitly pull these even though they are embedded in
			// UserModels because it is possible to use teams to specify
			// mailing lists or push scripts without specifying users.
			if (userService != null) {
				Collection<TeamModel> teams = FederationUtils.getTeams(registration);
				if (teams != null && teams.size() > 0) {
					for (TeamModel team : teams) {
						userService.updateTeamModel(team);
					}
				}
			}
		} catch (ForbiddenException e) {
			// ignore forbidden exceptions
		} catch (IOException e) {
			logger.warn(MessageFormat.format(
					"Failed to retrieve TEAMS from federated gitblit ({0} @ {1})",
					registration.name, registration.url), e);
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
		} catch (ForbiddenException e) {
			// ignore forbidden exceptions
		} catch (IOException e) {
			logger.warn(MessageFormat.format(
					"Failed to retrieve SETTINGS from federated gitblit ({0} @ {1})",
					registration.name, registration.url), e);
		}

		try {
			// Pull SCRIPTS
			Map<String, String> scripts = FederationUtils.getScripts(registration);
			if (scripts != null && scripts.size() > 0) {
				for (Map.Entry<String, String> script : scripts.entrySet()) {
					String scriptName = script.getKey();
					if (scriptName.endsWith(".groovy")) {
						scriptName = scriptName.substring(0, scriptName.indexOf(".groovy"));
					}
					File file = new File(registrationFolderFile, registration.name + "_"
							+ scriptName + ".groovy");
					FileUtils.writeContent(file, script.getValue());
				}
			}
		} catch (ForbiddenException e) {
			// ignore forbidden exceptions
		} catch (IOException e) {
			logger.warn(MessageFormat.format(
					"Failed to retrieve SCRIPTS from federated gitblit ({0} @ {1})",
					registration.name, registration.url), e);
		}
	}

	/**
	 * Sends a status acknowledgment to the origin Gitblit instance. This
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
		logger.info(MessageFormat.format("Pull status sent to {0}", registration.url));
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
