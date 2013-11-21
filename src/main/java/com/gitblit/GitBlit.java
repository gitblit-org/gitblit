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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletContext;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpServletRequest;

import org.apache.wicket.resource.ContextRelativeResource;
import org.apache.wicket.util.resource.ResourceStreamNotFoundException;
import org.slf4j.Logger;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.FederationRequest;
import com.gitblit.Constants.FederationToken;
import com.gitblit.dagger.DaggerContextListener;
import com.gitblit.fanout.FanoutNioService;
import com.gitblit.fanout.FanoutService;
import com.gitblit.fanout.FanoutSocketService;
import com.gitblit.git.GitDaemon;
import com.gitblit.git.GitServlet;
import com.gitblit.manager.IFederationManager;
import com.gitblit.manager.IGitblitManager;
import com.gitblit.manager.IManager;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IProjectManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.ISessionManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.models.FederationModel;
import com.gitblit.models.FederationProposal;
import com.gitblit.models.FederationSet;
import com.gitblit.models.GitClientApplication;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.RepositoryUrl;
import com.gitblit.models.ServerSettings;
import com.gitblit.models.SettingModel;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.ContainerUtils;
import com.gitblit.utils.FederationUtils;
import com.gitblit.utils.HttpUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.JsonUtils;
import com.gitblit.utils.ObjectCache;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitblitWicketFilter;
import com.gitblit.wicket.WicketUtils;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import dagger.ObjectGraph;

/**
 * GitBlit is the servlet context listener singleton that acts as the core for
 * the web ui and the servlets. This class is either directly instantiated by
 * the GitBlitServer class (Gitblit GO) or is reflectively instantiated by the
 * servlet 3 container (Gitblit WAR or Express).
 *
 * This class is the central logic processor for Gitblit. All settings, user
 * object, and repository object operations pass through this class.
 *
 * @author James Moger
 *
 */
@WebListener
public class GitBlit extends DaggerContextListener
					 implements IFederationManager,
								IGitblitManager {

	private static GitBlit gitblit;

	private final IStoredSettings goSettings;

	private final File goBaseFolder;

	private final List<IManager> managers = new ArrayList<IManager>();

	private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(10);

	private final List<FederationModel> federationRegistrations = Collections
			.synchronizedList(new ArrayList<FederationModel>());

	private final ObjectCache<Collection<GitClientApplication>> clientApplications = new ObjectCache<Collection<GitClientApplication>>();

	private final Map<String, FederationModel> federationPullResults = new ConcurrentHashMap<String, FederationModel>();

	private IStoredSettings settings;

	private FanoutService fanoutService;

	private GitDaemon gitDaemon;

	public GitBlit() {
		this.goSettings = null;
		this.goBaseFolder = null;
	}

	public GitBlit(IStoredSettings settings, File baseFolder) {
		this.goSettings = settings;
		this.goBaseFolder = baseFolder;
		gitblit = this;
	}

	/**
	 * Returns the Gitblit singleton.
	 *
	 * @return gitblit singleton
	 */
	public static GitBlit self() {
		return gitblit;
	}

	@SuppressWarnings("unchecked")
	public static <X> X getManager(Class<X> managerClass) {
		if (managerClass.isAssignableFrom(GitBlit.class)) {
			return (X) gitblit;
		}

		for (IManager manager : gitblit.managers) {
			if (managerClass.isAssignableFrom(manager.getClass())) {
				return (X) manager;
			}
		}
		return null;
	}

	/**
	 * Returns the path of the proposals folder. This method checks to see if
	 * Gitblit is running on a cloud service and may return an adjusted path.
	 *
	 * @return the proposals folder path
	 */
	@Override
	public File getProposalsFolder() {
		return getManager(IRuntimeManager.class).getFileOrFolder(Keys.federation.proposalsFolder, "${baseFolder}/proposals");
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

		// git daemon url
		String gitDaemonUrl = getGitDaemonUrl(request, user, repository);
		if (!StringUtils.isEmpty(gitDaemonUrl)) {
			AccessPermission permission = getGitDaemonAccessPermission(user, repository);
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
				if(!StringUtils.isEmpty(username)) {
					list.add(new RepositoryUrl(MessageFormat.format(url, repository.name, username), null));
				}
			} else {
				// external url does not require username
				list.add(new RepositoryUrl(MessageFormat.format(url, repository.name), null));
			}
		}
		return list;
	}

	protected String getRepositoryUrl(HttpServletRequest request, String username, RepositoryModel repository) {
		StringBuilder sb = new StringBuilder();
		sb.append(HttpUtils.getGitblitURL(request));
		sb.append(Constants.GIT_PATH);
		sb.append(repository.name);

		// inject username into repository url if authentication is required
		if (repository.accessRestriction.exceeds(AccessRestrictionType.NONE)
				&& !StringUtils.isEmpty(username)) {
			sb.insert(sb.indexOf("://") + 3, username + "@");
		}
		return sb.toString();
	}

	protected String getGitDaemonUrl(HttpServletRequest request, UserModel user, RepositoryModel repository) {
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

	protected AccessPermission getGitDaemonAccessPermission(UserModel user, RepositoryModel repository) {
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

	/**
	 * Returns the list of custom client applications to be used for the
	 * repository url panel;
	 *
	 * @return a collection of client applications
	 */
	@Override
	public Collection<GitClientApplication> getClientApplications() {
		// prefer user definitions, if they exist
		File userDefs = new File(getManager(IRuntimeManager.class).getBaseFolder(), "clientapps.json");
		if (userDefs.exists()) {
			Date lastModified = new Date(userDefs.lastModified());
			if (clientApplications.hasCurrent("user", lastModified)) {
				return clientApplications.getObject("user");
			} else {
				// (re)load user definitions
				try {
					InputStream is = new FileInputStream(userDefs);
					Collection<GitClientApplication> clients = readClientApplications(is);
					is.close();
					if (clients != null) {
						clientApplications.updateObject("user", lastModified, clients);
						return clients;
					}
				} catch (IOException e) {
					logger.error("Failed to deserialize " + userDefs.getAbsolutePath(), e);
				}
			}
		}

		// no user definitions, use system definitions
		if (!clientApplications.hasCurrent("system", new Date(0))) {
			try {
				InputStream is = getClass().getResourceAsStream("/clientapps.json");
				Collection<GitClientApplication> clients = readClientApplications(is);
				is.close();
				if (clients != null) {
					clientApplications.updateObject("system", new Date(0), clients);
				}
			} catch (IOException e) {
				logger.error("Failed to deserialize clientapps.json resource!", e);
			}
		}

		return clientApplications.getObject("system");
	}

	private Collection<GitClientApplication> readClientApplications(InputStream is) {
		try {
			Type type = new TypeToken<Collection<GitClientApplication>>() {
			}.getType();
			InputStreamReader reader = new InputStreamReader(is);
			Gson gson = JsonUtils.gson();
			Collection<GitClientApplication> links = gson.fromJson(reader, type);
			return links;
		} catch (JsonIOException e) {
			logger.error("Error deserializing client applications!", e);
		} catch (JsonSyntaxException e) {
			logger.error("Error deserializing client applications!", e);
		}
		return null;
	}

	/**
	 * Open a file resource using the Servlet container.
	 * @param file to open
	 * @return InputStream of the opened file
	 * @throws ResourceStreamNotFoundException
	 */
	public InputStream getResourceAsStream(String file) throws ResourceStreamNotFoundException {
		ContextRelativeResource res = WicketUtils.getResource(file);
		return res.getResourceStream().getInputStream();
	}

	@Override
	public UserModel getFederationUser() {
		// the federation user is an administrator
		UserModel federationUser = new UserModel(Constants.FEDERATION_USER);
		federationUser.canAdmin = true;
		return federationUser;
	}

	/**
	 * Adds/updates a complete user object keyed by username. This method allows
	 * for renaming a user.
	 *
	 * @see IUserService.updateUserModel(String, UserModel)
	 * @param username
	 * @param user
	 * @param isCreate
	 * @throws GitBlitException
	 */
	@Override
	public void updateUserModel(String username, UserModel user, boolean isCreate)
			throws GitBlitException {
		if (!username.equalsIgnoreCase(user.username)) {
			if (getManager(IUserManager.class).getUserModel(user.username) != null) {
				throw new GitBlitException(MessageFormat.format(
						"Failed to rename ''{0}'' because ''{1}'' already exists.", username,
						user.username));
			}

			// rename repositories and owner fields for all repositories
			for (RepositoryModel model : getManager(IRepositoryManager.class).getRepositoryModels(user)) {
				if (model.isUsersPersonalRepository(username)) {
					// personal repository
					model.addOwner(user.username);
					String oldRepositoryName = model.name;
					model.name = user.getPersonalPath() + model.name.substring(model.projectPath.length());
					model.projectPath = user.getPersonalPath();
					getManager(IRepositoryManager.class).updateRepositoryModel(oldRepositoryName, model, false);
				} else if (model.isOwner(username)) {
					// common/shared repo
					model.addOwner(user.username);
					getManager(IRepositoryManager.class).updateRepositoryModel(model.name, model, false);
				}
			}
		}
		if (!getManager(IUserManager.class).updateUserModel(username, user)) {
			throw new GitBlitException(isCreate ? "Failed to add user!" : "Failed to update user!");
		}
	}

	/**
	 * Updates the TeamModel object for the specified name.
	 *
	 * @param teamname
	 * @param team
	 * @param isCreate
	 */
	@Override
	public void updateTeamModel(String teamname, TeamModel team, boolean isCreate)
			throws GitBlitException {
		if (!teamname.equalsIgnoreCase(team.name)) {
			if (getManager(IUserManager.class).getTeamModel(team.name) != null) {
				throw new GitBlitException(MessageFormat.format(
						"Failed to rename ''{0}'' because ''{1}'' already exists.", teamname,
						team.name));
			}
		}
		if (!getManager(IUserManager.class).updateTeamModel(teamname, team)) {
			throw new GitBlitException(isCreate ? "Failed to add team!" : "Failed to update team!");
		}
	}


	/**
	 * Returns Gitblit's scheduled executor service for scheduling tasks.
	 *
	 * @return scheduledExecutor
	 */
	public ScheduledExecutorService executor() {
		return scheduledExecutor;
	}

	@Override
	public boolean canFederate() {
		String passphrase = settings.getString(Keys.federation.passphrase, "");
		return !StringUtils.isEmpty(passphrase);
	}

	/**
	 * Configures this Gitblit instance to pull any registered federated gitblit
	 * instances.
	 */
	private void configureFederation() {
		boolean validPassphrase = true;
		String passphrase = settings.getString(Keys.federation.passphrase, "");
		if (StringUtils.isEmpty(passphrase)) {
			logger.warn("Federation passphrase is blank! This server can not be PULLED from.");
			validPassphrase = false;
		}
		if (validPassphrase) {
			// standard tokens
			for (FederationToken tokenType : FederationToken.values()) {
				logger.info(MessageFormat.format("Federation {0} token = {1}", tokenType.name(),
						getFederationToken(tokenType)));
			}

			// federation set tokens
			for (String set : settings.getStrings(Keys.federation.sets)) {
				logger.info(MessageFormat.format("Federation Set {0} token = {1}", set,
						getFederationToken(set)));
			}
		}

		// Schedule the federation executor
		List<FederationModel> registrations = getFederationRegistrations();
		if (registrations.size() > 0) {
			FederationPullExecutor executor = new FederationPullExecutor(registrations, true);
			scheduledExecutor.schedule(executor, 1, TimeUnit.MINUTES);
		}
	}

	/**
	 * Returns the list of federated gitblit instances that this instance will
	 * try to pull.
	 *
	 * @return list of registered gitblit instances
	 */
	@Override
	public List<FederationModel> getFederationRegistrations() {
		if (federationRegistrations.isEmpty()) {
			federationRegistrations.addAll(FederationUtils.getFederationRegistrations(settings));
		}
		return federationRegistrations;
	}

	/**
	 * Retrieve the specified federation registration.
	 *
	 * @param name
	 *            the name of the registration
	 * @return a federation registration
	 */
	@Override
	public FederationModel getFederationRegistration(String url, String name) {
		// check registrations
		for (FederationModel r : getFederationRegistrations()) {
			if (r.name.equals(name) && r.url.equals(url)) {
				return r;
			}
		}

		// check the results
		for (FederationModel r : getFederationResultRegistrations()) {
			if (r.name.equals(name) && r.url.equals(url)) {
				return r;
			}
		}
		return null;
	}

	/**
	 * Returns the list of federation sets.
	 *
	 * @return list of federation sets
	 */
	@Override
	public List<FederationSet> getFederationSets(String gitblitUrl) {
		List<FederationSet> list = new ArrayList<FederationSet>();
		// generate standard tokens
		for (FederationToken type : FederationToken.values()) {
			FederationSet fset = new FederationSet(type.toString(), type, getFederationToken(type));
			fset.repositories = getRepositories(gitblitUrl, fset.token);
			list.add(fset);
		}
		// generate tokens for federation sets
		for (String set : settings.getStrings(Keys.federation.sets)) {
			FederationSet fset = new FederationSet(set, FederationToken.REPOSITORIES,
					getFederationToken(set));
			fset.repositories = getRepositories(gitblitUrl, fset.token);
			list.add(fset);
		}
		return list;
	}

	/**
	 * Returns the list of possible federation tokens for this Gitblit instance.
	 *
	 * @return list of federation tokens
	 */
	@Override
	public List<String> getFederationTokens() {
		List<String> tokens = new ArrayList<String>();
		// generate standard tokens
		for (FederationToken type : FederationToken.values()) {
			tokens.add(getFederationToken(type));
		}
		// generate tokens for federation sets
		for (String set : settings.getStrings(Keys.federation.sets)) {
			tokens.add(getFederationToken(set));
		}
		return tokens;
	}

	/**
	 * Returns the specified federation token for this Gitblit instance.
	 *
	 * @param type
	 * @return a federation token
	 */
	@Override
	public String getFederationToken(FederationToken type) {
		return getFederationToken(type.name());
	}

	/**
	 * Returns the specified federation token for this Gitblit instance.
	 *
	 * @param value
	 * @return a federation token
	 */
	@Override
	public String getFederationToken(String value) {
		String passphrase = settings.getString(Keys.federation.passphrase, "");
		return StringUtils.getSHA1(passphrase + "-" + value);
	}

	/**
	 * Compares the provided token with this Gitblit instance's tokens and
	 * determines if the requested permission may be granted to the token.
	 *
	 * @param req
	 * @param token
	 * @return true if the request can be executed
	 */
	@Override
	public boolean validateFederationRequest(FederationRequest req, String token) {
		String all = getFederationToken(FederationToken.ALL);
		String unr = getFederationToken(FederationToken.USERS_AND_REPOSITORIES);
		String jur = getFederationToken(FederationToken.REPOSITORIES);
		switch (req) {
		case PULL_REPOSITORIES:
			return token.equals(all) || token.equals(unr) || token.equals(jur);
		case PULL_USERS:
		case PULL_TEAMS:
			return token.equals(all) || token.equals(unr);
		case PULL_SETTINGS:
		case PULL_SCRIPTS:
			return token.equals(all);
		default:
			break;
		}
		return false;
	}

	/**
	 * Acknowledge and cache the status of a remote Gitblit instance.
	 *
	 * @param identification
	 *            the identification of the pulling Gitblit instance
	 * @param registration
	 *            the registration from the pulling Gitblit instance
	 * @return true if acknowledged
	 */
	@Override
	public boolean acknowledgeFederationStatus(String identification, FederationModel registration) {
		// reset the url to the identification of the pulling Gitblit instance
		registration.url = identification;
		String id = identification;
		if (!StringUtils.isEmpty(registration.folder)) {
			id += "-" + registration.folder;
		}
		federationPullResults.put(id, registration);
		return true;
	}

	/**
	 * Returns the list of registration results.
	 *
	 * @return the list of registration results
	 */
	@Override
	public List<FederationModel> getFederationResultRegistrations() {
		return new ArrayList<FederationModel>(federationPullResults.values());
	}

	/**
	 * Submit a federation proposal. The proposal is cached locally and the
	 * Gitblit administrator(s) are notified via email.
	 *
	 * @param proposal
	 *            the proposal
	 * @param gitblitUrl
	 *            the url of your gitblit instance to send an email to
	 *            administrators
	 * @return true if the proposal was submitted
	 */
	@Override
	public boolean submitFederationProposal(FederationProposal proposal, String gitblitUrl) {
		// convert proposal to json
		String json = JsonUtils.toJsonString(proposal);

		try {
			// make the proposals folder
			File proposalsFolder = getProposalsFolder();
			proposalsFolder.mkdirs();

			// cache json to a file
			File file = new File(proposalsFolder, proposal.token + Constants.PROPOSAL_EXT);
			com.gitblit.utils.FileUtils.writeContent(file, json);
		} catch (Exception e) {
			logger.error(MessageFormat.format("Failed to cache proposal from {0}", proposal.url), e);
		}

		// send an email, if possible
		getManager(INotificationManager.class).sendMailToAdministrators("Federation proposal from " + proposal.url,
				"Please review the proposal @ " + gitblitUrl + "/proposal/" + proposal.token);
		return true;
	}

	/**
	 * Returns the list of pending federation proposals
	 *
	 * @return list of federation proposals
	 */
	@Override
	public List<FederationProposal> getPendingFederationProposals() {
		List<FederationProposal> list = new ArrayList<FederationProposal>();
		File folder = getProposalsFolder();
		if (folder.exists()) {
			File[] files = folder.listFiles(new FileFilter() {
				@Override
				public boolean accept(File file) {
					return file.isFile()
							&& file.getName().toLowerCase().endsWith(Constants.PROPOSAL_EXT);
				}
			});
			for (File file : files) {
				String json = com.gitblit.utils.FileUtils.readContent(file, null);
				FederationProposal proposal = JsonUtils.fromJsonString(json,
						FederationProposal.class);
				list.add(proposal);
			}
		}
		return list;
	}

	/**
	 * Get repositories for the specified token.
	 *
	 * @param gitblitUrl
	 *            the base url of this gitblit instance
	 * @param token
	 *            the federation token
	 * @return a map of <cloneurl, RepositoryModel>
	 */
	@Override
	public Map<String, RepositoryModel> getRepositories(String gitblitUrl, String token) {
		Map<String, String> federationSets = new HashMap<String, String>();
		for (String set : settings.getStrings(Keys.federation.sets)) {
			federationSets.put(getFederationToken(set), set);
		}

		// Determine the Gitblit clone url
		StringBuilder sb = new StringBuilder();
		sb.append(gitblitUrl);
		sb.append(Constants.GIT_PATH);
		sb.append("{0}");
		String cloneUrl = sb.toString();

		// Retrieve all available repositories
		UserModel user = getFederationUser();
		List<RepositoryModel> list = getManager(IRepositoryManager.class).getRepositoryModels(user);

		// create the [cloneurl, repositoryModel] map
		Map<String, RepositoryModel> repositories = new HashMap<String, RepositoryModel>();
		for (RepositoryModel model : list) {
			// by default, setup the url for THIS repository
			String url = MessageFormat.format(cloneUrl, model.name);
			switch (model.federationStrategy) {
			case EXCLUDE:
				// skip this repository
				continue;
			case FEDERATE_ORIGIN:
				// federate the origin, if it is defined
				if (!StringUtils.isEmpty(model.origin)) {
					url = model.origin;
				}
				break;
			default:
				break;
			}

			if (federationSets.containsKey(token)) {
				// include repositories only for federation set
				String set = federationSets.get(token);
				if (model.federationSets.contains(set)) {
					repositories.put(url, model);
				}
			} else {
				// standard federation token for ALL
				repositories.put(url, model);
			}
		}
		return repositories;
	}

	/**
	 * Creates a proposal from the token.
	 *
	 * @param gitblitUrl
	 *            the url of this Gitblit instance
	 * @param token
	 * @return a potential proposal
	 */
	@Override
	public FederationProposal createFederationProposal(String gitblitUrl, String token) {
		FederationToken tokenType = FederationToken.REPOSITORIES;
		for (FederationToken type : FederationToken.values()) {
			if (token.equals(getFederationToken(type))) {
				tokenType = type;
				break;
			}
		}
		Map<String, RepositoryModel> repositories = getRepositories(gitblitUrl, token);
		FederationProposal proposal = new FederationProposal(gitblitUrl, tokenType, token,
				repositories);
		return proposal;
	}

	/**
	 * Returns the proposal identified by the supplied token.
	 *
	 * @param token
	 * @return the specified proposal or null
	 */
	@Override
	public FederationProposal getPendingFederationProposal(String token) {
		List<FederationProposal> list = getPendingFederationProposals();
		for (FederationProposal proposal : list) {
			if (proposal.token.equals(token)) {
				return proposal;
			}
		}
		return null;
	}

	/**
	 * Deletes a pending federation proposal.
	 *
	 * @param a
	 *            proposal
	 * @return true if the proposal was deleted
	 */
	@Override
	public boolean deletePendingFederationProposal(FederationProposal proposal) {
		File folder = getProposalsFolder();
		File file = new File(folder, proposal.token + Constants.PROPOSAL_EXT);
		return file.delete();
	}

	/**
	 * Parse the properties file and aggregate all the comments by the setting
	 * key. A setting model tracks the current value, the default value, the
	 * description of the setting and and directives about the setting.
	 *
	 * @return Map<String, SettingModel>
	 */
	private ServerSettings loadSettingModels(ServerSettings settingsModel) {
		// this entire "supports" concept will go away with user service refactoring
		UserModel externalUser = new UserModel(Constants.EXTERNAL_ACCOUNT);
		externalUser.password = Constants.EXTERNAL_ACCOUNT;
		IUserManager userManager = getManager(IUserManager.class);
		settingsModel.supportsCredentialChanges = userManager.supportsCredentialChanges(externalUser);
		settingsModel.supportsDisplayNameChanges = userManager.supportsDisplayNameChanges(externalUser);
		settingsModel.supportsEmailAddressChanges = userManager.supportsEmailAddressChanges(externalUser);
		settingsModel.supportsTeamMembershipChanges = userManager.supportsTeamMembershipChanges(externalUser);
		try {
			// Read bundled Gitblit properties to extract setting descriptions.
			// This copy is pristine and only used for populating the setting
			// models map.
			InputStream is = getClass().getResourceAsStream("/reference.properties");
			BufferedReader propertiesReader = new BufferedReader(new InputStreamReader(is));
			StringBuilder description = new StringBuilder();
			SettingModel setting = new SettingModel();
			String line = null;
			while ((line = propertiesReader.readLine()) != null) {
				if (line.length() == 0) {
					description.setLength(0);
					setting = new SettingModel();
				} else {
					if (line.charAt(0) == '#') {
						if (line.length() > 1) {
							String text = line.substring(1).trim();
							if (SettingModel.CASE_SENSITIVE.equals(text)) {
								setting.caseSensitive = true;
							} else if (SettingModel.RESTART_REQUIRED.equals(text)) {
								setting.restartRequired = true;
							} else if (SettingModel.SPACE_DELIMITED.equals(text)) {
								setting.spaceDelimited = true;
							} else if (text.startsWith(SettingModel.SINCE)) {
								try {
									setting.since = text.split(" ")[1];
								} catch (Exception e) {
									setting.since = text;
								}
							} else {
								description.append(text);
								description.append('\n');
							}
						}
					} else {
						String[] kvp = line.split("=", 2);
						String key = kvp[0].trim();
						setting.name = key;
						setting.defaultValue = kvp[1].trim();
						setting.currentValue = setting.defaultValue;
						setting.description = description.toString().trim();
						settingsModel.add(setting);
						description.setLength(0);
						setting = new SettingModel();
					}
				}
			}
			propertiesReader.close();
		} catch (NullPointerException e) {
			logger.error("Failed to find resource copy of gitblit.properties");
		} catch (IOException e) {
			logger.error("Failed to load resource copy of gitblit.properties");
		}
		return settingsModel;
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
		}
	}

	protected void configureGitDaemon() {
		int port = settings.getInteger(Keys.git.daemonPort, 0);
		String bindInterface = settings.getString(Keys.git.daemonBindInterface, "localhost");
		if (port > 0) {
			try {
				// HACK temporary pending manager separation and injection
				Gitblit gitblit = new Gitblit(
						getManager(IRuntimeManager.class),
						getManager(INotificationManager.class),
						getManager(IUserManager.class),
						getManager(ISessionManager.class),
						getManager(IRepositoryManager.class),
						getManager(IProjectManager.class),
						this,
						this);
				gitDaemon = new GitDaemon(gitblit);
				gitDaemon.start();
			} catch (IOException e) {
				gitDaemon = null;
				logger.error(MessageFormat.format("Failed to start Git daemon on {0}:{1,number,0}", bindInterface, port), e);
			}
		}
	}

	protected final Logger getLogger() {
		return logger;
	}

	protected final ScheduledExecutorService getScheduledExecutor() {
		return scheduledExecutor;
	}

	/**
	 * Configure Gitblit from the web.xml, if no configuration has already been
	 * specified.
	 *
	 * @see ServletContextListener.contextInitialize(ServletContextEvent)
	 */
	@Override
	protected void beforeServletInjection(ServletContext context) {
		ObjectGraph injector = getInjector(context);

		// create the runtime settings object
		IStoredSettings runtimeSettings = injector.get(IStoredSettings.class);
		this.settings = runtimeSettings; // XXX remove me eventually
		final File baseFolder;

		if (goSettings != null) {
			// Gitblit GO
			logger.debug("configuring Gitblit GO");
			baseFolder = configureGO(context, goSettings, goBaseFolder, runtimeSettings);
		} else {
			// servlet container
			WebXmlSettings webxmlSettings = new WebXmlSettings(context);
			String contextRealPath = context.getRealPath("/");
			File contextFolder = (contextRealPath != null) ? new File(contextRealPath) : null;

			if (!StringUtils.isEmpty(System.getenv("OPENSHIFT_DATA_DIR"))) {
				// RedHat OpenShift
				logger.debug("configuring Gitblit Express");
				baseFolder = configureExpress(context, webxmlSettings, contextFolder, runtimeSettings);
			} else {
				// standard WAR
				logger.debug("configuring Gitblit WAR");
				baseFolder = configureWAR(context, webxmlSettings, contextFolder, runtimeSettings);
			}

			// Test for Tomcat forward-slash/%2F issue and auto-adjust settings
			ContainerUtils.CVE_2007_0450.test(runtimeSettings);
		}

		// Runtime manager is a container for settings and other parameters
		IRuntimeManager runtime = startManager(injector, IRuntimeManager.class);
		runtime.setBaseFolder(baseFolder);
		runtime.getStatus().isGO = goSettings != null;
		runtime.getStatus().servletContainer = context.getServerInfo();

		startManager(injector, INotificationManager.class);
		startManager(injector, IUserManager.class);
		startManager(injector, ISessionManager.class);
		startManager(injector, IRepositoryManager.class);
		startManager(injector, IProjectManager.class);

		logger.info("Gitblit base folder     = " + baseFolder.getAbsolutePath());

		loadSettingModels(runtime.getSettingsModel());

		if (true/*startFederation*/) {
			configureFederation();
		}
		configureFanout();
		configureGitDaemon();
	}

	/**
	 * Configures Gitblit GO
	 *
	 * @param context
	 * @param settings
	 * @param baseFolder
	 * @param runtimeSettings
	 * @return the base folder
	 */
	protected File configureGO(
			ServletContext context,
			IStoredSettings goSettings,
			File goBaseFolder,
			IStoredSettings runtimeSettings) {

		// merge the stored settings into the runtime settings
		//
		// if runtimeSettings is also a FileSettings w/o a specified target file,
		// the target file for runtimeSettings is set to "localSettings".
		runtimeSettings.merge(goSettings);
		File base = goBaseFolder;
		return base;
	}


	/**
	 * Configures a standard WAR instance of Gitblit.
	 *
	 * @param context
	 * @param webxmlSettings
	 * @param contextFolder
	 * @param runtimeSettings
	 * @return the base folder
	 */
	protected File configureWAR(
			ServletContext context,
			WebXmlSettings webxmlSettings,
			File contextFolder,
			IStoredSettings runtimeSettings) {

		// Gitblit is running in a standard servlet container
		logger.info("WAR contextFolder is " + ((contextFolder != null) ? contextFolder.getAbsolutePath() : "<empty>"));

		String path = webxmlSettings.getString(Constants.baseFolder, Constants.contextFolder$ + "/WEB-INF/data");

		if (path.contains(Constants.contextFolder$) && contextFolder == null) {
			// warn about null contextFolder (issue-199)
			logger.error("");
			logger.error(MessageFormat.format("\"{0}\" depends on \"{1}\" but \"{2}\" is returning NULL for \"{1}\"!",
					Constants.baseFolder, Constants.contextFolder$, context.getServerInfo()));
			logger.error(MessageFormat.format("Please specify a non-parameterized path for <context-param> {0} in web.xml!!", Constants.baseFolder));
			logger.error(MessageFormat.format("OR configure your servlet container to specify a \"{0}\" parameter in the context configuration!!", Constants.baseFolder));
			logger.error("");
		}

		try {
			// try to lookup JNDI env-entry for the baseFolder
			InitialContext ic = new InitialContext();
			Context env = (Context) ic.lookup("java:comp/env");
			String val = (String) env.lookup("baseFolder");
			if (!StringUtils.isEmpty(val)) {
				path = val;
			}
		} catch (NamingException n) {
			logger.error("Failed to get JNDI env-entry: " + n.getExplanation());
		}

		File base = com.gitblit.utils.FileUtils.resolveParameter(Constants.contextFolder$, contextFolder, path);
		base.mkdirs();

		// try to extract the data folder resource to the baseFolder
		File localSettings = new File(base, "gitblit.properties");
		if (!localSettings.exists()) {
			extractResources(context, "/WEB-INF/data/", base);
		}

		// delegate all config to baseFolder/gitblit.properties file
		FileSettings fileSettings = new FileSettings(localSettings.getAbsolutePath());

		// merge the stored settings into the runtime settings
		//
		// if runtimeSettings is also a FileSettings w/o a specified target file,
		// the target file for runtimeSettings is set to "localSettings".
		runtimeSettings.merge(fileSettings);

		return base;
	}

	/**
	 * Configures an OpenShift instance of Gitblit.
	 *
	 * @param context
	 * @param webxmlSettings
	 * @param contextFolder
	 * @param runtimeSettings
	 * @return the base folder
	 */
	private File configureExpress(
			ServletContext context,
			WebXmlSettings webxmlSettings,
			File contextFolder,
			IStoredSettings runtimeSettings) {

		// Gitblit is running in OpenShift/JBoss
		String openShift = System.getenv("OPENSHIFT_DATA_DIR");
		File base = new File(openShift);
		logger.info("EXPRESS contextFolder is " + contextFolder.getAbsolutePath());

		// Copy the included scripts to the configured groovy folder
		String path = webxmlSettings.getString(Keys.groovy.scriptsFolder, "groovy");
		File localScripts = com.gitblit.utils.FileUtils.resolveParameter(Constants.baseFolder$, base, path);
		if (!localScripts.exists()) {
			File warScripts = new File(contextFolder, "/WEB-INF/data/groovy");
			if (!warScripts.equals(localScripts)) {
				try {
					com.gitblit.utils.FileUtils.copy(localScripts, warScripts.listFiles());
				} catch (IOException e) {
					logger.error(MessageFormat.format(
							"Failed to copy included Groovy scripts from {0} to {1}",
							warScripts, localScripts));
				}
			}
		}

		// merge the WebXmlSettings into the runtime settings (for backwards-compatibilty)
		runtimeSettings.merge(webxmlSettings);

		// settings are to be stored in openshift/gitblit.properties
		File localSettings = new File(base, "gitblit.properties");
		FileSettings fileSettings = new FileSettings(localSettings.getAbsolutePath());

		// merge the stored settings into the runtime settings
		//
		// if runtimeSettings is also a FileSettings w/o a specified target file,
		// the target file for runtimeSettings is set to "localSettings".
		runtimeSettings.merge(fileSettings);

		return base;
	}

	protected void extractResources(ServletContext context, String path, File toDir) {
		for (String resource : context.getResourcePaths(path)) {
			// extract the resource to the directory if it does not exist
			File f = new File(toDir, resource.substring(path.length()));
			if (!f.exists()) {
				InputStream is = null;
				OutputStream os = null;
				try {
					if (resource.charAt(resource.length() - 1) == '/') {
						// directory
						f.mkdirs();
						extractResources(context, resource, f);
					} else {
						// file
						f.getParentFile().mkdirs();
						is = context.getResourceAsStream(resource);
						os = new FileOutputStream(f);
						byte [] buffer = new byte[4096];
						int len = 0;
						while ((len = is.read(buffer)) > -1) {
							os.write(buffer, 0, len);
						}
					}
				} catch (FileNotFoundException e) {
					logger.error("Failed to find resource \"" + resource + "\"", e);
				} catch (IOException e) {
					logger.error("Failed to copy resource \"" + resource + "\" to " + f, e);
				} finally {
					if (is != null) {
						try {
							is.close();
						} catch (IOException e) {
							// ignore
						}
					}
					if (os != null) {
						try {
							os.close();
						} catch (IOException e) {
							// ignore
						}
					}
				}
			}
		}
	}

	/**
	 * Gitblit is being shutdown either because the servlet container is
	 * shutting down or because the servlet container is re-deploying Gitblit.
	 */
	@Override
	protected void destroyContext(ServletContext context) {
		logger.info("Gitblit context destroyed by servlet container.");
		for (IManager manager : managers) {
			logger.debug("stopping {}", manager.getClass().getSimpleName());
			manager.stop();
		}

		scheduledExecutor.shutdownNow();
		if (fanoutService != null) {
			fanoutService.stop();
		}
		if (gitDaemon != null) {
			gitDaemon.stop();
		}
	}

	/**
	 * Creates a personal fork of the specified repository. The clone is view
	 * restricted by default and the owner of the source repository is given
	 * access to the clone.
	 *
	 * @param repository
	 * @param user
	 * @return the repository model of the fork, if successful
	 * @throws GitBlitException
	 */
	@Override
	public RepositoryModel fork(RepositoryModel repository, UserModel user) throws GitBlitException {
		String cloneName = MessageFormat.format("{0}/{1}.git", user.getPersonalPath(), StringUtils.stripDotGit(StringUtils.getLastPathElement(repository.name)));
		String fromUrl = MessageFormat.format("file://{0}/{1}", getManager(IRepositoryManager.class).getRepositoriesFolder().getAbsolutePath(), repository.name);

		// clone the repository
		try {
			JGitUtils.cloneRepository(getManager(IRepositoryManager.class).getRepositoriesFolder(), cloneName, fromUrl, true, null);
		} catch (Exception e) {
			throw new GitBlitException(e);
		}

		// create a Gitblit repository model for the clone
		RepositoryModel cloneModel = repository.cloneAs(cloneName);
		// owner has REWIND/RW+ permissions
		cloneModel.addOwner(user.username);
		getManager(IRepositoryManager.class).updateRepositoryModel(cloneName, cloneModel, false);

		// add the owner of the source repository to the clone's access list
		if (!ArrayUtils.isEmpty(repository.owners)) {
			for (String owner : repository.owners) {
				UserModel originOwner = getManager(IUserManager.class).getUserModel(owner);
				if (originOwner != null) {
					originOwner.setRepositoryPermission(cloneName, AccessPermission.CLONE);
					updateUserModel(originOwner.username, originOwner, false);
				}
			}
		}

		// grant origin's user list clone permission to fork
		List<String> users = getManager(IRepositoryManager.class).getRepositoryUsers(repository);
		List<UserModel> cloneUsers = new ArrayList<UserModel>();
		for (String name : users) {
			if (!name.equalsIgnoreCase(user.username)) {
				UserModel cloneUser = getManager(IUserManager.class).getUserModel(name);
				if (cloneUser.canClone(repository)) {
					// origin user can clone origin, grant clone access to fork
					cloneUser.setRepositoryPermission(cloneName, AccessPermission.CLONE);
				}
				cloneUsers.add(cloneUser);
			}
		}
		getManager(IUserManager.class).updateUserModels(cloneUsers);

		// grant origin's team list clone permission to fork
		List<String> teams = getManager(IRepositoryManager.class).getRepositoryTeams(repository);
		List<TeamModel> cloneTeams = new ArrayList<TeamModel>();
		for (String name : teams) {
			TeamModel cloneTeam = getManager(IUserManager.class).getTeamModel(name);
			if (cloneTeam.canClone(repository)) {
				// origin team can clone origin, grant clone access to fork
				cloneTeam.setRepositoryPermission(cloneName, AccessPermission.CLONE);
			}
			cloneTeams.add(cloneTeam);
		}
		getManager(IUserManager.class).updateTeamModels(cloneTeams);

		// add this clone to the cached model
		getManager(IRepositoryManager.class).addToCachedRepositoryList(cloneModel);
		return cloneModel;
	}

	@Override
	protected Object [] getModules() {
		return new Object [] { new DaggerModule(this) };
	}

	protected <X extends IManager> X startManager(ObjectGraph injector, Class<X> clazz) {
		logger.debug("injecting and starting {}", clazz.getSimpleName());
		X x = injector.get(clazz);
		x.setup();
		managers.add(x);
		return x;
	}

	/**
	 * Instantiate and inject all filters and servlets into the container using
	 * the servlet 3 specification.
	 */
	@Override
	protected void injectServlets(ServletContext context) {
		// access restricted servlets
		serve(context, Constants.GIT_PATH, GitServlet.class, GitFilter.class);
		serve(context, Constants.PAGES, PagesServlet.class, PagesFilter.class);
		serve(context, Constants.RPC_PATH, RpcServlet.class, RpcFilter.class);
		serve(context, Constants.ZIP_PATH, DownloadZipServlet.class, DownloadZipFilter.class);
		serve(context, Constants.SYNDICATION_PATH, SyndicationServlet.class, SyndicationFilter.class);

		// servlets
		serve(context, Constants.FEDERATION_PATH, FederationServlet.class);
		serve(context, Constants.SPARKLESHARE_INVITE_PATH, SparkleShareInviteServlet.class);
		serve(context, Constants.BRANCH_GRAPH_PATH, BranchGraphServlet.class);
		file(context, "/robots.txt", RobotsTxtServlet.class);
		file(context, "/logo.png", LogoServlet.class);

		// optional force basic authentication
		filter(context, "/*", EnforceAuthenticationFilter.class, null);

		// Wicket
		String toIgnore = StringUtils.flattenStrings(getRegisteredPaths(), ",");
		Map<String, String> params = new HashMap<String, String>();
		params.put(GitblitWicketFilter.FILTER_MAPPING_PARAM, "/*");
		params.put(GitblitWicketFilter.IGNORE_PATHS_PARAM, toIgnore);
		filter(context, "/*", GitblitWicketFilter.class, params);
	}
}
