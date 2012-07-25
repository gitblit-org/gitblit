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
import org.apache.wicket.markup.html.image.Image;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.ExternalImage;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.CommitHeaderPanel;
import com.gitblit.wicket.panels.PathBreadcrumbsPanel;

public class BlobPage extends RepositoryPage {

	public BlobPage(PageParameters params) {
		super(params);

		Repository r = getRepository();
		final String blobPath = WicketUtils.getPath(params);
		String [] encodings = GitBlit.getEncodings();
		
		if (StringUtils.isEmpty(blobPath)) {
			// blob by objectid

			add(new BookmarkablePageLink<Void>("blameLink", BlamePage.class,
					WicketUtils.newPathParameter(repositoryName, objectId, blobPath))
					.setEnabled(false));
			add(new BookmarkablePageLink<Void>("historyLink", HistoryPage.class).setEnabled(false));
			add(new BookmarkablePageLink<Void>("rawLink", RawPage.class,
					WicketUtils.newPathParameter(repositoryName, objectId, blobPath)));
			add(new BookmarkablePageLink<Void>("headLink", BlobPage.class).setEnabled(false));
			add(new CommitHeaderPanel("commitHeader", objectId));
			add(new PathBreadcrumbsPanel("breadcrumbs", repositoryName, blobPath, objectId));
			Component c = new Label("blobText", JGitUtils.getStringContent(r, objectId, encodings));
			WicketUtils.setCssClass(c, "plainprint");
			add(c);
		} else {
			// standard blob view
			String extension = null;
			if (blobPath.lastIndexOf('.') > -1) {
				extension = blobPath.substring(blobPath.lastIndexOf('.') + 1).toLowerCase();
			}

			// see if we should redirect to the markdown page
			for (String ext : GitBlit.getStrings(Keys.web.markdownExtensions)) {
				if (ext.equals(extension)) {
					setResponsePage(MarkdownPage.class, params);
					return;
				}
			}

			// manually get commit because it can be null
			RevCommit commit = JGitUtils.getCommit(r, objectId);

			// blob page links
			add(new BookmarkablePageLink<Void>("blameLink", BlamePage.class,
					WicketUtils.newPathParameter(repositoryName, objectId, blobPath)));
			add(new BookmarkablePageLink<Void>("historyLink", HistoryPage.class,
					WicketUtils.newPathParameter(repositoryName, objectId, blobPath)));
			add(new BookmarkablePageLink<Void>("rawLink", RawPage.class,
					WicketUtils.newPathParameter(repositoryName, objectId, blobPath)));
			add(new BookmarkablePageLink<Void>("headLink", BlobPage.class,
					WicketUtils.newPathParameter(repositoryName, Constants.HEAD, blobPath)));

			add(new CommitHeaderPanel("commitHeader", repositoryName, commit));

			add(new PathBreadcrumbsPanel("breadcrumbs", repositoryName, blobPath, objectId));

			// Map the extensions to types
			Map<String, Integer> map = new HashMap<String, Integer>();
			for (String ext : GitBlit.getStrings(Keys.web.prettyPrintExtensions)) {
				map.put(ext.toLowerCase(), 1);
			}
			for (String ext : GitBlit.getStrings(Keys.web.imageExtensions)) {
				map.put(ext.toLowerCase(), 2);
			}
			for (String ext : GitBlit.getStrings(Keys.web.binaryExtensions)) {
				map.put(ext.toLowerCase(), 3);
			}

			if (extension != null) {
				int type = 0;
				if (map.containsKey(extension)) {
					type = map.get(extension);
				}
				Component c = null;
				Component i = null;
				switch (type) {
				case 1:
					// PrettyPrint blob text
					c = new Label("blobText", JGitUtils.getStringContent(r, commit.getTree(),
							blobPath, encodings));
					WicketUtils.setCssClass(c, "prettyprint linenums");
					i = new Image("blobImage").setVisible(false);
					break;
				case 2:
					// image blobs
					c = new Label("blobText").setVisible(false);
					i = new ExternalImage("blobImage", urlFor(RawPage.class, WicketUtils.newPathParameter(repositoryName, objectId, blobPath)).toString());
					break;
				case 3:
					// binary blobs
					c = new Label("blobText", "Binary File");
					i = new Image("blobImage").setVisible(false);
					break;
				default:
					// plain text
					c = new Label("blobText", JGitUtils.getStringContent(r, commit.getTree(),
							blobPath, encodings));
					WicketUtils.setCssClass(c, "plainprint");
					i = new Image("blobImage").setVisible(false);
				}
				add(c);
				add(i);
			} else {
				// plain text
				Label blobLabel = new Label("blobText", JGitUtils.getStringContent(r,
						commit.getTree(), blobPath, encodings));
				WicketUtils.setCssClass(blobLabel, "plainprint");
				add(blobLabel);
				add(new Image("blobImage").setVisible(false));
			}
		}
	}

	@Override
	protected String getPageName() {
		return getString("gb.view");
	}
}
