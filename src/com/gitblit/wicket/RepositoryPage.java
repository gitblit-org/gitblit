package com.gitblit.wicket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.protocol.http.servlet.ServletWebRequest;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.GitBlit;
import com.gitblit.StoredSettings;
import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.pages.RepositoriesPage;
import com.gitblit.wicket.panels.PageLinksPanel;
import com.gitblit.wicket.panels.RefsPanel;

public abstract class RepositoryPage extends BasePage {

	protected final String repositoryName;
	protected final String objectId;
	protected String description;

	private transient Repository r = null;

	public RepositoryPage(PageParameters params) {
		super(params);
		if (!params.containsKey("r")) {
			error("Repository not specified!");
			redirectToInterceptPage(new RepositoriesPage());
		}
		repositoryName = WicketUtils.getRepositoryName(params);
		objectId = WicketUtils.getObject(params);

		Repository r = getRepository();

		// setup the page links and disable this page's link
		PageLinksPanel pageLinks = new PageLinksPanel("pageLinks", r, repositoryName, getPageName());
		add(pageLinks);
		pageLinks.disablePageLink(getPageName());

		setStatelessHint(true);
	}

	protected Repository getRepository() {
		if (r == null) {
			ServletWebRequest servletWebRequest = (ServletWebRequest) getRequest();
			HttpServletRequest req = servletWebRequest.getHttpServletRequest();
			req.getServerName();

			Repository r = GitBlit.self().getRepository(req, repositoryName);
			if (r == null) {
				error("Can not load repository " + repositoryName);
				redirectToInterceptPage(new RepositoriesPage());
				return null;
			}
			description = JGitUtils.getRepositoryDescription(r);
			this.r = r;
		}
		return r;
	}

	protected void addRefs(Repository r, RevCommit c) {
		add(new RefsPanel("refsPanel", repositoryName,  c, JGitUtils.getAllRefs(r)));
	}

	protected void addFullText(String wicketId, String text, boolean substituteRegex) {
		String html = WicketUtils.breakLines(text);
		if (substituteRegex) {
			Map<String, String> map = new HashMap<String, String>();
			// global regex keys
			for (String key : StoredSettings.getAllKeys("regex.global")) {
				String subKey = key.substring(key.lastIndexOf('.') + 1);
				map.put(subKey, StoredSettings.getString(key, ""));
			}

			// repository-specific regex keys
			List<String> keys = StoredSettings.getAllKeys("regex." + repositoryName.toLowerCase());
			for (String key : keys) {
				String subKey = key.substring(key.lastIndexOf('.') + 1);
				map.put(subKey, StoredSettings.getString(key, ""));
			}

			for (String key : map.keySet()) {
				String definition = map.get(key).trim();
				String[] chunks = definition.split("!!!");
				if (chunks.length == 2) {
					html = html.replaceAll(chunks[0], chunks[1]);
				} else {
					logger.warn(key + " improperly formatted.  Use !!! to separate match from replacement: " + definition);
				}
			}
		}
		add(new Label(wicketId, html).setEscapeModelStrings(false));
	}

	protected abstract String getPageName();

	@Override
	protected void onBeforeRender() {
		// dispose of repository object
		if (r != null) {
			r.close();
			r = null;
		}
		// setup page header and footer
		setupPage(repositoryName, "/ " + getPageName());
		super.onBeforeRender();
	}

	protected PageParameters newRepositoryParameter() {
		return WicketUtils.newRepositoryParameter(repositoryName);
	}

	protected PageParameters newCommitParameter() {
		return WicketUtils.newObjectParameter(repositoryName, objectId);
	}

	protected PageParameters newCommitParameter(String commitId) {
		return WicketUtils.newObjectParameter(repositoryName, commitId);
	}

	protected PageParameters newPathParameter(String path) {
		return WicketUtils.newPathParameter(repositoryName, objectId, path);
	}
}
