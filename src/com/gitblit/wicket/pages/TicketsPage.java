package com.gitblit.wicket.pages;

import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;

import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.models.TicketModel;

public class TicketsPage extends RepositoryPage {

	public TicketsPage(PageParameters params) {
		super(params);

		List<TicketModel> tickets = JGitUtils.getTickets(getRepository());

		// header
		add(new LinkPanel("header", "title", repositoryName, SummaryPage.class, newRepositoryParameter()));

		ListDataProvider<TicketModel> ticketsDp = new ListDataProvider<TicketModel>(tickets);
		DataView<TicketModel> ticketsView = new DataView<TicketModel>("ticket", ticketsDp) {
			private static final long serialVersionUID = 1L;
			int counter = 0;

			public void populateItem(final Item<TicketModel> item) {
				final TicketModel entry = item.getModelObject();
				Label stateLabel = new Label("ticketState", entry.state);
				WicketUtils.setTicketCssClass(stateLabel, entry.state);
				item.add(stateLabel);
				item.add(WicketUtils.createDateLabel("ticketDate", entry.date, GitBlitWebSession.get().getTimezone()));
				item.add(new Label("ticketHandler", StringUtils.trimString(entry.handler.toLowerCase(), 30)));
				item.add(new LinkPanel("ticketTitle", "list subject", StringUtils.trimString(entry.title, 80), TicketPage.class, newPathParameter(entry.name)));

				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(ticketsView);
	}

	@Override
	protected String getPageName() {
		return getString("gb.tickets");
	}
}
