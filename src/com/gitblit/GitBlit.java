package com.gitblit;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.apache.wicket.Request;
import org.apache.wicket.protocol.http.WebResponse;
import org.apache.wicket.protocol.http.servlet.ServletWebRequest;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.http.server.resolver.FileResolver;
import org.eclipse.jgit.http.server.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.User;
import com.gitblit.wicket.models.RepositoryModel;

public class GitBlit implements ServletContextListener {

	private final static GitBlit gitblit;

	private final Logger logger = LoggerFactory.getLogger(GitBlit.class);

	private FileResolver repositoryResolver;

	private File repositories;

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

	public User authenticate(String username, char[] password) {
		if (loginService == null) {
			return null;
		}
		return loginService.authenticate(username, password);
	}

	public User authenticate(Cookie[] cookies) {
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

	public void setCookie(WebResponse response, User user) {
		Cookie userCookie = new Cookie(Constants.NAME, user.getCookie());
		userCookie.setMaxAge(Integer.MAX_VALUE);
		userCookie.setPath("/");
		response.addCookie(userCookie);
	}

	public List<String> getRepositoryList() {
		return JGitUtils.getRepositoryList(repositories, exportAll, storedSettings.getBoolean(Keys.git.nestedRepositories, true));
	}

	public List<RepositoryModel> getRepositories(Request request) {
		List<String> list = getRepositoryList();
		ServletWebRequest servletWebRequest = (ServletWebRequest) request;
		HttpServletRequest req = servletWebRequest.getHttpServletRequest();

		List<RepositoryModel> repositories = new ArrayList<RepositoryModel>();
		for (String repo : list) {
			Repository r = getRepository(req, repo);
			String description = JGitUtils.getRepositoryDescription(r);
			String owner = JGitUtils.getRepositoryOwner(r);
			Date lastchange = JGitUtils.getLastChange(r);
			r.close();
			repositories.add(new RepositoryModel(repo, description, owner, lastchange));
		}
		return repositories;
	}

	public Repository getRepository(HttpServletRequest req, String repositoryName) {
		Repository r = null;
		try {
			r = repositoryResolver.open(req, repositoryName);
		} catch (RepositoryNotFoundException e) {
			r = null;
			logger.error("Failed to find repository " + repositoryName);
			e.printStackTrace();
		} catch (ServiceNotEnabledException e) {
			r = null;
			e.printStackTrace();
		}
		return r;
	}

	public void setupContext(IStoredSettings settings) {
		logger.info("Setting up GitBlit context from " + settings.toString());
		this.storedSettings = settings;
		repositories = new File(settings.getString(Keys.git.repositoriesFolder, "repos"));
		exportAll = settings.getBoolean(Keys.git.exportAll, true);
		repositoryResolver = new FileResolver(repositories, exportAll);
	}

	@Override
	public void contextInitialized(ServletContextEvent contextEvent) {
		logger.info("GitBlit context initialization by servlet container...");
		if (storedSettings == null) {
			WebXmlSettings webxmlSettings = new WebXmlSettings(contextEvent.getServletContext());
			setupContext(webxmlSettings);
		} else {
			logger.info("GitBlit context already setup by " + storedSettings.toString());	
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent contextEvent) {
		logger.info("GitBlit context destroyed by servlet container.");
	}
}
