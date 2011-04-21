package com.gitblit.wicket.pages;

import java.io.StringReader;
import java.io.StringWriter;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tautua.markdownpapers.Markdown;
import org.tautua.markdownpapers.parser.ParseException;

import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.WicketUtils;

public class MarkdownPage extends RepositoryPage {
	
	private final Logger logger = LoggerFactory.getLogger(MarkdownPage.class);

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
		String htmlText = "";
		try {
			String rawText = JGitUtils.getRawContentAsString(r, commit, markdownPath);
			StringReader reader = new StringReader(rawText);
			StringWriter writer = new StringWriter();
			Markdown md = new Markdown();
			md.transform(reader, writer);
			htmlText = writer.toString();
		} catch (ParseException p) {
			logger.error("Failed to parse markdown text from " + markdownPath, p);
		}
		
		// Add the html to the page
		add(new Label("markdownText", htmlText).setEscapeModelStrings(false));
	}

	@Override
	protected String getPageName() {
		return getString("gb.markdown");
	}
}
