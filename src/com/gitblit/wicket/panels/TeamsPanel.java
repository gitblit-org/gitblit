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
import com.gitblit.wicket.pages.EditTeamPage;

public class TeamsPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	public TeamsPanel(String wicketId, final boolean showAdmin) {
		super(wicketId);

		Fragment adminLinks = new Fragment("adminPanel", "adminLinks", this);
		adminLinks.add(new BookmarkablePageLink<Void>("newTeam", EditTeamPage.class));
		add(adminLinks.setVisible(showAdmin));

		final List<String> teamnames = GitBlit.self().getAllTeamnames();
		DataView<String> teamsView = new DataView<String>("teamRow", new ListDataProvider<String>(
				teamnames)) {
			private static final long serialVersionUID = 1L;
			private int counter;

			@Override
			protected void onBeforeRender() {
				super.onBeforeRender();
				counter = 0;
			}

			public void populateItem(final Item<String> item) {
				final String entry = item.getModelObject();
				LinkPanel editLink = new LinkPanel("teamname", "list", entry, EditTeamPage.class,
						WicketUtils.newTeamnameParameter(entry));
				WicketUtils.setHtmlTooltip(editLink, getString("gb.edit") + " " + entry);
				item.add(editLink);
				Fragment teamLinks = new Fragment("teamLinks", "teamAdminLinks", this);
				teamLinks.add(new BookmarkablePageLink<Void>("editTeam", EditTeamPage.class,
						WicketUtils.newTeamnameParameter(entry)));
				Link<Void> deleteLink = new Link<Void>("deleteTeam") {

					private static final long serialVersionUID = 1L;

					@Override
					public void onClick() {
						if (GitBlit.self().deleteTeam(entry)) {
							teamnames.remove(entry);
							info(MessageFormat.format("Team ''{0}'' deleted.", entry));
						} else {
							error(MessageFormat.format("Failed to delete team ''{0}''!", entry));
						}
					}
				};
				deleteLink.add(new JavascriptEventConfirmation("onclick", MessageFormat.format(
						"Delete team \"{0}\"?", entry)));
				teamLinks.add(deleteLink);
				item.add(teamLinks);

				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(teamsView.setVisible(showAdmin));
	}
}
