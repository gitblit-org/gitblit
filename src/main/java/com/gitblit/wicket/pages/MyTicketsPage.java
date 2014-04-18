package com.gitblit.wicket.pages;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.models.TicketModel;
import com.gitblit.tickets.ITicketService;
import com.gitblit.wicket.GitBlitWebApp;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.LinkPanel;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.IDataProvider;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.model.IModel;
import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;

public class MyTicketsPage extends RootPage {
	
	public MyTicketsPage(PageParameters params)
	{
		this();	
	}
	
	public MyTicketsPage()
	{
		super();
		setupPage("", "");
		
		UserModel currentUser = GitBlitWebSession.get().getUser();
		if (currentUser == null) {
			currentUser = UserModel.ANONYMOUS;
		}
		String username = currentUser.getName();
		
		// TODO - Recover the Welcome message
		String message = "Welcome on GitBlit";
		this.add(new Label("myTicketsMessage", message));
		
		Fragment fragment = new Fragment("headerContent", "ticketsHeader", this);
		add(fragment);
		
		ITicketService tickets = GitBlitWebApp.get().tickets();
		List<TicketModel> returnedTickets = tickets.getTickets(null);
		List<TicketModel> yourTickets = new ArrayList<TicketModel>();
		
		for(int i = 0; i < returnedTickets.size(); i++)
		{
			TicketModel ticket = returnedTickets.get(i);
			if(ticket.isOpen())
			{
				if(ticket.isResponsible(username) || ticket.isAuthor(username)
						|| ticket.isVoter(username) || ticket.isWatching(username))
				{
					yourTickets.add(ticket);
				}
			}
		}
		
		final ListDataProvider<TicketModel> dp = new ListDataProvider<TicketModel>(yourTickets);
		
		DataView<TicketModel> dataView = new DataView<TicketModel>("row", dp) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void populateItem(Item<TicketModel> item) {
				TicketModel ticketModel = item.getModelObject();
				RepositoryModel repository = app().repositories().getRepositoryModel(ticketModel.repository);
				
				Fragment row = new Fragment("rowContent", "ticketRow", this);
				item.add(row);
				
				Component swatch;
				if(repository.isBare)
				{
					swatch = new Label("repositorySwatch", "&nbsp;").setEscapeModelStrings(false);
				}
				else
				{
					swatch = new Label("repositorySwatch", "!");
					WicketUtils.setHtmlTooltip(swatch, getString("gb.workingCopyWarning"));
				}
				WicketUtils.setCssBackground(swatch, repository.toString());
				row.add(swatch);
				
				PageParameters pp = WicketUtils.newRepositoryParameter(repository.name);
				Class<? extends BasePage> linkPage;
				if (repository.hasCommits) {
					// repository has content
					linkPage = SummaryPage.class;
				} else {
					// new/empty repository OR proposed repository
					linkPage = EmptyRepositoryPage.class;
				}
				
				String ticketUrl = app().tickets().getTicketUrl(ticketModel);
				
				row.add(new LinkPanel("repositoryName", "list", repository.name, linkPage, pp));
				row.add(new LinkPanel("ticketName", "list", ticketModel.title, ticketUrl));
				row.add(new LinkPanel("ticketDescription", "list", ticketModel.body, ticketUrl));
				row.add(new Label("ticketResponsible", ticketModel.responsible));
			}
		};
		
		add(dataView);
	}
}
