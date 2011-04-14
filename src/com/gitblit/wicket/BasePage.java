package com.gitblit.wicket;

import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.protocol.http.servlet.ServletWebRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.Keys;
import com.gitblit.StoredSettings;
import com.gitblit.wicket.pages.SummaryPage;

public abstract class BasePage extends WebPage {

	Logger logger = LoggerFactory.getLogger(BasePage.class);

	public BasePage() {
		super();
	}

	public BasePage(PageParameters params) {
		super(params);
	}

	protected void setupPage(String repositoryName, String pageName) {
		if (repositoryName != null && repositoryName.trim().length() > 0) {
			add(new Label("title", getServerName() + " - " + repositoryName));
		} else {
			add(new Label("title", getServerName()));
		}
		// header
		String siteName = StoredSettings.getString(Keys.web_siteName, Constants.NAME);
		if (siteName == null || siteName.trim().length() == 0) {
			siteName = Constants.NAME;
		}
		add(new Label("siteName", siteName));
		add(new LinkPanel("repositoryName", null, repositoryName, SummaryPage.class, WicketUtils.newRepositoryParameter(repositoryName)));
		add(new Label("pageName", pageName));

		// footer
		User user = null;
		if (StoredSettings.getBoolean(Keys.web_authenticate, true)) {
			user = GitBlitWebSession.get().getUser();
			add(new Label("userText", "Logout " + user.toString()));
		} else {
			add(new Label("userText", ""));
		}
		add(new Label("gbVersion", "v" + Constants.VERSION));
		if (StoredSettings.getBoolean(Keys.server_aggressiveHeapManagement, false)) {
			System.gc();
		}
	}

	protected TimeZone getTimeZone() {
		return StoredSettings.getBoolean(Keys.web_useClientTimezone, false) ? GitBlitWebSession.get().getTimezone() : TimeZone.getDefault();
	}

	protected String getServerName() {
		ServletWebRequest servletWebRequest = (ServletWebRequest) getRequest();
		HttpServletRequest req = servletWebRequest.getHttpServletRequest();
		return req.getServerName();
	}

	public void error(String message, Throwable t) {
		super.error(message);
		logger.error(message, t);
	}
}
