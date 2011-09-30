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
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.wicket.Component;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.resource.ContextRelativeResource;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.utils.MarkdownUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.RepositoriesPanel;

public class RepositoriesPage extends RootPage {

	public RepositoriesPage() {
		super();
		setupPage("", "");

		// Load the markdown welcome message
		String messageSource = GitBlit.getString(Keys.web.repositoriesMessage, "gitblit");
		String message = "";
		if (messageSource.equalsIgnoreCase("gitblit")) {
			// Read default welcome message
			try {
				ContextRelativeResource res = WicketUtils.getResource("welcome.mkd");
				InputStream is = res.getResourceStream().getInputStream();
				InputStreamReader reader = new InputStreamReader(is);
				message = MarkdownUtils.transformMarkdown(reader);
				reader.close();
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
						warn(message, t);
					}
				} else {
					message = messageSource + " is not a valid file.";
				}
			}
		}
		Component repositoriesMessage = new Label("repositoriesMessage", message)
				.setEscapeModelStrings(false).setVisible(message.length() > 0);
		add(repositoriesMessage);
		RepositoriesPanel repositories = new RepositoriesPanel("repositoriesPanel", showAdmin,
				null, getAccessRestrictions());
		// push the panel down if we are hiding the admin controls and the
		// welcome message
		if (!showAdmin && !repositoriesMessage.isVisible()) {
			WicketUtils.setCssStyle(repositories, "padding-top:5px;");
		}
		add(repositories);
	}
}
