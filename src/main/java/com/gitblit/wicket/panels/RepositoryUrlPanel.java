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
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.protocol.http.WebRequest;

import com.gitblit.Constants;
import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.SparkleShareInviteServlet;
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
	
	private final String primaryUrl;

	public RepositoryUrlPanel(String wicketId, boolean onlyPrimary, UserModel user, 
			RepositoryModel repository, Localizer localizer, Component owner) {
		super(wicketId);
		if (user == null) {
			user = UserModel.ANONYMOUS;
		}
		List<String> repositoryUrls = new ArrayList<String>();

		AccessPermission accessPermission = null;
		if (GitBlit.getBoolean(Keys.git.enableGitServlet, true)) {
			accessPermission = user.getRepositoryPermission(repository).permission;
			repositoryUrls.add(getRepositoryUrl(repository));
		}
		repositoryUrls.addAll(GitBlit.self().getOtherCloneUrls(repository.name, UserModel.ANONYMOUS.equals(user) ? "" : user.username));
		
		primaryUrl = repositoryUrls.size() == 0 ? "" : repositoryUrls.remove(0);

		add(new DetailedRepositoryUrlPanel("repositoryPrimaryUrl", localizer, owner, repository.name, primaryUrl, accessPermission));
		
		if (!onlyPrimary) {
			Component gitDaemonUrlPanel = createGitDaemonUrlPanel("repositoryGitDaemonUrl", user, repository);
			if (!StringUtils.isEmpty(primaryUrl) && gitDaemonUrlPanel instanceof DetailedRepositoryUrlPanel) {
				WicketUtils.setCssStyle(gitDaemonUrlPanel, "padding-top: 10px");
			}
			add(gitDaemonUrlPanel);
		} else {
			add(new Label("repositoryGitDaemonUrl").setVisible(false));
		}
		
		final List<AppCloneLink> cloneLinks = new ArrayList<AppCloneLink>();
		if (user.canClone(repository) && GitBlit.getBoolean(Keys.web.allowAppCloneLinks, true)) {
			// universal app clone urls
//			cloneLinks.add(new AppCloneLink(localizer.getString("gb.cloneWithSmartGit", owner),
//					MessageFormat.format("smartgit://cloneRepo/{0}", primaryUrl),
//					"Syntevo SmartGit\u2122"));

			if (isWindows()) {
				// Windows client app clone urls
				cloneLinks.add(new AppCloneLink(localizer.getString("gb.cloneWithSourceTree", owner),
						MessageFormat.format("sourcetree://cloneRepo/{0}", primaryUrl),
						"Atlassian SourceTree\u2122"));
//				cloneLinks.add(new AppCloneLink(
//						MessageFormat.format(localizer.getString("gb.cloneWithGitHub", owner), "Windows"),
//						MessageFormat.format("github-windows://openRepo/{0}", primaryUrl)));
			} else if (isMac()) {
				// Mac client app clone urls
				cloneLinks.add(new AppCloneLink(localizer.getString("gb.cloneWithSourceTree", owner),
						MessageFormat.format("sourcetree://cloneRepo/{0}", primaryUrl),
						"Atlassian SourceTree\u2122"));
//				cloneLinks.add(new AppCloneLink(
//						MessageFormat.format(localizer.getString("gb.cloneWithGitHub", owner), "Mac"),
//						MessageFormat.format("github-mac://openRepo/{0}", primaryUrl)));
			}

			// sparkleshare invite url
			String sparkleshareUrl = getSparkleShareInviteUrl(user, repository);
			if (!StringUtils.isEmpty(sparkleshareUrl)) {
				cloneLinks.add(new AppCloneLink(localizer.getString("gb.cloneWithSparkleShare", owner),
						sparkleshareUrl, "SparkleShare \u2122", "icon-star"));
			}
		}

		// app clone links
		ListDataProvider<AppCloneLink> appLinks = new ListDataProvider<AppCloneLink>(cloneLinks);
		DataView<AppCloneLink> appCloneLinks = new DataView<AppCloneLink>("appCloneLink", appLinks) {
			private static final long serialVersionUID = 1L;
			int count;
			
			public void populateItem(final Item<AppCloneLink> item) {
				final AppCloneLink appLink = item.getModelObject();
				item.add(new Label("icon", MessageFormat.format("<i class=\"{0}\"></i>", appLink.icon)).setEscapeModelStrings(false));
				LinkPanel linkPanel = new LinkPanel("link", null, appLink.name, appLink.url);
				if (!StringUtils.isEmpty(appLink.tooltip)) {
					WicketUtils.setHtmlTooltip(linkPanel, appLink.tooltip);
				}
				item.add(linkPanel);
				item.add(new Label("separator", "|").setVisible(count < (cloneLinks.size() - 1)));
				count++;
			}
		};
		add(appCloneLinks);
	}
	
	public String getPrimaryUrl() {
		return primaryUrl;
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
	
	protected Component createGitDaemonUrlPanel(String wicketId, UserModel user, RepositoryModel repository) {
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
			
			if (AccessPermission.NONE.equals(gitDaemonPermission)) {
				// repository prohibits all anonymous access
				return new Label(wicketId).setVisible(false);
			} else {
				// repository allows some form of anonymous access
				return new DetailedRepositoryUrlPanel(wicketId, getLocalizer(), this, repository.name, gitDaemonUrl, gitDaemonPermission);
			}
		} else {
			// git daemon is not running
			return new Label(wicketId).setVisible(false);
		}
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
	
	static class AppCloneLink implements Serializable {
		
		private static final long serialVersionUID = 1L;
		
		final String name;
		final String url;
		final String tooltip;
		final String icon;
		
		public AppCloneLink(String name, String url, String tooltip) {
			this(name, url, tooltip, "icon-download");
		}
		
		public AppCloneLink(String name, String url, String tooltip, String icon) {
			this.name = name;
			this.url = url;
			this.tooltip = tooltip;
			this.icon = icon;
		}
	}
}
