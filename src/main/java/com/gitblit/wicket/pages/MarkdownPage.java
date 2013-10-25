/*
 * Copyright 2011 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.wicket.pages;

import java.text.MessageFormat;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.MarkdownUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.CacheControl;
import com.gitblit.wicket.CacheControl.LastModified;
import com.gitblit.wicket.WicketUtils;

@CacheControl(LastModified.BOOT)
public class MarkdownPage extends RepositoryPage {

	public MarkdownPage(PageParameters params) {
		super(params);

		final String path = WicketUtils.getPath(params).replace("%2f", "/").replace("%2F", "/");

		Repository r = getRepository();
		RevCommit commit = JGitUtils.getCommit(r, objectId);
		String [] encodings = GitBlit.getEncodings();
		List<String> extensions = GitBlit.getStrings(Keys.web.markdownExtensions);

		// Read raw markdown content and transform it to html
		String markdownPath = path;
		String markdownText = JGitUtils.getStringContent(r, commit.getTree(), path, encodings);
		if (StringUtils.isEmpty(markdownText)) {
			String name = path;
			if (path.indexOf('.') > -1) {
				name = path.substring(0, path.lastIndexOf('.'));
			}

			for (String ext : extensions) {
				String checkName = name + "." + ext;
				markdownText = JGitUtils.getStringContent(r, commit.getTree(), checkName, encodings);
				if (!StringUtils.isEmpty(markdownText)) {
					// found it
					markdownPath = path;
					break;
				}
			}
		}

		// markdown page links
		add(new BookmarkablePageLink<Void>("blameLink", BlamePage.class,
				WicketUtils.newPathParameter(repositoryName, objectId, markdownPath)));
		add(new BookmarkablePageLink<Void>("historyLink", HistoryPage.class,
				WicketUtils.newPathParameter(repositoryName, objectId, markdownPath)));
		add(new BookmarkablePageLink<Void>("rawLink", RawPage.class, WicketUtils.newPathParameter(
				repositoryName, objectId, markdownPath)));
		add(new BookmarkablePageLink<Void>("headLink", MarkdownPage.class,
				WicketUtils.newPathParameter(repositoryName, Constants.HEAD, markdownPath)));

		String htmlText;
		try {
			htmlText = MarkdownUtils.transformMarkdown(markdownText, getMarkdownLinkRenderer());
		} catch (Exception e) {
			logger.error("failed to transform markdown", e);
			if (markdownText == null) {
				markdownText = String.format("Markdown document <b>%1$s</b> not found in <em>%2$s</em>", markdownPath, repositoryName);
			}
			markdownText = MessageFormat.format("<div class=\"alert alert-error\"><strong>{0}:</strong> {1}</div>{2}", getString("gb.error"), getString("gb.markdownFailure"), markdownText);
			htmlText = StringUtils.breakLinesForHtml(markdownText);
		}

		// Add the html to the page
		add(new Label("markdownText", htmlText).setEscapeModelStrings(false));
	}

	@Override
	protected String getPageName() {
		return getString("gb.markdown");
	}

	@Override
	protected Class<? extends BasePage> getRepoNavPageClass() {
		return DocsPage.class;
	}
}
