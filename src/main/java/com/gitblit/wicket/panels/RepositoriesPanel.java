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
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.IDataProvider;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Keys;
import com.gitblit.models.ProjectModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TreeNodeModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.ModelUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.BasePage;
import com.gitblit.wicket.pages.ProjectPage;
import com.gitblit.wicket.pages.RepositoriesPage;
import com.gitblit.wicket.pages.SummaryPage;
import com.gitblit.wicket.pages.UserPage;

public class RepositoriesPanel extends BasePanel {

    private static final long serialVersionUID = 1L;

    private enum CollapsibleRepositorySetting {
        DISABLED,

        EXPANDED,

        COLLAPSED;

        public static CollapsibleRepositorySetting get(String name) {
            CollapsibleRepositorySetting returnVal = CollapsibleRepositorySetting.DISABLED;
            for (CollapsibleRepositorySetting setting : values()) {
                if (setting.name().equalsIgnoreCase(name)) {
                    returnVal = setting;
                    break;
                }
            }
            return returnVal;
        }
    }

    public RepositoriesPanel(String wicketId, final boolean showAdmin, final boolean showManagement, List<RepositoryModel> models, boolean enableLinks,
            final Map<AccessRestrictionType, String> accessRestrictionTranslations) {
        super(wicketId);

        final boolean linksActive = enableLinks;
        final boolean showSize = app().settings().getBoolean(Keys.web.showRepositorySizes, true);
        final String collapsibleRespositorySetting = app().settings().getString(Keys.web.collapsibleRepositoryGroups, null);
        final CollapsibleRepositorySetting collapsibleRepoGroups = CollapsibleRepositorySetting.get(collapsibleRespositorySetting);

        final UserModel user = GitBlitWebSession.get().getUser();

        IDataProvider<RepositoryModel> dp = null;

        Fragment managementLinks;
        if (showAdmin) {
            // user is admin
            managementLinks = new Fragment("managementPanel", "adminLinks", this);
            managementLinks.add(new Link<Void>("clearCache") {

                private static final long serialVersionUID = 1L;

                @Override
                public void onClick() {
                    app().repositories().resetRepositoryListCache();
                    setResponsePage(RepositoriesPage.class);
                }
            }.setVisible(app().settings().getBoolean(Keys.git.cacheRepositoryList, true)));
            managementLinks.add(new BookmarkablePageLink<Void>("newRepository", app().getNewRepositoryPage()));
            add(managementLinks);
        } else if (showManagement && user != null && user.canCreate()) {
            // user can create personal repositories
            managementLinks = new Fragment("managementPanel", "personalLinks", this);
            managementLinks.add(new BookmarkablePageLink<Void>("newRepository", app().getNewRepositoryPage()));
            add(managementLinks);
        } else {
            // user has no management permissions
            add(new Label("managementPanel").setVisible(false));
        }

        if (app().settings().getString(Keys.web.repositoryListType, "flat").equalsIgnoreCase("tree")) {
            TreeNodeModel tree = new TreeNodeModel();
            for (RepositoryModel model : models) {
                String rootPath = StringUtils.getRootPath(model.name);
                if (StringUtils.isEmpty(rootPath)) {
                    tree.add(model);
                } else {
                    // create folder structure
                    tree.add(rootPath, model);
                }
            }

            WebMarkupContainer container = new WebMarkupContainer("row");
            add(container);
            container.add(new NestedRepositoryTreePanel("rowContent", Model.of(tree), accessRestrictionTranslations, enableLinks));

            Fragment fragment = new Fragment("headerContent", "groupRepositoryHeader", this);
            Fragment allCollapsible = new Fragment("allCollapsible", "tableAllCollapsible", this);
            fragment.add(allCollapsible);
            add(fragment);

        } else if (app().settings().getString(Keys.web.repositoryListType, "flat").equalsIgnoreCase("grouped")) {
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
                roots.add(0, "");
                groups.put("", rootRepositories);
            }

            List<RepositoryModel> groupedModels = new ArrayList<RepositoryModel>();
            for (String root : roots) {
                List<RepositoryModel> subModels = groups.get(root);
                ProjectModel project = app().projects().getProjectModel(root);
                GroupRepositoryModel group = new GroupRepositoryModel(project == null ? root : project.name, subModels.size());
                if (project != null) {
                    group.title = project.title;
                    group.description = project.description;
                }
                groupedModels.add(group);
                Collections.sort(subModels);
                groupedModels.addAll(subModels);
            }
            dp = new ListDataProvider<RepositoryModel>(groupedModels);
        } else {
            dp = new SortableRepositoriesProvider(models);
        }

        if (dp != null) {
            final boolean showSwatch = app().settings().getBoolean(Keys.web.repositoryListSwatches, true);

            DataView<RepositoryModel> dataView = new DataView<RepositoryModel>("row", dp) {
                private static final long serialVersionUID = 1L;
                int counter;
                String currGroupName;

                @Override
                protected void onBeforeRender() {
                    super.onBeforeRender();
                    counter = 0;
                }

                @Override
                public void populateItem(final Item<RepositoryModel> item) {
                    final RepositoryModel entry = item.getModelObject();
                    if (entry instanceof GroupRepositoryModel) {
                        GroupRepositoryModel groupRow = (GroupRepositoryModel) entry;
                        currGroupName = entry.name;
                        Fragment row = new Fragment("rowContent", "groupRepositoryRow", this);
                        if (collapsibleRepoGroups == CollapsibleRepositorySetting.EXPANDED) {
                            Fragment groupCollapsible = new Fragment("groupCollapsible", "tableGroupMinusCollapsible", this);
                            row.add(groupCollapsible);
                        } else if (collapsibleRepoGroups == CollapsibleRepositorySetting.COLLAPSED) {
                            Fragment groupCollapsible = new Fragment("groupCollapsible", "tableGroupPlusCollapsible", this);
                            row.add(groupCollapsible);
                        } else {
                            Fragment groupCollapsible = new Fragment("groupCollapsible", "emptyFragment", this);
                            row.add(groupCollapsible);
                        }
                        item.add(row);

                        String name = groupRow.name;
                        if (name.startsWith(ModelUtils.getUserRepoPrefix())) {
                            // user page
                            String username = ModelUtils.getUserNameFromRepoPath(name);
                            UserModel user = app().users().getUserModel(username);
                            row.add(new LinkPanel("groupName", null, (user == null ? username : user.getDisplayName()) + " (" + groupRow.count + ")", UserPage.class,
                                    WicketUtils.newUsernameParameter(username)));
                            row.add(new Label("groupDescription", getString("gb.personalRepositories")));
                        } else {
                            // project page
                            row.add(new LinkPanel("groupName", null, groupRow.toString(), ProjectPage.class, WicketUtils.newProjectParameter(entry.name)));
                            row.add(new Label("groupDescription", entry.description == null ? "" : entry.description));
                        }
                        WicketUtils.setCssClass(item, "group collapsible");
                        // reset counter so that first row is light background
                        counter = 0;
                        return;
                    }
                    Fragment row = new Fragment("rowContent", "repositoryRow", this);
                    item.add(row);

                    // show colored repository type icon
                    Fragment iconFragment;
                    if (entry.isMirror) {
                        iconFragment = new Fragment("repoIcon", "mirrorIconFragment", this);
                    } else if (entry.isFork()) {
                        iconFragment = new Fragment("repoIcon", "forkIconFragment", this);
                    } else if (entry.isBare) {
                        iconFragment = new Fragment("repoIcon", "repoIconFragment", this);
                    } else {
                        iconFragment = new Fragment("repoIcon", "cloneIconFragment", this);
                    }
                    if (showSwatch) {
                        WicketUtils.setCssStyle(iconFragment, "color:" + StringUtils.getColor(entry.toString()));
                    }
                    row.add(iconFragment);

                    // try to strip group name for less cluttered list
                    String repoName = entry.toString();
                    if (!StringUtils.isEmpty(currGroupName) && (repoName.indexOf('/') > -1)) {
                        repoName = repoName.substring(currGroupName.length() + 1);
                    }

                    if (linksActive) {
                        Class<? extends BasePage> linkPage = SummaryPage.class;
                        PageParameters pp = WicketUtils.newRepositoryParameter(entry.name);
                        row.add(new LinkPanel("repositoryName", "list", repoName, linkPage, pp));
                        row.add(new LinkPanel("repositoryDescription", "list", entry.description, linkPage, pp));
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
                        row.add(new Label("repositorySize", "<span class='empty'>(" + getString("gb.empty") + ")</span>").setEscapeModelStrings(false));
                    }

                    if (entry.isSparkleshared()) {
                        row.add(WicketUtils.newImage("sparkleshareIcon", "star_16x16.png", getString("gb.isSparkleshared")));
                    } else {
                        row.add(WicketUtils.newClearPixel("sparkleshareIcon").setVisible(false));
                    }

                    if (!entry.isMirror && entry.isFrozen) {
                        row.add(WicketUtils.newImage("frozenIcon", "cold_16x16.png", getString("gb.isFrozen")));
                    } else {
                        row.add(WicketUtils.newClearPixel("frozenIcon").setVisible(false));
                    }

                    if (entry.isFederated) {
                        row.add(WicketUtils.newImage("federatedIcon", "federated_16x16.png", getString("gb.isFederated")));
                    } else {
                        row.add(WicketUtils.newClearPixel("federatedIcon").setVisible(false));
                    }

                    if (entry.isMirror) {
                        row.add(WicketUtils.newImage("accessRestrictionIcon", "mirror_16x16.png", getString("gb.isMirror")));
                    } else {
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
                    }

                    String owner = "";
                    if (!ArrayUtils.isEmpty(entry.owners)) {
                        // display first owner
                        for (String username : entry.owners) {
                            UserModel ownerModel = app().users().getUserModel(username);
                            if (ownerModel != null) {
                                owner = ownerModel.getDisplayName();
                                break;
                            }
                        }
                        if (entry.owners.size() > 1) {
                            owner += ", ...";
                        }
                    }
                    Label ownerLabel = new Label("repositoryOwner", owner);
                    WicketUtils.setHtmlTooltip(ownerLabel, ArrayUtils.toString(entry.owners));
                    row.add(ownerLabel);

                    String lastChange;
                    if (entry.lastChange.getTime() == 0) {
                        lastChange = "--";
                    } else {
                        lastChange = getTimeUtils().timeAgo(entry.lastChange);
                    }
                    Label lastChangeLabel = new Label("repositoryLastChange", lastChange);
                    row.add(lastChangeLabel);
                    WicketUtils.setCssClass(lastChangeLabel, getTimeUtils().timeAgoCss(entry.lastChange));
                    if (!StringUtils.isEmpty(entry.lastChangeAuthor)) {
                        WicketUtils.setHtmlTooltip(lastChangeLabel, getString("gb.author") + ": " + entry.lastChangeAuthor);
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
                if (collapsibleRepoGroups == CollapsibleRepositorySetting.EXPANDED || collapsibleRepoGroups == CollapsibleRepositorySetting.COLLAPSED) {
                    Fragment allCollapsible = new Fragment("allCollapsible", "tableAllCollapsible", this);
                    fragment.add(allCollapsible);
                } else {
                    Fragment allCollapsible = new Fragment("allCollapsible", "emptyFragment", this);
                    fragment.add(allCollapsible);
                }
                add(fragment);
            }
        }
    }

    private static class GroupRepositoryModel extends RepositoryModel {

        private static final long serialVersionUID = 1L;

        int count;
        String title;

        GroupRepositoryModel(String name, int count) {
            super(name, "", "", new Date(0));
            this.count = count;
        }

        @Override
        public String toString() {
            return (StringUtils.isEmpty(title) ? name : title) + " (" + count + ")";
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

    private static class SortableRepositoriesProvider extends SortableDataProvider<RepositoryModel> {

        private static final long serialVersionUID = 1L;

        private List<RepositoryModel> list;

        protected SortableRepositoriesProvider(List<RepositoryModel> list) {
            this.list = list;
            setSort(SortBy.date.name(), false);
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
                        String own1 = ArrayUtils.toString(o1.owners);
                        String own2 = ArrayUtils.toString(o2.owners);
                        if (asc) {
                            return own1.compareTo(own2);
                        }
                        return own2.compareTo(own1);
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
