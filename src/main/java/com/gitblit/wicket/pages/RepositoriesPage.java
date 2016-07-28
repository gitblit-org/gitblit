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
package com.gitblit.wicket.pages;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.eclipse.jgit.lib.Constants;

import com.gitblit.Keys;
import com.gitblit.models.Menu.ParameterMenuItem;
import com.gitblit.models.NavLink;
import com.gitblit.models.NavLink.DropDownPageMenuNavLink;
import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.MarkdownUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.CacheControl;
import com.gitblit.wicket.CacheControl.LastModified;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.RepositoriesPanel;

@CacheControl(LastModified.ACTIVITY)
public class RepositoriesPage extends RootPage {

	public RepositoriesPage() {
		super();
		setup(null);
	}

	public RepositoriesPage(PageParameters params) {
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
		boolean authenticateView = app().settings().getBoolean(Keys.web.authenticateViewPages, true);
		if (authenticateView && !GitBlitWebSession.get().isLoggedIn()) {
			String messageSource = app().settings().getString(Keys.web.loginMessage, "gitblit");
			String message = readMarkdown(messageSource, "login.mkd");
			Component repositoriesMessage = new Label("repositoriesMessage", message);
			add(repositoriesMessage.setEscapeModelStrings(false));
			add(new Label("repositoriesPanel"));
			return;
		}

		// Load the markdown welcome message
		String messageSource = app().settings().getString(Keys.web.repositoriesMessage, "gitblit");
		String message = readMarkdown(messageSource, "welcome.mkd");
		Component repositoriesMessage = new Label("repositoriesMessage", message)
				.setEscapeModelStrings(false).setVisible(message.length() > 0);
		add(repositoriesMessage);

		// conditionally include personal repositories in this page
		List<RepositoryModel> repositories = getRepositories(params);
		if (!app().settings().getBoolean(Keys.web.includePersonalRepositories, true)) {
			Iterator<RepositoryModel> itr = repositories.iterator();
			while (itr.hasNext()) {
				RepositoryModel rm = itr.next();
				if (rm.isPersonalRepository()) {
					itr.remove();
				}
			}
		}

		RepositoriesPanel repositoriesPanel = new RepositoriesPanel("repositoriesPanel", showAdmin,
				true, repositories, true, getAccessRestrictions());
		// push the panel down if we are hiding the admin controls and the
		// welcome message
		if (!showAdmin && !repositoriesMessage.isVisible()) {
			WicketUtils.setCssStyle(repositoriesPanel, "padding-top:5px;");
		}
		add(repositoriesPanel);
	}

	@Override
	protected void addDropDownMenus(List<NavLink> navLinks) {
		PageParameters params = getPageParameters();

		DropDownPageMenuNavLink menu = new DropDownPageMenuNavLink("gb.filters",
				RepositoriesPage.class);
		// preserve time filter option on repository choices
		menu.menuItems.addAll(getRepositoryFilterItems(params));

		// preserve repository filter option on time choices
		menu.menuItems.addAll(getTimeFilterItems(params));

		if (menu.menuItems.size() > 0) {
			// Reset Filter
			menu.menuItems.add(new ParameterMenuItem(getString("gb.reset")));
		}

		navLinks.add(menu);
	}

	private String readMarkdown(String messageSource, String resource) {
		String message = "";
		if (messageSource.equalsIgnoreCase("gitblit")) {
			// Read default message
			message = readDefaultMarkdown(resource);
		} else {
			// Read user-supplied message
			if (!StringUtils.isEmpty(messageSource)) {
				File file = app().runtime().getFileOrFolder(messageSource);
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
		String base = file.substring(0, file.lastIndexOf('.'));
		String ext = file.substring(file.lastIndexOf('.'));
		String lc = getLanguageCode();
		String cc = getCountryCode();

		// try to read file_en-us.ext, file_en.ext, file.ext
		List<String> files = new ArrayList<String>();
		if (!StringUtils.isEmpty(lc)) {
			if (!StringUtils.isEmpty(cc)) {
				files.add(base + "_" + lc + "-" + cc + ext);
				files.add(base + "_" + lc + "_" + cc.toUpperCase() + ext);
			}
			files.add(base + "_" + lc + ext);
		}
		files.add(file);

		for (String name : files) {
			String message;
			InputStreamReader reader = null;
			try {
				InputStream is = getClass().getResourceAsStream("/" + name);
				if (is == null) {
					continue;
				}
				reader = new InputStreamReader(is, Constants.CHARACTER_ENCODING);
				message = MarkdownUtils.transformMarkdown(reader);
				reader.close();
				return message;
			} catch (Throwable t) {
				message = MessageFormat.format(getString("gb.failedToReadMessage"), file);
				error(message, t, false);
				return message;
			} finally {
				if (reader != null) {
					try {
						reader.close();
					} catch (Exception e) {
					}
				}
			}
		}
		return MessageFormat.format(getString("gb.failedToReadMessage"), file);
	}
}
