package com.gitblit.wicket.pages;

import java.text.ParseException;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.WicketUtils;

public class MarkdownPage extends RepositoryPage {
	
	public MarkdownPage(PageParameters params) {
		super(params);

		final String markdownPath = WicketUtils.getPath(params);

		Repository r = getRepository();
		RevCommit commit = JGitUtils.getCommit(r, objectId);

		// markdown page links
		add(new Label("blameLink", getString("gb.blame")));
		add(new BookmarkablePageLink<Void>("historyLink", HistoryPage.class, WicketUtils.newPathParameter(repositoryName, objectId, markdownPath)));
		add(new BookmarkablePageLink<Void>("rawLink", RawPage.class, WicketUtils.newPathParameter(repositoryName, objectId, markdownPath)));
		add(new BookmarkablePageLink<Void>("headLink", MarkdownPage.class, WicketUtils.newPathParameter(repositoryName, Constants.HEAD, markdownPath)));

		// Read raw markdown content and transform it to html		
		String markdownText = JGitUtils.getRawContentAsString(r, commit, markdownPath);
		String htmlText;
		try {
			htmlText = StringUtils.transformMarkdown(markdownText);
		} catch (ParseException p) {
			error(p.getMessage());
			htmlText = markdownText;
		}
		
		// Add the html to the page
		add(new Label("markdownText", htmlText).setEscapeModelStrings(false));
	}

	@Override
	protected String getPageName() {
		return getString("gb.markdown");
	}
}
