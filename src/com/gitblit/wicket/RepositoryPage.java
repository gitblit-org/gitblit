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

import com.gitblit.StoredSettings;
import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.pages.RepositoriesPage;
import com.gitblit.wicket.panels.PageLinksPanel;
import com.gitblit.wicket.panels.RefsPanel;

public abstract class RepositoryPage extends BasePage {

	protected final String repositoryName;
	protected final String commitId;
	protected String description;

	private transient Repository r = null;

	public RepositoryPage(PageParameters params) {
		super(params);
		if (!params.containsKey("p")) {
			error("Repository not specified!");
			redirectToInterceptPage(new RepositoriesPage());
		}
		repositoryName = params.getString("p", "");
		commitId = params.getString("h", "");

		Repository r = getRepository();

		add(new PageLinksPanel("pageLinks", r, repositoryName, getPageName()));
		setStatelessHint(true);
	}

	protected Repository getRepository() {
		if (r == null) {
			ServletWebRequest servletWebRequest = (ServletWebRequest) getRequest();
			HttpServletRequest req = servletWebRequest.getHttpServletRequest();
			req.getServerName();

			Repository r = GitBlitWebApp.get().getRepository(req, repositoryName);
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
		add(new RefsPanel("refsPanel", r, c));
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

	protected void addFooter() {
		r.close();
		setupPage(repositoryName, "/ " + getPageName());
	}

	protected PageParameters newRepositoryParameter() {
		return new PageParameters("p=" + repositoryName);
	}

	protected PageParameters newCommitParameter() {
		return newCommitParameter(commitId);
	}

	protected PageParameters newCommitParameter(String commitId) {
		if (commitId == null || commitId.trim().length() == 0) {
			return newRepositoryParameter();
		}
		return new PageParameters("p=" + repositoryName + ",h=" + commitId);
	}

	protected PageParameters newPathParameter(String path) {
		if (path == null || path.trim().length() == 0) {
			return newCommitParameter();
		}
		return new PageParameters("p=" + repositoryName + ",h=" + commitId + ",f=" + path);
	}
}
