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
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.resolver.FileResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;

public class GitBlit implements ServletContextListener {

	private static GitBlit gitblit;

	private final Logger logger = LoggerFactory.getLogger(GitBlit.class);

	private FileResolver<Void> repositoryResolver;

	private File repositoriesFolder;

	private boolean exportAll = true;

	private ILoginService loginService;

	private IStoredSettings storedSettings;

	public GitBlit() {
		if (gitblit == null) {
			// Singleton reference when running in standard servlet container
			gitblit = this;
		}
	}

	public static GitBlit self() {
		if (gitblit == null) {
			gitblit = new GitBlit();
		}
		return gitblit;
	}

	public static boolean getBoolean(String key, boolean defaultValue) {
		return self().storedSettings.getBoolean(key, defaultValue);
	}

	public static int getInteger(String key, int defaultValue) {
		return self().storedSettings.getInteger(key, defaultValue);
	}

	public static String getString(String key, String defaultValue) {
		return self().storedSettings.getString(key, defaultValue);
	}

	public static List<String> getStrings(String key) {
		return self().storedSettings.getStrings(key);
	}

	public static List<String> getAllKeys(String startingWith) {
		return self().storedSettings.getAllKeys(startingWith);
	}

	public static boolean isDebugMode() {
		return self().storedSettings.getBoolean(Keys.web.debugMode, false);
	}

	public List<String> getOtherCloneUrls(String repositoryName) {
		List<String> cloneUrls = new ArrayList<String>();
		for (String url : storedSettings.getStrings(Keys.web.otherUrls)) {
			cloneUrls.add(MessageFormat.format(url, repositoryName));
		}
		return cloneUrls;
	}

	public void setLoginService(ILoginService loginService) {
		logger.info("Setting up login service " + loginService.toString());
		this.loginService = loginService;
	}

	public UserModel authenticate(String username, char[] password) {
		if (loginService == null) {
			return null;
		}
		return loginService.authenticate(username, password);
	}

	public List<String> getAllUsernames() {
		List<String> names = new ArrayList<String>(loginService.getAllUsernames());
		Collections.sort(names);
		return names;
	}

	public boolean deleteUser(String username) {
		return loginService.deleteUser(username);
	}

	public UserModel getUserModel(String username) {
		UserModel user = loginService.getUserModel(username);
		return user;
	}

	public List<String> getRepositoryUsers(RepositoryModel repository) {
		return loginService.getUsernamesForRole(repository.name);
	}

	public boolean setRepositoryUsers(RepositoryModel repository, List<String> repositoryUsers) {
		return loginService.setUsernamesForRole(repository.name, repositoryUsers);
	}

	public void editUserModel(String username, UserModel user, boolean isCreate)
			throws GitBlitException {
		if (!loginService.updateUserModel(username, user)) {
			throw new GitBlitException(isCreate ? "Failed to add user!" : "Failed to update user!");
		}
	}

	public List<String> getRepositoryList() {
		return JGitUtils.getRepositoryList(repositoriesFolder, exportAll,
				storedSettings.getBoolean(Keys.git.searchRepositoriesSubfolders, true));
	}

	public Repository getRepository(String repositoryName) {
		Repository r = null;
		try {
			r = repositoryResolver.open(null, repositoryName);
		} catch (RepositoryNotFoundException e) {
			r = null;
			logger.error("GitBlit.getRepository(String) failed to find "
					+ new File(repositoriesFolder, repositoryName).getAbsolutePath());
		} catch (ServiceNotEnabledException e) {
			r = null;
			e.printStackTrace();
		}
		return r;
	}

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

	public RepositoryModel getRepositoryModel(UserModel user, String repositoryName) {
		RepositoryModel model = getRepositoryModel(repositoryName);
		if (model.accessRestriction.atLeast(AccessRestrictionType.VIEW)) {
			if (user != null && user.canAccessRepository(model.name)) {
				return model;
			}
			return null;
		} else {
			return model;
		}
	}

	public RepositoryModel getRepositoryModel(String repositoryName) {
		Repository r = getRepository(repositoryName);
		if (r == null) {
			return null;
		}
		RepositoryModel model = new RepositoryModel();
		model.name = repositoryName;
		model.hasCommits = JGitUtils.hasCommits(r);
		model.lastChange = JGitUtils.getLastChange(r);
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
		}
		r.close();
		return model;
	}

	private String getConfig(StoredConfig config, String field, String defaultValue) {
		String value = config.getString("gitblit", null, field);
		if (StringUtils.isEmpty(value)) {
			return defaultValue;
		}
		return value;
	}

	private boolean getConfig(StoredConfig config, String field, boolean defaultValue) {
		return config.getBoolean("gitblit", field, defaultValue);
	}

	public void editRepositoryModel(String repositoryName, RepositoryModel repository,
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
				if (!loginService.renameRole(repositoryName, repository.name)) {
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
			} catch (ServiceNotEnabledException e) {
				logger.error("Service not enabled", e);
			}
		}

		// update settings
		if (r != null) {
			StoredConfig config = JGitUtils.readConfig(r);
			config.setString("gitblit", null, "description", repository.description);
			config.setString("gitblit", null, "owner", repository.owner);
			config.setBoolean("gitblit", null, "useTickets", repository.useTickets);
			config.setBoolean("gitblit", null, "useDocs", repository.useDocs);
			config.setString("gitblit", null, "accessRestriction",
					repository.accessRestriction.name());
			config.setBoolean("gitblit", null, "showRemoteBranches", repository.showRemoteBranches);
			config.setBoolean("gitblit", null, "isFrozen", repository.isFrozen);
			config.setBoolean("gitblit", null, "showReadme", repository.showReadme);
			try {
				config.save();
			} catch (IOException e) {
				logger.error("Failed to save repository config!", e);
			}
			r.close();
		}
	}

	public boolean deleteRepositoryModel(RepositoryModel model) {
		return deleteRepository(model.name);
	}

	public boolean deleteRepository(String repositoryName) {
		try {
			File folder = new File(repositoriesFolder, repositoryName);
			if (folder.exists() && folder.isDirectory()) {
				FileUtils.delete(folder, FileUtils.RECURSIVE);
				if (loginService.deleteRole(repositoryName)) {
					return true;
				}
			}
		} catch (Throwable t) {
			logger.error(MessageFormat.format("Failed to delete repository {0}", repositoryName), t);
		}
		return false;
	}

	public String processCommitMessage(String repositoryName, String text) {
		String html = StringUtils.breakLinesForHtml(text);
		Map<String, String> map = new HashMap<String, String>();
		// global regex keys
		if (storedSettings.getBoolean(Keys.regex.global, false)) {
			for (String key : storedSettings.getAllKeys(Keys.regex.global)) {
				if (!key.equals(Keys.regex.global)) {
					String subKey = key.substring(key.lastIndexOf('.') + 1);
					map.put(subKey, storedSettings.getString(key, ""));
				}
			}
		}

		// repository-specific regex keys
		List<String> keys = storedSettings.getAllKeys(Keys.regex._ROOT + "."
				+ repositoryName.toLowerCase());
		for (String key : keys) {
			String subKey = key.substring(key.lastIndexOf('.') + 1);
			map.put(subKey, storedSettings.getString(key, ""));
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

	public void configureContext(IStoredSettings settings) {
		logger.info("Reading configuration from " + settings.toString());
		this.storedSettings = settings;
		repositoriesFolder = new File(settings.getString(Keys.git.repositoriesFolder, "git"));
		logger.info("Git repositories folder " + repositoriesFolder.getAbsolutePath());
		repositoryResolver = new FileResolver<Void>(repositoriesFolder, exportAll);
		String realm = settings.getString(Keys.realm.realmFile, "users.properties");
		ILoginService loginService = null;
		try {
			// Check to see if this "file" is a login service class
			Class<?> realmClass = Class.forName(realm);
			if (ILoginService.class.isAssignableFrom(realmClass)) {
				loginService = (ILoginService) realmClass.newInstance();
			}
		} catch (Throwable t) {
			// Not a login service class OR other issue
			// Use default file login service
			File realmFile = new File(realm);
			if (!realmFile.exists()) {
				try {
					realmFile.createNewFile();
				} catch (IOException x) {
					logger.error(
							MessageFormat.format("COULD NOT CREATE REALM FILE {0}!", realmFile), x);
				}
			}
			loginService = new FileLoginService(realmFile);
		}
		setLoginService(loginService);
	}

	@Override
	public void contextInitialized(ServletContextEvent contextEvent) {
		if (storedSettings == null) {
			// for running gitblit as a traditional webapp in a servlet
			// container
			WebXmlSettings webxmlSettings = new WebXmlSettings(contextEvent.getServletContext());
			configureContext(webxmlSettings);
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent contextEvent) {
		logger.info("Gitblit context destroyed by servlet container.");
	}
}
