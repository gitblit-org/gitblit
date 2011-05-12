package com.gitblit.wicket;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;

import org.apache.wicket.PageParameters;
import org.apache.wicket.RestartResponseAtInterceptPageException;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.protocol.http.servlet.ServletWebRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.wicket.pages.SummaryPage;

public abstract class BasePage extends WebPage {

	private final Logger logger;

	public BasePage() {
		super();
		logger = LoggerFactory.getLogger(getClass());
	}

	public BasePage(PageParameters params) {
		super(params);
		logger = LoggerFactory.getLogger(getClass());
	}

	protected void setupPage(String repositoryName, String pageName) {
		if (repositoryName != null && repositoryName.trim().length() > 0) {
			add(new Label("title", getServerName() + " - " + repositoryName));
		} else {
			add(new Label("title", getServerName()));
		}
		// header
		String siteName = GitBlit.self().settings().getString(Keys.web.siteName, Constants.NAME);
		if (siteName == null || siteName.trim().length() == 0) {
			siteName = Constants.NAME;
		}
		add(new Label("siteName", siteName));
		add(new LinkPanel("repositoryName", null, repositoryName, SummaryPage.class, WicketUtils.newRepositoryParameter(repositoryName)));
		add(new Label("pageName", pageName));

		// Feedback panel for info, warning, and non-fatal error messages
		add(new FeedbackPanel("feedback"));

		// footer
		if (GitBlit.self().settings().getBoolean(Keys.web.authenticateViewPages, true) || GitBlit.self().settings().getBoolean(Keys.web.authenticateAdminPages, true)) {
			if (GitBlitWebSession.get().isLoggedIn()) {
				// logout
				add(new LinkPanel("userPanel", null, getString("gb.logout") + " " + GitBlitWebSession.get().getUser().toString(), LogoutPage.class));
			} else {
				// login
				add(new LinkPanel("userPanel", null, getString("gb.login"), LoginPage.class));
			}
		} else {
			add(new Label("userPanel", ""));
		}
		add(new Label("gbVersion", "v" + Constants.VERSION));
		if (GitBlit.self().settings().getBoolean(Keys.web.aggressiveHeapManagement, false)) {
			System.gc();
		}
	}

	protected Map<AccessRestrictionType, String> getAccessRestrictions() {
		Map<AccessRestrictionType, String> map = new LinkedHashMap<AccessRestrictionType, String>();
		for (AccessRestrictionType type : AccessRestrictionType.values()) {
			switch (type) {
			case NONE:
				map.put(type, getString("gb.notRestricted"));
				break;
			case PUSH:
				map.put(type, getString("gb.pushRestricted"));
				break;
			case CLONE:
				map.put(type, getString("gb.cloneRestricted"));
				break;
			case VIEW:
				map.put(type, getString("gb.viewRestricted"));
				break;
			}
		}
		return map;
	}

	protected TimeZone getTimeZone() {
		return GitBlit.self().settings().getBoolean(Keys.web.useClientTimezone, false) ? GitBlitWebSession.get().getTimezone() : TimeZone.getDefault();
	}

	protected String getServerName() {
		ServletWebRequest servletWebRequest = (ServletWebRequest) getRequest();
		HttpServletRequest req = servletWebRequest.getHttpServletRequest();
		return req.getServerName();
	}

	public void error(String message, boolean redirect) {
		logger.error(message);
		if (redirect) {
			GitBlitWebSession.get().cacheErrorMessage(message);
			throw new RestartResponseAtInterceptPageException(getApplication().getHomePage());
		} else {
			super.error(message);
		}
	}

	public void error(String message, Throwable t, boolean redirect) {
		logger.error(message, t);
		if (redirect) {
			GitBlitWebSession.get().cacheErrorMessage(message);
			throw new RestartResponseAtInterceptPageException(getApplication().getHomePage());
		} else {
			super.error(message);
		}
	}
}
