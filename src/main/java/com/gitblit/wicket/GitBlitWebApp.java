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

import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.wicket.Application;
import org.apache.wicket.Request;
import org.apache.wicket.Response;
import org.apache.wicket.Session;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.protocol.http.WebApplication;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.IAuthenticationManager;
import com.gitblit.manager.IFederationManager;
import com.gitblit.manager.IGitblit;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IPluginManager;
import com.gitblit.manager.IProjectManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.tickets.ITicketService;
import com.gitblit.transport.ssh.IPublicKeyManager;
import com.gitblit.wicket.pages.ActivityPage;
import com.gitblit.wicket.pages.BlamePage;
import com.gitblit.wicket.pages.BlobDiffPage;
import com.gitblit.wicket.pages.BlobPage;
import com.gitblit.wicket.pages.BranchesPage;
import com.gitblit.wicket.pages.CommitDiffPage;
import com.gitblit.wicket.pages.CommitPage;
import com.gitblit.wicket.pages.ComparePage;
import com.gitblit.wicket.pages.DocPage;
import com.gitblit.wicket.pages.DocsPage;
import com.gitblit.wicket.pages.EditTicketPage;
import com.gitblit.wicket.pages.ExportTicketPage;
import com.gitblit.wicket.pages.FederationRegistrationPage;
import com.gitblit.wicket.pages.ForkPage;
import com.gitblit.wicket.pages.ForksPage;
import com.gitblit.wicket.pages.GitSearchPage;
import com.gitblit.wicket.pages.HistoryPage;
import com.gitblit.wicket.pages.LogPage;
import com.gitblit.wicket.pages.LogoutPage;
import com.gitblit.wicket.pages.LuceneSearchPage;
import com.gitblit.wicket.pages.MetricsPage;
import com.gitblit.wicket.pages.MyDashboardPage;
import com.gitblit.wicket.pages.NewTicketPage;
import com.gitblit.wicket.pages.OverviewPage;
import com.gitblit.wicket.pages.PatchPage;
import com.gitblit.wicket.pages.ProjectPage;
import com.gitblit.wicket.pages.ProjectsPage;
import com.gitblit.wicket.pages.RawPage;
import com.gitblit.wicket.pages.ReflogPage;
import com.gitblit.wicket.pages.RepositoriesPage;
import com.gitblit.wicket.pages.ReviewProposalPage;
import com.gitblit.wicket.pages.SummaryPage;
import com.gitblit.wicket.pages.TagPage;
import com.gitblit.wicket.pages.TagsPage;
import com.gitblit.wicket.pages.TicketsPage;
import com.gitblit.wicket.pages.TreePage;
import com.gitblit.wicket.pages.UserPage;
import com.gitblit.wicket.pages.UsersPage;

public class GitBlitWebApp extends WebApplication {

	private final Class<? extends WebPage> homePageClass = MyDashboardPage.class;

	private final Map<String, CacheControl> cacheablePages = new HashMap<String, CacheControl>();

	private final IStoredSettings settings;

	private final IRuntimeManager runtimeManager;

	private final IPluginManager pluginManager;

	private final INotificationManager notificationManager;

	private final IUserManager userManager;

	private final IAuthenticationManager authenticationManager;

	private final IPublicKeyManager publicKeyManager;

	private final IRepositoryManager repositoryManager;

	private final IProjectManager projectManager;

	private final IFederationManager federationManager;

	private final IGitblit gitblit;

	public GitBlitWebApp(
			IRuntimeManager runtimeManager,
			IPluginManager pluginManager,
			INotificationManager notificationManager,
			IUserManager userManager,
			IAuthenticationManager authenticationManager,
			IPublicKeyManager publicKeyManager,
			IRepositoryManager repositoryManager,
			IProjectManager projectManager,
			IFederationManager federationManager,
			IGitblit gitblit) {

		super();
		this.settings = runtimeManager.getSettings();
		this.runtimeManager = runtimeManager;
		this.pluginManager = pluginManager;
		this.notificationManager = notificationManager;
		this.userManager = userManager;
		this.authenticationManager = authenticationManager;
		this.publicKeyManager = publicKeyManager;
		this.repositoryManager = repositoryManager;
		this.projectManager = projectManager;
		this.federationManager = federationManager;
		this.gitblit = gitblit;
	}

	@Override
	public void init() {
		super.init();

		// Setup page authorization mechanism
		boolean useAuthentication = settings.getBoolean(Keys.web.authenticateViewPages, false)
				|| settings.getBoolean(Keys.web.authenticateAdminPages, false);
		if (useAuthentication) {
			AuthorizationStrategy authStrategy = new AuthorizationStrategy(settings, homePageClass);
			getSecuritySettings().setAuthorizationStrategy(authStrategy);
			getSecuritySettings().setUnauthorizedComponentInstantiationListener(authStrategy);
		}

		// Grab Browser info (like timezone, etc)
		if (settings.getBoolean(Keys.web.useClientTimezone, false)) {
			getRequestCycleSettings().setGatherExtendedBrowserInfo(true);
		}

		// configure the resource cache duration to 90 days for deployment
		if (!isDebugMode()) {
			getResourceSettings().setDefaultCacheDuration(90 * 86400);
		}

		// setup the standard gitweb-ish urls
		mount("/repositories", RepositoriesPage.class);
		mount("/overview", OverviewPage.class, "r", "h");
		mount("/summary", SummaryPage.class, "r");
		mount("/reflog", ReflogPage.class, "r", "h");
		mount("/commits", LogPage.class, "r", "h");
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
		mount("/compare", ComparePage.class, "r", "h");
		mount("/patch", PatchPage.class, "r", "h", "f");
		mount("/history", HistoryPage.class, "r", "h", "f");
		mount("/search", GitSearchPage.class);
		mount("/metrics", MetricsPage.class, "r");
		mount("/blame", BlamePage.class, "r", "h", "f");
		mount("/users", UsersPage.class);
		mount("/logout", LogoutPage.class);

		// setup ticket urls
		mount("/tickets", TicketsPage.class, "r", "h");
		mount("/tickets/new", NewTicketPage.class, "r");
		mount("/tickets/edit", EditTicketPage.class, "r", "h");
		mount("/tickets/export", ExportTicketPage.class, "r", "h");

		// setup the markup document urls
		mount("/docs", DocsPage.class, "r");
		mount("/doc", DocPage.class, "r", "h", "f");

		// federation urls
		mount("/proposal", ReviewProposalPage.class, "t");
		mount("/registration", FederationRegistrationPage.class, "u", "n");

		mount("/activity", ActivityPage.class, "r", "h");
		mount("/lucene", LuceneSearchPage.class);
		mount("/project", ProjectPage.class, "p");
		mount("/projects", ProjectsPage.class);
		mount("/user", UserPage.class, "user");
		mount("/forks", ForksPage.class, "r");
		mount("/fork", ForkPage.class, "r");

		getMarkupSettings().setDefaultMarkupEncoding("UTF-8");
		super.init();
	}

	private void mount(String location, Class<? extends WebPage> clazz, String... parameters) {
		if (parameters == null) {
			parameters = new String[] {};
		}
		if (!settings.getBoolean(Keys.web.mountParameters, true)) {
			parameters = new String[] {};
		}
		mount(new GitblitParamUrlCodingStrategy(settings, location, clazz, parameters));

		// map the mount point to the cache control definition
		if (clazz.isAnnotationPresent(CacheControl.class)) {
			CacheControl cacheControl = clazz.getAnnotation(CacheControl.class);
			cacheablePages.put(location.substring(1), cacheControl);
		}
	}

	@Override
	public Class<? extends WebPage> getHomePage() {
		return homePageClass;
	}

	public boolean isCacheablePage(String mountPoint) {
		return cacheablePages.containsKey(mountPoint);
	}

	public CacheControl getCacheControl(String mountPoint) {
		return cacheablePages.get(mountPoint);
	}

	@Override
	public final Session newSession(Request request, Response response) {
		GitBlitWebSession gitBlitWebSession = new GitBlitWebSession(request);

		Locale forcedLocale = runtime().getLocale();
		if (forcedLocale != null) {
			gitBlitWebSession.setLocale(forcedLocale);
		}
		return gitBlitWebSession;
	}

	public IStoredSettings settings() {
		return settings;
	}

	/**
	 * Is Gitblit running in debug mode?
	 *
	 * @return true if Gitblit is running in debug mode
	 */
	public boolean isDebugMode() {
		return runtimeManager.isDebugMode();
	}

	/*
	 * These methods look strange... and they are... but they are the first
	 * step towards modularization across multiple commits.
	 */
	public Date getBootDate() {
		return runtimeManager.getBootDate();
	}

	public Date getLastActivityDate() {
		return repositoryManager.getLastActivityDate();
	}

	public IRuntimeManager runtime() {
		return runtimeManager;
	}

	public IPluginManager plugins() {
		return pluginManager;
	}

	public INotificationManager notifier() {
		return notificationManager;
	}

	public IUserManager users() {
		return userManager;
	}

	public IAuthenticationManager authentication() {
		return authenticationManager;
	}

	public IPublicKeyManager keys() {
		return publicKeyManager;
	}

	public IRepositoryManager repositories() {
		return repositoryManager;
	}

	public IProjectManager projects() {
		return projectManager;
	}

	public IFederationManager federation() {
		return federationManager;
	}

	public IGitblit gitblit() {
		return gitblit;
	}

	public ITicketService tickets() {
		return gitblit.getTicketService();
	}

	public TimeZone getTimezone() {
		return runtimeManager.getTimezone();
	}

	@Override
	public final String getConfigurationType() {
		if (runtimeManager.isDebugMode()) {
			return Application.DEVELOPMENT;
		}
		return Application.DEPLOYMENT;
	}

	public static GitBlitWebApp get() {
		return (GitBlitWebApp) WebApplication.get();
	}
}
