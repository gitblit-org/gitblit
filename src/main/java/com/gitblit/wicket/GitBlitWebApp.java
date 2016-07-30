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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.sshd.server.ServerBuilder;
import org.apache.wicket.RuntimeConfigurationType;
import org.apache.wicket.Session;
import org.apache.wicket.application.IClassResolver;
import org.apache.wicket.core.util.file.WebApplicationPath;
import org.apache.wicket.core.util.resource.ClassPathResourceFinder;
import org.apache.wicket.core.util.resource.UrlResourceStream;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.Request;
import org.apache.wicket.request.Response;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.resource.IResource;
import org.apache.wicket.request.resource.ResourceReference;
import org.apache.wicket.request.resource.SharedResourceReference;
import org.apache.wicket.request.resource.IResource.Attributes;
import org.apache.wicket.request.resource.PackageResourceReference;
import org.apache.wicket.util.file.IResourceFinder;
import org.apache.wicket.util.resource.IResourceStream;
import org.apache.wicket.util.string.StringValue;
import org.apache.wicket.util.time.Duration;

import ro.fortsoft.pf4j.PluginState;
import ro.fortsoft.pf4j.PluginWrapper;

import com.gitblit.Constants;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.extensions.GitblitWicketPlugin;
import com.gitblit.manager.IAuthenticationManager;
import com.gitblit.manager.IFederationManager;
import com.gitblit.manager.IFilestoreManager;
import com.gitblit.manager.IGitblit;
import com.gitblit.manager.INotificationManager;
import com.gitblit.manager.IPluginManager;
import com.gitblit.manager.IProjectManager;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IServicesManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.tickets.ITicketService;
import com.gitblit.transport.ssh.IPublicKeyManager;
import com.gitblit.utils.XssFilter;
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
import com.gitblit.wicket.pages.EditFilePage;
import com.gitblit.wicket.pages.EditMilestonePage;
import com.gitblit.wicket.pages.EditRepositoryPage;
import com.gitblit.wicket.pages.EditTicketPage;
import com.gitblit.wicket.pages.ExportTicketPage;
import com.gitblit.wicket.pages.FederationRegistrationPage;
import com.gitblit.wicket.pages.FilestorePage;
import com.gitblit.wicket.pages.ForkPage;
import com.gitblit.wicket.pages.ForksPage;
import com.gitblit.wicket.pages.GitSearchPage;
import com.gitblit.wicket.pages.HistoryPage;
import com.gitblit.wicket.pages.LogPage;
import com.gitblit.wicket.pages.LogoutPage;
import com.gitblit.wicket.pages.LuceneSearchPage;
import com.gitblit.wicket.pages.MetricsPage;
import com.gitblit.wicket.pages.MyDashboardPage;
import com.gitblit.wicket.pages.MyTicketsPage;
import com.gitblit.wicket.pages.NewMilestonePage;
import com.gitblit.wicket.pages.NewRepositoryPage;
import com.gitblit.wicket.pages.NewTicketPage;
import com.gitblit.wicket.pages.OverviewPage;
import com.gitblit.wicket.pages.PatchPage;
import com.gitblit.wicket.pages.ProjectPage;
import com.gitblit.wicket.pages.ProjectsPage;
import com.gitblit.wicket.pages.ReflogPage;
import com.gitblit.wicket.pages.RepositoriesPage;
import com.gitblit.wicket.pages.ReviewProposalPage;
import com.gitblit.wicket.pages.SummaryPage;
import com.gitblit.wicket.pages.TagPage;
import com.gitblit.wicket.pages.TagsPage;
import com.gitblit.wicket.pages.TeamsPage;
import com.gitblit.wicket.pages.TicketsPage;
import com.gitblit.wicket.pages.TreePage;
import com.gitblit.wicket.pages.UserPage;
import com.gitblit.wicket.pages.UsersPage;
import com.gitblit.wicket.resources.bootstrap.Bootstrap;
import com.gitblit.wicket.resources.fontawesome.FontAwesome;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class GitBlitWebApp extends WebApplication implements GitblitWicketApp {

	private final Class<? extends WebPage> homePageClass = MyDashboardPage.class;

	private final Class<? extends WebPage> newRepositoryPageClass = NewRepositoryPage.class;

	private final Map<String, CacheControl> cacheablePages = new HashMap<String, CacheControl>();

	private final Provider<IPublicKeyManager> publicKeyManagerProvider;

	private final Provider<ITicketService> ticketServiceProvider;

	private final IStoredSettings settings;

	private final XssFilter xssFilter;

	private final IRuntimeManager runtimeManager;

	private final IPluginManager pluginManager;

	private final INotificationManager notificationManager;

	private final IUserManager userManager;

	private final IAuthenticationManager authenticationManager;

	private final IRepositoryManager repositoryManager;

	private final IProjectManager projectManager;

	private final IFederationManager federationManager;

	private final IGitblit gitblit;

	private final IServicesManager services;

	private final IFilestoreManager filestoreManager;

	private static final Instant APPLICATION_STARTUP_TIME = Instant.now();

	@Inject
	public GitBlitWebApp(Provider<IPublicKeyManager> publicKeyManagerProvider,
			Provider<ITicketService> ticketServiceProvider, IRuntimeManager runtimeManager,
			IPluginManager pluginManager, INotificationManager notificationManager, IUserManager userManager,
			IAuthenticationManager authenticationManager, IRepositoryManager repositoryManager,
			IProjectManager projectManager, IFederationManager federationManager, IGitblit gitblit,
			IServicesManager services, IFilestoreManager filestoreManager) {

		super();
		this.publicKeyManagerProvider = publicKeyManagerProvider;
		this.ticketServiceProvider = ticketServiceProvider;
		this.settings = runtimeManager.getSettings();
		this.xssFilter = runtimeManager.getXssFilter();
		this.runtimeManager = runtimeManager;
		this.pluginManager = pluginManager;
		this.notificationManager = notificationManager;
		this.userManager = userManager;
		this.authenticationManager = authenticationManager;
		this.repositoryManager = repositoryManager;
		this.projectManager = projectManager;
		this.federationManager = federationManager;
		this.gitblit = gitblit;
		this.services = services;
		this.filestoreManager = filestoreManager;
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
			getResourceSettings().setDefaultCacheDuration(Duration.days(90));
		}

		// setup the standard gitweb-ish urls
		mount("/repositories", RepositoriesPage.class);
		mount("/overview", OverviewPage.class, "r");
		mount("/summary", SummaryPage.class, "r");
		mount("/reflog", ReflogPage.class, "r");
		mount("/commits", LogPage.class, "r", "h");
		mount("/log", LogPage.class, "r", "h");
		mount("/tags", TagsPage.class, "r");
		mount("/branches", BranchesPage.class, "r");
		mount("/commit", CommitPage.class, "r", "h");
		mount("/tag", TagPage.class, "r", "h");
		mount("/tree", TreePage.class, "r", "h", "f");
		mount("/blob", BlobPage.class, "r", "h", "f");
		mount("/blobdiff", BlobDiffPage.class, "r", "h", "f");
		mount("/commitdiff", CommitDiffPage.class, "r", "h");
		mount("/compare", ComparePage.class, "r", "h");
		mount("/patch", PatchPage.class, "r", "h", "f");
		mount("/history", HistoryPage.class, "r", "h", "f");
		mount("/search", GitSearchPage.class);
		mount("/metrics", MetricsPage.class, "r");
		mount("/blame", BlamePage.class, "r", "h", "f");
		mount("/users", UsersPage.class);
		mount("/teams", TeamsPage.class);
		mount("/logout", LogoutPage.class);

		// setup ticket urls
		mount("/tickets", TicketsPage.class, "r", "h");
		mount("/tickets/new", NewTicketPage.class, "r");
		mount("/tickets/edit", EditTicketPage.class, "r", "h");
		mount("/tickets/export", ExportTicketPage.class, "r", "h");
		mount("/milestones/new", NewMilestonePage.class, "r");
		mount("/milestones/edit", EditMilestonePage.class, "r", "h");
		mount("/mytickets", MyTicketsPage.class, "r", "h");

		// setup the markup document urls
		mount("/docs", DocsPage.class, "r", "h");
		mount("/doc", DocPage.class, "r", "h", "f");
		mount("/editfile", EditFilePage.class, "r", "h", "f");

		// federation urls
		mount("/proposal", ReviewProposalPage.class, "t");
		mount("/registration", FederationRegistrationPage.class, "u", "n");

		mount("/new", NewRepositoryPage.class);
		mount("/edit", EditRepositoryPage.class, "r");
		mount("/activity", ActivityPage.class, "r", "h");
		mount("/lucene", LuceneSearchPage.class);
		mount("/project", ProjectPage.class, "p");
		mount("/projects", ProjectsPage.class);
		mount("/user", UserPage.class, "user");
		mount("/forks", ForksPage.class, "r");
		mount("/fork", ForkPage.class, "r");

		// filestore URL
		mount("/filestore", FilestorePage.class);
		Bootstrap.install(this);
		FontAwesome.install(this);
		
		// allow started Wicket plugins to initialize
		for (PluginWrapper pluginWrapper : pluginManager.getPlugins()) {
			if (PluginState.STARTED != pluginWrapper.getPluginState()) {
				continue;
			}
			if (pluginWrapper.getPlugin() instanceof GitblitWicketPlugin) {
				GitblitWicketPlugin wicketPlugin = (GitblitWicketPlugin) pluginWrapper.getPlugin();
				wicketPlugin.init(this);
			}
		}

		// customize the Wicket class resolver to load from plugins
		IClassResolver coreResolver = getApplicationSettings().getClassResolver();
		PluginClassResolver classResolver = new PluginClassResolver(coreResolver, pluginManager);
		getApplicationSettings().setClassResolver(classResolver);

		getMarkupSettings().setDefaultMarkupEncoding(Constants.ENCODING);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gitblit.wicket.Webapp#mount(java.lang.String, java.lang.Class,
	 * java.lang.String)
	 */
	@Override
	public void mount(String location, Class<? extends WebPage> clazz, String... parameters) {
		if (parameters == null) {
			parameters = new String[] {};
		}
		if (!settings.getBoolean(Keys.web.mountParameters, true)) {
			parameters = new String[] {};
		}
		// TODO: check if needed with wichet-7
		// mount(new GitblitParamUrlCodingStrategy(settings, xssFilter,
		// location, clazz, parameters));
		mountPage(location, clazz);

		// map the mount point to the cache control definition
		if (clazz.isAnnotationPresent(CacheControl.class)) {
			CacheControl cacheControl = clazz.getAnnotation(CacheControl.class);
			cacheablePages.put(location.substring(1), cacheControl);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gitblit.wicket.Webapp#getHomePage()
	 */
	@Override
	public Class<? extends WebPage> getHomePage() {
		return homePageClass;
	}

	public Class<? extends WebPage> getNewRepositoryPage() {
		return newRepositoryPageClass;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gitblit.wicket.Webapp#isCacheablePage(java.lang.String)
	 */
	@Override
	public boolean isCacheablePage(String mountPoint) {
		return cacheablePages.containsKey(mountPoint);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gitblit.wicket.Webapp#getCacheControl(java.lang.String)
	 */
	@Override
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gitblit.wicket.Webapp#settings()
	 */
	@Override
	public IStoredSettings settings() {
		return settings;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gitblit.wicket.Webapp#xssFilter()
	 */
	@Override
	public XssFilter xssFilter() {
		return xssFilter;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gitblit.wicket.Webapp#isDebugMode()
	 */
	@Override
	public boolean isDebugMode() {
		return runtimeManager.isDebugMode();
	}

	/*
	 * These methods look strange... and they are... but they are the first step
	 * towards modularization across multiple commits.
	 */
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gitblit.wicket.Webapp#getBootDate()
	 */
	@Override
	public Date getBootDate() {
		return runtimeManager.getBootDate();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gitblit.wicket.Webapp#getLastActivityDate()
	 */
	@Override
	public Date getLastActivityDate() {
		return repositoryManager.getLastActivityDate();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gitblit.wicket.Webapp#runtime()
	 */
	@Override
	public IRuntimeManager runtime() {
		return runtimeManager;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gitblit.wicket.Webapp#plugins()
	 */
	@Override
	public IPluginManager plugins() {
		return pluginManager;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gitblit.wicket.Webapp#notifier()
	 */
	@Override
	public INotificationManager notifier() {
		return notificationManager;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gitblit.wicket.Webapp#users()
	 */
	@Override
	public IUserManager users() {
		return userManager;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gitblit.wicket.Webapp#authentication()
	 */
	@Override
	public IAuthenticationManager authentication() {
		return authenticationManager;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gitblit.wicket.Webapp#keys()
	 */
	@Override
	public IPublicKeyManager keys() {
		return publicKeyManagerProvider.get();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gitblit.wicket.Webapp#repositories()
	 */
	@Override
	public IRepositoryManager repositories() {
		return repositoryManager;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gitblit.wicket.Webapp#projects()
	 */
	@Override
	public IProjectManager projects() {
		return projectManager;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gitblit.wicket.Webapp#federation()
	 */
	@Override
	public IFederationManager federation() {
		return federationManager;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gitblit.wicket.Webapp#gitblit()
	 */
	@Override
	public IGitblit gitblit() {
		return gitblit;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gitblit.wicket.Webapp#services()
	 */
	@Override
	public IServicesManager services() {
		return services;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gitblit.wicket.Webapp#tickets()
	 */
	@Override
	public ITicketService tickets() {
		return ticketServiceProvider.get();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.gitblit.wicket.Webapp#getTimezone()
	 */
	@Override
	public TimeZone getTimezone() {
		return runtimeManager.getTimezone();
	}

	public Instant getApplicationStartupTime() {
		return APPLICATION_STARTUP_TIME;
	}

	@Override
	public final RuntimeConfigurationType getConfigurationType() {
		if (runtimeManager.isDebugMode()) {
			return RuntimeConfigurationType.DEVELOPMENT;
		}
		return RuntimeConfigurationType.DEPLOYMENT;
	}

	public static GitBlitWebApp get() {
		return (GitBlitWebApp) WebApplication.get();
	}

	@Override
	public IFilestoreManager filestore() {
		return filestoreManager;
	}
}
