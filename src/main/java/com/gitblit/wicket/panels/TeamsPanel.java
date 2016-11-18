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

import com.gitblit.models.TeamModel;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.EditTeamPage;

public class TeamsPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	public TeamsPanel(String wicketId, final boolean showAdmin) {
		super(wicketId);

		Fragment adminLinks = new Fragment("adminPanel", "adminLinks", TeamsPanel.this);
		adminLinks.add(new BookmarkablePageLink<Void>("newTeam", EditTeamPage.class));
		add(adminLinks.setVisible(showAdmin));

		final List<TeamModel> teams = app().users().getAllTeams();
		DataView<TeamModel> teamsView = new DataView<TeamModel>("teamRow",
				new ListDataProvider<TeamModel>(teams)) {
			private static final long serialVersionUID = 1L;
			private int counter;

			@Override
			protected void onBeforeRender() {
				super.onBeforeRender();
				counter = 0;
			}

			@Override
			public void populateItem(final Item<TeamModel> item) {
				final TeamModel entry = item.getModelObject();
				LinkPanel editLink = new LinkPanel("teamname", "list", entry.name,
						EditTeamPage.class, WicketUtils.newTeamnameParameter(entry.name));
				WicketUtils.setHtmlTooltip(editLink, getString("gb.edit") + " " + entry.name);
				item.add(editLink);
				item.add(new Label("accountType", entry.accountType.name()));
				item.add(new Label("members", entry.users.size() > 0 ? ("" + entry.users.size())
						: ""));
				item.add(new Label("repositories",
						entry.repositories.size() > 0 ? ("" + entry.repositories.size()) : ""));
				Fragment teamLinks = new Fragment("teamLinks", "teamAdminLinks", TeamsPanel.this);
				teamLinks.add(new BookmarkablePageLink<Void>("editTeam", EditTeamPage.class,
						WicketUtils.newTeamnameParameter(entry.name)));
				Link<Void> deleteLink = new Link<Void>("deleteTeam") {

					private static final long serialVersionUID = 1L;

					@Override
					public void onClick() {
						if (app().users().deleteTeam(entry.name)) {
							teams.remove(entry);
							info(MessageFormat.format("Team ''{0}'' deleted.", entry.name));
						} else {
							error(MessageFormat
									.format("Failed to delete team ''{0}''!", entry.name));
						}
					}
				};
				deleteLink.add(new JavascriptEventConfirmation("click", MessageFormat.format(
						"Delete team \"{0}\"?", entry.name)));
				teamLinks.add(deleteLink);
				item.add(teamLinks);

				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(teamsView.setVisible(showAdmin));
	}
}
