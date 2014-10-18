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
package com.gitblit.wicket.pages;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;

import com.gitblit.Keys;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Status;
import com.gitblit.models.UserModel;
import com.gitblit.tickets.QueryBuilder;
import com.gitblit.tickets.QueryResult;
import com.gitblit.tickets.TicketIndexer.Lucene;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.TicketsUI;
import com.gitblit.wicket.TicketsUI.TicketSort;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.LinkPanel;
import com.gitblit.wicket.panels.TicketListPanel;
import com.gitblit.wicket.panels.TicketSearchForm;
import com.gitblit.wicket.panels.UserTitlePanel;

/**
 * My Tickets page
 *
 * @author Christian Buisson
 * @author James Moger
 */
public class MyTicketsPage extends RootPage {

	public MyTicketsPage() {
		this(null);
	}

	public MyTicketsPage(PageParameters params)	{
		super(params);
		setupPage("", getString("gb.myTickets"));

		UserModel currentUser = GitBlitWebSession.get().getUser();
		if (currentUser == null || UserModel.ANONYMOUS.equals(currentUser)) {
			setRedirect(true);
			setResponsePage(getApplication().getHomePage());
			return;
		}

		final String username = currentUser.getName();
		final String[] statiiParam = (params == null) ? TicketsUI.openStatii : params.getStringArray(Lucene.status.name());
		final String assignedToParam = (params == null) ? "" : params.getString(Lucene.responsible.name(), null);
		final String milestoneParam = (params == null) ? "" : params.getString(Lucene.milestone.name(), null);
		final String queryParam = (params == null || StringUtils.isEmpty(params.getString("q", null))) ? "watchedby:" + username : params.getString("q", null);
		final String searchParam = (params == null) ? "" : params.getString("s", null);
		final String sortBy = (params == null) ? "" : Lucene.fromString(params.getString("sort", Lucene.created.name())).name();
		final boolean desc = (params == null) ? true : !"asc".equals(params.getString("direction", "desc"));

		// add the user title panel
		add(new UserTitlePanel("userTitlePanel", currentUser, getString("gb.myTickets")));

		// add search form
		add(new TicketSearchForm("ticketSearchForm", null, searchParam, getClass(), params));

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
		
		add(new BookmarkablePageLink<Void>("maintenanceQuery", MyTicketsPage.class,
				queryParameters(
						Lucene.type.matches(TicketModel.Type.Maintenance.name()),
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
						TicketsUI.openStatii,
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
		add(new BookmarkablePageLink<Void>("responsibleQuery", MyTicketsPage.class,
				queryParameters(
						Lucene.responsible.matches(username),
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
		add(new BookmarkablePageLink<Void>("openTickets", MyTicketsPage.class, queryParameters(queryParam, milestoneParam, TicketsUI.openStatii, assignedToParam, sortBy, desc, 1)));
		add(new BookmarkablePageLink<Void>("closedTickets", MyTicketsPage.class, queryParameters(queryParam, milestoneParam, TicketsUI.closedStatii, assignedToParam, sortBy, desc, 1)));
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
				String css = TicketsUI.getStatusClass(status);
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

		List<QueryResult> results;
		if(StringUtils.isEmpty(searchParam)) {
			results = app().tickets().queryFor(luceneQuery, page, pageSize, sortBy, desc);
		} else {
			results = app().tickets().searchFor(null, searchParam, page, pageSize);
		}

		int totalResults = results.size() == 0 ? 0 : results.get(0).totalResults;
		buildPager(queryParam, milestoneParam, statiiParam, assignedToParam, sortBy, desc, page, pageSize, results.size(), totalResults);

		final boolean showSwatch = app().settings().getBoolean(Keys.web.repositoryListSwatches, true);
		add(new TicketListPanel("ticketList", results, showSwatch, true));
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
