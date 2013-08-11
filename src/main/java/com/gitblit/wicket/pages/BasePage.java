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
package com.gitblit.wicket.pages;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.apache.wicket.Application;
import org.apache.wicket.Page;
import org.apache.wicket.PageParameters;
import org.apache.wicket.RedirectToUrlException;
import org.apache.wicket.markup.html.CSSPackageResource;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.protocol.http.RequestUtils;
import org.apache.wicket.protocol.http.WebResponse;
import org.apache.wicket.protocol.http.servlet.ServletWebRequest;
import org.apache.wicket.util.time.Duration;
import org.apache.wicket.util.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.AuthorizationControl;
import com.gitblit.Constants.FederationStrategy;
import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.models.ProjectModel;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;
import com.gitblit.wicket.CacheControl;
import com.gitblit.wicket.GitBlitWebApp;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;

public abstract class BasePage extends SessionPage {

	private final Logger logger;
	
	private transient TimeUtils timeUtils;

	public BasePage() {
		super();
		logger = LoggerFactory.getLogger(getClass());
		customizeHeader();
	}

	public BasePage(PageParameters params) {
		super(params);
		logger = LoggerFactory.getLogger(getClass());
		customizeHeader();
	}
	
	private void customizeHeader() {
		if (GitBlit.getBoolean(Keys.web.useResponsiveLayout, true)) {
			add(CSSPackageResource.getHeaderContribution("bootstrap/css/bootstrap-responsive.css"));
		}
	}
	
	protected String getLanguageCode() {
		return GitBlitWebSession.get().getLocale().getLanguage();
	}
	
	protected String getCountryCode() {
		return GitBlitWebSession.get().getLocale().getCountry().toLowerCase();
	}
	
	protected TimeUtils getTimeUtils() {
		if (timeUtils == null) {
			ResourceBundle bundle;		
			try {
				bundle = ResourceBundle.getBundle("com.gitblit.wicket.GitBlitWebApp", GitBlitWebSession.get().getLocale());
			} catch (Throwable t) {
				bundle = ResourceBundle.getBundle("com.gitblit.wicket.GitBlitWebApp");
			}
			timeUtils = new TimeUtils(bundle, getTimeZone());
		}
		return timeUtils;
	}
	
	@Override
	protected void onBeforeRender() {
		if (GitBlit.isDebugMode()) {
			// strip Wicket tags in debug mode for jQuery DOM traversal
			Application.get().getMarkupSettings().setStripWicketTags(true);
		}
		super.onBeforeRender();
	}

	@Override
	protected void onAfterRender() {
		if (GitBlit.isDebugMode()) {
			// restore Wicket debug tags
			Application.get().getMarkupSettings().setStripWicketTags(false);
		}
		super.onAfterRender();
	}
		
	@Override
	protected void setHeaders(WebResponse response)	{
		int expires = GitBlit.getInteger(Keys.web.pageCacheExpires, 0);
		if (expires > 0) {
			// pages are personalized for the authenticated user so they must be
			// marked private to prohibit proxy servers from caching them
			response.setHeader("Cache-Control", "private, must-revalidate");
			setLastModified();
		} else {
			// use default Wicket caching behavior
			super.setHeaders(response);
		}
	}
	
	/**
	 * Sets the last-modified header date, if appropriate, for this page.  The
	 * date used is determined by the CacheControl annotation.
	 * 
	 */
	protected void setLastModified() {
		if (getClass().isAnnotationPresent(CacheControl.class)) {
			CacheControl cacheControl = getClass().getAnnotation(CacheControl.class);
			switch (cacheControl.value()) {
			case ACTIVITY:
				setLastModified(GitBlit.getLastActivityDate());
				break;
			case BOOT:
				setLastModified(GitBlit.getBootDate());
				break;
			case NONE:
				break;
			default:
				logger.warn(getClass().getSimpleName() + ": unhandled LastModified type " + cacheControl.value());
				break;
			}
		}
	}
	
	/**
	 * Sets the last-modified header field and the expires field.
	 * 
	 * @param when
	 */
	protected final void setLastModified(Date when) {
		if (when == null) {
			return;
		}
		
		if (when.before(GitBlit.getBootDate())) {
			// last-modified can not be before the Gitblit boot date
			// this helps ensure that pages are properly refreshed after a
			// server config change
			when = GitBlit.getBootDate();
		}
		
		int expires = GitBlit.getInteger(Keys.web.pageCacheExpires, 0);
		WebResponse response = (WebResponse) getResponse();
		response.setLastModifiedTime(Time.valueOf(when));
		response.setDateHeader("Expires", System.currentTimeMillis() + Duration.minutes(expires).getMilliseconds());
	}

	protected void setupPage(String repositoryName, String pageName) {
		String siteName = GitBlit.getString(Keys.web.siteName, Constants.NAME);
		if (StringUtils.isEmpty(siteName)) {
			siteName = Constants.NAME;
		}
		if (repositoryName != null && repositoryName.trim().length() > 0) {
			add(new Label("title", repositoryName + " - " + siteName));
		} else {
			add(new Label("title", siteName));
		}

		ExternalLink rootLink = new ExternalLink("rootLink", urlFor(GitBlitWebApp.HOME_PAGE_CLASS, null).toString());
		WicketUtils.setHtmlTooltip(rootLink, GitBlit.getString(Keys.web.siteName, Constants.NAME));
		add(rootLink);

		// Feedback panel for info, warning, and non-fatal error messages
		add(new FeedbackPanel("feedback"));

		add(new Label("gbVersion", "v" + Constants.getVersion()));
		if (GitBlit.getBoolean(Keys.web.aggressiveHeapManagement, false)) {
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
	
	protected Map<AccessPermission, String> getAccessPermissions() {
		Map<AccessPermission, String> map = new LinkedHashMap<AccessPermission, String>();
		for (AccessPermission type : AccessPermission.values()) {
			switch (type) {
			case NONE:
				map.put(type, MessageFormat.format(getString("gb.noPermission"), type.code));
				break;
			case EXCLUDE:
				map.put(type, MessageFormat.format(getString("gb.excludePermission"), type.code));
				break;
			case VIEW:
				map.put(type, MessageFormat.format(getString("gb.viewPermission"), type.code));
				break;
			case CLONE:
				map.put(type, MessageFormat.format(getString("gb.clonePermission"), type.code));
				break;
			case PUSH:
				map.put(type, MessageFormat.format(getString("gb.pushPermission"), type.code));
				break;
			case CREATE:
				map.put(type, MessageFormat.format(getString("gb.createPermission"), type.code));
				break;
			case DELETE:
				map.put(type, MessageFormat.format(getString("gb.deletePermission"), type.code));
				break;
			case REWIND:
				map.put(type, MessageFormat.format(getString("gb.rewindPermission"), type.code));
				break;
			}
		}
		return map;
	}
	
	protected Map<FederationStrategy, String> getFederationTypes() {
		Map<FederationStrategy, String> map = new LinkedHashMap<FederationStrategy, String>();
		for (FederationStrategy type : FederationStrategy.values()) {
			switch (type) {
			case EXCLUDE:
				map.put(type, getString("gb.excludeFromFederation"));
				break;
			case FEDERATE_THIS:
				map.put(type, getString("gb.federateThis"));
				break;
			case FEDERATE_ORIGIN:
				map.put(type, getString("gb.federateOrigin"));
				break;
			}
		}
		return map;
	}
	
	protected Map<AuthorizationControl, String> getAuthorizationControls() {
		Map<AuthorizationControl, String> map = new LinkedHashMap<AuthorizationControl, String>();
		for (AuthorizationControl type : AuthorizationControl.values()) {
			switch (type) {
			case AUTHENTICATED:
				map.put(type, getString("gb.allowAuthenticatedDescription"));
				break;
			case NAMED:
				map.put(type, getString("gb.allowNamedDescription"));
				break;
			}
		}
		return map;
	}

	protected TimeZone getTimeZone() {
		return GitBlit.getBoolean(Keys.web.useClientTimezone, false) ? GitBlitWebSession.get()
				.getTimezone() : GitBlit.getTimezone();
	}

	protected String getServerName() {
		ServletWebRequest servletWebRequest = (ServletWebRequest) getRequest();
		HttpServletRequest req = servletWebRequest.getHttpServletRequest();
		return req.getServerName();
	}
	
	protected List<ProjectModel> getProjectModels() {
		final UserModel user = GitBlitWebSession.get().getUser();
		List<ProjectModel> projects = GitBlit.self().getProjectModels(user, true);
		return projects;
	}
	
	protected List<ProjectModel> getProjects(PageParameters params) {
		if (params == null) {
			return getProjectModels();
		}

		boolean hasParameter = false;
		String regex = WicketUtils.getRegEx(params);
		String team = WicketUtils.getTeam(params);
		int daysBack = params.getInt("db", 0);
		int maxDaysBack = GitBlit.getInteger(Keys.web.activityDurationMaximum, 30);

		List<ProjectModel> availableModels = getProjectModels();
		Set<ProjectModel> models = new HashSet<ProjectModel>();

		if (!StringUtils.isEmpty(regex)) {
			// filter the projects by the regex
			hasParameter = true;
			Pattern pattern = Pattern.compile(regex);
			for (ProjectModel model : availableModels) {
				if (pattern.matcher(model.name).find()) {
					models.add(model);
				}
			}
		}

		if (!StringUtils.isEmpty(team)) {
			// filter the projects by the specified teams
			hasParameter = true;
			List<String> teams = StringUtils.getStringsFromValue(team, ",");

			// need TeamModels first
			List<TeamModel> teamModels = new ArrayList<TeamModel>();
			for (String name : teams) {
				TeamModel teamModel = GitBlit.self().getTeamModel(name);
				if (teamModel != null) {
					teamModels.add(teamModel);
				}
			}

			// brute-force our way through finding the matching models
			for (ProjectModel projectModel : availableModels) {
				for (String repositoryName : projectModel.repositories) {
					for (TeamModel teamModel : teamModels) {
						if (teamModel.hasRepositoryPermission(repositoryName)) {
							models.add(projectModel);
						}
					}
				}
			}
		}

		if (!hasParameter) {
			models.addAll(availableModels);
		}

		// time-filter the list
		if (daysBack > 0) {
			if (maxDaysBack > 0 && daysBack > maxDaysBack) {
				daysBack = maxDaysBack;
			}
			Calendar cal = Calendar.getInstance();
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			cal.set(Calendar.MILLISECOND, 0);
			cal.add(Calendar.DATE, -1 * daysBack);
			Date threshold = cal.getTime();
			Set<ProjectModel> timeFiltered = new HashSet<ProjectModel>();
			for (ProjectModel model : models) {
				if (model.lastChange.after(threshold)) {
					timeFiltered.add(model);
				}
			}
			models = timeFiltered;
		}

		List<ProjectModel> list = new ArrayList<ProjectModel>(models);
		Collections.sort(list);
		return list;
	}

	public void warn(String message, Throwable t) {
		logger.warn(message, t);
	}
	
	public void error(String message, boolean redirect) {
		error(message, null, redirect ? getApplication().getHomePage() : null);
	}

	public void error(String message, Throwable t, boolean redirect) {
		error(message, t, getApplication().getHomePage());
	}
	
	public void error(String message, Throwable t, Class<? extends Page> toPage) {
		error(message, t, toPage, null);
	}
	
	public void error(String message, Throwable t, Class<? extends Page> toPage, PageParameters params) {
		if (t == null) {
			logger.error(message  + " for " + GitBlitWebSession.get().getUsername());
		} else {
			logger.error(message  + " for " + GitBlitWebSession.get().getUsername(), t);
		}
		if (toPage != null) {
			GitBlitWebSession.get().cacheErrorMessage(message);
			String relativeUrl = urlFor(toPage, params).toString();
			String absoluteUrl = RequestUtils.toAbsolutePath(relativeUrl);
			throw new RedirectToUrlException(absoluteUrl);
		} else {
			super.error(message);
		}
	}

	public void authenticationError(String message) {
		logger.error(getRequest().getURL() + " for " + GitBlitWebSession.get().getUsername());
		if (!GitBlitWebSession.get().isLoggedIn()) {
			// cache the request if we have not authenticated.
			// the request will continue after authentication.
			GitBlitWebSession.get().cacheRequest(getClass());
		}
		error(message, true);
	}
}
