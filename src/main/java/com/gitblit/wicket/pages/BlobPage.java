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

	protected String fileExtension;

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
				switch (type) {
				case 2:
					// image blobs
					add(new Label("blobText").setVisible(false));
					add(new ExternalImage("blobImage", urlFor(RawPage.class, WicketUtils.newPathParameter(repositoryName, objectId, blobPath)).toString()));
					break;
				case 3:
					// binary blobs
					add(new Label("blobText", "Binary File"));
					add(new Image("blobImage").setVisible(false));
					break;
				default:
					// plain text
					String source = JGitUtils.getStringContent(r, commit.getTree(), blobPath, encodings);
					String table;
					if (source == null) {
						table = missingBlob(blobPath, commit);
					} else {
						table = generateSourceView(source, extension, type == 1);
					}
					add(new Label("blobText", table).setEscapeModelStrings(false));
					add(new Image("blobImage").setVisible(false));
					fileExtension = extension;
				}
			} else {
				// plain text
				String source = JGitUtils.getStringContent(r, commit.getTree(), blobPath, encodings);
				String table;
				if (source == null) {
					table = missingBlob(blobPath, commit);
				} else {
					table = generateSourceView(source, null, false);
				}
				add(new Label("blobText", table).setEscapeModelStrings(false));
				add(new Image("blobImage").setVisible(false));
			}
		}
	}
	
	protected String missingBlob(String blobPath, RevCommit commit) {
		StringBuilder sb = new StringBuilder();
		sb.append("<div class=\"alert alert-error\">");
		String pattern = getString("gb.doesNotExistInTree").replace("{0}", "<b>{0}</b>").replace("{1}", "<b>{1}</b>");
		sb.append(MessageFormat.format(pattern, blobPath, commit.getTree().getId().getName()));
		sb.append("</div>");
		return sb.toString();
	}

	protected String generateSourceView(String source, String extension, boolean prettyPrint) {
		String [] lines = source.split("\n");
		
		StringBuilder sb = new StringBuilder();
		sb.append("<!-- start blob table -->");
		sb.append("<table width=\"100%\"><tbody><tr>");
		
		// nums column
		sb.append("<!-- start nums column -->");
		sb.append("<td id=\"nums\">");
		sb.append("<pre>");
		String numPattern = "<span id=\"L{0}\" class=\"num\">{0}</span>\n";
		for (int i = 0; i < lines.length; i++) {
			sb.append(MessageFormat.format(numPattern, "" + (i + 1)));
		}
		sb.append("</pre>");
		sb.append("<!-- end nums column -->");
		sb.append("</td>");
		
		sb.append("<!-- start lines column -->");
		sb.append("<td id=\"lines\">");
		sb.append("<div class=\"sourceview\">");
		if (prettyPrint) {
			sb.append("<pre class=\"prettyprint lang-" + extension + "\">");
		} else {
			sb.append("<pre class=\"plainprint\">");
		}
		lines = StringUtils.escapeForHtml(source, true).split("\n");
		
		sb.append("<table width=\"100%\"><tbody>");
		
		String linePattern = "<tr class=\"{0}\"><td><a href=\"#L{2}\">{1}</a>\r</tr>";
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i].replace('\r', ' ');
			String cssClass = (i % 2 == 0) ? "even" : "odd";
			sb.append(MessageFormat.format(linePattern, cssClass, line, "" + (i + 1)));
		}
		sb.append("</tbody></table></pre>");
		sb.append("</pre>");
		sb.append("</div>");
		sb.append("</td>");
		sb.append("<!-- end lines column -->");
		
		sb.append("</tr></tbody></table>");
		sb.append("<!-- end blob table -->");
		
		return sb.toString();
	}

	@Override
	protected String getPageName() {
		return getString("gb.view");
	}
}
