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

import java.text.MessageFormat;
import java.util.List;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;

import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.EditUserPage;

public class UsersPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	public UsersPanel(String wicketId, final boolean showAdmin) {
		super(wicketId);

		Fragment adminLinks = new Fragment("adminPanel", "adminLinks", UsersPanel.this);
		adminLinks.add(new BookmarkablePageLink<Void>("newUser", EditUserPage.class));
		add(adminLinks.setVisible(showAdmin));

		final List<UserModel> users = app().users().getAllUsers();
		DataView<UserModel> usersView = new DataView<UserModel>("userRow",
				new ListDataProvider<UserModel>(users)) {
			private static final long serialVersionUID = 1L;
			private int counter;

			@Override
			protected void onBeforeRender() {
				super.onBeforeRender();
				counter = 0;
			}

			@Override
			public void populateItem(final Item<UserModel> item) {
				final UserModel entry = item.getModelObject();
				String css = "list" + (entry.disabled ? "-strikethrough":"");
				LinkPanel editLink = new LinkPanel("username", css, entry.username,
						EditUserPage.class, WicketUtils.newUsernameParameter(entry.username));
				WicketUtils.setHtmlTooltip(editLink, getString("gb.edit") + " " + entry.getDisplayName());
				item.add(editLink);

				if (StringUtils.isEmpty(entry.displayName)) {
					item.add(new Label("displayName").setVisible(false));
				} else {
					editLink = new LinkPanel("displayName", css, entry.getDisplayName(),
						EditUserPage.class, WicketUtils.newUsernameParameter(entry.username));
					WicketUtils.setHtmlTooltip(editLink, getString("gb.edit") + " " + entry.getDisplayName());
					item.add(editLink);
				}

				if (StringUtils.isEmpty(entry.emailAddress)) {
					item.add(new Label("emailAddress").setVisible(false));
				} else {
					editLink = new LinkPanel("emailAddress", css, entry.emailAddress,
						EditUserPage.class, WicketUtils.newUsernameParameter(entry.username));
					WicketUtils.setHtmlTooltip(editLink, getString("gb.edit") + " " + entry.getDisplayName());
					item.add(editLink);
				}

				item.add(new Label("accountType", entry.accountType.name() + (entry.canAdmin() ? ", admin":"")));
				item.add(new Label("teams", entry.teams.size() > 0 ? ("" + entry.teams.size()) : ""));
				item.add(new Label("repositories",
						entry.permissions.size() > 0 ? ("" + entry.permissions.size()) : ""));
				Fragment userLinks = new Fragment("userLinks", "userAdminLinks", UsersPanel.this);
				userLinks.add(new BookmarkablePageLink<Void>("editUser", EditUserPage.class,
						WicketUtils.newUsernameParameter(entry.username)));
				Link<Void> deleteLink = new Link<Void>("deleteUser") {

					private static final long serialVersionUID = 1L;

					@Override
					public void onClick() {
						if (app().users().deleteUser(entry.username)) {
							users.remove(entry);
							info(MessageFormat.format(getString("gb.userDeleted"), entry.username));
						} else {
							error(MessageFormat.format(getString("gb.deleteUserFailed"),
									entry.username));
						}
					}
				};
				deleteLink.add(new JavascriptEventConfirmation("click", MessageFormat.format(
						getString("gb.deleteUser"), entry.username)));
				userLinks.add(deleteLink);
				item.add(userLinks);

				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(usersView.setVisible(showAdmin));
	}
}
