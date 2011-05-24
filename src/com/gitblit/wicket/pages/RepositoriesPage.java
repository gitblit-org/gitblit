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
import com.gitblit.wicket.BasePage;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.RepositoriesPanel;
import com.gitblit.wicket.panels.UsersPanel;

public class RepositoriesPage extends BasePage {

	public RepositoriesPage() {
		super();
		setupPage("", "");

		final boolean showAdmin;
		if (GitBlit.self().settings().getBoolean(Keys.web.authenticateAdminPages, true)) {
			boolean allowAdmin = GitBlit.self().settings().getBoolean(Keys.web.allowAdministration, false);
			showAdmin = allowAdmin && GitBlitWebSession.get().canAdmin();
			// authentication requires state and session
			setStatelessHint(false);
		} else {
			showAdmin = GitBlit.self().settings().getBoolean(Keys.web.allowAdministration, false);
			if (GitBlit.self().settings().getBoolean(Keys.web.authenticateViewPages, false)) {
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
		String messageSource = GitBlit.self().settings().getString(Keys.web.repositoriesMessage, "gitblit");
		String message = "<br/>";
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
		add(repositoriesMessage);
		add(new RepositoriesPanel("repositoriesPanel", showAdmin, getAccessRestrictions()));		
		add(new UsersPanel("usersPanel", showAdmin).setVisible(showAdmin));
	}
}
