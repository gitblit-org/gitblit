package com.gitblit.wicket;

import org.apache.wicket.Application;
import org.apache.wicket.Page;
import org.apache.wicket.Request;
import org.apache.wicket.Response;
import org.apache.wicket.Session;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.protocol.http.request.urlcompressing.UrlCompressingWebRequestProcessor;
import org.apache.wicket.request.IRequestCycleProcessor;
import org.apache.wicket.request.target.coding.MixedParamUrlCodingStrategy;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.wicket.pages.BlobDiffPage;
import com.gitblit.wicket.pages.BlobPage;
import com.gitblit.wicket.pages.BranchesPage;
import com.gitblit.wicket.pages.CommitDiffPage;
import com.gitblit.wicket.pages.CommitPage;
import com.gitblit.wicket.pages.HistoryPage;
import com.gitblit.wicket.pages.LogPage;
import com.gitblit.wicket.pages.PatchPage;
import com.gitblit.wicket.pages.RawPage;
import com.gitblit.wicket.pages.RepositoriesPage;
import com.gitblit.wicket.pages.SummaryPage;
import com.gitblit.wicket.pages.TagPage;
import com.gitblit.wicket.pages.TagsPage;
import com.gitblit.wicket.pages.TicketsPage;
import com.gitblit.wicket.pages.TicketPage;
import com.gitblit.wicket.pages.TreePage;

public class GitBlitWebApp extends WebApplication {

	@Override
	public void init() {
		super.init();

		// Setup page authorization mechanism
		boolean useAuthentication = GitBlit.self().settings().getBoolean(Keys.web.authenticateViewPages, false) || GitBlit.self().settings().getBoolean(Keys.web.authenticateAdminPages, false);
		if (useAuthentication) {
			AuthorizationStrategy authStrategy = new AuthorizationStrategy();
			getSecuritySettings().setAuthorizationStrategy(authStrategy);
			getSecuritySettings().setUnauthorizedComponentInstantiationListener(authStrategy);
		}

		// Grab Browser info (like timezone, etc)
		if (GitBlit.self().settings().getBoolean(Keys.web.useClientTimezone, false)) {
			getRequestCycleSettings().setGatherExtendedBrowserInfo(true);
		}

		// setup the standard gitweb-ish urls
		mount(new MixedParamUrlCodingStrategy("/summary", SummaryPage.class, new String[] { "r" }));
		mount(new MixedParamUrlCodingStrategy("/log", LogPage.class, new String[] { "r", "h" }));
		mount(new MixedParamUrlCodingStrategy("/tags", TagsPage.class, new String[] { "r" }));
		mount(new MixedParamUrlCodingStrategy("/branches", BranchesPage.class, new String[] { "r" }));
		mount(new MixedParamUrlCodingStrategy("/commit", CommitPage.class, new String[] { "r", "h" }));
		mount(new MixedParamUrlCodingStrategy("/tag", TagPage.class, new String[] { "r", "h" }));
		mount(new MixedParamUrlCodingStrategy("/tree", TreePage.class, new String[] { "r", "h", "f" }));
		mount(new MixedParamUrlCodingStrategy("/blob", BlobPage.class, new String[] { "r", "h", "f" }));
		mount(new MixedParamUrlCodingStrategy("/raw", RawPage.class, new String[] { "r", "h", "f" }));
		mount(new MixedParamUrlCodingStrategy("/blobdiff", BlobDiffPage.class, new String[] { "r", "h", "f" }));
		mount(new MixedParamUrlCodingStrategy("/commitdiff", CommitDiffPage.class, new String[] { "r", "h" }));
		mount(new MixedParamUrlCodingStrategy("/patch", PatchPage.class, new String[] { "r", "h", "f" }));
		mount(new MixedParamUrlCodingStrategy("/history", HistoryPage.class, new String[] { "r", "h", "f" }));

		// setup ticket urls
		mount(new MixedParamUrlCodingStrategy("/tickets", TicketsPage.class, new String[] { "r" }));
		mount(new MixedParamUrlCodingStrategy("/ticket", TicketPage.class, new String[] { "r", "h", "f" }));

		// setup login/logout urls, if we are using authentication
		if (useAuthentication) {
			mount(new MixedParamUrlCodingStrategy("/login", LoginPage.class, new String[] {}));
			mount(new MixedParamUrlCodingStrategy("/logout", LogoutPage.class, new String[] {}));
		}
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
	protected final IRequestCycleProcessor newRequestCycleProcessor() {
		return new UrlCompressingWebRequestProcessor();
	}

	@Override
	public final String getConfigurationType() {
		if (GitBlit.self().isDebugMode())
			return Application.DEVELOPMENT;
		return Application.DEPLOYMENT;
	}

	public static GitBlitWebApp get() {
		return (GitBlitWebApp) WebApplication.get();
	}
}
