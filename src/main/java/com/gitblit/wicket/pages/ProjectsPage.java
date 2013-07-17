/*
 * Copyright 2012 gitblit.com.
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

import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.models.ProjectModel;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.PageRegistration;
import com.gitblit.wicket.PageRegistration.DropDownMenuItem;
import com.gitblit.wicket.PageRegistration.DropDownMenuRegistration;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.LinkPanel;

public class ProjectsPage extends RootPage {

	public ProjectsPage() {
		super();
		setup(null);
	}

	public ProjectsPage(PageParameters params) {
		super(params);
		setup(params);
	}

	@Override
	protected boolean reusePageParameters() {
		return true;
	}
	
	@Override
	protected Class<? extends BasePage> getRootNavPageClass() {
		return RepositoriesPage.class;
	}

	@Override
	protected List<ProjectModel> getProjectModels() {
		return GitBlit.self().getProjectModels(getRepositoryModels(), false);
	}

	private void setup(PageParameters params) {
		setupPage("", "");
		// check to see if we should display a login message
		boolean authenticateView = GitBlit.getBoolean(Keys.web.authenticateViewPages, true);
		if (authenticateView && !GitBlitWebSession.get().isLoggedIn()) {
			add(new Label("projectsPanel"));
			return;
		}

		List<ProjectModel> projects = getProjects(params);

		ListDataProvider<ProjectModel> dp = new ListDataProvider<ProjectModel>(projects);

		DataView<ProjectModel> dataView = new DataView<ProjectModel>("project", dp) {
			private static final long serialVersionUID = 1L;
			int counter;

			@Override
			protected void onBeforeRender() {
				super.onBeforeRender();
				counter = 0;
			}

			public void populateItem(final Item<ProjectModel> item) {
				final ProjectModel entry = item.getModelObject();

				PageParameters pp = WicketUtils.newProjectParameter(entry.name);
				item.add(new LinkPanel("projectTitle", "list", entry.getDisplayName(),
						ProjectPage.class, pp));
				item.add(new LinkPanel("projectDescription", "list", entry.description,
						ProjectPage.class, pp));

				item.add(new Label("repositoryCount", entry.repositories.size()
						+ " "
						+ (entry.repositories.size() == 1 ? getString("gb.repository")
								: getString("gb.repositories"))));

				String lastChange;
				if (entry.lastChange.getTime() == 0) {
					lastChange = "--";
				} else {
					lastChange = getTimeUtils().timeAgo(entry.lastChange);
				}
				Label lastChangeLabel = new Label("projectLastChange", lastChange);
				item.add(lastChangeLabel);
				WicketUtils.setCssClass(lastChangeLabel, getTimeUtils()
						.timeAgoCss(entry.lastChange));
				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(dataView);
	}

	@Override
	protected void addDropDownMenus(List<PageRegistration> pages) {
		PageParameters params = getPageParameters();
		
		DropDownMenuRegistration menu = new DropDownMenuRegistration("gb.filters",
				ProjectsPage.class);
		// preserve time filter option on repository choices
		menu.menuItems.addAll(getRepositoryFilterItems(params));

		// preserve repository filter option on time choices
		menu.menuItems.addAll(getTimeFilterItems(params));

		if (menu.menuItems.size() > 0) {
			// Reset Filter
			menu.menuItems.add(new DropDownMenuItem(getString("gb.reset"), null, null));
		}

		pages.add(menu);
	}
}
