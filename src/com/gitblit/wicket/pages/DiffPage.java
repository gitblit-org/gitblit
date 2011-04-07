package com.gitblit.wicket.pages;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.panels.PathBreadcrumbsPanel;

public class DiffPage extends RepositoryPage {

	public DiffPage(PageParameters params) {
		super(params);

		final String blobPath = params.getString("f", null);

		Repository r = getRepository();
		RevCommit commit = JGitUtils.getCommit(r, commitId);
		String diff;
		if (blobPath != null && blobPath.length() > 0) {
			// blob diff
			diff = JGitUtils.getCommitDiff(r, commit, blobPath, true);
		} else {
			// commit diff
			diff = JGitUtils.getCommitDiff(r, commit, true);
		}

		// diff page links
		add(new Label("historyLink", "history"));
		add(new Label("rawLink", "raw"));
		add(new Label("headLink", "HEAD"));

		add(new LinkPanel("shortlog", "title", commit.getShortMessage(), CommitPage.class, newCommitParameter()));

		add(new PathBreadcrumbsPanel("breadcrumbs", repositoryName, blobPath, commitId));

		add(new Label("diffText", diff).setEscapeModelStrings(false));

		// footer
		addFooter();
	}
	
	@Override
	protected String getPageName() {
		return "diff";
	}
}
