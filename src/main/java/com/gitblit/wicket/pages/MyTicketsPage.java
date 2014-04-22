package com.gitblit.wicket.pages;

import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;

import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.UserModel;
import com.gitblit.models.TicketModel.Status;
import com.gitblit.models.TicketModel.Type;
import com.gitblit.tickets.ITicketService;
import com.gitblit.tickets.QueryBuilder;
import com.gitblit.tickets.QueryResult;
import com.gitblit.tickets.TicketIndexer.Lucene;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebApp;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.GravatarImage;
import com.gitblit.wicket.panels.LinkPanel;

public class MyTicketsPage extends RootPage {
	
	public MyTicketsPage()
	{
		this(null);	
	}
	
	public MyTicketsPage(PageParameters params)
	{
		super(params);
		setupPage("", getString("gb.mytickets"));
		
		UserModel currentUser = GitBlitWebSession.get().getUser();
		if (currentUser == null) {
			setRedirect(true);
			setResponsePage(getApplication().getHomePage());
			return;
		}
		String username = currentUser.getName();
		
		QueryBuilder qb = QueryBuilder
			.q(Lucene.createdby.matches(username))
			.or(Lucene.responsible.matches(username))
			.or(Lucene.watchedby.matches(username));
		
		ITicketService tickets = GitBlitWebApp.get().tickets();
		List<QueryResult> results = tickets.queryFor(qb.build(), 0, 0, Lucene.updated.name(), true);
		
		final ListDataProvider<QueryResult> dp = new ListDataProvider<QueryResult>(results);
		
		DataView<QueryResult> dataView = new DataView<QueryResult>("row", dp) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void populateItem(Item<QueryResult> item) {
				QueryResult ticket = item.getModelObject();
				RepositoryModel repository = app().repositories().getRepositoryModel(ticket.repository);
				
				Component swatch = new Label("repositorySwatch", "&nbsp;").setEscapeModelStrings(false);
				WicketUtils.setCssBackground(swatch, repository.toString());
				item.add(swatch);
				
				PageParameters rp = WicketUtils.newRepositoryParameter(ticket.repository);
				PageParameters tp = WicketUtils.newObjectParameter(ticket.repository, "" + ticket.number);
				item.add(new LinkPanel("repositoryName", "list", StringUtils.stripDotGit(ticket.repository), SummaryPage.class, rp));
				
				item.add(getStateIcon("ticketIcon", ticket.type, ticket.status));
				item.add(new Label("ticketNumber", "" + ticket.number));
				item.add(new LinkPanel("ticketTitle", "list", ticket.title, TicketsPage.class, tp));

				// votes indicator
				Label v = new Label("ticketVotes", "" + ticket.votesCount);
				WicketUtils.setHtmlTooltip(v, getString("gb.votes"));
				item.add(v.setVisible(ticket.votesCount > 0));

				Label ticketStatus = new Label("ticketStatus", ticket.status.toString());
				String statusClass = getStatusClass(ticket.status);
				WicketUtils.setCssClass(ticketStatus, statusClass);
				item.add(ticketStatus);
				
				UserModel responsible = app().users().getUserModel(ticket.responsible);
				if (responsible == null) {
					if (ticket.responsible == null) {
						item.add(new Label("ticketResponsibleImg").setVisible(false));
					} else {
						item.add(new GravatarImage("ticketResponsibleImg", ticket.responsible, ticket.responsible, null, 16, true));
					}
					item.add(new Label("ticketResponsible", ticket.responsible));
				} else {
					item.add(new GravatarImage("ticketResponsibleImg", responsible, null, 16, true));
					item.add(new LinkPanel("ticketResponsible", null, responsible.getDisplayName(), UserPage.class, WicketUtils.newUsernameParameter(ticket.responsible)));
				}
			}
		};
		
		add(dataView);
	}
	
	protected Label getStateIcon(String wicketId, TicketModel ticket) {
		return getStateIcon(wicketId, ticket.type, ticket.status);
	}

	protected Label getStateIcon(String wicketId, Type type, Status state) {
		Label label = new Label(wicketId);
		if (type == null) {
			type = Type.defaultType;
		}
		switch (type) {
		case Proposal:
			WicketUtils.setCssClass(label, "fa fa-code-fork");
			break;
		case Bug:
			WicketUtils.setCssClass(label, "fa fa-bug");
			break;
		case Enhancement:
			WicketUtils.setCssClass(label, "fa fa-magic");
			break;
		case Question:
			WicketUtils.setCssClass(label, "fa fa-question");
			break;
		default:
			// standard ticket
			WicketUtils.setCssClass(label, "fa fa-ticket");
		}
		WicketUtils.setHtmlTooltip(label, getTypeState(type, state));
		return label;
	}

	protected String getTypeState(Type type, Status state) {
		return state.toString() + " " + type.toString();
	}

	protected String getLozengeClass(Status status, boolean subtle) {
		if (status == null) {
			status = Status.New;
		}
		String css = "";
		switch (status) {
		case Declined:
		case Duplicate:
		case Invalid:
		case Wontfix:
		case Abandoned:
			css = "aui-lozenge-error";
			break;
		case Fixed:
		case Merged:
		case Resolved:
			css = "aui-lozenge-success";
			break;
		case New:
			css = "aui-lozenge-complete";
			break;
		case On_Hold:
			css = "aui-lozenge-current";
			break;
		default:
			css = "";
			break;
		}

		return "aui-lozenge" + (subtle ? " aui-lozenge-subtle": "") + (css.isEmpty() ? "" : " ") + css;
	}

	protected String getStatusClass(Status status) {
		String css = "";
		switch (status) {
		case Declined:
		case Duplicate:
		case Invalid:
		case Wontfix:
		case Abandoned:
			css = "resolution-error";
			break;
		case Fixed:
		case Merged:
		case Resolved:
			css = "resolution-success";
			break;
		case New:
			css = "resolution-complete";
			break;
		case On_Hold:
			css = "resolution-current";
			break;
		default:
			css = "";
			break;
		}

		return "resolution" + (css.isEmpty() ? "" : " ") + css;
	}
}
