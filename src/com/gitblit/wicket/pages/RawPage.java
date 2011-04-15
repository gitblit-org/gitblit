package com.gitblit.wicket.pages;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.protocol.http.servlet.ServletWebRequest;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.WicketUtils;

public class RawPage extends WebPage {

	public RawPage(PageParameters params) {
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

		Repository r = GitBlit.self().getRepository(req, repositoryName);
		if (r == null) {
			error("Can not load repository " + repositoryName);
			redirectToInterceptPage(new RepositoriesPage());
			return;
		}

		RevCommit commit = JGitUtils.getCommit(r, objectId);

		String extension = null;
		if (blobPath.lastIndexOf('.') > -1) {
			extension = blobPath.substring(blobPath.lastIndexOf('.') + 1);
		}

		// Map the extensions to types
		Map<String, Integer> map = new HashMap<String, Integer>();
		for (String ext : GitBlit.self().settings().getStrings(Keys.web.imageExtensions)) {
			map.put(ext.toLowerCase(), 2);
		}
		for (String ext : GitBlit.self().settings().getStrings(Keys.web.binaryExtensions)) {
			map.put(ext.toLowerCase(), 3);
		}

		if (extension != null) {
			int type = 0;
			if (map.containsKey(extension)) {
				type = map.get(extension);
			}
			Component c = null;
			switch (type) {
			case 2:
				// TODO image blobs
				c = new Label("rawText", "Image File");
				break;
			case 3:
				// TODO binary blobs
				c = new Label("rawText", "Binary File");
				break;
			default:
				// plain text
				c = new Label("rawText", JGitUtils.getRawContentAsString(r, commit, blobPath));
				WicketUtils.setCssClass(c, "plainprint");
			}
			add(c);
		} else {
			// plain text
			Label blobLabel = new Label("rawText", JGitUtils.getRawContentAsString(r, commit, blobPath));
			WicketUtils.setCssClass(blobLabel, "plainprint");
			add(blobLabel);
		}
		r.close();
	}
}
