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
package com.gitblit.wicket.panels;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.Localizer;
import org.apache.wicket.RequestCycle;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.image.ContextImage;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.protocol.http.WebRequest;
import org.apache.wicket.protocol.http.request.WebClientInfo;

import com.gitblit.Constants;
import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.SparkleShareInviteServlet;
import com.gitblit.models.GitClientApplication;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;

/**
 * Smart repository url panel which can display multiple Gitblit repository urls
 * and also supports 3rd party app clone links.
 * 
 * @author James Moger
 *
 */
public class RepositoryUrlPanel extends BasePanel {

	private static final long serialVersionUID = 1L;
	
	private final RepoUrl primaryUrl;

	public RepositoryUrlPanel(String wicketId, boolean onlyPrimary, UserModel user, 
			final RepositoryModel repository, Localizer localizer, Component owner) {
		super(wicketId);
		if (user == null) {
			user = UserModel.ANONYMOUS;
		}
		List<RepoUrl> repositoryUrls = new ArrayList<RepoUrl>();

		// http/https url
		if (GitBlit.getBoolean(Keys.git.enableGitServlet, true)) {
			AccessPermission permission = user.getRepositoryPermission(repository).permission;
			if (permission.exceeds(AccessPermission.NONE)) {
				repositoryUrls.add(new RepoUrl(getRepositoryUrl(repository), permission));
			}
		}
		
		// git daemon url
		String gitDaemonUrl = getGitDaemonUrl(user, repository);
		if (!StringUtils.isEmpty(gitDaemonUrl)) {
			AccessPermission permission = getGitDaemonAccessPermission(user, repository);
			if (permission.exceeds(AccessPermission.NONE)) {
				repositoryUrls.add(new RepoUrl(gitDaemonUrl, permission));
			}
		}
		
		// add all other urls
		for (String url : GitBlit.self().getOtherCloneUrls(repository.name, UserModel.ANONYMOUS.equals(user) ? "" : user.username)) {
			repositoryUrls.add(new RepoUrl(url, null));
		}
		
		// grab primary url from the top of the list
		primaryUrl = repositoryUrls.size() == 0 ? null : repositoryUrls.get(0);

		add(new DetailedRepositoryUrlPanel("repositoryPrimaryUrl", localizer, owner, 
				repository.name, primaryUrl == null ? "" : primaryUrl.url,
				primaryUrl == null ? null : primaryUrl.permission));
		
		if (onlyPrimary) {
			// only displaying the primary url
			add(new Label("urlMenus").setVisible(false));
			return;
		}
		
		final String clonePattern = localizer.getString("gb.cloneUrl", owner);
		final String visitSitePattern = localizer.getString("gb.visitSite", owner);
		
		GitClientApplication URLS = new GitClientApplication();
		URLS.name = "URLs";
		URLS.command = "{0}";
		URLS.attribution = "Repository URLs";
		URLS.isApplication = false;
		URLS.isActive = true;
		
		GitClientApplication GIT = new GitClientApplication();
		GIT.name = "Git";
		GIT.command = "git clone {0}";
		GIT.productUrl = "http://git-scm.org";
		GIT.attribution = "Git Syntax";
		GIT.isApplication = false;
		GIT.isActive = true;
		
		final List<GitClientApplication> clientApps = new ArrayList<GitClientApplication>();
		clientApps.add(URLS);
		clientApps.add(GIT);
		
		final String userAgent = ((WebClientInfo) GitBlitWebSession.get().getClientInfo()).getUserAgent();
		boolean allowAppLinks = GitBlit.getBoolean(Keys.web.allowAppCloneLinks, true);
		if (user.canClone(repository)) {
			for (GitClientApplication app : GitBlit.self().getClientApplications()) {
				if (app.isActive && app.allowsPlatform(userAgent) && (!app.isApplication || (app.isApplication && allowAppLinks))) {
					clientApps.add(app);
				}
			}

			// sparkleshare invite url
			String sparkleshareUrl = getSparkleShareInviteUrl(user, repository);
			if (!StringUtils.isEmpty(sparkleshareUrl) && allowAppLinks) {
				GitClientApplication link = new GitClientApplication();
				link.name = "SparkleShare";
				link.cloneUrl = sparkleshareUrl;
				link.attribution = "SparkleShare\u2122";
				link.platforms = new String [] { "windows", "macintosh", "linux" };
				link.productUrl = "http://sparkleshare.org";
				link.isApplication = true;
				link.isActive = true;
				clientApps.add(link);
			}
		}
		
		final ListDataProvider<RepoUrl> repoUrls = new ListDataProvider<RepoUrl>(repositoryUrls);

		// app clone links
		ListDataProvider<GitClientApplication> appLinks = new ListDataProvider<GitClientApplication>(clientApps);
		DataView<GitClientApplication> urlMenus = new DataView<GitClientApplication>("urlMenus", appLinks) {
			private static final long serialVersionUID = 1L;
			
			public void populateItem(final Item<GitClientApplication> item) {
				final GitClientApplication cloneLink = item.getModelObject();
				item.add(new Label("productName", cloneLink.name));
				
				// a nested repeater for all repo links
				DataView<RepoUrl> repoLinks = new DataView<RepoUrl>("repoLinks", repoUrls) {
					private static final long serialVersionUID = 1L;

					public void populateItem(final Item<RepoUrl> repoLinkItem) {
						RepoUrl repoUrl = repoLinkItem.getModelObject();
						if (!StringUtils.isEmpty(cloneLink.cloneUrl)) {
							// custom registered url
							Fragment fragment = new Fragment("repoLink", "linkFragment", this);
							String name;
							if (repoUrl.permission != null) {
								name = MessageFormat.format("{0} ({1})", repoUrl.url, repoUrl.permission);
							} else {
								name = repoUrl.url;
							}
							String url = MessageFormat.format(cloneLink.cloneUrl, repoUrl);
							fragment.add(new LinkPanel("content", null, MessageFormat.format(clonePattern, name), url));
							repoLinkItem.add(fragment);
							String tooltip = getProtocolPermissionDescription(repository, repoUrl);
							WicketUtils.setHtmlTooltip(fragment, tooltip);
						} else if (!StringUtils.isEmpty(cloneLink.command)) {
							// command-line
							Fragment fragment = new Fragment("repoLink", "commandFragment", this);
							WicketUtils.setCssClass(fragment, "repositoryUrlMenuItem");
							String command = MessageFormat.format(cloneLink.command, repoUrl);
							fragment.add(new Label("content", command));
							repoLinkItem.add(fragment);
							String tooltip = getProtocolPermissionDescription(repository, repoUrl);
							WicketUtils.setHtmlTooltip(fragment, tooltip);
							
							// copy function for command
							if (GitBlit.getBoolean(Keys.web.allowFlashCopyToClipboard, true)) {
								// clippy: flash-based copy & paste
								Fragment copyFragment = new Fragment("copyFunction", "clippyPanel", this);
								String baseUrl = WicketUtils.getGitblitURL(getRequest());
								ShockWaveComponent clippy = new ShockWaveComponent("clippy", baseUrl + "/clippy.swf");
								clippy.setValue("flashVars", "text=" + StringUtils.encodeURL(command));
								copyFragment.add(clippy);
								fragment.add(copyFragment);
							} else {
								// javascript: manual copy & paste with modal browser prompt dialog
								Fragment copyFragment = new Fragment("copyFunction", "jsPanel", this);
								ContextImage img = WicketUtils.newImage("copyIcon", "clippy.png");
								img.add(new JavascriptTextPrompt("onclick", "Copy to Clipboard (Ctrl+C, Enter)", command));
								copyFragment.add(img);
								fragment.add(copyFragment);
							}
						}
					}};
				item.add(repoLinks);
				
				item.add(new Label("productAttribution", cloneLink.attribution));
				if (!StringUtils.isEmpty(cloneLink.productUrl)) {
					LinkPanel productlinkPanel = new LinkPanel("productLink", null,
							MessageFormat.format(visitSitePattern, cloneLink.name), cloneLink.productUrl, true);
					item.add(productlinkPanel);
				} else {
					item.add(new Label("productLink").setVisible(false));
				}
			}
		};
		add(urlMenus);
	}
	
	public String getPrimaryUrl() {
		return primaryUrl == null ? "" : primaryUrl.url;
	}
	
	protected String getRepositoryUrl(RepositoryModel repository) {
		StringBuilder sb = new StringBuilder();
		sb.append(WicketUtils.getGitblitURL(RequestCycle.get().getRequest()));
		sb.append(Constants.GIT_PATH);
		sb.append(repository.name);
		
		// inject username into repository url if authentication is required
		if (repository.accessRestriction.exceeds(AccessRestrictionType.NONE)
				&& GitBlitWebSession.get().isLoggedIn()) {
			String username = GitBlitWebSession.get().getUsername();
			sb.insert(sb.indexOf("://") + 3, username + "@");
		}
		return sb.toString();
	}
	
	protected String getGitDaemonUrl(UserModel user, RepositoryModel repository) {
		int gitDaemonPort = GitBlit.getInteger(Keys.git.daemonPort, 0);
		if (gitDaemonPort > 0 && user.canClone(repository)) {
			String servername = ((WebRequest) getRequest()).getHttpServletRequest().getServerName();
			String gitDaemonUrl;
			if (gitDaemonPort == 9418) {
				// standard port
				gitDaemonUrl = MessageFormat.format("git://{0}/{1}", servername, repository.name);
			} else {
				// non-standard port
				gitDaemonUrl = MessageFormat.format("git://{0}:{1,number,0}/{2}", servername, gitDaemonPort, repository.name);
			}
			return gitDaemonUrl;
		}
		return null;
	}
	
	protected AccessPermission getGitDaemonAccessPermission(UserModel user, RepositoryModel repository) {
		int gitDaemonPort = GitBlit.getInteger(Keys.git.daemonPort, 0);
		if (gitDaemonPort > 0 && user.canClone(repository)) {
			AccessPermission gitDaemonPermission = user.getRepositoryPermission(repository).permission;;
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

	protected String getSparkleShareInviteUrl(UserModel user, RepositoryModel repository) {
		if (repository.isBare && repository.isSparkleshared()) {
			String username = null;
			if (UserModel.ANONYMOUS != user) {
				username = user.username;
			}
			if (GitBlit.getBoolean(Keys.git.enableGitServlet, true) || (GitBlit.getInteger(Keys.git.daemonPort, 0) > 0)) {
				// Gitblit as server
				// ensure user can rewind
				if (user.canRewindRef(repository)) {
					String baseURL = WicketUtils.getGitblitURL(RequestCycle.get().getRequest());
					return SparkleShareInviteServlet.asLink(baseURL, repository.name, username);
				}
			} else {
				// Gitblit as viewer, assume RW+ permission
				String baseURL = WicketUtils.getGitblitURL(RequestCycle.get().getRequest());
				return SparkleShareInviteServlet.asLink(baseURL, repository.name, username);
			}
		}
		return null;
	}
	
	protected String getProtocolPermissionDescription(RepositoryModel repository, RepoUrl repoUrl) {
		String protocol = repoUrl.url.substring(0, repoUrl.url.indexOf("://"));
		String note;
		if (repoUrl.permission == null) {
			note = MessageFormat.format(getString("gb.externalPermissions"), protocol, repository.name);			
		} else {
			note = null;			
			String key;
			switch (repoUrl.permission) {
				case OWNER:
				case REWIND:
					key = "gb.rewindPermission";
					break;
				case DELETE:
					key = "gb.deletePermission";
					break;
				case CREATE:
					key = "gb.createPermission";
					break;
				case PUSH:
					key = "gb.pushPermission";
					break;
				case CLONE:
					key = "gb.clonePermission";
					break;
				default:
					key = null;
					note = getString("gb.viewAccess");
					break;
			}
			
			if (note == null) {
				String pattern = getString(key);
				String description = MessageFormat.format(pattern, repoUrl.permission.toString());
				String permissionPattern = getString("gb.yourProtocolPermissionIs");
				note = MessageFormat.format(permissionPattern, protocol.toUpperCase(), repository, description);
			}
		}
		return note;
	}
	
	private class RepoUrl implements Serializable {
		
		private static final long serialVersionUID = 1L;
		
		final String url;
		final AccessPermission permission;
		
		RepoUrl(String url, AccessPermission permission) {
			this.url = url;
			this.permission = permission;
		}
		
		@Override
		public String toString() {
			return url;
		}
	}
}
