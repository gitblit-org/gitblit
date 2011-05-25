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
import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.CommitHeaderPanel;
import com.gitblit.wicket.panels.PathBreadcrumbsPanel;

public class BlobPage extends RepositoryPage {

	public BlobPage(PageParameters params) {
		super(params);

		final String blobPath = WicketUtils.getPath(params);

		String extension = null;
		if (blobPath.lastIndexOf('.') > -1) {
			extension = blobPath.substring(blobPath.lastIndexOf('.') + 1).toLowerCase();
		}
		
		// see if we should redirect to the markdown page
		for (String ext : GitBlit.self().settings().getStrings(Keys.web.markdownExtensions)) {
			if (ext.equals(extension)) {
				setResponsePage(MarkdownPage.class, params);
				return;
			}
		}
		
		// standard blob view
		Repository r = getRepository();
		RevCommit commit = getCommit();

		// blob page links
		add(new Label("blameLink", getString("gb.blame")));
		add(new BookmarkablePageLink<Void>("historyLink", HistoryPage.class, WicketUtils.newPathParameter(repositoryName, objectId, blobPath)));
		add(new BookmarkablePageLink<Void>("rawLink", RawPage.class, WicketUtils.newPathParameter(repositoryName, objectId, blobPath)));
		add(new BookmarkablePageLink<Void>("headLink", BlobPage.class, WicketUtils.newPathParameter(repositoryName, Constants.HEAD, blobPath)));

		add(new CommitHeaderPanel("commitHeader", repositoryName, commit));

		add(new PathBreadcrumbsPanel("breadcrumbs", repositoryName, blobPath, objectId));

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
				WicketUtils.setCssClass(c, "prettyprint linenums");
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
