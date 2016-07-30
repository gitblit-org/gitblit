/*
 * Copyright 2013 gitblit.com.
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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.util.string.StringValue;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.Keys;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Status;
import com.gitblit.models.UserModel;
import com.gitblit.tickets.QueryBuilder;
import com.gitblit.tickets.QueryResult;
import com.gitblit.tickets.TicketIndexer.Lucene;
import com.gitblit.tickets.TicketLabel;
import com.gitblit.tickets.TicketMilestone;
import com.gitblit.tickets.TicketResponsible;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.TicketsUI;
import com.gitblit.wicket.TicketsUI.TicketQuery;
import com.gitblit.wicket.TicketsUI.TicketSort;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.LinkPanel;
import com.gitblit.wicket.panels.TicketListPanel;
import com.gitblit.wicket.panels.TicketSearchForm;

public class TicketsPage extends RepositoryPage {

	final TicketResponsible any;

	public TicketsPage(PageParameters params) {
		super(params);

		if (!app().tickets().isReady()) {
			// tickets prohibited
			setResponsePage(SummaryPage.class, WicketUtils.newRepositoryParameter(repositoryName));
		} else if (!app().tickets().hasTickets(getRepositoryModel())) {
			// no tickets for this repository
			setResponsePage(NoTicketsPage.class, WicketUtils.newRepositoryParameter(repositoryName));
		} else {
			String id = WicketUtils.getObject(params);
			if (id != null) {
				// view the ticket with the TicketPage
				setResponsePage(TicketPage.class, params);
			}
		}

		// set stateless page preference
		setStatelessHint(true);

		any = new TicketResponsible(getString("gb.any"), "[* TO *]", null);

		UserModel user = GitBlitWebSession.get().getUser();
		boolean isAuthenticated = user != null && user.isAuthenticated;

		final String [] statiiParam = (String[]) params.getValues(Lucene.status.name()).stream().map(StringValue::toString).toArray();
		final String assignedToParam = params.get(Lucene.responsible.name()).toString(null);
		final String milestoneParam = params.get(Lucene.milestone.name()).toString(null);
		final String queryParam = params.get("q").toString(null);
		final String searchParam = params.get("s").toString(null);
		final String sortBy = Lucene.fromString(params.get("sort").toString( Lucene.created.name())).name();
		final boolean desc = !"asc".equals(params.get("direction").toString("desc"));

		// add search form
		add(new TicketSearchForm("ticketSearchForm", repositoryName, searchParam, getClass(), params));

		final String activeQuery;
		if (!StringUtils.isEmpty(searchParam)) {
			activeQuery = searchParam;
		} else if (StringUtils.isEmpty(queryParam)) {
			activeQuery = "";
		} else {
			activeQuery = queryParam;
		}

		// build Lucene query from defaults and request parameters
		QueryBuilder qb = new QueryBuilder(queryParam);
		if (!qb.containsField(Lucene.rid.name())) {
			// specify the repository
			qb.and(Lucene.rid.matches(getRepositoryModel().getRID()));
		}
		if (!qb.containsField(Lucene.responsible.name())) {
			// specify the responsible
			qb.and(Lucene.responsible.matches(assignedToParam));
		}
		if (!qb.containsField(Lucene.milestone.name())) {
			// specify the milestone
			qb.and(Lucene.milestone.matches(milestoneParam));
		}
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
		final String luceneQuery = qb.build();

		// open milestones
		List<TicketMilestone> milestones = app().tickets().getMilestones(getRepositoryModel(), Status.Open);
		TicketMilestone currentMilestone = null;
		if (!StringUtils.isEmpty(milestoneParam)) {
			for (TicketMilestone tm : milestones) {
				if (tm.name.equals(milestoneParam)) {
					// get the milestone (queries the index)
					currentMilestone = app().tickets().getMilestone(getRepositoryModel(), milestoneParam);
					break;
				}
			}

			if (currentMilestone == null) {
				// milestone not found, create a temporary one
				currentMilestone = new TicketMilestone(milestoneParam);
				String q = QueryBuilder.q(Lucene.rid.matches(getRepositoryModel().getRID())).and(Lucene.milestone.matches(milestoneParam)).build();
				currentMilestone.tickets = app().tickets().queryFor(q, 1, 0, Lucene.number.name(), true);
				milestones.add(currentMilestone);
			}
		}

		Fragment milestonePanel;
		if (currentMilestone == null) {
			milestonePanel = new Fragment("milestonePanel", "noMilestoneFragment", this);
			add(milestonePanel);
		} else {
			milestonePanel = new Fragment("milestonePanel", "milestoneProgressFragment", this);
			milestonePanel.add(new Label("currentMilestone", currentMilestone.name));
			if (currentMilestone.due == null) {
				milestonePanel.add(new Label("currentDueDate", getString("gb.notSpecified")));
			} else {
				milestonePanel.add(WicketUtils.createDateLabel("currentDueDate", currentMilestone.due, GitBlitWebSession
						.get().getTimezone(), getTimeUtils(), false));
			}
			Label label = new Label("progress");
			WicketUtils.setCssStyle(label, "width:" + currentMilestone.getProgress() + "%;");
			milestonePanel.add(label);

			milestonePanel.add(new LinkPanel("openTickets", null,
					MessageFormat.format(getString("gb.nOpenTickets"), currentMilestone.getOpenTickets()),
					TicketsPage.class,
					queryParameters(null, currentMilestone.name, TicketsUI.openStatii, null, sortBy, desc, 1)));

			milestonePanel.add(new LinkPanel("closedTickets", null,
					MessageFormat.format(getString("gb.nClosedTickets"), currentMilestone.getClosedTickets()),
					TicketsPage.class,
					queryParameters(null, currentMilestone.name, TicketsUI.closedStatii, null, sortBy, desc, 1)));

			milestonePanel.add(new Label("totalTickets", MessageFormat.format(getString("gb.nTotalTickets"), currentMilestone.getTotalTickets())));
			add(milestonePanel);
		}

		Fragment milestoneDropdown = new Fragment("milestoneDropdown", "milestoneDropdownFragment", this);
		PageParameters resetMilestone = queryParameters(queryParam, null, statiiParam, assignedToParam, sortBy, desc, 1);
		milestoneDropdown.add(new BookmarkablePageLink<Void>("resetMilestone", TicketsPage.class, resetMilestone));

		ListDataProvider<TicketMilestone> milestonesDp = new ListDataProvider<TicketMilestone>(milestones);
		DataView<TicketMilestone> milestonesMenu = new DataView<TicketMilestone>("milestone", milestonesDp) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(final Item<TicketMilestone> item) {
				final TicketMilestone tm = item.getModelObject();
				PageParameters params = queryParameters(queryParam, tm.name, statiiParam, assignedToParam, sortBy, desc, 1);
				item.add(new LinkPanel("milestoneLink", null, tm.name, TicketsPage.class, params).setRenderBodyOnly(true));
			}
		};
		milestoneDropdown.add(milestonesMenu);
		milestonePanel.add(milestoneDropdown);

		// search or query tickets
		int page = Math.max(1,  WicketUtils.getPage(params));
		int pageSize = app().settings().getInteger(Keys.tickets.perPage, 25);
		List<QueryResult> results;
		if (StringUtils.isEmpty(searchParam)) {
			results = app().tickets().queryFor(luceneQuery, page, pageSize, sortBy, desc);
		} else {
			results = app().tickets().searchFor(getRepositoryModel(), searchParam, page, pageSize);
		}
		int totalResults = results.size() == 0 ? 0 : results.get(0).totalResults;

		// standard queries
		add(new BookmarkablePageLink<Void>("changesQuery", TicketsPage.class,
				queryParameters(
						Lucene.type.matches(TicketModel.Type.Proposal.name()),
						milestoneParam,
						statiiParam,
						assignedToParam,
						sortBy,
						desc,
						1)));

		add(new BookmarkablePageLink<Void>("bugsQuery", TicketsPage.class,
				queryParameters(
						Lucene.type.matches(TicketModel.Type.Bug.name()),
						milestoneParam,
						statiiParam,
						assignedToParam,
						sortBy,
						desc,
						1)));

		add(new BookmarkablePageLink<Void>("enhancementsQuery", TicketsPage.class,
				queryParameters(
						Lucene.type.matches(TicketModel.Type.Enhancement.name()),
						milestoneParam,
						statiiParam,
						assignedToParam,
						sortBy,
						desc,
						1)));

		add(new BookmarkablePageLink<Void>("tasksQuery", TicketsPage.class,
				queryParameters(
						Lucene.type.matches(TicketModel.Type.Task.name()),
						milestoneParam,
						statiiParam,
						assignedToParam,
						sortBy,
						desc,
						1)));

		add(new BookmarkablePageLink<Void>("questionsQuery", TicketsPage.class,
				queryParameters(
						Lucene.type.matches(TicketModel.Type.Question.name()),
						milestoneParam,
						statiiParam,
						assignedToParam,
						sortBy,
						desc,
						1)));
		
		add(new BookmarkablePageLink<Void>("maintenanceQuery", TicketsPage.class,
				queryParameters(
						Lucene.type.matches(TicketModel.Type.Maintenance.name()),
						milestoneParam,
						statiiParam,
						assignedToParam,
						sortBy,
						desc,
						1)));

		add(new BookmarkablePageLink<Void>("resetQuery", TicketsPage.class,
				queryParameters(
						null,
						milestoneParam,
						TicketsUI.openStatii,
						null,
						null,
						true,
						1)));

		if (isAuthenticated) {
			add(new Label("userDivider"));
			add(new BookmarkablePageLink<Void>("createdQuery", TicketsPage.class,
					queryParameters(
							Lucene.createdby.matches(user.username),
							milestoneParam,
							statiiParam,
							assignedToParam,
							sortBy,
							desc,
							1)));

			add(new BookmarkablePageLink<Void>("watchedQuery", TicketsPage.class,
					queryParameters(
							Lucene.watchedby.matches(user.username),
							milestoneParam,
							statiiParam,
							assignedToParam,
							sortBy,
							desc,
							1)));
			add(new BookmarkablePageLink<Void>("mentionsQuery", TicketsPage.class,
					queryParameters(
							Lucene.mentions.matches(user.username),
							milestoneParam,
							statiiParam,
							assignedToParam,
							sortBy,
							desc,
							1)));
		} else {
			add(new Label("userDivider").setVisible(false));
			add(new Label("createdQuery").setVisible(false));
			add(new Label("watchedQuery").setVisible(false));
			add(new Label("mentionsQuery").setVisible(false));
		}

		Set<TicketQuery> dynamicQueries = new TreeSet<TicketQuery>();
		for (TicketLabel label : app().tickets().getLabels(getRepositoryModel())) {
			String q = QueryBuilder.q(Lucene.labels.matches(label.name)).build();
			dynamicQueries.add(new TicketQuery(label.name, q).color(label.color));
		}

		for (QueryResult ticket : results) {
			if (!StringUtils.isEmpty(ticket.topic)) {
				String q = QueryBuilder.q(Lucene.topic.matches(ticket.topic)).build();
				dynamicQueries.add(new TicketQuery(ticket.topic, q));
			}

			if (!ArrayUtils.isEmpty(ticket.labels)) {
				for (String label : ticket.labels) {
					String q = QueryBuilder.q(Lucene.labels.matches(label)).build();
					dynamicQueries.add(new TicketQuery(label, q));
				}
			}
		}

		if (dynamicQueries.size() == 0) {
			add(new Label("dynamicQueries").setVisible(false));
		} else {
			Fragment fragment = new Fragment("dynamicQueries", "dynamicQueriesFragment", this);
			ListDataProvider<TicketQuery> dynamicQueriesDp = new ListDataProvider<TicketQuery>(new ArrayList<TicketQuery>(dynamicQueries));
			DataView<TicketQuery> dynamicQueriesList = new DataView<TicketQuery>("dynamicQuery", dynamicQueriesDp) {
				private static final long serialVersionUID = 1L;

				@Override
				public void populateItem(final Item<TicketQuery> item) {
					final TicketQuery tq = item.getModelObject();
					Component swatch = new Label("swatch", "&nbsp;").setEscapeModelStrings(false);
					if (StringUtils.isEmpty(tq.color)) {
						// calculate a color
						tq.color = StringUtils.getColor(tq.name);
					}
					String background = MessageFormat.format("background-color:{0};", tq.color);
					swatch.add(new AttributeModifier("style", background));
					item.add(swatch);
					if (activeQuery.contains(tq.query)) {
						// selected
						String q = QueryBuilder.q(activeQuery).remove(tq.query).build();
						PageParameters params = queryParameters(q, milestoneParam, statiiParam, assignedToParam, sortBy, desc, 1);
						item.add(new LinkPanel("link", "active", tq.name, TicketsPage.class, params).setRenderBodyOnly(true));
						Label checked = new Label("checked");
						WicketUtils.setCssClass(checked, "iconic-o-x");
						item.add(checked);
						item.add(new AttributeModifier("style", background));
					} else {
						// unselected
						String q = QueryBuilder.q(queryParam).toSubquery().and(tq.query).build();
						PageParameters params = queryParameters(q, milestoneParam, statiiParam, assignedToParam, sortBy, desc, 1);
						item.add(new LinkPanel("link", null, tq.name, TicketsPage.class, params).setRenderBodyOnly(true));
						item.add(new Label("checked").setVisible(false));
					}
				}
			};
			fragment.add(dynamicQueriesList);
			add(fragment);
		}

		// states
		if (ArrayUtils.isEmpty(statiiParam)) {
			add(new Label("selectedStatii", getString("gb.all")));
		} else {
			add(new Label("selectedStatii", StringUtils.flattenStrings(Arrays.asList(statiiParam), ",")));
		}
		add(new BookmarkablePageLink<Void>("openTickets", TicketsPage.class, queryParameters(queryParam, milestoneParam, TicketsUI.openStatii, assignedToParam, sortBy, desc, 1)));
		add(new BookmarkablePageLink<Void>("closedTickets", TicketsPage.class, queryParameters(queryParam, milestoneParam, TicketsUI.closedStatii, assignedToParam, sortBy, desc, 1)));
		add(new BookmarkablePageLink<Void>("allTickets", TicketsPage.class, queryParameters(queryParam, milestoneParam, null, assignedToParam, sortBy, desc, 1)));

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
				item.add(new LinkPanel("statusLink", css, status.toString(), TicketsPage.class, p).setRenderBodyOnly(true));
			}
		};
		add(statiiLinks);

		// responsible filter
		List<TicketResponsible> responsibles = new ArrayList<TicketResponsible>();
		for (RegistrantAccessPermission perm : app().repositories().getUserAccessPermissions(getRepositoryModel())) {
			if (perm.permission.atLeast(AccessPermission.PUSH)) {
				UserModel u = app().users().getUserModel(perm.registrant);
				responsibles.add(new TicketResponsible(u));
			}
		}
		Collections.sort(responsibles);
		responsibles.add(0, any);

		TicketResponsible currentResponsible = null;
		for (TicketResponsible u : responsibles) {
			if (u.username.equals(assignedToParam)) {
				currentResponsible = u;
				break;
			}
		}

		add(new Label("currentResponsible", currentResponsible == null ? "" : currentResponsible.displayname));
		ListDataProvider<TicketResponsible> responsibleDp = new ListDataProvider<TicketResponsible>(responsibles);
		DataView<TicketResponsible> responsibleMenu = new DataView<TicketResponsible>("responsible", responsibleDp) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(final Item<TicketResponsible> item) {
				final TicketResponsible u = item.getModelObject();
				PageParameters params = queryParameters(queryParam, milestoneParam, statiiParam, u.username, sortBy, desc, 1);
				item.add(new LinkPanel("responsibleLink", null, u.displayname, TicketsPage.class, params).setRenderBodyOnly(true));
			}
		};
		add(responsibleMenu);
		PageParameters resetResponsibleParams = queryParameters(queryParam, milestoneParam, statiiParam, null, sortBy, desc, 1);
		add(new BookmarkablePageLink<Void>("resetResponsible", TicketsPage.class, resetResponsibleParams));

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
		sortChoices.add(new TicketSort(getString("gb.sortHighestPriority"), Lucene.priority.name(), true));
		sortChoices.add(new TicketSort(getString("gb.sortLowestPriority"), Lucene.priority.name(), false));
		sortChoices.add(new TicketSort(getString("gb.sortHighestSeverity"), Lucene.severity.name(), true));
		sortChoices.add(new TicketSort(getString("gb.sortLowestSeverity"), Lucene.severity.name(), false));
		
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
				item.add(new LinkPanel("sortLink", null, ts.name, TicketsPage.class, params).setRenderBodyOnly(true));
			}
		};
		add(sortMenu);


		// paging links
		buildPager(queryParam, milestoneParam, statiiParam, assignedToParam, sortBy, desc, page, pageSize, results.size(), totalResults);

		add(new TicketListPanel("ticketList", results, false, false));

		// new milestone link
		RepositoryModel repositoryModel = getRepositoryModel();
		final boolean acceptingUpdates = app().tickets().isAcceptingTicketUpdates(repositoryModel)
				 && user != null && user.canAdmin(getRepositoryModel());
		if (acceptingUpdates) {
			add(new LinkPanel("newMilestone", null, getString("gb.newMilestone"),
				NewMilestonePage.class, WicketUtils.newRepositoryParameter(repositoryName)));
		} else {
			add(new Label("newMilestone").setVisible(false));
		}

		// milestones list
		List<TicketMilestone> openMilestones = new ArrayList<TicketMilestone>();
		List<TicketMilestone> closedMilestones = new ArrayList<TicketMilestone>();
		for (TicketMilestone milestone : app().tickets().getMilestones(repositoryModel)) {
			if (milestone.isOpen()) {
				openMilestones.add(milestone);
			} else {
				closedMilestones.add(milestone);
			}
		}
		Collections.sort(openMilestones, new Comparator<TicketMilestone>() {
			@Override
			public int compare(TicketMilestone o1, TicketMilestone o2) {
				return o2.due.compareTo(o1.due);
			}
		});

		Collections.sort(closedMilestones, new Comparator<TicketMilestone>() {
			@Override
			public int compare(TicketMilestone o1, TicketMilestone o2) {
				return o2.due.compareTo(o1.due);
			}
		});

		DataView<TicketMilestone> openMilestonesList = milestoneList("openMilestonesList", openMilestones, acceptingUpdates);
		add(openMilestonesList);

		DataView<TicketMilestone> closedMilestonesList = milestoneList("closedMilestonesList", closedMilestones, acceptingUpdates);
		add(closedMilestonesList);
	}

	protected DataView<TicketMilestone> milestoneList(String wicketId, List<TicketMilestone> milestones, final boolean acceptingUpdates) {
		ListDataProvider<TicketMilestone> milestonesDp = new ListDataProvider<TicketMilestone>(milestones);
		DataView<TicketMilestone> milestonesList = new DataView<TicketMilestone>(wicketId, milestonesDp) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(final Item<TicketMilestone> item) {
				Fragment entryPanel = new Fragment("entryPanel", "milestoneListFragment", this);
				item.add(entryPanel);

				final TicketMilestone tm = item.getModelObject();
				String [] states;
				if (tm.isOpen()) {
					states = TicketsUI.openStatii;
				} else {
					states = TicketsUI.closedStatii;
				}
				PageParameters params = queryParameters(null, tm.name, states, null, null, true, 1);
				entryPanel.add(new LinkPanel("milestoneName", null, tm.name, TicketsPage.class, params).setRenderBodyOnly(true));

				String css;
				String status = tm.status.name();
				switch (tm.status) {
				case Open:
					if (tm.isOverdue()) {
						css = "aui-lozenge aui-lozenge-subtle aui-lozenge-error";
						status = "overdue";
					} else {
						css = "aui-lozenge aui-lozenge-subtle";
					}
					break;
				default:
					css = "aui-lozenge";
					break;
				}
				Label stateLabel = new Label("milestoneState", status);
				WicketUtils.setCssClass(stateLabel, css);
				entryPanel.add(stateLabel);

				if (tm.due == null) {
					entryPanel.add(new Label("milestoneDue", getString("gb.notSpecified")));
				} else {
					entryPanel.add(WicketUtils.createDatestampLabel("milestoneDue", tm.due, getTimeZone(), getTimeUtils()));
				}
				if (acceptingUpdates) {
					entryPanel.add(new LinkPanel("editMilestone", null, getString("gb.edit"), EditMilestonePage.class,
						WicketUtils.newObjectParameter(repositoryName, tm.name)));
				} else {
					entryPanel.add(new Label("editMilestone").setVisible(false));
				}

				if (tm.isOpen()) {
					// re-load milestone with query results
					TicketMilestone m = app().tickets().getMilestone(getRepositoryModel(), tm.name);

					Fragment milestonePanel = new Fragment("milestonePanel", "openMilestoneFragment", this);
					Label label = new Label("progress");
					WicketUtils.setCssStyle(label, "width:" + m.getProgress() + "%;");
					milestonePanel.add(label);

					milestonePanel.add(new LinkPanel("openTickets", null,
							MessageFormat.format(getString("gb.nOpenTickets"), m.getOpenTickets()),
							TicketsPage.class,
							queryParameters(null, tm.name, TicketsUI.openStatii, null, null, true, 1)));

					milestonePanel.add(new LinkPanel("closedTickets", null,
							MessageFormat.format(getString("gb.nClosedTickets"), m.getClosedTickets()),
							TicketsPage.class,
							queryParameters(null, tm.name, TicketsUI.closedStatii, null, null, true, 1)));

					milestonePanel.add(new Label("totalTickets", MessageFormat.format(getString("gb.nTotalTickets"), m.getTotalTickets())));
					entryPanel.add(milestonePanel);
				} else {
					entryPanel.add(new Label("milestonePanel").setVisible(false));
				}
			}
		};
		return milestonesList;
	}

	protected PageParameters queryParameters(
			String query,
			String milestone,
			String[] states,
			String assignedTo,
			String sort,
			boolean descending,
			int page) {

		PageParameters params = WicketUtils.newRepositoryParameter(repositoryName);
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

	protected PageParameters newTicketParameter(QueryResult ticket) {
		return WicketUtils.newObjectParameter(repositoryName, "" + ticket.number);
	}

	@Override
	protected String getPageName() {
		return getString("gb.tickets");
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
		add(new BookmarkablePageLink<Void>("prevLink", TicketsPage.class, queryParameters(query, milestone, states, assignedTo, sort, desc, page - 1)).setEnabled(allowPrev).setVisible(showNav));
		add(new BookmarkablePageLink<Void>("nextLink", TicketsPage.class, queryParameters(query, milestone, states, assignedTo, sort, desc, page + 1)).setEnabled(allowNext).setVisible(showNav));

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
				LinkPanel link = new LinkPanel("page", null, "" + i, TicketsPage.class, queryParameters(query, milestone, states, assignedTo, sort, desc, i));
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
