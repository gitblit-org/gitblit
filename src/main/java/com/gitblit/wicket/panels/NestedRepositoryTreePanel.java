package com.gitblit.wicket.panels;

import java.util.Map;

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Keys;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TreeNodeModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.ModelUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.BasePage;
import com.gitblit.wicket.pages.ProjectPage;
import com.gitblit.wicket.pages.SummaryPage;
import com.gitblit.wicket.pages.UserPage;

public class NestedRepositoryTreePanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	public NestedRepositoryTreePanel(final String wicketId, final IModel<TreeNodeModel> model, final Map<AccessRestrictionType, String> accessRestrictionTranslations, final boolean linksActive) {
		super(wicketId);

		final boolean showSize = app().settings().getBoolean(Keys.web.showRepositorySizes, true);
		final boolean showSwatch = app().settings().getBoolean(Keys.web.repositoryListSwatches, true);

		final TreeNodeModel node = model.getObject();
		Fragment nodeHeader = new Fragment("nodeHeader", "groupRepositoryRow", this);
		add(nodeHeader);
		WebMarkupContainer firstColumn = new WebMarkupContainer("firstColumn");
		nodeHeader.add(firstColumn);
		RepeatingView depth = new RepeatingView("depth");
		for(int i=0; i<node.getDepth();i++) {
			depth.add(new WebMarkupContainer(depth.newChildId()));
		}
		firstColumn.add(depth);
		firstColumn.add(new Fragment("groupCollapsible", "tableGroupMinusCollapsible", this));
		if(node.getParent()!=null) {
			addChildOfNodeIdCssClassesToRow(nodeHeader, node.getParent());
		}
		nodeHeader.add(new AttributeAppender("data-node-id", Model.of(node.hashCode()), " "));

		String name = node.getName();
		if (name.startsWith(ModelUtils.getUserRepoPrefix())) {
			// user page
			String username = ModelUtils.getUserNameFromRepoPath(name);
			UserModel user = app().users().getUserModel(username);
			firstColumn.add(new LinkPanel("groupName", null, (user == null ? username : user.getDisplayName()), UserPage.class, WicketUtils.newUsernameParameter(username)));
			nodeHeader.add(new Label("groupDescription", getString("gb.personalRepositories")));
		} else {
			// project page
			firstColumn.add(new LinkPanel("groupName", null, name, ProjectPage.class, WicketUtils.newProjectParameter(name)));
			nodeHeader.add(new Label("groupDescription", ""));
		}
		WicketUtils.addCssClass(nodeHeader, "group collapsible tree");

		add(new ListView<TreeNodeModel>("subFolders", node.getSubFolders()) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void populateItem(ListItem<TreeNodeModel> item) {
				item.add(new NestedRepositoryTreePanel("rowContent", item.getModel(), accessRestrictionTranslations, linksActive));
			}

			@Override
			public boolean isVisible() {
				return super.isVisible() && !node.getSubFolders().isEmpty();
			}
		});

		add(new ListView<RepositoryModel>("repositories", node.getRepositories()) {
			private static final long serialVersionUID = 1L;

			int counter = 0;

			@Override
			public boolean isVisible() {
				return super.isVisible() && !node.getRepositories().isEmpty();
			}

			@Override
			protected void populateItem(ListItem<RepositoryModel> item) {

				RepositoryModel entry = item.getModelObject();
				WebMarkupContainer rowContent = new WebMarkupContainer("rowContent");
				item.add(rowContent);
				addChildOfNodeIdCssClassesToRow(rowContent, node);
				WebMarkupContainer firstColumn = new WebMarkupContainer("firstColumn");
				rowContent.add(firstColumn);
				RepeatingView depth = new RepeatingView("depth");
				for(int i=0; i<node.getDepth();i++) {
					depth.add(new WebMarkupContainer(depth.newChildId()));
				}
				firstColumn.add(depth);

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
				firstColumn.add(iconFragment);

				// try to strip group name for less cluttered list
				String repoName = StringUtils.getLastPathElement(entry.toString());

				if (linksActive) {
					Class<? extends BasePage> linkPage = SummaryPage.class;
					PageParameters pp = WicketUtils.newRepositoryParameter(entry.name);
					firstColumn.add(new LinkPanel("repositoryName", "list", repoName, linkPage, pp));
					rowContent.add(new LinkPanel("repositoryDescription", "list", entry.description, linkPage, pp));
				} else {
					// no links like on a federation page
					firstColumn.add(new Label("repositoryName", repoName));
					rowContent.add(new Label("repositoryDescription", entry.description));
				}
				if (entry.hasCommits) {
					// Existing repository
					rowContent.add(new Label("repositorySize", entry.size).setVisible(showSize));
				} else {
					// New repository
					rowContent.add(new Label("repositorySize", "<span class='empty'>(" + getString("gb.empty") + ")</span>").setEscapeModelStrings(false));
				}

				if (entry.isSparkleshared()) {
					rowContent.add(WicketUtils.newImage("sparkleshareIcon", "star_16x16.png", getString("gb.isSparkleshared")));
				} else {
					rowContent.add(WicketUtils.newClearPixel("sparkleshareIcon").setVisible(false));
				}

				if (!entry.isMirror && entry.isFrozen) {
					rowContent.add(WicketUtils.newImage("frozenIcon", "cold_16x16.png", getString("gb.isFrozen")));
				} else {
					rowContent.add(WicketUtils.newClearPixel("frozenIcon").setVisible(false));
				}

				if (entry.isFederated) {
					rowContent.add(WicketUtils.newImage("federatedIcon", "federated_16x16.png", getString("gb.isFederated")));
				} else {
					rowContent.add(WicketUtils.newClearPixel("federatedIcon").setVisible(false));
				}

				if (entry.isMirror) {
					rowContent.add(WicketUtils.newImage("accessRestrictionIcon", "mirror_16x16.png", getString("gb.isMirror")));
				} else {
					switch (entry.accessRestriction) {
					case NONE:
						rowContent.add(WicketUtils.newBlankImage("accessRestrictionIcon"));
						break;
					case PUSH:
						rowContent.add(WicketUtils.newImage("accessRestrictionIcon", "lock_go_16x16.png", accessRestrictionTranslations.get(entry.accessRestriction)));
						break;
					case CLONE:
						rowContent.add(WicketUtils.newImage("accessRestrictionIcon", "lock_pull_16x16.png", accessRestrictionTranslations.get(entry.accessRestriction)));
						break;
					case VIEW:
						rowContent.add(WicketUtils.newImage("accessRestrictionIcon", "shield_16x16.png", accessRestrictionTranslations.get(entry.accessRestriction)));
						break;
					default:
						rowContent.add(WicketUtils.newBlankImage("accessRestrictionIcon"));
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
				rowContent.add(ownerLabel);

				String lastChange;
				if (entry.lastChange.getTime() == 0) {
					lastChange = "--";
				} else {
					lastChange = getTimeUtils().timeAgo(entry.lastChange);
				}
				Label lastChangeLabel = new Label("repositoryLastChange", lastChange);
				rowContent.add(lastChangeLabel);
				WicketUtils.setCssClass(lastChangeLabel, getTimeUtils().timeAgoCss(entry.lastChange));
				if (!StringUtils.isEmpty(entry.lastChangeAuthor)) {
					WicketUtils.setHtmlTooltip(lastChangeLabel, getString("gb.author") + ": " + entry.lastChangeAuthor);
				}

				String clazz = counter % 2 == 0 ? "light" : "dark";
				WicketUtils.addCssClass(rowContent, clazz);
				counter++;
			}
		});

	}

	private void addChildOfNodeIdCssClassesToRow(Component row, TreeNodeModel parentNode) {
		row.add(new AttributeAppender("class", Model.of("child-of-"+ parentNode.hashCode()), " "));
		if(parentNode.getParent() != null) {
			addChildOfNodeIdCssClassesToRow(row, parentNode.getParent());
		}
	}
}
