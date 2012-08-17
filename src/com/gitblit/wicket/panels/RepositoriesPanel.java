/*
 * Copyright 2011 gitblit.com.
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
package com.gitblit.wicket.panels;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.extensions.markup.html.repeater.data.sort.OrderByBorder;
import org.apache.wicket.extensions.markup.html.repeater.util.SortParam;
import org.apache.wicket.extensions.markup.html.repeater.util.SortableDataProvider;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.link.Link;
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
import com.gitblit.SyndicationServlet;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.BasePage;
import com.gitblit.wicket.pages.EditRepositoryPage;
import com.gitblit.wicket.pages.EmptyRepositoryPage;
import com.gitblit.wicket.pages.RepositoriesPage;
import com.gitblit.wicket.pages.SummaryPage;

public class RepositoriesPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	public RepositoriesPanel(String wicketId, final boolean showAdmin,
			List<RepositoryModel> models, boolean enableLinks,
			final Map<AccessRestrictionType, String> accessRestrictionTranslations) {
		super(wicketId);

		final boolean linksActive = enableLinks;
		final boolean showSize = GitBlit.getBoolean(Keys.web.showRepositorySizes, true);

		final UserModel user = GitBlitWebSession.get().getUser();

		final IDataProvider<RepositoryModel> dp;

		Fragment adminLinks = new Fragment("adminPanel", "adminLinks", this);
		adminLinks.add(new Link<Void>("clearCache") {

			private static final long serialVersionUID = 1L;

			@Override
			public void onClick() {
				GitBlit.self().resetRepositoryListCache();
				setResponsePage(RepositoriesPage.class);
			}
		}.setVisible(GitBlit.getBoolean(Keys.git.cacheRepositoryList, true)));
		adminLinks.add(new BookmarkablePageLink<Void>("newRepository", EditRepositoryPage.class));
		add(adminLinks.setVisible(showAdmin));

		if (GitBlit.getString(Keys.web.repositoryListType, "flat").equalsIgnoreCase("grouped")) {
			List<RepositoryModel> rootRepositories = new ArrayList<RepositoryModel>();
			Map<String, List<RepositoryModel>> groups = new HashMap<String, List<RepositoryModel>>();
			for (RepositoryModel model : models) {
				String rootPath = StringUtils.getRootPath(model.name);
				if (StringUtils.isEmpty(rootPath)) {
					// root repository
					rootRepositories.add(model);
				} else {
					// non-root, grouped repository
					if (!groups.containsKey(rootPath)) {
						groups.put(rootPath, new ArrayList<RepositoryModel>());
					}
					groups.get(rootPath).add(model);
				}
			}
			List<String> roots = new ArrayList<String>(groups.keySet());
			Collections.sort(roots);

			if (rootRepositories.size() > 0) {
				// inject the root repositories at the top of the page
				String rootPath = GitBlit.getString(Keys.web.repositoryRootGroupName, " ");
				roots.add(0, rootPath);
				groups.put(rootPath, rootRepositories);
			}
			List<RepositoryModel> groupedModels = new ArrayList<RepositoryModel>();
			for (String root : roots) {
				List<RepositoryModel> subModels = groups.get(root);
				groupedModels.add(new GroupRepositoryModel(root, subModels.size()));
				Collections.sort(subModels);
				groupedModels.addAll(subModels);
			}
			dp = new RepositoriesProvider(groupedModels);
		} else {
			dp = new SortableRepositoriesProvider(models);
		}

		final String baseUrl = WicketUtils.getGitblitURL(getRequest());
		final boolean showSwatch = GitBlit.getBoolean(Keys.web.repositoryListSwatches, true);

		DataView<RepositoryModel> dataView = new DataView<RepositoryModel>("row", dp) {
			private static final long serialVersionUID = 1L;
			int counter;
			String currGroupName;

			@Override
			protected void onBeforeRender() {
				super.onBeforeRender();
				counter = 0;
			}

			public void populateItem(final Item<RepositoryModel> item) {
				final RepositoryModel entry = item.getModelObject();
				if (entry instanceof GroupRepositoryModel) {
					currGroupName = entry.name;
					Fragment row = new Fragment("rowContent", "groupRepositoryRow", this);
					item.add(row);
					row.add(new Label("groupName", entry.toString()));
					WicketUtils.setCssClass(item, "group");
					// reset counter so that first row is light background
					counter = 0;
					return;
				}
				Fragment row = new Fragment("rowContent", "repositoryRow", this);
				item.add(row);

				// try to strip group name for less cluttered list
				String repoName = entry.toString();
				if (!StringUtils.isEmpty(currGroupName) && (repoName.indexOf('/') > -1)) {
					repoName = repoName.substring(currGroupName.length() + 1);
				}
								
				// repository swatch
				Component swatch;
				if (entry.isBare){
					swatch = new Label("repositorySwatch", "&nbsp;").setEscapeModelStrings(false);
				} else {
					swatch = new Label("repositorySwatch", "!");
					WicketUtils.setHtmlTooltip(swatch, getString("gb.workingCopyWarning"));
				}
				WicketUtils.setCssBackground(swatch, entry.toString());
				row.add(swatch);
				swatch.setVisible(showSwatch);

				if (linksActive) {
					Class<? extends BasePage> linkPage;
					if (entry.hasCommits) {
						// repository has content
						linkPage = SummaryPage.class;
					} else {
						// new/empty repository OR proposed repository
						linkPage = EmptyRepositoryPage.class;
					}

					PageParameters pp = WicketUtils.newRepositoryParameter(entry.name);
					row.add(new LinkPanel("repositoryName", "list", repoName, linkPage, pp));
					row.add(new LinkPanel("repositoryDescription", "list", entry.description,
							linkPage, pp));
				} else {
					// no links like on a federation page
					row.add(new Label("repositoryName", repoName));
					row.add(new Label("repositoryDescription", entry.description));
				}
				if (entry.hasCommits) {
					// Existing repository
					row.add(new Label("repositorySize", entry.size).setVisible(showSize));
				} else {
					// New repository
					row.add(new Label("repositorySize", "<span class='empty'>(" + getString("gb.empty") + ")</span>")
							.setEscapeModelStrings(false));
				}

				if (entry.useTickets) {
					row.add(WicketUtils.newImage("ticketsIcon", "bug_16x16.png",
							getString("gb.tickets")));
				} else {
					row.add(WicketUtils.newBlankImage("ticketsIcon"));
				}

				if (entry.useDocs) {
					row.add(WicketUtils
							.newImage("docsIcon", "book_16x16.png", getString("gb.docs")));
				} else {
					row.add(WicketUtils.newBlankImage("docsIcon"));
				}

				if (entry.isFrozen) {
					row.add(WicketUtils.newImage("frozenIcon", "cold_16x16.png",
							getString("gb.isFrozen")));
				} else {
					row.add(WicketUtils.newClearPixel("frozenIcon").setVisible(false));
				}

				if (entry.isFederated) {
					row.add(WicketUtils.newImage("federatedIcon", "federated_16x16.png",
							getString("gb.isFederated")));
				} else {
					row.add(WicketUtils.newClearPixel("federatedIcon").setVisible(false));
				}
				switch (entry.accessRestriction) {
				case NONE:
					row.add(WicketUtils.newBlankImage("accessRestrictionIcon"));
					break;
				case PUSH:
					row.add(WicketUtils.newImage("accessRestrictionIcon", "lock_go_16x16.png",
							accessRestrictionTranslations.get(entry.accessRestriction)));
					break;
				case CLONE:
					row.add(WicketUtils.newImage("accessRestrictionIcon", "lock_pull_16x16.png",
							accessRestrictionTranslations.get(entry.accessRestriction)));
					break;
				case VIEW:
					row.add(WicketUtils.newImage("accessRestrictionIcon", "shield_16x16.png",
							accessRestrictionTranslations.get(entry.accessRestriction)));
					break;
				default:
					row.add(WicketUtils.newBlankImage("accessRestrictionIcon"));
				}

				row.add(new Label("repositoryOwner", entry.owner));

				String lastChange;
				if (entry.lastChange.getTime() == 0) {
					lastChange = "--";
				} else {
					lastChange = getTimeUtils().timeAgo(entry.lastChange);
				}
				Label lastChangeLabel = new Label("repositoryLastChange", lastChange);
				row.add(lastChangeLabel);
				WicketUtils.setCssClass(lastChangeLabel, getTimeUtils().timeAgoCss(entry.lastChange));

				boolean showOwner = user != null && user.username.equalsIgnoreCase(entry.owner);
				if (showAdmin) {
					Fragment repositoryLinks = new Fragment("repositoryLinks",
							"repositoryAdminLinks", this);
					repositoryLinks.add(new BookmarkablePageLink<Void>("editRepository",
							EditRepositoryPage.class, WicketUtils
									.newRepositoryParameter(entry.name)));
					Link<Void> deleteLink = new Link<Void>("deleteRepository") {

						private static final long serialVersionUID = 1L;

						@Override
						public void onClick() {
							if (GitBlit.self().deleteRepositoryModel(entry)) {
								info(MessageFormat.format(getString("gb.repositoryDeleted"), entry));
								if (dp instanceof SortableRepositoriesProvider) {
									((SortableRepositoriesProvider) dp).remove(entry);
								} else {
									((RepositoriesProvider) dp).remove(entry);
								}
							} else {
								error(MessageFormat.format(getString("gb.repositoryDeleteFailed"), entry));
							}
						}
					};
					deleteLink.add(new JavascriptEventConfirmation("onclick", MessageFormat.format(
							getString("gb.deleteRepository"), entry)));
					repositoryLinks.add(deleteLink);
					row.add(repositoryLinks);
				} else if (showOwner) {
					Fragment repositoryLinks = new Fragment("repositoryLinks",
							"repositoryOwnerLinks", this);
					repositoryLinks.add(new BookmarkablePageLink<Void>("editRepository",
							EditRepositoryPage.class, WicketUtils
									.newRepositoryParameter(entry.name)));
					row.add(repositoryLinks);
				} else {
					row.add(new Label("repositoryLinks"));
				}
				row.add(new ExternalLink("syndication", SyndicationServlet.asLink(baseUrl,
						entry.name, null, 0)).setVisible(linksActive));
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

	private static class GroupRepositoryModel extends RepositoryModel {

		private static final long serialVersionUID = 1L;

		int count;

		GroupRepositoryModel(String name, int count) {
			super(name, "", "", new Date(0));
			this.count = count;
		}

		@Override
		public String toString() {
			return name + " (" + count + ")";
		}
	}

	protected enum SortBy {
		repository, description, owner, date;
	}

	protected OrderByBorder newSort(String wicketId, SortBy field, SortableDataProvider<?> dp,
			final DataView<?> dataView) {
		return new OrderByBorder(wicketId, field.name(), dp) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void onSortChanged() {
				dataView.setCurrentPage(0);
			}
		};
	}

	private static class RepositoriesProvider extends ListDataProvider<RepositoryModel> {

		private static final long serialVersionUID = 1L;

		public RepositoriesProvider(List<RepositoryModel> list) {
			super(list);
		}

		@Override
		public List<RepositoryModel> getData() {
			return super.getData();
		}

		public void remove(RepositoryModel model) {
			int index = getData().indexOf(model);
			RepositoryModel groupModel = null;
			if (index == (getData().size() - 1)) {
				// last element
				if (index > 0) {
					// previous element is group header, then this is last
					// repository in group. remove group too.
					if (getData().get(index - 1) instanceof GroupRepositoryModel) {
						groupModel = getData().get(index - 1);
					}
				}
			} else if (index < (getData().size() - 1)) {
				// not last element. check next element for group match.
				if (getData().get(index - 1) instanceof GroupRepositoryModel
						&& getData().get(index + 1) instanceof GroupRepositoryModel) {
					// repository is sandwiched by group headers so this
					// repository is the only element in the group. remove
					// group.
					groupModel = getData().get(index - 1);
				}
			}

			if (groupModel == null) {
				// Find the group and decrement the count
				for (int i = index; i >= 0; i--) {
					if (getData().get(i) instanceof GroupRepositoryModel) {
						((GroupRepositoryModel) getData().get(i)).count--;
						break;
					}
				}
			} else {
				// Remove the group header
				getData().remove(groupModel);
			}

			getData().remove(model);
		}
	}

	private static class SortableRepositoriesProvider extends SortableDataProvider<RepositoryModel> {

		private static final long serialVersionUID = 1L;

		private List<RepositoryModel> list;

		protected SortableRepositoriesProvider(List<RepositoryModel> list) {
			this.list = list;
			setSort(SortBy.date.name(), false);
		}

		public void remove(RepositoryModel model) {
			list.remove(model);
		}

		@Override
		public int size() {
			if (list == null) {
				return 0;
			}
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
						if (asc) {
							return o1.lastChange.compareTo(o2.lastChange);
						}
						return o2.lastChange.compareTo(o1.lastChange);
					}
				});
			} else if (prop.equals(SortBy.repository.name())) {
				Collections.sort(list, new Comparator<RepositoryModel>() {
					@Override
					public int compare(RepositoryModel o1, RepositoryModel o2) {
						if (asc) {
							return o1.name.compareTo(o2.name);
						}
						return o2.name.compareTo(o1.name);
					}
				});
			} else if (prop.equals(SortBy.owner.name())) {
				Collections.sort(list, new Comparator<RepositoryModel>() {
					@Override
					public int compare(RepositoryModel o1, RepositoryModel o2) {
						if (asc) {
							return o1.owner.compareTo(o2.owner);
						}
						return o2.owner.compareTo(o1.owner);
					}
				});
			} else if (prop.equals(SortBy.description.name())) {
				Collections.sort(list, new Comparator<RepositoryModel>() {
					@Override
					public int compare(RepositoryModel o1, RepositoryModel o2) {
						if (asc) {
							return o1.description.compareTo(o2.description);
						}
						return o2.description.compareTo(o1.description);
					}
				});
			}
			return list.subList(first, first + count).iterator();
		}
	}
}
