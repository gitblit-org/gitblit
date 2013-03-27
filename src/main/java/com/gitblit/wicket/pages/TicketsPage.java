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

import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;

import com.gitblit.models.TicketModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TicgitUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.LinkPanel;

public class TicketsPage extends RepositoryPage {

	public TicketsPage(PageParameters params) {
		super(params);

		List<TicketModel> tickets = TicgitUtils.getTickets(getRepository());

		// header
		add(new LinkPanel("header", "title", repositoryName, SummaryPage.class,
				newRepositoryParameter()));

		ListDataProvider<TicketModel> ticketsDp = new ListDataProvider<TicketModel>(tickets);
		DataView<TicketModel> ticketsView = new DataView<TicketModel>("ticket", ticketsDp) {
			private static final long serialVersionUID = 1L;
			int counter;

			public void populateItem(final Item<TicketModel> item) {
				final TicketModel entry = item.getModelObject();
				Label stateLabel = new Label("ticketState", entry.state);
				WicketUtils.setTicketCssClass(stateLabel, entry.state);
				item.add(stateLabel);
				item.add(WicketUtils.createDateLabel("ticketDate", entry.date, GitBlitWebSession
						.get().getTimezone(), getTimeUtils()));
				item.add(new Label("ticketHandler", StringUtils.trimString(
						entry.handler.toLowerCase(), 30)));
				item.add(new LinkPanel("ticketTitle", "list subject", StringUtils.trimString(
						entry.title, 80), TicketPage.class, newPathParameter(entry.name)));

				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(ticketsView);
	}

	protected PageParameters newPathParameter(String path) {
		return WicketUtils.newPathParameter(repositoryName, objectId, path);
	}

	@Override
	protected String getPageName() {
		return getString("gb.tickets");
	}
}
