package com.gitblit.wicket.pages;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.target.basic.RedirectRequestTarget;

import com.gitblit.Keys;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Status;
import com.gitblit.models.TicketModel.Type;
import com.gitblit.models.UserModel;
import com.gitblit.tickets.ITicketService;
import com.gitblit.tickets.QueryBuilder;
import com.gitblit.tickets.QueryResult;
import com.gitblit.tickets.TicketIndexer.Lucene;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebApp;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.SessionlessForm;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.GravatarImage;
import com.gitblit.wicket.panels.LinkPanel;

public class MyTicketsPage extends RootPage {

	public static final String [] openStatii = new String [] { Status.New.name().toLowerCase(), Status.Open.name().toLowerCase() };

	public static final String [] closedStatii = new String [] { "!" + Status.New.name().toLowerCase(), "!" + Status.Open.name().toLowerCase() };

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

		final String username = currentUser.getName();
		final String[] statiiParam = (params == null) ? openStatii : params.getStringArray(Lucene.status.name());
		final String assignedToParam = (params == null) ? "" : params.getString(Lucene.responsible.name(), null);
		final String milestoneParam = (params == null) ? "" : params.getString(Lucene.milestone.name(), null);
		final String queryParam = (params == null || StringUtils.isEmpty(params.getString("q", null))) ? "watchedby:" + username : params.getString("q", null);
		final String searchParam = (params == null) ? "" : params.getString("s", null);
		final String sortBy = (params == null) ? "" : Lucene.fromString(params.getString("sort", Lucene.created.name())).name();
		final boolean desc = (params == null) ? true : !"asc".equals(params.getString("direction", "desc"));

		// add search form
		TicketSearchForm searchForm = new TicketSearchForm("ticketSearchForm", searchParam);
		add(searchForm);
		searchForm.setTranslatedAttributes();

		// standard queries
		add(new BookmarkablePageLink<Void>("changesQuery", MyTicketsPage.class,
				queryParameters(
						Lucene.type.matches(TicketModel.Type.Proposal.name()),
						milestoneParam,
						statiiParam,
						assignedToParam,
						sortBy,
						desc,
						1)));

		add(new BookmarkablePageLink<Void>("bugsQuery", MyTicketsPage.class,
				queryParameters(
						Lucene.type.matches(TicketModel.Type.Bug.name()),
						milestoneParam,
						statiiParam,
						assignedToParam,
						sortBy,
						desc,
						1)));

		add(new BookmarkablePageLink<Void>("enhancementsQuery", MyTicketsPage.class,
				queryParameters(
						Lucene.type.matches(TicketModel.Type.Enhancement.name()),
						milestoneParam,
						statiiParam,
						assignedToParam,
						sortBy,
						desc,
						1)));

		add(new BookmarkablePageLink<Void>("tasksQuery", MyTicketsPage.class,
				queryParameters(
						Lucene.type.matches(TicketModel.Type.Task.name()),
						milestoneParam,
						statiiParam,
						assignedToParam,
						sortBy,
						desc,
						1)));

		add(new BookmarkablePageLink<Void>("questionsQuery", MyTicketsPage.class,
				queryParameters(
						Lucene.type.matches(TicketModel.Type.Question.name()),
						milestoneParam,
						statiiParam,
						assignedToParam,
						sortBy,
						desc,
						1)));

		add(new BookmarkablePageLink<Void>("resetQuery", MyTicketsPage.class,
				queryParameters(
						null,
						milestoneParam,
						openStatii,
						null,
						null,
						true,
						1)));

		add(new Label("userDivider"));
		add(new BookmarkablePageLink<Void>("createdQuery", MyTicketsPage.class,
				queryParameters(
						Lucene.createdby.matches(username),
						milestoneParam,
						statiiParam,
						assignedToParam,
						sortBy,
						desc,
						1)));

		add(new BookmarkablePageLink<Void>("watchedQuery", MyTicketsPage.class,
				queryParameters(
						Lucene.watchedby.matches(username),
						milestoneParam,
						statiiParam,
						assignedToParam,
						sortBy,
						desc,
						1)));
		add(new BookmarkablePageLink<Void>("mentionsQuery", MyTicketsPage.class,
				queryParameters(
						Lucene.mentions.matches(username),
						milestoneParam,
						statiiParam,
						assignedToParam,
						sortBy,
						desc,
						1)));

		// states
		if (ArrayUtils.isEmpty(statiiParam)) {
			add(new Label("selectedStatii", getString("gb.all")));
		} else {
			add(new Label("selectedStatii", StringUtils.flattenStrings(Arrays.asList(statiiParam), ",")));
		}
		add(new BookmarkablePageLink<Void>("openTickets", MyTicketsPage.class, queryParameters(queryParam, milestoneParam, openStatii, assignedToParam, sortBy, desc, 1)));
		add(new BookmarkablePageLink<Void>("closedTickets", MyTicketsPage.class, queryParameters(queryParam, milestoneParam, closedStatii, assignedToParam, sortBy, desc, 1)));
		add(new BookmarkablePageLink<Void>("allTickets", MyTicketsPage.class, queryParameters(queryParam, milestoneParam, null, assignedToParam, sortBy, desc, 1)));

		// by status
		List<Status> statii = new ArrayList<Status>(Arrays.asList(Status.values()));
		statii.remove(Status.Closed);
		ListDataProvider<Status> resolutionsDp = new ListDataProvider<Status>(statii);
		DataView<Status> statiiLinks = new DataView<Status>("statii", resolutionsDp) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(final Item<Status> item) {
				final Status status = item.getModelObject();
				PageParameters p = queryParameters(queryParam, milestoneParam, new String [] { status.name().toLowerCase() }, assignedToParam, sortBy, desc, 1);
				String css = getStatusClass(status);
				item.add(new LinkPanel("statusLink", css, status.toString(), MyTicketsPage.class, p).setRenderBodyOnly(true));
			}
		};
		add(statiiLinks);

		List<TicketSort> sortChoices = new ArrayList<TicketSort>();
		sortChoices.add(new TicketSort(getString("gb.sortNewest"), Lucene.created.name(), true));
		sortChoices.add(new TicketSort(getString("gb.sortOldest"), Lucene.created.name(), false));
		sortChoices.add(new TicketSort(getString("gb.sortMostRecentlyUpdated"), Lucene.updated.name(), true));
		sortChoices.add(new TicketSort(getString("gb.sortLeastRecentlyUpdated"), Lucene.updated.name(), false));
		sortChoices.add(new TicketSort(getString("gb.sortMostComments"), Lucene.comments.name(), true));
		sortChoices.add(new TicketSort(getString("gb.sortLeastComments"), Lucene.comments.name(), false));
		sortChoices.add(new TicketSort(getString("gb.sortMostPatchsetRevisions"), Lucene.patchsets.name(), true));
		sortChoices.add(new TicketSort(getString("gb.sortLeastPatchsetRevisions"), Lucene.patchsets.name(), false));
		sortChoices.add(new TicketSort(getString("gb.sortMostVotes"), Lucene.votes.name(), true));
		sortChoices.add(new TicketSort(getString("gb.sortLeastVotes"), Lucene.votes.name(), false));

		TicketSort currentSort = sortChoices.get(0);
		for (TicketSort ts : sortChoices) {
			if (ts.sortBy.equals(sortBy) && desc == ts.desc) {
				currentSort = ts;
				break;
			}
		}
		add(new Label("currentSort", currentSort.name));

		ListDataProvider<TicketSort> sortChoicesDp = new ListDataProvider<TicketSort>(sortChoices);
		DataView<TicketSort> sortMenu = new DataView<TicketSort>("sort", sortChoicesDp) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(final Item<TicketSort> item) {
				final TicketSort ts = item.getModelObject();
				PageParameters params = queryParameters(queryParam, milestoneParam, statiiParam, assignedToParam, ts.sortBy, ts.desc, 1);
				item.add(new LinkPanel("sortLink", null, ts.name, MyTicketsPage.class, params).setRenderBodyOnly(true));
			}
		};
		add(sortMenu);

		// Build Query here
		QueryBuilder qb = new QueryBuilder(queryParam);
		if (!qb.containsField(Lucene.status.name()) && !ArrayUtils.isEmpty(statiiParam)) {
			// specify the states
			boolean not = false;
			QueryBuilder q = new QueryBuilder();
			for (String state : statiiParam) {
				if (state.charAt(0) == '!') {
					not = true;
					q.and(Lucene.status.doesNotMatch(state.substring(1)));
				} else {
					q.or(Lucene.status.matches(state));
				}
			}
			if (not) {
				qb.and(q.toString());
			} else {
				qb.and(q.toSubquery().toString());
			}
		}

		final String luceneQuery;
		if (qb.containsField(Lucene.createdby.name())
				|| qb.containsField(Lucene.responsible.name())
				|| qb.containsField(Lucene.watchedby.name())) {
			// focused "my tickets" query
			luceneQuery = qb.build();
		} else {
			// general "my tickets" query
			QueryBuilder myQuery = new QueryBuilder();
			myQuery.or(Lucene.createdby.matches(username));
			myQuery.or(Lucene.responsible.matches(username));
			myQuery.or(Lucene.watchedby.matches(username));
			myQuery.and(qb.toSubquery().toString());
			luceneQuery = myQuery.build();
		}

		// paging links
		int page = (params != null) ? Math.max(1, WicketUtils.getPage(params)) : 1;
		int pageSize = app().settings().getInteger(Keys.tickets.perPage, 25);

		ITicketService tickets = GitBlitWebApp.get().tickets();
		List<QueryResult> results;
		if(StringUtils.isEmpty(searchParam))
		{
			results = tickets.queryFor(luceneQuery, page, pageSize, sortBy, desc);
		}
		else
		{
			results = tickets.searchFor(null, searchParam, page, pageSize);
		}
		int totalResults = results.size() == 0 ? 0 : results.get(0).totalResults;
		buildPager(queryParam, milestoneParam, statiiParam, assignedToParam, sortBy, desc, page, pageSize, results.size(), totalResults);

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

	private class TicketSort implements Serializable {

		private static final long serialVersionUID = 1L;

		final String name;
		final String sortBy;
		final boolean desc;

		TicketSort(String name, String sortBy, boolean desc) {
			this.name = name;
			this.sortBy = sortBy;
			this.desc = desc;
		}
	}

	private class TicketSearchForm extends SessionlessForm<Void> implements Serializable {
		private static final long serialVersionUID = 1L;

		private final IModel<String> searchBoxModel;;

		public TicketSearchForm(String id, String text) {
			super(id, MyTicketsPage.this.getClass(), MyTicketsPage.this.getPageParameters());

			this.searchBoxModel = new Model<String>(text == null ? "" : text);

			TextField<String> searchBox = new TextField<String>("ticketSearchBox", searchBoxModel);
			add(searchBox);
		}

		void setTranslatedAttributes() {
			WicketUtils.setHtmlTooltip(get("ticketSearchBox"),
					MessageFormat.format(getString("gb.searchTicketsTooltip"), ""));
			WicketUtils.setInputPlaceholder(get("ticketSearchBox"), getString("gb.searchTickets"));
		}

		@Override
		public void onSubmit() {
			String searchString = searchBoxModel.getObject();
			if (StringUtils.isEmpty(searchString)) {
				// redirect to self to avoid wicket page update bug
				String absoluteUrl = getCanonicalUrl();
				getRequestCycle().setRequestTarget(new RedirectRequestTarget(absoluteUrl));
				return;
			}

			// use an absolute url to workaround Wicket-Tomcat problems with
			// mounted url parameters (issue-111)
			PageParameters params = WicketUtils.newRepositoryParameter("");
			params.add("s", searchString);
			String absoluteUrl = getCanonicalUrl(MyTicketsPage.class, params);
			getRequestCycle().setRequestTarget(new RedirectRequestTarget(absoluteUrl));
		}
	}

	protected PageParameters queryParameters(
			String query,
			String milestone,
			String[] states,
			String assignedTo,
			String sort,
			boolean descending,
			int page) {

		PageParameters params = WicketUtils.newRepositoryParameter("");
		if (!StringUtils.isEmpty(query)) {
			params.add("q", query);
		}
		if (!StringUtils.isEmpty(milestone)) {
			params.add(Lucene.milestone.name(), milestone);
		}
		if (!ArrayUtils.isEmpty(states)) {
			for (String state : states) {
				params.add(Lucene.status.name(), state);
			}
		}
		if (!StringUtils.isEmpty(assignedTo)) {
			params.add(Lucene.responsible.name(), assignedTo);
		}
		if (!StringUtils.isEmpty(sort)) {
			params.add("sort", sort);
		}
		if (!descending) {
			params.add("direction", "asc");
		}
		if (page > 1) {
			params.add("pg", "" + page);
		}
		return params;
	}

	protected void buildPager(
			final String query,
			final String milestone,
			final String [] states,
			final String assignedTo,
			final String sort,
			final boolean desc,
			final int page,
			int pageSize,
			int count,
			int total) {

		boolean showNav = total > (2 * pageSize);
		boolean allowPrev = page > 1;
		boolean allowNext = (pageSize * (page - 1) + count) < total;
		add(new BookmarkablePageLink<Void>("prevLink", MyTicketsPage.class, queryParameters(query, milestone, states, assignedTo, sort, desc, page - 1)).setEnabled(allowPrev).setVisible(showNav));
		add(new BookmarkablePageLink<Void>("nextLink", MyTicketsPage.class, queryParameters(query, milestone, states, assignedTo, sort, desc, page + 1)).setEnabled(allowNext).setVisible(showNav));

		if (total <= pageSize) {
			add(new Label("pageLink").setVisible(false));
			return;
		}

		// determine page numbers to display
		int pages = count == 0 ? 0 : ((total / pageSize) + (total % pageSize == 0 ? 0 : 1));
		// preferred number of pagelinks
		int segments = 5;
		if (pages < segments) {
			// not enough data for preferred number of page links
			segments = pages;
		}
		int minpage = Math.min(Math.max(1, page - 2), pages - (segments - 1));
		int maxpage = Math.min(pages, minpage + (segments - 1));
		List<Integer> sequence = new ArrayList<Integer>();
		for (int i = minpage; i <= maxpage; i++) {
			sequence.add(i);
		}

		ListDataProvider<Integer> pagesDp = new ListDataProvider<Integer>(sequence);
		DataView<Integer> pagesView = new DataView<Integer>("pageLink", pagesDp) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(final Item<Integer> item) {
				final Integer i = item.getModelObject();
				LinkPanel link = new LinkPanel("page", null, "" + i, MyTicketsPage.class, queryParameters(query, milestone, states, assignedTo, sort, desc, i));
				link.setRenderBodyOnly(true);
				if (i == page) {
					WicketUtils.setCssClass(item, "active");
				}
				item.add(link);
			}
		};
		add(pagesView);
	}
}
