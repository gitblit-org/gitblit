/*
 * Copyright 2012 gitblit.com.
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

import java.util.Map;

import org.apache.wicket.Component;
import org.apache.wicket.Localizer;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.panel.Fragment;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Keys;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.servlet.SyndicationServlet;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.GitBlitRequestUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.EditRepositoryPage;
import com.gitblit.wicket.pages.LogPage;
import com.gitblit.wicket.pages.SummaryPage;
import com.gitblit.wicket.pages.TreePage;

public class ProjectRepositoryPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	public ProjectRepositoryPanel(String wicketId, Localizer localizer, Component parent,
			final boolean isAdmin, final RepositoryModel entry,
			final Map<AccessRestrictionType, String> accessRestrictions) {
		super(wicketId);

		final boolean showSwatch = app().settings().getBoolean(Keys.web.repositoryListSwatches, true);
		final boolean showSize = app().settings().getBoolean(Keys.web.showRepositorySizes, true);

		PageParameters pp = WicketUtils.newRepositoryParameter(entry.name);
		add(new LinkPanel("repositoryName", "list", StringUtils.getRelativePath(entry.projectPath,
				StringUtils.stripDotGit(entry.name)), SummaryPage.class, pp));
		add(new Label("repositoryDescription", entry.description).setVisible(!StringUtils
				.isEmpty(entry.description)));

		Fragment iconFragment;
		if (entry.isMirror) {
			iconFragment = new Fragment("repoIcon", "mirrorIconFragment", this);
		} else if (entry.isFork()) {
			iconFragment = new Fragment("repoIcon", "forkIconFragment", this);
		} else if (entry.isBare) {
			iconFragment = new Fragment("repoIcon", "repoIconFragment", this);
		} else {
			iconFragment = new Fragment("repoIcon", "cloneIconFragment", this);
		}
		if (showSwatch) {
			WicketUtils.setCssStyle(iconFragment, "color:" + StringUtils.getColor(entry.toString()));
		}
		add(iconFragment);

		if (StringUtils.isEmpty(entry.originRepository)) {
			add(new Label("originRepository").setVisible(false));
		} else {
			Fragment forkFrag = new Fragment("originRepository", "originFragment", this);
			forkFrag.add(new LinkPanel("originRepository", null, StringUtils.stripDotGit(entry.originRepository),
					SummaryPage.class, WicketUtils.newRepositoryParameter(entry.originRepository)));
			add(forkFrag);
		}

		if (entry.isSparkleshared()) {
			add(WicketUtils.newImage("sparkleshareIcon", "star_16x16.png", localizer.getString("gb.isSparkleshared", parent)));
		} else {
			add(WicketUtils.newClearPixel("sparkleshareIcon").setVisible(false));
		}

		if (!entry.isMirror && entry.isFrozen) {
			add(WicketUtils.newImage("frozenIcon", "cold_16x16.png", localizer.getString("gb.isFrozen", parent)));
		} else {
			add(WicketUtils.newClearPixel("frozenIcon").setVisible(false));
		}

		if (entry.isFederated) {
			add(WicketUtils.newImage("federatedIcon", "federated_16x16.png", localizer.getString("gb.isFederated", parent)));
		} else {
			add(WicketUtils.newClearPixel("federatedIcon").setVisible(false));
		}

		if (ArrayUtils.isEmpty(entry.owners)) {
			add(new Label("repositoryOwner").setVisible(false));
		} else {
			String owner = "";
			for (String username : entry.owners) {
				UserModel ownerModel = app().users().getUserModel(username);

				if (ownerModel != null) {
					owner = ownerModel.getDisplayName();
				}
			}
			if (entry.owners.size() > 1) {
				owner += ", ...";
			}
			Label ownerLabel = (new Label("repositoryOwner", owner + " (" +
					localizer.getString("gb.owner", parent) + ")"));
			WicketUtils.setHtmlTooltip(ownerLabel, ArrayUtils.toString(entry.owners));
			add(ownerLabel);
		}

		UserModel user = GitBlitWebSession.get().getUser();
		if (user == null) {
			user = UserModel.ANONYMOUS;
		}
		Fragment repositoryLinks;
		if (user.canAdmin(entry)) {
			repositoryLinks = new Fragment("repositoryLinks", "repositoryOwnerLinks", this);
			repositoryLinks.add(new BookmarkablePageLink<Void>("editRepository", EditRepositoryPage.class,
					WicketUtils.newRepositoryParameter(entry.name)));
		} else {
			repositoryLinks = new Fragment("repositoryLinks", "repositoryUserLinks", this);
		}

		repositoryLinks.add(new BookmarkablePageLink<Void>("tree", TreePage.class, WicketUtils
				.newRepositoryParameter(entry.name)).setEnabled(entry.hasCommits));

		repositoryLinks.add(new BookmarkablePageLink<Void>("log", LogPage.class, WicketUtils
				.newRepositoryParameter(entry.name)).setEnabled(entry.hasCommits));

		add(repositoryLinks);

		String lastChange;
		if (entry.lastChange.getTime() == 0) {
			lastChange = "--";
		} else {
			lastChange = getTimeUtils().timeAgo(entry.lastChange);
		}
		Label lastChangeLabel = new Label("repositoryLastChange", lastChange);
		add(lastChangeLabel);
		WicketUtils.setCssClass(lastChangeLabel, getTimeUtils().timeAgoCss(entry.lastChange));

		if (entry.hasCommits) {
			// Existing repository
			add(new Label("repositorySize", entry.size).setVisible(showSize));
		} else {
			// New repository
			add(new Label("repositorySize", localizer.getString("gb.empty", parent)).setEscapeModelStrings(false));
		}

		add(new ExternalLink("syndication", SyndicationServlet.asLink(GitBlitRequestUtils
				.getRelativePathPrefixToContextRoot(), entry.name, null, 0)));
	}
}
