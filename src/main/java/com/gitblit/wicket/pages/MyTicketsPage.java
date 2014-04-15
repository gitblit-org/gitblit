package com.gitblit.wicket.pages;

import java.util.ArrayList;
import java.util.List;

import com.gitblit.models.UserModel;
import com.gitblit.models.TicketModel;
import com.gitblit.tickets.ITicketService;
import com.gitblit.wicket.GitBlitWebApp;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.panels.LinkPanel;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
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
		
		ITicketService tickets = GitBlitWebApp.get().tickets();
		List<TicketModel> returnedTickets = tickets.getTickets(null);
		
		List<TicketModel> responsibleTickets = new ArrayList<TicketModel>();
		List<TicketModel> authorTickets = new ArrayList<TicketModel>();
		List<TicketModel> votedTickets = new ArrayList<TicketModel>();
		List<TicketModel> watchedTickets = new ArrayList<TicketModel>();
		for(int i = 0; i < returnedTickets.size(); i++)
		{
			TicketModel ticket = returnedTickets.get(i);
			if(ticket.isOpen())
			{
				if(ticket.isResponsible(username))
				{
					responsibleTickets.add(ticket);
				}
				if(ticket.isAuthor(username))
				{
					authorTickets.add(ticket);
				}
				if(ticket.isVoter(username))
				{
					votedTickets.add(ticket);
				}
				if(ticket.isWatching(username))
				{
					watchedTickets.add(ticket);
				}
			}
		}
		
		ListView<TicketModel> responsibleView = new ListView<TicketModel>("responsibleTickets", responsibleTickets)
		{
			private static final long serialVersionUID = 1L;
			
			@Override
			public void populateItem(final ListItem<TicketModel> item)
			{
				final TicketModel ticket = item.getModelObject();
				String ticketUrl = app().tickets().getTicketUrl(ticket);
				item.add(new LinkPanel("ticketName", "", ticket.title, ticketUrl));
				item.add(new Label("ticketDescription", ticket.body));
			}
		};
		
		ListView<TicketModel> authorView = new ListView<TicketModel>("authorTickets", authorTickets)
		{
			private static final long serialVersionUID = 1L;
			
			@Override
			public void populateItem(final ListItem<TicketModel> item)
			{
				final TicketModel ticket = item.getModelObject();
				String ticketUrl = app().tickets().getTicketUrl(ticket);
				item.add(new LinkPanel("ticketName", "", ticket.title, ticketUrl));
				item.add(new Label("ticketDescription", ticket.body));
			}
		};
		
		ListView<TicketModel> votedView = new ListView<TicketModel>("votedTickets", votedTickets)
		{
			private static final long serialVersionUID = 1L;
			
			@Override
			public void populateItem(final ListItem<TicketModel> item)
			{
				final TicketModel ticket = item.getModelObject();
				String ticketUrl = app().tickets().getTicketUrl(ticket);
				item.add(new LinkPanel("ticketName", "", ticket.title, ticketUrl));
				item.add(new Label("ticketDescription", ticket.body));
			}
		};
		
		ListView<TicketModel> watchedView = new ListView<TicketModel>("watchedTickets", watchedTickets)
		{
			private static final long serialVersionUID = 1L;
			
			@Override
			public void populateItem(final ListItem<TicketModel> item)
			{
				final TicketModel ticket = item.getModelObject();
				String ticketUrl = app().tickets().getTicketUrl(ticket);
				item.add(new LinkPanel("ticketName", "", ticket.title, ticketUrl));
				item.add(new Label("ticketDescription", ticket.body));
			}
		};
		
		add(responsibleView);
		add(authorView);
		add(votedView);
		add(watchedView);
	}
}
