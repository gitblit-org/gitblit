package com.gitblit.wicket.panels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.wicket.PageParameters;
import org.apache.wicket.extensions.markup.html.repeater.data.sort.OrderByBorder;
import org.apache.wicket.extensions.markup.html.repeater.util.SortParam;
import org.apache.wicket.extensions.markup.html.repeater.util.SortableDataProvider;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.IDataProvider;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.models.RepositoryModel;
import com.gitblit.wicket.models.UserModel;
import com.gitblit.wicket.pages.EditRepositoryPage;
import com.gitblit.wicket.pages.SummaryPage;


public class RepositoriesPanel extends BasePanel {

	private static final long serialVersionUID = 1L;
	
	public RepositoriesPanel(String wicketId, final boolean showAdmin, final Map<AccessRestrictionType, String> accessRestrictionTranslations) {
		super(wicketId);
		
		final UserModel user = GitBlitWebSession.get().getUser();
		List<RepositoryModel> models = GitBlit.self().getRepositoryModels(user);
		IDataProvider<RepositoryModel> dp;
		
		Fragment adminLinks = new Fragment("adminPanel", "adminLinks", this);
		adminLinks.add(new BookmarkablePageLink<Void>("newRepository", EditRepositoryPage.class));
		add(adminLinks.setVisible(showAdmin));
		
		if (GitBlit.self().settings().getString(Keys.web.repositoryListType, "flat").equalsIgnoreCase("grouped")) {
			Map<String, List<RepositoryModel>> groups = new HashMap<String, List<RepositoryModel>>();
			for (RepositoryModel model : models) {
				String rootPath = StringUtils.getRootPath(model.name);
				if (StringUtils.isEmpty(rootPath)) {
					rootPath = GitBlit.self().settings().getString(Keys.web.repositoryRootGroupName, " ");
				}
				if (!groups.containsKey(rootPath)) {
					groups.put(rootPath, new ArrayList<RepositoryModel>());
				}
				groups.get(rootPath).add(model);
			}
			List<String> roots = new ArrayList<String>(groups.keySet());
			Collections.sort(roots);
			List<RepositoryModel> groupedModels = new ArrayList<RepositoryModel>();
			for (String root : roots) {
				List<RepositoryModel> subModels = groups.get(root);
				groupedModels.add(new GroupRepositoryModel(root + " (" + subModels.size() + ")"));
				groupedModels.addAll(subModels);
			}
			dp = new ListDataProvider<RepositoryModel>(groupedModels);
		} else {
			dp = new DataProvider(models);
		}

		DataView<RepositoryModel> dataView = new DataView<RepositoryModel>("row", dp) {
			private static final long serialVersionUID = 1L;
			int counter = 0;

			public void populateItem(final Item<RepositoryModel> item) {
				final RepositoryModel entry = item.getModelObject();
				if (entry instanceof GroupRepositoryModel) {
					Fragment row = new Fragment("rowContent", "groupRepositoryRow", this);
					item.add(row);
					row.add(new Label("groupName", entry.name));
					WicketUtils.setCssClass(item, "group");
					return;
				}
				Fragment row = new Fragment("rowContent", "repositoryRow", this);
				item.add(row);
				if (entry.hasCommits) {
					// Existing repository
					PageParameters pp = WicketUtils.newRepositoryParameter(entry.name);
					row.add(new LinkPanel("repositoryName", "list", entry.name, SummaryPage.class, pp));
					row.add(new LinkPanel("repositoryDescription", "list", entry.description, SummaryPage.class, pp));
				} else {
					// New repository
					row.add(new Label("repositoryName", entry.name + "<span class='empty'>(empty)</span>").setEscapeModelStrings(false));
					row.add(new Label("repositoryDescription", entry.description));
				}

				if (entry.useTickets) {
					row.add(WicketUtils.newImage("ticketsIcon", "bug_16x16.png", getString("gb.tickets")));
				} else {
					row.add(WicketUtils.newBlankImage("ticketsIcon"));
				}

				if (entry.useDocs) {
					row.add(WicketUtils.newImage("docsIcon", "book_16x16.png", getString("gb.docs")));
				} else {
					row.add(WicketUtils.newBlankImage("docsIcon"));
				}

				if (entry.isFrozen) {
					row.add(WicketUtils.newImage("frozenIcon", "cold_16x16.png", getString("gb.isFrozen")));
				} else {
					row.add(WicketUtils.newClearPixel("frozenIcon").setVisible(false));
				}
				switch (entry.accessRestriction) {
				case NONE:
					row.add(WicketUtils.newBlankImage("accessRestrictionIcon"));
					break;
				case PUSH:
					row.add(WicketUtils.newImage("accessRestrictionIcon", "lock_go_16x16.png", accessRestrictionTranslations.get(entry.accessRestriction)));
					break;
				case CLONE:
					row.add(WicketUtils.newImage("accessRestrictionIcon", "lock_pull_16x16.png", accessRestrictionTranslations.get(entry.accessRestriction)));
					break;
				case VIEW:
					row.add(WicketUtils.newImage("accessRestrictionIcon", "shield_16x16.png", accessRestrictionTranslations.get(entry.accessRestriction)));
					break;
				default:
					row.add(WicketUtils.newBlankImage("accessRestrictionIcon"));
				}

				row.add(new Label("repositoryOwner", entry.owner));

				String lastChange = TimeUtils.timeAgo(entry.lastChange);
				Label lastChangeLabel = new Label("repositoryLastChange", lastChange);
				row.add(lastChangeLabel);
				WicketUtils.setCssClass(lastChangeLabel, TimeUtils.timeAgoCss(entry.lastChange));

				boolean showOwner = user != null && user.getUsername().equalsIgnoreCase(entry.owner);				
				if (showAdmin) {
					Fragment repositoryLinks = new Fragment("repositoryLinks", "repositoryAdminLinks", this);
					repositoryLinks.add(new BookmarkablePageLink<Void>("editRepository", EditRepositoryPage.class, WicketUtils.newRepositoryParameter(entry.name)));
					repositoryLinks.add(new BookmarkablePageLink<Void>("renameRepository", EditRepositoryPage.class, WicketUtils.newRepositoryParameter(entry.name)).setEnabled(false));
					repositoryLinks.add(new BookmarkablePageLink<Void>("deleteRepository", EditRepositoryPage.class, WicketUtils.newRepositoryParameter(entry.name)).setEnabled(false));
					row.add(repositoryLinks);
				} else if (showOwner) {
					Fragment repositoryLinks = new Fragment("repositoryLinks", "repositoryOwnerLinks", this);
					repositoryLinks.add(new BookmarkablePageLink<Void>("editRepository", EditRepositoryPage.class, WicketUtils.newRepositoryParameter(entry.name)));
					row.add(repositoryLinks);
				} else {
					row.add(new Label("repositoryLinks"));
				}
				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(dataView);

		if (dp instanceof SortableDataProvider<?>) {
			// add sortable header
			SortableDataProvider<?> sdp = (SortableDataProvider<?>) dp;
			Fragment fragment = new Fragment("headerContent", "flatRepositoryHeader", this);
			fragment.add(newSort("orderByRepository", SortBy.repository, sdp, dataView));
			fragment.add(newSort("orderByDescription", SortBy.description, sdp, dataView));
			fragment.add(newSort("orderByOwner", SortBy.owner, sdp, dataView));
			fragment.add(newSort("orderByDate", SortBy.date, sdp, dataView));
			add(fragment);
		} else {
			// not sortable
			Fragment fragment = new Fragment("headerContent", "groupRepositoryHeader", this);
			add(fragment);
		}
	}
	
	private class GroupRepositoryModel extends RepositoryModel {

		private static final long serialVersionUID = 1L;

		GroupRepositoryModel(String name) {
			super(name, "", "", new Date(0));
		}
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
