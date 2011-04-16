package com.gitblit.wicket.pages;

import java.util.HashMap;
import java.util.Map;

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.PathBreadcrumbsPanel;

public class BlobPage extends RepositoryPage {

	public BlobPage(PageParameters params) {
		super(params);

		final String blobPath = WicketUtils.getPath(params);

		Repository r = getRepository();
		RevCommit commit = JGitUtils.getCommit(r, objectId);

		// blob page links
		add(new Label("blameLink", getString("gb.blame")));
		add(new BookmarkablePageLink<Void>("historyLink", HistoryPage.class, WicketUtils.newPathParameter(repositoryName, objectId, blobPath)));
		add(new BookmarkablePageLink<Void>("rawLink", RawPage.class, WicketUtils.newPathParameter(repositoryName, objectId, blobPath)));
		add(new BookmarkablePageLink<Void>("headLink", BlobPage.class, WicketUtils.newPathParameter(repositoryName, Constants.HEAD, blobPath)));

		add(new LinkPanel("shortlog", "title", commit.getShortMessage(), CommitPage.class, newCommitParameter()));

		add(new PathBreadcrumbsPanel("breadcrumbs", repositoryName, blobPath, objectId));
		String extension = null;
		if (blobPath.lastIndexOf('.') > -1) {
			extension = blobPath.substring(blobPath.lastIndexOf('.') + 1);
		}

		// Map the extensions to types
		Map<String, Integer> map = new HashMap<String, Integer>();
		for (String ext : GitBlit.self().settings().getStrings(Keys.web.prettyPrintExtensions)) {
			map.put(ext.toLowerCase(), 1);
		}
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
			case 1:
				// PrettyPrint blob text
				c = new Label("blobText", JGitUtils.getRawContentAsString(r, commit, blobPath));
				WicketUtils.setCssClass(c, "prettyprint");
				break;
			case 2:
				// TODO image blobs
				c = new Label("blobText", "Image File");
				break;
			case 3:
				// TODO binary blobs
				c = new Label("blobText", "Binary File");
				break;
			default:
				// plain text
				c = new Label("blobText", JGitUtils.getRawContentAsString(r, commit, blobPath));
				WicketUtils.setCssClass(c, "plainprint");
			}
			add(c);
		} else {
			// plain text
			Label blobLabel = new Label("blobText", JGitUtils.getRawContentAsString(r, commit, blobPath));
			WicketUtils.setCssClass(blobLabel, "plainprint");
			add(blobLabel);
		}
	}

	@Override
	protected String getPageName() {
		return getString("gb.view");
	}
}
