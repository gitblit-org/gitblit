package com.gitblit;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.http.Cookie;

import org.apache.wicket.protocol.http.WebResponse;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.resolver.FileResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.models.RepositoryModel;
import com.gitblit.wicket.models.UserModel;

public class GitBlit implements ServletContextListener {

	private final static GitBlit gitblit;

	private final Logger logger = LoggerFactory.getLogger(GitBlit.class);

	private FileResolver<Void> repositoryResolver;

	private File repositoriesFolder;

	private boolean exportAll;

	private ILoginService loginService;

	private IStoredSettings storedSettings;

	static {
		gitblit = new GitBlit();
	}

	public static GitBlit self() {
		return gitblit;
	}

	private GitBlit() {
	}

	public IStoredSettings settings() {
		return storedSettings;
	}

	public boolean isDebugMode() {
		return storedSettings.getBoolean(Keys.web.debugMode, false);
	}

	public String getCloneUrl(String repositoryName) {
		return storedSettings.getString(Keys.git.cloneUrl, "https://localhost/git/") + repositoryName;
	}

	public void setLoginService(ILoginService loginService) {
		this.loginService = loginService;
	}

	public UserModel authenticate(String username, char[] password) {
		if (loginService == null) {
			return null;
		}
		return loginService.authenticate(username, password);
	}

	public UserModel authenticate(Cookie[] cookies) {
		if (loginService == null) {
			return null;
		}
		if (cookies != null && cookies.length > 0) {
			for (Cookie cookie : cookies) {
				if (cookie.getName().equals(Constants.NAME)) {
					String value = cookie.getValue();
					return loginService.authenticate(value.toCharArray());
				}
			}
		}
		return null;
	}

	public void setCookie(WebResponse response, UserModel user) {
		Cookie userCookie = new Cookie(Constants.NAME, user.getCookie());
		userCookie.setMaxAge(Integer.MAX_VALUE);
		userCookie.setPath("/");
		response.addCookie(userCookie);
	}
	
	public List<String> getAllUsernames() {
		return loginService.getAllUsernames();
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

	public void editUserModel(UserModel user, boolean isCreate) throws GitBlitException {
		if (!loginService.updateUserModel(user)) {
			throw new GitBlitException(isCreate ? "Failed to add user!" : "Failed to update user!");
		}
	}

	public List<String> getRepositoryList() {
		return JGitUtils.getRepositoryList(repositoriesFolder, exportAll, storedSettings.getBoolean(Keys.git.nestedRepositories, true));
	}

	public Repository getRepository(String repositoryName) {
		Repository r = null;
		try {
			r = repositoryResolver.open(null, repositoryName);
		} catch (RepositoryNotFoundException e) {
			r = null;
			logger.error("GitBlit.getRepository(String) failed to find repository " + repositoryName);
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
		RepositoryModel model = new RepositoryModel();
		model.name = repositoryName;
		model.hasCommits = JGitUtils.hasCommits(r);
		model.lastChange = JGitUtils.getLastChange(r);
		StoredConfig config = JGitUtils.readConfig(r);
		if (config != null) {
			model.description = config.getString("gitblit", null, "description");
			model.owner = config.getString("gitblit", null, "owner");
			model.useTickets = config.getBoolean("gitblit", "useTickets", false);
			model.useDocs = config.getBoolean("gitblit", "useDocs", false);
			model.accessRestriction = AccessRestrictionType.fromName(config.getString("gitblit", null, "accessRestriction"));
			model.showRemoteBranches = config.getBoolean("gitblit", "showRemoteBranches", false);
		}
		r.close();
		return model;
	}

	public void editRepositoryModel(RepositoryModel repository, boolean isCreate) throws GitBlitException {
		Repository r = null;
		if (isCreate) {
			if (new File(repositoriesFolder, repository.name).exists()) {
				throw new GitBlitException(MessageFormat.format("Can not create repository {0} because it already exists.", repository.name));
			}
			// create repository
			logger.info("create repository " + repository.name);
			r = JGitUtils.createRepository(repositoriesFolder, repository.name, true);
		} else {
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
		StoredConfig config = JGitUtils.readConfig(r);
		config.setString("gitblit", null, "description", repository.description);
		config.setString("gitblit", null, "owner", repository.owner);
		config.setBoolean("gitblit", null, "useTickets", repository.useTickets);
		config.setBoolean("gitblit", null, "useDocs", repository.useDocs);
		config.setString("gitblit", null, "accessRestriction", repository.accessRestriction.name());
		config.setBoolean("gitblit", null, "showRemoteBranches", repository.showRemoteBranches);
		try {
			config.save();
		} catch (IOException e) {
			logger.error("Failed to save repository config!", e);
		}
		r.close();
	}

	public void configureContext(IStoredSettings settings) {
		logger.info("Using configuration from " + settings.toString());
		this.storedSettings = settings;
		repositoriesFolder = new File(settings.getString(Keys.git.repositoriesFolder, "repos"));
		exportAll = settings.getBoolean(Keys.git.exportAll, true);
		repositoryResolver = new FileResolver<Void>(repositoriesFolder, exportAll);
	}

	@Override
	public void contextInitialized(ServletContextEvent contextEvent) {
		if (storedSettings == null) {
			WebXmlSettings webxmlSettings = new WebXmlSettings(contextEvent.getServletContext());
			configureContext(webxmlSettings);
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent contextEvent) {
		logger.info("GitBlit context destroyed by servlet container.");
	}
}
