/*
 * Copyright 2014 gitblit.com.
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
import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.behavior.SimpleAttributeModifier;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.Constants;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.tickets.QueryResult;
import com.gitblit.tickets.TicketLabel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.BugtraqProcessor;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.TicketsUI;
import com.gitblit.wicket.TicketsUI.Indicator;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.TicketsPage;
import com.gitblit.wicket.pages.UserPage;

/**
 *
 * The ticket list panel lists tickets in a table.
 *
 * @author James Moger
 *
 */
public class TicketListPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	public TicketListPanel(String wicketId, List<QueryResult> list, final boolean showSwatch, final boolean showRepository) {
		super(wicketId);

		final ListDataProvider<QueryResult> dp = new ListDataProvider<QueryResult>(list);
		DataView<QueryResult> dataView = new DataView<QueryResult>("row", dp) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void populateItem(Item<QueryResult> item) {
				final QueryResult ticket = item.getModelObject();

				if (showSwatch) {
					// set repository color
					String color = StringUtils.getColor(StringUtils.stripDotGit(ticket.repository));
					WicketUtils.setCssStyle(item, MessageFormat.format("border-left: 2px solid {0};", color));
				}

				PageParameters tp = WicketUtils.newObjectParameter(ticket.repository, "" + ticket.number);

				if (showRepository) {
					String name = StringUtils.stripDotGit(ticket.repository);
					PageParameters rp =  WicketUtils.newOpenTicketsParameter(ticket.repository);
					LinkPanel link = new LinkPanel("ticketsLink", null, name, TicketsPage.class, rp);
					WicketUtils.setCssBackground(link, name);
					item.add(link);
				} else {
					item.add(new Label("ticketsLink").setVisible(false));
				}

				item.add(TicketsUI.getStateIcon("state", ticket.type, ticket.status));
				item.add(new Label("id", "" + ticket.number));
				UserModel creator = app().users().getUserModel(ticket.createdBy);
				if (creator != null) {
					item.add(new LinkPanel("createdBy", null, creator.getDisplayName(),
							UserPage.class, WicketUtils.newUsernameParameter(ticket.createdBy)));
				} else {
					item.add(new Label("createdBy", ticket.createdBy));
				}
				item.add(WicketUtils.createDateLabel("createDate", ticket.createdAt, GitBlitWebSession
						.get().getTimezone(), getTimeUtils(), false));

				if (ticket.updatedAt == null) {
					item.add(new Label("updated").setVisible(false));
				} else {
					Fragment updated = new Fragment("updated", "updatedFragment", this);
					UserModel updater = app().users().getUserModel(ticket.updatedBy);
					if (updater != null) {
						updated.add(new LinkPanel("updatedBy", null, updater.getDisplayName(),
								UserPage.class, WicketUtils.newUsernameParameter(ticket.updatedBy)));
					} else {
						updated.add(new Label("updatedBy", ticket.updatedBy));
					}
					updated.add(WicketUtils.createDateLabel("updateDate", ticket.updatedAt, GitBlitWebSession
							.get().getTimezone(), getTimeUtils(), false));
					item.add(updated);
				}

				item.add(new LinkPanel("title", "list subject", StringUtils.trimString(
						ticket.title, Constants.LEN_SHORTLOG), TicketsPage.class, tp));

				ListDataProvider<String> labelsProvider = new ListDataProvider<String>(ticket.getLabels());
				DataView<String> labelsView = new DataView<String>("labels", labelsProvider) {
					private static final long serialVersionUID = 1L;

					@Override
					public void populateItem(final Item<String> labelItem) {
						RepositoryModel repository = app().repositories().getRepositoryModel(ticket.repository);
						Label label;
						TicketLabel tLabel;
						if (repository == null) {
							label = new Label("label", labelItem.getModelObject());
							tLabel = new TicketLabel(labelItem.getModelObject());
						} else {
							Repository db = app().repositories().getRepository(repository.name);
							BugtraqProcessor btp  = new BugtraqProcessor(app().settings());
							String content = btp.processText(db, repository.name, labelItem.getModelObject());
							db.close();

							label = new Label("label", content);
							label.setEscapeModelStrings(false);

							tLabel = app().tickets().getLabel(repository, labelItem.getModelObject());
						}

						String background = MessageFormat.format("background-color:{0};", tLabel.color);
						label.add(new SimpleAttributeModifier("style", background));
						labelItem.add(label);
					}
				};
				item.add(labelsView);

				if (StringUtils.isEmpty(ticket.responsible)) {
					item.add(new Label("responsible").setVisible(false));
				} else {
					UserModel responsible = app().users().getUserModel(ticket.responsible);
					if (responsible == null) {
						responsible = new UserModel(ticket.responsible);
					}
					GravatarImage avatar = new GravatarImage("responsible", responsible.getDisplayName(),
							responsible.emailAddress, null, 16, true);
					avatar.setTooltip(getString("gb.responsible") + ": " + responsible.getDisplayName());
					item.add(avatar);
				}

				// votes indicator
				Label v = new Label("votes", "" + ticket.votesCount);
				WicketUtils.setHtmlTooltip(v, getString("gb.votes"));
				item.add(v.setVisible(ticket.votesCount > 0));

				// watching indicator
				item.add(new Label("watching").setVisible(ticket.isWatching(GitBlitWebSession.get().getUsername())));

				// status indicator
				String css = TicketsUI.getLozengeClass(ticket.status, true);
				Label l = new Label("status", ticket.status.toString());
				WicketUtils.setCssClass(l, css);
				item.add(l);

				// add the ticket indicators/icons
				List<Indicator> indicators = new ArrayList<Indicator>();

				// comments
				if (ticket.commentsCount > 0) {
					int count = ticket.commentsCount;
					String pattern = getString("gb.nComments");
					if (count == 1) {
						pattern = getString("gb.oneComment");
					}
					indicators.add(new Indicator("fa fa-comment", count, pattern));
				}

				// participants
				if (!ArrayUtils.isEmpty(ticket.participants)) {
					int count = ticket.participants.size();
					if (count > 1) {
						String pattern = getString("gb.nParticipants");
						indicators.add(new Indicator("fa fa-user", count, pattern));
					}
				}

				// attachments
				if (!ArrayUtils.isEmpty(ticket.attachments)) {
					int count = ticket.attachments.size();
					String pattern = getString("gb.nAttachments");
					if (count == 1) {
						pattern = getString("gb.oneAttachment");
					}
					indicators.add(new Indicator("fa fa-file", count, pattern));
				}

				// patchset revisions
				if (ticket.patchset != null) {
					int count = ticket.patchset.commits;
					String pattern = getString("gb.nCommits");
					if (count == 1) {
						pattern = getString("gb.oneCommit");
					}
					indicators.add(new Indicator("fa fa-code", count, pattern));
				}

				// milestone
				if (!StringUtils.isEmpty(ticket.milestone)) {
					indicators.add(new Indicator("fa fa-bullseye", ticket.milestone));
				}

				ListDataProvider<Indicator> indicatorsDp = new ListDataProvider<Indicator>(indicators);
				DataView<Indicator> indicatorsView = new DataView<Indicator>("indicators", indicatorsDp) {
					private static final long serialVersionUID = 1L;

					@Override
					public void populateItem(final Item<Indicator> item) {
						Indicator indicator = item.getModelObject();
						String tooltip = indicator.getTooltip();

						Label icon = new Label("icon");
						WicketUtils.setCssClass(icon, indicator.css);
						item.add(icon);

						if (indicator.count > 0) {
							Label count = new Label("count", "" + indicator.count);
							item.add(count.setVisible(!StringUtils.isEmpty(tooltip)));
						} else {
							item.add(new Label("count").setVisible(false));
						}

						WicketUtils.setHtmlTooltip(item, tooltip);
					}
				};
				item.add(indicatorsView);
			}
		};

		add(dataView);
	}
}

