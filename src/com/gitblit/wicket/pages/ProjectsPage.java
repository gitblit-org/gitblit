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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.resource.ContextRelativeResource;
import org.apache.wicket.util.resource.ResourceStreamNotFoundException;
import org.eclipse.jgit.lib.Constants;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.models.ProjectModel;
import com.gitblit.utils.MarkdownUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.PageRegistration;
import com.gitblit.wicket.PageRegistration.DropDownMenuItem;
import com.gitblit.wicket.PageRegistration.DropDownMenuRegistration;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.LinkPanel;

public class ProjectsPage extends RootPage {

	List<ProjectModel> projectModels = new ArrayList<ProjectModel>();

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

	private void setup(PageParameters params) {
		setupPage("", "");
		// check to see if we should display a login message
		boolean authenticateView = GitBlit.getBoolean(Keys.web.authenticateViewPages, true);
		if (authenticateView && !GitBlitWebSession.get().isLoggedIn()) {
			String messageSource = GitBlit.getString(Keys.web.loginMessage, "gitblit");
			String message = readMarkdown(messageSource, "login.mkd");
			Component repositoriesMessage = new Label("projectsMessage", message);
			add(repositoriesMessage.setEscapeModelStrings(false));
			add(new Label("projectsPanel"));
			return;
		}

		// Load the markdown welcome message
		String messageSource = GitBlit.getString(Keys.web.repositoriesMessage, "gitblit");
		String message = readMarkdown(messageSource, "welcome.mkd");
		Component projectsMessage = new Label("projectsMessage", message).setEscapeModelStrings(
				false).setVisible(message.length() > 0);
		add(projectsMessage);

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

		// push the panel down if we are hiding the admin controls and the
		// welcome message
		if (!showAdmin && !projectsMessage.isVisible()) {
			WicketUtils.setCssStyle(dataView, "padding-top:5px;");
		}
	}

	@Override
	protected void addDropDownMenus(List<PageRegistration> pages) {
		PageParameters params = getPageParameters();
		
		pages.add(0, new PageRegistration("gb.projects", ProjectsPage.class, params));

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

	private String readMarkdown(String messageSource, String resource) {
		String message = "";
		if (messageSource.equalsIgnoreCase("gitblit")) {
			// Read default message
			message = readDefaultMarkdown(resource);
		} else {
			// Read user-supplied message
			if (!StringUtils.isEmpty(messageSource)) {
				File file = new File(messageSource);
				if (file.exists()) {
					try {
						FileInputStream fis = new FileInputStream(file);
						InputStreamReader reader = new InputStreamReader(fis,
								Constants.CHARACTER_ENCODING);
						message = MarkdownUtils.transformMarkdown(reader);
						reader.close();
					} catch (Throwable t) {
						message = getString("gb.failedToRead") + " " + file;
						warn(message, t);
					}
				} else {
					message = messageSource + " " + getString("gb.isNotValidFile");
				}
			}
		}
		return message;
	}

	private String readDefaultMarkdown(String file) {
		String content = readDefaultMarkdown(file, getLanguageCode());
		if (StringUtils.isEmpty(content)) {
			content = readDefaultMarkdown(file, null);
		}
		return content;
	}

	private String readDefaultMarkdown(String file, String lc) {
		if (!StringUtils.isEmpty(lc)) {
			// convert to file_lc.mkd
			file = file.substring(0, file.lastIndexOf('.')) + "_" + lc
					+ file.substring(file.lastIndexOf('.'));
		}
		String message;
		try {
			ContextRelativeResource res = WicketUtils.getResource(file);
			InputStream is = res.getResourceStream().getInputStream();
			InputStreamReader reader = new InputStreamReader(is, Constants.CHARACTER_ENCODING);
			message = MarkdownUtils.transformMarkdown(reader);
			reader.close();
		} catch (ResourceStreamNotFoundException t) {
			if (lc == null) {
				// could not find default language resource
				message = MessageFormat.format(getString("gb.failedToReadMessage"), file);
				error(message, t, false);
			} else {
				// ignore so we can try default language resource
				message = null;
			}
		} catch (Throwable t) {
			message = MessageFormat.format(getString("gb.failedToReadMessage"), file);
			error(message, t, false);
		}
		return message;
	}
}
