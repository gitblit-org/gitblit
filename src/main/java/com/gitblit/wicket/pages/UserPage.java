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
package com.gitblit.wicket.pages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.PersonIdent;

import com.gitblit.Keys;
import com.gitblit.models.ProjectModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebApp;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.GitblitRedirectException;
import com.gitblit.wicket.PageRegistration;
import com.gitblit.wicket.PageRegistration.DropDownMenuItem;
import com.gitblit.wicket.PageRegistration.DropDownMenuRegistration;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.GravatarImage;
import com.gitblit.wicket.panels.LinkPanel;
import com.gitblit.wicket.panels.ProjectRepositoryPanel;

public class UserPage extends RootPage {

	List<ProjectModel> projectModels = new ArrayList<ProjectModel>();

	public UserPage() {
		super();
		throw new GitblitRedirectException(GitBlitWebApp.get().getHomePage());
	}

	public UserPage(PageParameters params) {
		super(params);
		setup(params);
	}

	@Override
	protected boolean reusePageParameters() {
		return true;
	}

	private void setup(PageParameters params) {
		setupPage("", "");
		// check to see if we should display a login message
		boolean authenticateView = app().settings().getBoolean(Keys.web.authenticateViewPages, true);
		if (authenticateView && !GitBlitWebSession.get().isLoggedIn()) {
			authenticationError("Please login");
			return;
		}

		String userName = WicketUtils.getUsername(params);
		if (StringUtils.isEmpty(userName)) {
			throw new GitblitRedirectException(GitBlitWebApp.get().getHomePage());
		}

		UserModel user = app().users().getUserModel(userName);
		if (user == null) {
			// construct a temporary user model
			user = new UserModel(userName);
		}

		add(new Label("userDisplayName", user.getDisplayName()));
		add(new Label("userUsername", user.username));
		LinkPanel email = new LinkPanel("userEmail", null, user.emailAddress, "mailto:#");
		email.setRenderBodyOnly(true);
		add(email.setVisible(app().settings().getBoolean(Keys.web.showEmailAddresses, true) && !StringUtils.isEmpty(user.emailAddress)));

		PersonIdent person = new PersonIdent(user.getDisplayName(), user.emailAddress == null ? user.getDisplayName() : user.emailAddress);
		add(new GravatarImage("gravatar", person, 210));

		UserModel sessionUser = GitBlitWebSession.get().getUser();
		if (sessionUser != null && user.canCreate() && sessionUser.equals(user)) {
			// user can create personal repositories
			add(new BookmarkablePageLink<Void>("newRepository", EditRepositoryPage.class));
		} else {
			add(new Label("newRepository").setVisible(false));
		}

		List<RepositoryModel> repositories = getRepositories(params);

		Collections.sort(repositories, new Comparator<RepositoryModel>() {
			@Override
			public int compare(RepositoryModel o1, RepositoryModel o2) {
				// reverse-chronological sort
				return o2.lastChange.compareTo(o1.lastChange);
			}
		});

		final ListDataProvider<RepositoryModel> dp = new ListDataProvider<RepositoryModel>(repositories);
		DataView<RepositoryModel> dataView = new DataView<RepositoryModel>("repositoryList", dp) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(final Item<RepositoryModel> item) {
				final RepositoryModel entry = item.getModelObject();

				ProjectRepositoryPanel row = new ProjectRepositoryPanel("repository",
						getLocalizer(), this, showAdmin, entry, getAccessRestrictions());
				item.add(row);
			}
		};
		add(dataView);
	}

	@Override
	protected void addDropDownMenus(List<PageRegistration> pages) {
		PageParameters params = getPageParameters();

		DropDownMenuRegistration menu = new DropDownMenuRegistration("gb.filters",
				UserPage.class);
		// preserve time filter option on repository choices
		menu.menuItems.addAll(getRepositoryFilterItems(params));

		// preserve repository filter option on time choices
		menu.menuItems.addAll(getTimeFilterItems(params));

		if (menu.menuItems.size() > 0) {
			// Reset Filter
			menu.menuItems.add(new DropDownMenuItem(getString("gb.reset"), null, null));
		}

		pages.add(menu);
	}
}
