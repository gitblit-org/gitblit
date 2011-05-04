package com.gitblit.wicket.pages;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.extensions.markup.html.repeater.data.sort.OrderByBorder;
import org.apache.wicket.extensions.markup.html.repeater.util.SortParam;
import org.apache.wicket.extensions.markup.html.repeater.util.SortableDataProvider;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.resource.ContextRelativeResource;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.utils.MarkdownUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;
import com.gitblit.wicket.BasePage;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.models.RepositoryModel;

public class RepositoriesPage extends BasePage {

	public RepositoriesPage() {
		super();
		setupPage("", "");

		final boolean showAdmin;
		if (GitBlit.self().settings().getBoolean(Keys.web.authenticateAdminPages, true)) {
			boolean allowAdmin = GitBlit.self().settings().getBoolean(Keys.web.allowAdministration, false);
			showAdmin = allowAdmin && GitBlitWebSession.get().canAdmin();
		} else {
			showAdmin = GitBlit.self().settings().getBoolean(Keys.web.allowAdministration, false);
		}

		Fragment adminLinks = new Fragment("adminPanel", "adminLinks", this);
		adminLinks.add(new BookmarkablePageLink<Void>("newRepository", EditRepositoryPage.class));
		adminLinks.add(new BookmarkablePageLink<Void>("newUser", RepositoriesPage.class));
		add(adminLinks.setVisible(showAdmin));

		// display an error message cached from a redirect
		String cachedMessage = GitBlitWebSession.get().clearErrorMessage();
		if (!StringUtils.isEmpty(cachedMessage)) {
			error(cachedMessage);
			System.out.println("displayed message");
		}
		
		// Load the markdown welcome message
		String messageSource = GitBlit.self().settings().getString(Keys.web.repositoriesMessage, "gitblit");
		String message = "";
		if (messageSource.equalsIgnoreCase("gitblit")) {
			// Read default welcome message
			try {
				ContextRelativeResource res = WicketUtils.getResource("welcome.mkd");
				InputStream is = res.getResourceStream().getInputStream();
				InputStreamReader reader = new InputStreamReader(is);
				message = MarkdownUtils.transformMarkdown(reader);
			} catch (Throwable t) {
				message = "Failed to read default welcome message!";
				error(message, t, false);
			}
		} else {
			// Read user-supplied welcome message
			if (!StringUtils.isEmpty(messageSource)) {
				File file = new File(messageSource);
				if (file.exists()) {
					try {
						FileReader reader = new FileReader(file);
						message = MarkdownUtils.transformMarkdown(reader);
					} catch (Throwable t) {
						message = "Failed to read " + file;
						error(message, t, false);
					}
				} else {
					message = messageSource + " is not a valid file.";
				}
			}
		}
		Component repositoriesMessage = new Label("repositoriesMessage", message).setEscapeModelStrings(false);
		if (!showAdmin) {
			WicketUtils.setCssStyle(repositoriesMessage, "padding-top:10px");
		}
		add(repositoriesMessage);

		List<RepositoryModel> rows = GitBlit.self().getRepositoryModels();
		DataProvider dp = new DataProvider(rows);
		DataView<RepositoryModel> dataView = new DataView<RepositoryModel>("repository", dp) {
			private static final long serialVersionUID = 1L;
			int counter = 0;

			public void populateItem(final Item<RepositoryModel> item) {
				final RepositoryModel entry = item.getModelObject();
				if (entry.hasCommits) {
					// Existing repository
					PageParameters pp = WicketUtils.newRepositoryParameter(entry.name);
					item.add(new LinkPanel("repositoryName", "list", entry.name, SummaryPage.class, pp));
					item.add(new LinkPanel("repositoryDescription", "list", entry.description, SummaryPage.class, pp));
				} else {
					// New repository
					item.add(new Label("repositoryName", entry.name + "<span class='empty'>(empty)</span>").setEscapeModelStrings(false));
					item.add(new Label("repositoryDescription", entry.description));					
				}
				
				if (entry.useTickets) {
					item.add(WicketUtils.newImage("ticketsIcon", "bug_16x16.png", getString("gb.tickets")));
				} else {
					item.add(WicketUtils.newClearPixel("ticketsIcon"));
				}
				
				if (entry.useDocs) {
					item.add(WicketUtils.newImage("docsIcon", "book_16x16.png", getString("gb.docs")));
				} else {
					item.add(WicketUtils.newClearPixel("docsIcon"));
				}
				
				if (entry.useRestrictedAccess) {
					item.add(WicketUtils.newImage("restrictedAccessIcon", "lock_16x16.png", getString("gb.restrictedAccess")));
				} else {
					item.add(WicketUtils.newClearPixel("restrictedAccessIcon"));
				}
				
				item.add(new Label("repositoryOwner", entry.owner));

				String lastChange = TimeUtils.timeAgo(entry.lastChange);
				Label lastChangeLabel = new Label("repositoryLastChange", lastChange);
				item.add(lastChangeLabel);
				WicketUtils.setCssClass(lastChangeLabel, TimeUtils.timeAgoCss(entry.lastChange));

				if (showAdmin) {
					Fragment repositoryLinks = new Fragment("repositoryLinks", "repositoryAdminLinks", this);
					repositoryLinks.add(new BookmarkablePageLink<Void>("editRepository", EditRepositoryPage.class, WicketUtils.newRepositoryParameter(entry.name)));
					repositoryLinks.add(new BookmarkablePageLink<Void>("renameRepository", EditRepositoryPage.class, WicketUtils.newRepositoryParameter(entry.name)).setEnabled(false));
					repositoryLinks.add(new BookmarkablePageLink<Void>("deleteRepository", EditRepositoryPage.class, WicketUtils.newRepositoryParameter(entry.name)).setEnabled(false));
					item.add(repositoryLinks);
				} else {
					item.add(new Label("repositoryLinks"));
				}
				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(dataView);

		add(newSort("orderByRepository", SortBy.repository, dp, dataView));
		add(newSort("orderByDescription", SortBy.description, dp, dataView));
		add(newSort("orderByOwner", SortBy.owner, dp, dataView));
		add(newSort("orderByDate", SortBy.date, dp, dataView));
	}

	protected enum SortBy {
		repository, description, owner, date;
	}

	protected OrderByBorder newSort(String wicketId, SortBy field, SortableDataProvider<?> dp, final DataView<?> dataView) {
		return new OrderByBorder(wicketId, field.name(), dp) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void onSortChanged() {
				dataView.setCurrentPage(0);
			}
		};
	}

	private class DataProvider extends SortableDataProvider<RepositoryModel> {
		private static final long serialVersionUID = 1L;
		private List<RepositoryModel> list = null;

		protected DataProvider(List<RepositoryModel> list) {
			this.list = list;
			setSort(SortBy.date.name(), false);
		}

		@Override
		public int size() {
			if (list == null)
				return 0;
			return list.size();
		}

		@Override
		public IModel<RepositoryModel> model(RepositoryModel header) {
			return new Model<RepositoryModel>(header);
		}

		@Override
		public Iterator<RepositoryModel> iterator(int first, int count) {
			SortParam sp = getSort();
			String prop = sp.getProperty();
			final boolean asc = sp.isAscending();

			if (prop == null || prop.equals(SortBy.date.name())) {
				Collections.sort(list, new Comparator<RepositoryModel>() {
					@Override
					public int compare(RepositoryModel o1, RepositoryModel o2) {
						if (asc)
							return o1.lastChange.compareTo(o2.lastChange);
						return o2.lastChange.compareTo(o1.lastChange);
					}
				});
			} else if (prop.equals(SortBy.repository.name())) {
				Collections.sort(list, new Comparator<RepositoryModel>() {
					@Override
					public int compare(RepositoryModel o1, RepositoryModel o2) {
						if (asc)
							return o1.name.compareTo(o2.name);
						return o2.name.compareTo(o1.name);
					}
				});
			} else if (prop.equals(SortBy.owner.name())) {
				Collections.sort(list, new Comparator<RepositoryModel>() {
					@Override
					public int compare(RepositoryModel o1, RepositoryModel o2) {
						if (asc)
							return o1.owner.compareTo(o2.owner);
						return o2.owner.compareTo(o1.owner);
					}
				});
			} else if (prop.equals(SortBy.description.name())) {
				Collections.sort(list, new Comparator<RepositoryModel>() {
					@Override
					public int compare(RepositoryModel o1, RepositoryModel o2) {
						if (asc)
							return o1.description.compareTo(o2.description);
						return o2.description.compareTo(o1.description);
					}
				});
			}
			return list.subList(first, first + count).iterator();
		}
	}
}
