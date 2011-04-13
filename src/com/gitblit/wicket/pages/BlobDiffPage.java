package com.gitblit.wicket.pages;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.PathBreadcrumbsPanel;

public class BlobDiffPage extends RepositoryPage {

	public BlobDiffPage(PageParameters params) {
		super(params);

		final String blobPath = WicketUtils.getPath(params);

		Repository r = getRepository();
		RevCommit commit = JGitUtils.getCommit(r, objectId);
		String diff = JGitUtils.getCommitDiff(r, commit, blobPath, true);
		add(new BookmarkablePageLink<Void>("patchLink", PatchPage.class, WicketUtils.newPathParameter(repositoryName, objectId, blobPath)));
		add(new BookmarkablePageLink<Void>("commitLink", CommitPage.class, WicketUtils.newObjectParameter(repositoryName, objectId)));
		add(new BookmarkablePageLink<Void>("commitDiffLink", CommitDiffPage.class, WicketUtils.newObjectParameter(repositoryName, objectId)));

		// diff page links
		add(new Label("blameLink", getString("gb.blame")));
		add(new Label("historyLink", getString("gb.history")));

		add(new LinkPanel("shortlog", "title", commit.getShortMessage(), CommitPage.class, newCommitParameter()));

		add(new PathBreadcrumbsPanel("breadcrumbs", repositoryName, blobPath, objectId));
		
		add(new Label("diffText", diff).setEscapeModelStrings(false));
	}
	
	@Override
	protected String getPageName() {
		return getString("gb.diff");
	}
}
