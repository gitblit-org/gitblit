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
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.RepositoriesPanel;
import com.gitblit.wicket.panels.UsersPanel;

public class RepositoriesPage extends BasePage {

	public RepositoriesPage() {
		super();
		setupPage("", "");

		final boolean showAdmin;
		if (GitBlit.getBoolean(Keys.web.authenticateAdminPages, true)) {
			boolean allowAdmin = GitBlit.getBoolean(Keys.web.allowAdministration, false);
			showAdmin = allowAdmin && GitBlitWebSession.get().canAdmin();
			// authentication requires state and session
			setStatelessHint(false);
		} else {
			showAdmin = GitBlit.getBoolean(Keys.web.allowAdministration, false);
			if (GitBlit.getBoolean(Keys.web.authenticateViewPages, false)) {
				// authentication requires state and session
				setStatelessHint(false);
			} else {
				// no authentication required, no state and no session required
				setStatelessHint(true);
			}
		}

		// display an error message cached from a redirect
		String cachedMessage = GitBlitWebSession.get().clearErrorMessage();
		if (!StringUtils.isEmpty(cachedMessage)) {
			error(cachedMessage);
		}

		// Load the markdown welcome message
		String messageSource = GitBlit.getString(Keys.web.repositoriesMessage, "gitblit");
		String message = "<br/>";
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
				.setEscapeModelStrings(false);
		add(repositoriesMessage);
		add(new RepositoriesPanel("repositoriesPanel", showAdmin, getAccessRestrictions()));
		add(new UsersPanel("usersPanel", showAdmin).setVisible(showAdmin));
	}
}
