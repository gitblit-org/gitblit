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

import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;

import com.gitblit.GitBlit;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.EditUserPage;

public class UsersPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	public UsersPanel(String wicketId, final boolean showAdmin) {
		super(wicketId);

		Fragment adminLinks = new Fragment("adminPanel", "adminLinks", this);
		adminLinks.add(new BookmarkablePageLink<Void>("newUser", EditUserPage.class));
		add(adminLinks.setVisible(showAdmin));

		final List<String> usernames = GitBlit.self().getAllUsernames();
		DataView<String> usersView = new DataView<String>("userRow", new ListDataProvider<String>(
				usernames)) {
			private static final long serialVersionUID = 1L;
			private int counter;

			@Override
			protected void onBeforeRender() {
				super.onBeforeRender();
				counter = 0;
			}

			public void populateItem(final Item<String> item) {
				final String entry = item.getModelObject();
				LinkPanel editLink = new LinkPanel("username", "list", entry, EditUserPage.class,
						WicketUtils.newUsernameParameter(entry));
				WicketUtils.setHtmlTooltip(editLink, getString("gb.edit") + " " + entry);
				item.add(editLink);
				Fragment userLinks = new Fragment("userLinks", "userAdminLinks", this);
				userLinks.add(new BookmarkablePageLink<Void>("editUser", EditUserPage.class,
						WicketUtils.newUsernameParameter(entry)));
				Link<Void> deleteLink = new Link<Void>("deleteUser") {

					private static final long serialVersionUID = 1L;

					@Override
					public void onClick() {
						if (GitBlit.self().deleteUser(entry)) {
							usernames.remove(entry);
							info(MessageFormat.format("User ''{0}'' deleted.", entry));
						} else {
							error(MessageFormat.format("Failed to delete user ''{0}''!", entry));
						}
					}
				};
				deleteLink.add(new JavascriptEventConfirmation("onclick", MessageFormat.format(
						"Delete user \"{0}\"?", entry)));
				userLinks.add(deleteLink);
				item.add(userLinks);

				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(usersView.setVisible(showAdmin));
	}
}
