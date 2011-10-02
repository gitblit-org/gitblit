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
import java.io.FileFilter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.http.Cookie;

import org.apache.wicket.protocol.http.WebResponse;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.resolver.FileResolver;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.FederationRequest;
import com.gitblit.Constants.FederationStrategy;
import com.gitblit.Constants.FederationToken;
import com.gitblit.models.FederationModel;
import com.gitblit.models.FederationProposal;
import com.gitblit.models.FederationSet;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ByteFormat;
import com.gitblit.utils.FederationUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.JsonUtils;
import com.gitblit.utils.StringUtils;

/**
 * GitBlit is the servlet context listener singleton that acts as the core for
 * the web ui and the servlets. This class is either directly instantiated by
 * the GitBlitServer class (Gitblit GO) or is reflectively instantiated from the
 * definition in the web.xml file (Gitblit WAR).
 * 
 * This class is the central logic processor for Gitblit. All settings, user
 * object, and repository object operations pass through this class.
 * 
 * Repository Resolution. There are two pathways for finding repositories. One
 * pathway, for web ui display and repository authentication & authorization, is
 * within this class. The other pathway is through the standard GitServlet.
 * 
 * @author James Moger
 * 
 */
public class GitBlit implements ServletContextListener {

	private static GitBlit gitblit;

	private final Logger logger = LoggerFactory.getLogger(GitBlit.class);

	private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(5);

	private final List<FederationModel> federationRegistrations = Collections
			.synchronizedList(new ArrayList<FederationModel>());

	private final Map<String, FederationModel> federationPullResults = new ConcurrentHashMap<String, FederationModel>();

	private RepositoryResolver<Void> repositoryResolver;

	private File repositoriesFolder;

	private boolean exportAll = true;

	private IUserService userService;

	private IStoredSettings settings;

	private MailExecutor mailExecutor;

	public GitBlit() {
		if (gitblit == null) {
			// set the static singleton reference
			gitblit = this;
		}
	}

	/**
	 * Returns the Gitblit singleton.
	 * 
	 * @return gitblit singleton
	 */
	public static GitBlit self() {
		if (gitblit == null) {
			new GitBlit();
		}
		return gitblit;
	}

	/**
	 * Determine if this is the GO variant of Gitblit.
	 * 
	 * @return true if this is the GO variant of Gitblit.
	 */
	public static boolean isGO() {
		return self().settings instanceof FileSettings;
	}

	/**
	 * Returns the boolean value for the specified key. If the key does not
	 * exist or the value for the key can not be interpreted as a boolean, the
	 * defaultValue is returned.
	 * 
	 * @see IStoredSettings.getBoolean(String, boolean)
	 * @param key
	 * @param defaultValue
	 * @return key value or defaultValue
	 */
	public static boolean getBoolean(String key, boolean defaultValue) {
		return self().settings.getBoolean(key, defaultValue);
	}

	/**
	 * Returns the integer value for the specified key. If the key does not
	 * exist or the value for the key can not be interpreted as an integer, the
	 * defaultValue is returned.
	 * 
	 * @see IStoredSettings.getInteger(String key, int defaultValue)
	 * @param key
	 * @param defaultValue
	 * @return key value or defaultValue
	 */
	public static int getInteger(String key, int defaultValue) {
		return self().settings.getInteger(key, defaultValue);
	}

	/**
	 * Returns the char value for the specified key. If the key does not exist
	 * or the value for the key can not be interpreted as a character, the
	 * defaultValue is returned.
	 * 
	 * @see IStoredSettings.getChar(String key, char defaultValue)
	 * @param key
	 * @param defaultValue
	 * @return key value or defaultValue
	 */
	public static char getChar(String key, char defaultValue) {
		return self().settings.getChar(key, defaultValue);
	}

	/**
	 * Returns the string value for the specified key. If the key does not exist
	 * or the value for the key can not be interpreted as a string, the
	 * defaultValue is returned.
	 * 
	 * @see IStoredSettings.getString(String key, String defaultValue)
	 * @param key
	 * @param defaultValue
	 * @return key value or defaultValue
	 */
	public static String getString(String key, String defaultValue) {
		return self().settings.getString(key, defaultValue);
	}

	/**
	 * Returns a list of space-separated strings from the specified key.
	 * 
	 * @see IStoredSettings.getStrings(String key)
	 * @param name
	 * @return list of strings
	 */
	public static List<String> getStrings(String key) {
		return self().settings.getStrings(key);
	}

	/**
	 * Returns the list of keys whose name starts with the specified prefix. If
	 * the prefix is null or empty, all key names are returned.
	 * 
	 * @see IStoredSettings.getAllKeys(String key)
	 * @param startingWith
	 * @return list of keys
	 */

	public static List<String> getAllKeys(String startingWith) {
		return self().settings.getAllKeys(startingWith);
	}

	/**
	 * Is Gitblit running in debug mode?
	 * 
	 * @return true if Gitblit is running in debug mode
	 */
	public static boolean isDebugMode() {
		return self().settings.getBoolean(Keys.web.debugMode, false);
	}

	/**
	 * Returns the list of non-Gitblit clone urls. This allows Gitblit to
	 * advertise alternative urls for Git client repository access.
	 * 
	 * @param repositoryName
	 * @return list of non-gitblit clone urls
	 */
	public List<String> getOtherCloneUrls(String repositoryName) {
		List<String> cloneUrls = new ArrayList<String>();
		for (String url : settings.getStrings(Keys.web.otherUrls)) {
			cloneUrls.add(MessageFormat.format(url, repositoryName));
		}
		return cloneUrls;
	}

	/**
	 * Set the user service. The user service authenticates all users and is
	 * responsible for managing user permissions.
	 * 
	 * @param userService
	 */
	public void setUserService(IUserService userService) {
		logger.info("Setting up user service " + userService.toString());
		this.userService = userService;
		this.userService.setup(settings);
	}

	/**
	 * Authenticate a user based on a username and password.
	 * 
	 * @see IUserService.authenticate(String, char[])
	 * @param username
	 * @param password
	 * @return a user object or null
	 */
	public UserModel authenticate(String username, char[] password) {
		if (StringUtils.isEmpty(username)) {
			// can not authenticate empty username
			return null;
		}
		String pw = new String(password);
		if (StringUtils.isEmpty(pw)) {
			// can not authenticate empty password
			return null;
		}

		// check to see if this is the federation user
		if (canFederate()) {
			if (username.equalsIgnoreCase(Constants.FEDERATION_USER)) {
				List<String> tokens = getFederationTokens();
				if (tokens.contains(pw)) {
					// the federation user is an administrator
					UserModel federationUser = new UserModel(Constants.FEDERATION_USER);
					federationUser.canAdmin = true;
					return federationUser;
				}
			}
		}

		// delegate authentication to the user service
		if (userService == null) {
			return null;
		}
		return userService.authenticate(username, password);
	}

	/**
	 * Authenticate a user based on their cookie.
	 * 
	 * @param cookies
	 * @return a user object or null
	 */
	public UserModel authenticate(Cookie[] cookies) {
		if (userService == null) {
			return null;
		}
		if (userService.supportsCookies()) {
			if (cookies != null && cookies.length > 0) {
				for (Cookie cookie : cookies) {
					if (cookie.getName().equals(Constants.NAME)) {
						String value = cookie.getValue();
						return userService.authenticate(value.toCharArray());
					}
				}
			}
		}
		return null;
	}

	/**
	 * Sets a cookie for the specified user.
	 * 
	 * @param response
	 * @param user
	 */
	public void setCookie(WebResponse response, UserModel user) {
		if (userService == null) {
			return;
		}
		if (userService.supportsCookies()) {
			Cookie userCookie;
			if (user == null) {
				// clear cookie for logout
				userCookie = new Cookie(Constants.NAME, "");
			} else {
				// set cookie for login
				char[] cookie = userService.getCookie(user);
				userCookie = new Cookie(Constants.NAME, new String(cookie));
				userCookie.setMaxAge(Integer.MAX_VALUE);
			}
			userCookie.setPath("/");
			response.addCookie(userCookie);
		}
	}

	/**
	 * Returns the list of all users available to the login service.
	 * 
	 * @see IUserService.getAllUsernames()
	 * @return list of all usernames
	 */
	public List<String> getAllUsernames() {
		List<String> names = new ArrayList<String>(userService.getAllUsernames());
		Collections.sort(names);
		return names;
	}

	/**
	 * Delete the user object with the specified username
	 * 
	 * @see IUserService.deleteUser(String)
	 * @param username
	 * @return true if successful
	 */
	public boolean deleteUser(String username) {
		return userService.deleteUser(username);
	}

	/**
	 * Retrieve the user object for the specified username.
	 * 
	 * @see IUserService.getUserModel(String)
	 * @param username
	 * @return a user object or null
	 */
	public UserModel getUserModel(String username) {
		UserModel user = userService.getUserModel(username);
		return user;
	}

	/**
	 * Returns the list of all users who are allowed to bypass the access
	 * restriction placed on the specified repository.
	 * 
	 * @see IUserService.getUsernamesForRepositoryRole(String)
	 * @param repository
	 * @return list of all usernames that can bypass the access restriction
	 */
	public List<String> getRepositoryUsers(RepositoryModel repository) {
		return userService.getUsernamesForRepositoryRole(repository.name);
	}

	/**
	 * Sets the list of all uses who are allowed to bypass the access
	 * restriction placed on the specified repository.
	 * 
	 * @see IUserService.setUsernamesForRepositoryRole(String, List<String>)
	 * @param repository
	 * @param usernames
	 * @return true if successful
	 */
	public boolean setRepositoryUsers(RepositoryModel repository, List<String> repositoryUsers) {
		return userService.setUsernamesForRepositoryRole(repository.name, repositoryUsers);
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
	public void updateUserModel(String username, UserModel user, boolean isCreate)
			throws GitBlitException {
		if (!userService.updateUserModel(username, user)) {
			throw new GitBlitException(isCreate ? "Failed to add user!" : "Failed to update user!");
		}
	}

	/**
	 * Returns the list of all repositories available to Gitblit. This method
	 * does not consider user access permissions.
	 * 
	 * @return list of all repositories
	 */
	public List<String> getRepositoryList() {
		return JGitUtils.getRepositoryList(repositoriesFolder, exportAll,
				settings.getBoolean(Keys.git.searchRepositoriesSubfolders, true));
	}

	/**
	 * Returns the JGit repository for the specified name.
	 * 
	 * @param repositoryName
	 * @return repository or null
	 */
	public Repository getRepository(String repositoryName) {
		Repository r = null;
		try {
			r = repositoryResolver.open(null, repositoryName);
		} catch (RepositoryNotFoundException e) {
			r = null;
			logger.error("GitBlit.getRepository(String) failed to find "
					+ new File(repositoriesFolder, repositoryName).getAbsolutePath());
		} catch (ServiceNotAuthorizedException e) {
			r = null;
			logger.error("GitBlit.getRepository(String) failed to find "
					+ new File(repositoriesFolder, repositoryName).getAbsolutePath(), e);
		} catch (ServiceNotEnabledException e) {
			r = null;
			logger.error("GitBlit.getRepository(String) failed to find "
					+ new File(repositoriesFolder, repositoryName).getAbsolutePath(), e);
		}
		return r;
	}

	/**
	 * Returns the list of repository models that are accessible to the user.
	 * 
	 * @param user
	 * @return list of repository models accessible to user
	 */
	public List<RepositoryModel> getRepositoryModels(UserModel user) {
		List<String> list = getRepositoryList();
		List<RepositoryModel> repositories = new ArrayList<RepositoryModel>();
		for (String repo : list) {
			RepositoryModel model = getRepositoryModel(user, repo);
			if (model != null) {
				repositories.add(model);
			}
		}
		return repositories;
	}

	/**
	 * Returns a repository model if the repository exists and the user may
	 * access the repository.
	 * 
	 * @param user
	 * @param repositoryName
	 * @return repository model or null
	 */
	public RepositoryModel getRepositoryModel(UserModel user, String repositoryName) {
		RepositoryModel model = getRepositoryModel(repositoryName);
		if (model == null) {
			return null;
		}
		if (model.accessRestriction.atLeast(AccessRestrictionType.VIEW)) {
			if (user != null && user.canAccessRepository(model.name)) {
				return model;
			}
			return null;
		} else {
			return model;
		}
	}

	/**
	 * Returns the repository model for the specified repository. This method
	 * does not consider user access permissions.
	 * 
	 * @param repositoryName
	 * @return repository model or null
	 */
	public RepositoryModel getRepositoryModel(String repositoryName) {
		Repository r = getRepository(repositoryName);
		if (r == null) {
			return null;
		}
		RepositoryModel model = new RepositoryModel();
		model.name = repositoryName;
		model.hasCommits = JGitUtils.hasCommits(r);
		model.lastChange = JGitUtils.getLastChange(r, null);
		StoredConfig config = JGitUtils.readConfig(r);
		if (config != null) {
			model.description = getConfig(config, "description", "");
			model.owner = getConfig(config, "owner", "");
			model.useTickets = getConfig(config, "useTickets", false);
			model.useDocs = getConfig(config, "useDocs", false);
			model.accessRestriction = AccessRestrictionType.fromName(getConfig(config,
					"accessRestriction", null));
			model.showRemoteBranches = getConfig(config, "showRemoteBranches", false);
			model.isFrozen = getConfig(config, "isFrozen", false);
			model.showReadme = getConfig(config, "showReadme", false);
			model.federationStrategy = FederationStrategy.fromName(getConfig(config,
					"federationStrategy", null));
			model.federationSets = new ArrayList<String>(Arrays.asList(config.getStringList(
					"gitblit", null, "federationSets")));
			model.isFederated = getConfig(config, "isFederated", false);
			model.origin = config.getString("remote", "origin", "url");
		}
		r.close();
		if (getBoolean(Keys.web.showRepositorySizes, true)) {
			ByteFormat byteFormat = new ByteFormat();
			model.size = byteFormat.format(calculateSize(model));			
		}
		return model;
	}

	/**
	 * Returns the size in bytes of the repository.
	 * 
	 * @param model
	 * @return size in bytes
	 */
	public long calculateSize(RepositoryModel model) {
		File gitDir = FileKey.resolve(new File(repositoriesFolder, model.name), FS.DETECTED);
		return com.gitblit.utils.FileUtils.folderSize(gitDir);
	}

	/**
	 * Ensure that a cached repository is completely closed and its resources
	 * are properly released.
	 * 
	 * @param repositoryName
	 */
	private void closeRepository(String repositoryName) {
		Repository repository = getRepository(repositoryName);
		// assume 2 uses in case reflection fails
		int uses = 2;
		try {
			// The FileResolver caches repositories which is very useful
			// for performance until you want to delete a repository.
			// I have to use reflection to call close() the correct
			// number of times to ensure that the object and ref databases
			// are properly closed before I can delete the repository from
			// the filesystem.
			Field useCnt = Repository.class.getDeclaredField("useCnt");
			useCnt.setAccessible(true);
			uses = ((AtomicInteger) useCnt.get(repository)).get();
		} catch (Exception e) {
			logger.warn(MessageFormat
					.format("Failed to reflectively determine use count for repository {0}",
							repositoryName), e);
		}
		if (uses > 0) {
			logger.info(MessageFormat
					.format("{0}.useCnt={1}, calling close() {2} time(s) to close object and ref databases",
							repositoryName, uses, uses));
			for (int i = 0; i < uses; i++) {
				repository.close();
			}
		}
	}

	/**
	 * Returns the gitblit string vlaue for the specified key. If key is not
	 * set, returns defaultValue.
	 * 
	 * @param config
	 * @param field
	 * @param defaultValue
	 * @return field value or defaultValue
	 */
	private String getConfig(StoredConfig config, String field, String defaultValue) {
		String value = config.getString("gitblit", null, field);
		if (StringUtils.isEmpty(value)) {
			return defaultValue;
		}
		return value;
	}

	/**
	 * Returns the gitblit boolean vlaue for the specified key. If key is not
	 * set, returns defaultValue.
	 * 
	 * @param config
	 * @param field
	 * @param defaultValue
	 * @return field value or defaultValue
	 */
	private boolean getConfig(StoredConfig config, String field, boolean defaultValue) {
		return config.getBoolean("gitblit", field, defaultValue);
	}

	/**
	 * Creates/updates the repository model keyed by reopsitoryName. Saves all
	 * repository settings in .git/config. This method allows for renaming
	 * repositories and will update user access permissions accordingly.
	 * 
	 * All repositories created by this method are bare and automatically have
	 * .git appended to their names, which is the standard convention for bare
	 * repositories.
	 * 
	 * @param repositoryName
	 * @param repository
	 * @param isCreate
	 * @throws GitBlitException
	 */
	public void updateRepositoryModel(String repositoryName, RepositoryModel repository,
			boolean isCreate) throws GitBlitException {
		Repository r = null;
		if (isCreate) {
			// ensure created repository name ends with .git
			if (!repository.name.toLowerCase().endsWith(org.eclipse.jgit.lib.Constants.DOT_GIT_EXT)) {
				repository.name += org.eclipse.jgit.lib.Constants.DOT_GIT_EXT;
			}
			if (new File(repositoriesFolder, repository.name).exists()) {
				throw new GitBlitException(MessageFormat.format(
						"Can not create repository ''{0}'' because it already exists.",
						repository.name));
			}
			// create repository
			logger.info("create repository " + repository.name);
			r = JGitUtils.createRepository(repositoriesFolder, repository.name);
		} else {
			// rename repository
			if (!repositoryName.equalsIgnoreCase(repository.name)) {
				closeRepository(repositoryName);
				File folder = new File(repositoriesFolder, repositoryName);
				File destFolder = new File(repositoriesFolder, repository.name);
				if (destFolder.exists()) {
					throw new GitBlitException(
							MessageFormat
									.format("Can not rename repository ''{0}'' to ''{1}'' because ''{1}'' already exists.",
											repositoryName, repository.name));
				}
				if (!folder.renameTo(destFolder)) {
					throw new GitBlitException(MessageFormat.format(
							"Failed to rename repository ''{0}'' to ''{1}''.", repositoryName,
							repository.name));
				}
				// rename the roles
				if (!userService.renameRepositoryRole(repositoryName, repository.name)) {
					throw new GitBlitException(MessageFormat.format(
							"Failed to rename repository permissions ''{0}'' to ''{1}''.",
							repositoryName, repository.name));
				}
			}

			// load repository
			logger.info("edit repository " + repository.name);
			try {
				r = repositoryResolver.open(null, repository.name);
			} catch (RepositoryNotFoundException e) {
				logger.error("Repository not found", e);
			} catch (ServiceNotAuthorizedException e) {
				logger.error("Service not authorized", e);
			} catch (ServiceNotEnabledException e) {
				logger.error("Service not enabled", e);
			}
		}

		// update settings
		if (r != null) {
			updateConfiguration(r, repository);
			r.close();
		}
	}

	/**
	 * Updates the Gitblit configuration for the specified repository.
	 * 
	 * @param r
	 *            the Git repository
	 * @param repository
	 *            the Gitblit repository model
	 */
	public void updateConfiguration(Repository r, RepositoryModel repository) {
		StoredConfig config = JGitUtils.readConfig(r);
		config.setString("gitblit", null, "description", repository.description);
		config.setString("gitblit", null, "owner", repository.owner);
		config.setBoolean("gitblit", null, "useTickets", repository.useTickets);
		config.setBoolean("gitblit", null, "useDocs", repository.useDocs);
		config.setString("gitblit", null, "accessRestriction", repository.accessRestriction.name());
		config.setBoolean("gitblit", null, "showRemoteBranches", repository.showRemoteBranches);
		config.setBoolean("gitblit", null, "isFrozen", repository.isFrozen);
		config.setBoolean("gitblit", null, "showReadme", repository.showReadme);
		config.setStringList("gitblit", null, "federationSets", repository.federationSets);
		config.setString("gitblit", null, "federationStrategy",
				repository.federationStrategy.name());
		config.setBoolean("gitblit", null, "isFederated", repository.isFederated);
		try {
			config.save();
		} catch (IOException e) {
			logger.error("Failed to save repository config!", e);
		}
	}

	/**
	 * Deletes the repository from the file system and removes the repository
	 * permission from all repository users.
	 * 
	 * @param model
	 * @return true if successful
	 */
	public boolean deleteRepositoryModel(RepositoryModel model) {
		return deleteRepository(model.name);
	}

	/**
	 * Deletes the repository from the file system and removes the repository
	 * permission from all repository users.
	 * 
	 * @param repositoryName
	 * @return true if successful
	 */
	public boolean deleteRepository(String repositoryName) {
		try {
			closeRepository(repositoryName);
			File folder = new File(repositoriesFolder, repositoryName);
			if (folder.exists() && folder.isDirectory()) {
				FileUtils.delete(folder, FileUtils.RECURSIVE | FileUtils.RETRY);
				if (userService.deleteRepositoryRole(repositoryName)) {
					return true;
				}
			}
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Failed to delete repository {0}", repositoryName), t);
		}
		return false;
	}

	/**
	 * Returns an html version of the commit message with any global or
	 * repository-specific regular expression substitution applied.
	 * 
	 * @param repositoryName
	 * @param text
	 * @return html version of the commit message
	 */
	public String processCommitMessage(String repositoryName, String text) {
		String html = StringUtils.breakLinesForHtml(text);
		Map<String, String> map = new HashMap<String, String>();
		// global regex keys
		if (settings.getBoolean(Keys.regex.global, false)) {
			for (String key : settings.getAllKeys(Keys.regex.global)) {
				if (!key.equals(Keys.regex.global)) {
					String subKey = key.substring(key.lastIndexOf('.') + 1);
					map.put(subKey, settings.getString(key, ""));
				}
			}
		}

		// repository-specific regex keys
		List<String> keys = settings.getAllKeys(Keys.regex._ROOT + "."
				+ repositoryName.toLowerCase());
		for (String key : keys) {
			String subKey = key.substring(key.lastIndexOf('.') + 1);
			map.put(subKey, settings.getString(key, ""));
		}

		for (Entry<String, String> entry : map.entrySet()) {
			String definition = entry.getValue().trim();
			String[] chunks = definition.split("!!!");
			if (chunks.length == 2) {
				html = html.replaceAll(chunks[0], chunks[1]);
			} else {
				logger.warn(entry.getKey()
						+ " improperly formatted.  Use !!! to separate match from replacement: "
						+ definition);
			}
		}
		return html;
	}

	/**
	 * Returns Gitblit's scheduled executor service for scheduling tasks.
	 * 
	 * @return scheduledExecutor
	 */
	public ScheduledExecutorService executor() {
		return scheduledExecutor;
	}

	public static boolean canFederate() {
		String passphrase = getString(Keys.federation.passphrase, "");
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
	public String getFederationToken(FederationToken type) {
		return getFederationToken(type.name());
	}

	/**
	 * Returns the specified federation token for this Gitblit instance.
	 * 
	 * @param value
	 * @return a federation token
	 */
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
	public boolean validateFederationRequest(FederationRequest req, String token) {
		String all = getFederationToken(FederationToken.ALL);
		String unr = getFederationToken(FederationToken.USERS_AND_REPOSITORIES);
		String jur = getFederationToken(FederationToken.REPOSITORIES);
		switch (req) {
		case PULL_REPOSITORIES:
			return token.equals(all) || token.equals(unr) || token.equals(jur);
		case PULL_USERS:
			return token.equals(all) || token.equals(unr);
		case PULL_SETTINGS:
			return token.equals(all);
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
	public boolean submitFederationProposal(FederationProposal proposal, String gitblitUrl) {
		// convert proposal to json
		String json = JsonUtils.toJsonString(proposal);

		try {
			// make the proposals folder
			File proposalsFolder = new File(getString(Keys.federation.proposalsFolder, "proposals")
					.trim());
			proposalsFolder.mkdirs();

			// cache json to a file
			File file = new File(proposalsFolder, proposal.token + Constants.PROPOSAL_EXT);
			com.gitblit.utils.FileUtils.writeContent(file, json);
		} catch (Exception e) {
			logger.error(MessageFormat.format("Failed to cache proposal from {0}", proposal.url), e);
		}

		// send an email, if possible
		try {
			Message message = mailExecutor.createMessageForAdministrators();
			if (message != null) {
				message.setSubject("Federation proposal from " + proposal.url);
				message.setText("Please review the proposal @ " + gitblitUrl + "/proposal/"
						+ proposal.token);
				mailExecutor.queue(message);
			}
		} catch (Throwable t) {
			logger.error("Failed to notify administrators of proposal", t);
		}
		return true;
	}

	/**
	 * Returns the list of pending federation proposals
	 * 
	 * @return list of federation proposals
	 */
	public List<FederationProposal> getPendingFederationProposals() {
		List<FederationProposal> list = new ArrayList<FederationProposal>();
		File folder = new File(getString(Keys.federation.proposalsFolder, "proposals").trim());
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
	public Map<String, RepositoryModel> getRepositories(String gitblitUrl, String token) {
		Map<String, String> federationSets = new HashMap<String, String>();
		for (String set : getStrings(Keys.federation.sets)) {
			federationSets.put(getFederationToken(set), set);
		}

		// Determine the Gitblit clone url
		StringBuilder sb = new StringBuilder();
		sb.append(gitblitUrl);
		sb.append(Constants.GIT_PATH);
		sb.append("{0}");
		String cloneUrl = sb.toString();

		// Retrieve all available repositories
		UserModel user = new UserModel(Constants.FEDERATION_USER);
		user.canAdmin = true;
		List<RepositoryModel> list = getRepositoryModels(user);

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
	public boolean deletePendingFederationProposal(FederationProposal proposal) {
		File folder = new File(getString(Keys.federation.proposalsFolder, "proposals").trim());
		File file = new File(folder, proposal.token + Constants.PROPOSAL_EXT);
		return file.delete();
	}

	/**
	 * Notify the administrators by email.
	 * 
	 * @param subject
	 * @param message
	 */
	public void notifyAdministrators(String subject, String message) {
		try {
			Message mail = mailExecutor.createMessageForAdministrators();
			if (mail != null) {
				mail.setSubject(subject);
				mail.setText(message);
				mailExecutor.queue(mail);
			}
		} catch (MessagingException e) {
			logger.error("Messaging error", e);
		}
	}

	/**
	 * Configure the Gitblit singleton with the specified settings source. This
	 * source may be file settings (Gitblit GO) or may be web.xml settings
	 * (Gitblit WAR).
	 * 
	 * @param settings
	 */
	public void configureContext(IStoredSettings settings, boolean startFederation) {
		logger.info("Reading configuration from " + settings.toString());
		this.settings = settings;
		repositoriesFolder = new File(settings.getString(Keys.git.repositoriesFolder, "git"));
		logger.info("Git repositories folder " + repositoriesFolder.getAbsolutePath());
		repositoryResolver = new FileResolver<Void>(repositoriesFolder, exportAll);
		String realm = settings.getString(Keys.realm.userService, "users.properties");
		IUserService loginService = null;
		try {
			// check to see if this "file" is a login service class
			Class<?> realmClass = Class.forName(realm);
			if (IUserService.class.isAssignableFrom(realmClass)) {
				loginService = (IUserService) realmClass.newInstance();
			}
		} catch (Throwable t) {
			// not a login service class or class could not be instantiated.
			// try to use default file login service
			File realmFile = new File(realm);
			if (!realmFile.exists()) {
				try {
					realmFile.createNewFile();
				} catch (IOException x) {
					logger.error(
							MessageFormat.format("COULD NOT CREATE REALM FILE {0}!", realmFile), x);
				}
			}
			loginService = new FileUserService(realmFile);
		}
		setUserService(loginService);
		mailExecutor = new MailExecutor(settings);
		if (mailExecutor.isReady()) {
			scheduledExecutor.scheduleAtFixedRate(mailExecutor, 1, 2, TimeUnit.MINUTES);
		} else {
			logger.warn("Mail server is not properly configured.  Mail services disabled.");
		}
		if (startFederation) {
			configureFederation();
		}
	}

	/**
	 * Configure Gitblit from the web.xml, if no configuration has already been
	 * specified.
	 * 
	 * @see ServletContextListener.contextInitialize(ServletContextEvent)
	 */
	@Override
	public void contextInitialized(ServletContextEvent contextEvent) {
		if (settings == null) {
			// Gitblit WAR is running in a servlet container
			WebXmlSettings webxmlSettings = new WebXmlSettings(contextEvent.getServletContext());
			configureContext(webxmlSettings, true);
		}
	}

	/**
	 * Gitblit is being shutdown either because the servlet container is
	 * shutting down or because the servlet container is re-deploying Gitblit.
	 */
	@Override
	public void contextDestroyed(ServletContextEvent contextEvent) {
		logger.info("Gitblit context destroyed by servlet container.");
		scheduledExecutor.shutdownNow();
	}
}
