package com.gitblit.service;

import static org.eclipse.jgit.lib.Constants.DOT_GIT_EXT;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.ConfigUserService;
import com.gitblit.Constants;
import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.FederationPullStatus;
import com.gitblit.Constants.FederationStrategy;
import com.gitblit.GitBlitException.ForbiddenException;
import com.gitblit.IUserService;
import com.gitblit.Keys;
import com.gitblit.manager.IGitblit;
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

public abstract class FederationPullService implements Runnable {

	final Logger logger = LoggerFactory.getLogger(getClass());

	final IGitblit gitblit;

	private final List<FederationModel> registrations;

	/**
	 * Constructor for specifying a single federation registration. This
	 * constructor is used to schedule the next pull execution.
	 *
	 * @param provider
	 * @param registration
	 */
	public FederationPullService(IGitblit gitblit, FederationModel registration) {
		this(gitblit, Arrays.asList(registration));
	}

	/**
	 * Constructor to specify a group of federation registrations. This is
	 * normally used at startup to pull and then schedule the next update based
	 * on each registrations frequency setting.
	 *
	 * @param provider
	 * @param registrations
	 * @param isDaemon
	 *            if true, registrations are rescheduled in perpetuity. if
	 *            false, the federation pull operation is executed once.
	 */
	public FederationPullService(IGitblit gitblit, List<FederationModel> registrations) {
		this.gitblit = gitblit;
		this.registrations = registrations;
	}

	public abstract void reschedule(FederationModel registration);

	/**
	 * Run method for this pull service.
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
					logger.warn("Federation pull status of {} is now {}", registration.name, is.name());
					if (registration.notifyOnError) {
						String message = "Federation pull of " + registration.name + " @ "
								+ registration.url + " is now at " + is.name();
						gitblit.sendMailToAdministrators(
								"Pull Status of " + registration.name + " is " + is.name(),
								message);
					}
				}
			} catch (Throwable t) {
				logger.error("Failed to pull from federated gitblit ({} @ {})", registration.name, registration.url, t);
			} finally {
				reschedule(registration);
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
			logger.error("Illegal character '{}' in folder name '{}' of federation registration {}!",
							c, registrationFolder, registration.name);
			return;
		}
		File repositoriesFolder = gitblit.getRepositoriesFolder();
		File registrationFolderFile = new File(repositoriesFolder, registrationFolder);
		registrationFolderFile.mkdirs();

		// Clone/Pull the repository
		for (Map.Entry<String, RepositoryModel> entry : repositories.entrySet()) {
			String cloneUrl = entry.getKey();
			RepositoryModel repository = entry.getValue();
			if (!repository.hasCommits) {
				logger.warn("Skipping federated repository {} from {} @ {}. Repository is EMPTY.",
						repository.name, registration.name, registration.url);
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
			Repository existingRepository = gitblit.getRepository(repositoryName);

			if (existingRepository == null && gitblit.isCollectingGarbage(repositoryName)) {
				logger.warn("Skipping local repository {}, busy collecting garbage", repositoryName);
				continue;
			}

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
					logger.warn("Skipping federated repository {} from {} @ {}. Origin does not match, consider EXCLUDING.",
									repository.name, registration.name, registration.url);
					registration.updateStatus(repository, FederationPullStatus.SKIPPED);
					continue;
				}
			}

			// clone/pull this repository
			CredentialsProvider credentials = new UsernamePasswordCredentialsProvider(
					Constants.FEDERATION_USER, registration.token);
			logger.info("Pulling federated repository {} from {} @ {}", repository.name, registration.name, registration.url);

			CloneResult result = JGitUtils.cloneRepository(registrationFolderFile, repository.name,
					cloneUrl, registration.bare, credentials);
			Repository r = gitblit.getRepository(repositoryName);
			RepositoryModel rm = gitblit.getRepositoryModel(repositoryName);
			repository.isFrozen = registration.mirror;
			if (result.createdRepository) {
				// default local settings
				repository.federationStrategy = FederationStrategy.EXCLUDE;
				repository.isFrozen = registration.mirror;
				repository.showRemoteBranches = !registration.mirror;
				logger.info("     cloning {}", repository.name);
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
						// update local branches to match the remote tracking branches
						for (RefModel ref : JGitUtils.getRemoteBranches(r, false, -1)) {
							if (ref.displayName.startsWith("origin/")) {
								String branch = org.eclipse.jgit.lib.Constants.R_HEADS
										+ ref.displayName.substring(ref.displayName.indexOf('/') + 1);
								String hash = ref.getReferencedObjectId().getName();

								JGitUtils.setBranchRef(r, branch, hash);
								logger.info("     resetting {} of {} to {}", branch, repository.name, hash);
							}
						}

						String newHead;
						if (StringUtils.isEmpty(repository.HEAD)) {
							newHead = newFetchHead;
						} else {
							newHead = repository.HEAD;
						}
						JGitUtils.setHEADtoRef(r, newHead);
						logger.info("     resetting HEAD of {} to {}", repository.name, newHead);
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

			gitblit.updateConfiguration(r, repository);
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
							if (user.permissions != null) {
								// pulling from >= 1.2 version
								Map<String, AccessPermission> copy = new HashMap<String, AccessPermission>(user.permissions);
								user.permissions.clear();
								for (Map.Entry<String, AccessPermission> entry : copy.entrySet()) {
									user.setRepositoryPermission(registrationFolder + "/" + entry.getKey(), entry.getValue());
								}
							} else {
								// pulling from <= 1.1 version
								List<String> permissions = new ArrayList<String>(user.repositories);
								user.repositories.clear();
								for (String permission : permissions) {
									user.addRepositoryPermission(registrationFolder + "/" + permission);
								}
							}
						}

						// insert new user or update local user
						UserModel localUser = gitblit.getUserModel(user.username);
						if (localUser == null) {
							// create new local user
							gitblit.addUser(user);
						} else {
							// update repository permissions of local user
							if (user.permissions != null) {
								// pulling from >= 1.2 version
								Map<String, AccessPermission> copy = new HashMap<String, AccessPermission>(user.permissions);
								for (Map.Entry<String, AccessPermission> entry : copy.entrySet()) {
									localUser.setRepositoryPermission(entry.getKey(), entry.getValue());
								}
							} else {
								// pulling from <= 1.1 version
								for (String repository : user.repositories) {
									localUser.addRepositoryPermission(repository);
								}
							}
							localUser.password = user.password;
							localUser.canAdmin = user.canAdmin;
							gitblit.reviseUser(localUser.username, localUser);
						}

						for (String teamname : gitblit.getAllTeamNames()) {
							TeamModel team = gitblit.getTeamModel(teamname);
							if (user.isTeamMember(teamname) && !team.hasUser(user.username)) {
								// new team member
								team.addUser(user.username);
								gitblit.updateTeamModel(teamname, team);
							} else if (!user.isTeamMember(teamname) && team.hasUser(user.username)) {
								// remove team member
								team.removeUser(user.username);
								gitblit.updateTeamModel(teamname, team);
							}

							// update team repositories
							TeamModel remoteTeam = user.getTeam(teamname);
							if (remoteTeam != null) {
								if (remoteTeam.permissions != null) {
									// pulling from >= 1.2
									for (Map.Entry<String, AccessPermission> entry : remoteTeam.permissions.entrySet()){
										team.setRepositoryPermission(entry.getKey(), entry.getValue());
									}
									gitblit.updateTeamModel(teamname, team);
								} else if (!ArrayUtils.isEmpty(remoteTeam.repositories)) {
									// pulling from <= 1.1
									team.addRepositoryPermissions(remoteTeam.repositories);
									gitblit.updateTeamModel(teamname, team);
								}
							}
						}
					}
				}
			}
		} catch (ForbiddenException e) {
			// ignore forbidden exceptions
		} catch (IOException e) {
			logger.warn("Failed to retrieve USERS from federated gitblit ({} @ {})", registration.name, registration.url, e);
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
			logger.warn("Failed to retrieve TEAMS from federated gitblit ({} @ {})", registration.name, registration.url, e);
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
			logger.warn("Failed to retrieve SETTINGS from federated gitblit ({} @ {})",	registration.name, registration.url, e);
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
			logger.warn("Failed to retrieve SCRIPTS from federated gitblit ({} @ {})", registration.name, registration.url, e);
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
		String federationName = gitblit.getSettings().getString(Keys.federation.name, null);
		if (StringUtils.isEmpty(federationName)) {
			federationName = addr.getHostName();
		}
		FederationUtils.acknowledgeStatus(addr.getHostAddress(), registration);
		logger.info("Pull status sent to {}", registration.url);
	}
}