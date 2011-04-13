package com.gitblit.wicket.pages;

import javax.servlet.http.HttpServletRequest;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.protocol.http.servlet.ServletWebRequest;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.GitBlitWebApp;
import com.gitblit.wicket.WicketUtils;


public class PatchPage extends WebPage {

	public PatchPage(PageParameters params) {
		super(params);

		if (!params.containsKey("r")) {
			error("Repository not specified!");
			redirectToInterceptPage(new RepositoriesPage());
		}
		final String repositoryName = WicketUtils.getRepositoryName(params);
		final String objectId = WicketUtils.getObject(params);		
		final String blobPath = WicketUtils.getPath(params);

		ServletWebRequest servletWebRequest = (ServletWebRequest) getRequest();
		HttpServletRequest req = servletWebRequest.getHttpServletRequest();
		req.getServerName();

		Repository r = GitBlitWebApp.get().getRepository(req, repositoryName);
		if (r == null) {
			error("Can not load repository " + repositoryName);
			redirectToInterceptPage(new RepositoriesPage());
			return;
		}

		RevCommit commit = JGitUtils.getCommit(r, objectId);
		String patch = JGitUtils.getCommitPatch(r, commit, blobPath);
		add(new Label("patchText", patch));
		r.close();
	}	
}
