package com.gitblit.wicket;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.wicket.Application;
import org.apache.wicket.Page;
import org.apache.wicket.Request;
import org.apache.wicket.Response;
import org.apache.wicket.Session;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.protocol.http.request.urlcompressing.UrlCompressingWebRequestProcessor;
import org.apache.wicket.protocol.http.servlet.ServletWebRequest;
import org.apache.wicket.request.IRequestCycleProcessor;
import org.apache.wicket.request.target.coding.MixedParamUrlCodingStrategy;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.http.server.resolver.FileResolver;
import org.eclipse.jgit.http.server.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.GitBlitServer;
import com.gitblit.StoredSettings;
import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.models.RepositoryModel;
import com.gitblit.wicket.pages.BlobDiffPage;
import com.gitblit.wicket.pages.BlobPage;
import com.gitblit.wicket.pages.BranchesPage;
import com.gitblit.wicket.pages.CommitPage;
import com.gitblit.wicket.pages.CommitDiffPage;
import com.gitblit.wicket.pages.LogPage;
import com.gitblit.wicket.pages.PatchPage;
import com.gitblit.wicket.pages.RawPage;
import com.gitblit.wicket.pages.RepositoriesPage;
import com.gitblit.wicket.pages.SummaryPage;
import com.gitblit.wicket.pages.TagPage;
import com.gitblit.wicket.pages.TagsPage;
import com.gitblit.wicket.pages.TicGitPage;
import com.gitblit.wicket.pages.TicGitTicketPage;
import com.gitblit.wicket.pages.TreePage;


public class GitBlitWebApp extends WebApplication {

	public static int PAGING_ITEM_COUNT = 50;

	Logger logger = LoggerFactory.getLogger(GitBlitWebApp.class);

	FileResolver repositoryResolver;

	private File repositories;

	private boolean exportAll;

	@Override
	public void init() {
		super.init();

		// Grab Browser info (like timezone, etc)
		getRequestCycleSettings().setGatherExtendedBrowserInfo(true);

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
		
		// setup extended urls
		mount(new MixedParamUrlCodingStrategy("/ticgit", TicGitPage.class, new String[] { "p" }));
		mount(new MixedParamUrlCodingStrategy("/ticgittkt", TicGitTicketPage.class, new String[] { "p", "f" }));
		
		repositories = new File(StoredSettings.getString("repositoriesFolder", "repos"));
		exportAll = StoredSettings.getBoolean("exportAll", true);
		repositoryResolver = new FileResolver(repositories, exportAll);
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
		if (GitBlitServer.isDebugMode())
			return Application.DEVELOPMENT;
		return Application.DEPLOYMENT;
	}

	public List<String> getRepositoryList() {
		return JGitUtils.getRepositoryList(repositories, exportAll, StoredSettings.getBoolean("nestedRepositories", true));
	}

	public List<RepositoryModel> getRepositories(Request request) {
		List<String> list = getRepositoryList();
		ServletWebRequest servletWebRequest = (ServletWebRequest) request;
		HttpServletRequest req = servletWebRequest.getHttpServletRequest();

		List<RepositoryModel> repositories = new ArrayList<RepositoryModel>();
		for (String repo : list) {
			Repository r = getRepository(req, repo);
			String description = JGitUtils.getRepositoryDescription(r);
			String owner = JGitUtils.getRepositoryOwner(r);
			Date lastchange = JGitUtils.getLastChange(r);
			r.close();
			repositories.add(new RepositoryModel(repo, description, owner, lastchange));
		}
		return repositories;
	}

	public Repository getRepository(HttpServletRequest req, String repositoryName) {
		Repository r = null;
		try {
			r = repositoryResolver.open(req, repositoryName);
		} catch (RepositoryNotFoundException e) {
			r = null;
			logger.error("Failed to find repository " + repositoryName);
			e.printStackTrace();
		} catch (ServiceNotEnabledException e) {
			r = null;
			e.printStackTrace();
		}
		return r;
	}

	public String getCloneUrl(String repositoryName) {
		return StoredSettings.getString("cloneUrl", "https://localhost/git/") + repositoryName;
	}

	public static GitBlitWebApp get() {
		return (GitBlitWebApp) WebApplication.get();
	}
}
