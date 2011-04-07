package com.gitblit.wicket.pages;

import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.TicGitTicket;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.WicketUtils;

public class TicGitPage extends RepositoryPage {

	public TicGitPage(PageParameters params) {
		super(params);

		Repository r = getRepository();
		List<TicGitTicket> tickets = JGitUtils.getTicGitTickets(r);

		// shortlog
		add(new LinkPanel("summary", "title", repositoryName, SummaryPage.class, newRepositoryParameter()));

		ListDataProvider<TicGitTicket> ticketsDp = new ListDataProvider<TicGitTicket>(tickets);
		DataView<TicGitTicket> ticketsView = new DataView<TicGitTicket>("ticket", ticketsDp) {
			private static final long serialVersionUID = 1L;
			int counter = 0;

			public void populateItem(final Item<TicGitTicket> item) {
				final TicGitTicket entry = item.getModelObject();
				Label stateLabel = new Label("ticketState", entry.state);
				WicketUtils.setTicketCssClass(stateLabel, entry.state);
				item.add(stateLabel);
				item.add(createDateLabel("ticketDate", entry.date));
				item.add(new Label("ticketHandler", trimString(entry.handler, 30)));
				item.add(new LinkPanel("ticketTitle", null, trimString(entry.title, 80), TicGitTicketPage.class, newPathParameter(entry.name)));

				setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(ticketsView);
	}
	
	@Override
	protected String getPageName() {
		return getString("gb.ticgit");
	}
}
