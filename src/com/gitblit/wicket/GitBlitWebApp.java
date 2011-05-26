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
package com.gitblit.wicket;

import org.apache.wicket.Application;
import org.apache.wicket.Page;
import org.apache.wicket.Request;
import org.apache.wicket.Response;
import org.apache.wicket.Session;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.target.coding.MixedParamUrlCodingStrategy;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.wicket.pages.BlobDiffPage;
import com.gitblit.wicket.pages.BlobPage;
import com.gitblit.wicket.pages.BranchesPage;
import com.gitblit.wicket.pages.CommitDiffPage;
import com.gitblit.wicket.pages.CommitPage;
import com.gitblit.wicket.pages.DocsPage;
import com.gitblit.wicket.pages.HistoryPage;
import com.gitblit.wicket.pages.LogPage;
import com.gitblit.wicket.pages.MarkdownPage;
import com.gitblit.wicket.pages.PatchPage;
import com.gitblit.wicket.pages.RawPage;
import com.gitblit.wicket.pages.RepositoriesPage;
import com.gitblit.wicket.pages.SearchPage;
import com.gitblit.wicket.pages.SummaryPage;
import com.gitblit.wicket.pages.TagPage;
import com.gitblit.wicket.pages.TagsPage;
import com.gitblit.wicket.pages.TicketPage;
import com.gitblit.wicket.pages.TicketsPage;
import com.gitblit.wicket.pages.TreePage;

public class GitBlitWebApp extends WebApplication {

	@Override
	public void init() {
		super.init();

		// Setup page authorization mechanism
		boolean useAuthentication = GitBlit.getBoolean(Keys.web.authenticateViewPages, false)
				|| GitBlit.getBoolean(Keys.web.authenticateAdminPages, false);
		if (useAuthentication) {
			AuthorizationStrategy authStrategy = new AuthorizationStrategy();
			getSecuritySettings().setAuthorizationStrategy(authStrategy);
			getSecuritySettings().setUnauthorizedComponentInstantiationListener(authStrategy);
		}

		// Grab Browser info (like timezone, etc)
		if (GitBlit.getBoolean(Keys.web.useClientTimezone, false)) {
			getRequestCycleSettings().setGatherExtendedBrowserInfo(true);
		}

		// setup the standard gitweb-ish urls
		mount("/summary", SummaryPage.class, "r");
		mount("/log", LogPage.class, "r", "h");
		mount("/tags", TagsPage.class, "r");
		mount("/branches", BranchesPage.class, "r");
		mount("/commit", CommitPage.class, "r", "h");
		mount("/tag", TagPage.class, "r", "h");
		mount("/tree", TreePage.class, "r", "h", "f");
		mount("/blob", BlobPage.class, "r", "h", "f");
		mount("/raw", RawPage.class, "r", "h", "f");
		mount("/blobdiff", BlobDiffPage.class, "r", "h", "f");
		mount("/commitdiff", CommitDiffPage.class, "r", "h");
		mount("/patch", PatchPage.class, "r", "h", "f");
		mount("/history", HistoryPage.class, "r", "h", "f");
		mount("/search", SearchPage.class);

		// setup ticket urls
		mount("/tickets", TicketsPage.class, "r");
		mount("/ticket", TicketPage.class, "r", "h", "f");

		// setup the markdown urls
		mount("/docs", DocsPage.class, "r");
		mount("/markdown", MarkdownPage.class, "r", "h", "f");

		// setup login/logout urls, if we are using authentication
		if (useAuthentication) {
			mount("/login", LoginPage.class);
			mount("/logout", LogoutPage.class);
		}
	}

	private void mount(String location, Class<? extends WebPage> clazz, String... parameters) {
		if (parameters == null) {
			parameters = new String[] {};
		}
		mount(new MixedParamUrlCodingStrategy(location, clazz, parameters));
	}

	@Override
	public Class<? extends Page> getHomePage() {
		return RepositoriesPage.class;
	}

	@Override
	public final Session newSession(Request request, Response response) {
		return new GitBlitWebSession(request);
	}

	@Override
	public final String getConfigurationType() {
		if (GitBlit.self().isDebugMode()) {
			return Application.DEVELOPMENT;
		}
		return Application.DEPLOYMENT;
	}

	public static GitBlitWebApp get() {
		return (GitBlitWebApp) WebApplication.get();
	}
}
