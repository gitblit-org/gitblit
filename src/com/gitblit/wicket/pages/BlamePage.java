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

import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.models.AnnotatedLine;
import com.gitblit.utils.DiffUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.CommitHeaderPanel;
import com.gitblit.wicket.panels.LinkPanel;
import com.gitblit.wicket.panels.PathBreadcrumbsPanel;

public class BlamePage extends RepositoryPage {

	public BlamePage(PageParameters params) {
		super(params);

		final String blobPath = WicketUtils.getPath(params);

		RevCommit commit = getCommit();

		add(new BookmarkablePageLink<Void>("blobLink", BlobPage.class,
				WicketUtils.newPathParameter(repositoryName, objectId, blobPath)));
		add(new BookmarkablePageLink<Void>("commitLink", CommitPage.class,
				WicketUtils.newObjectParameter(repositoryName, objectId)));
		add(new BookmarkablePageLink<Void>("commitDiffLink", CommitDiffPage.class,
				WicketUtils.newObjectParameter(repositoryName, objectId)));

		// blame page links
		add(new BookmarkablePageLink<Void>("headLink", BlamePage.class,
				WicketUtils.newPathParameter(repositoryName, Constants.HEAD, blobPath)));
		add(new BookmarkablePageLink<Void>("historyLink", HistoryPage.class,
				WicketUtils.newPathParameter(repositoryName, objectId, blobPath)));

		add(new CommitHeaderPanel("commitHeader", repositoryName, commit));

		add(new PathBreadcrumbsPanel("breadcrumbs", repositoryName, blobPath, objectId));

		String format = GitBlit.getString(Keys.web.datetimestampLongFormat,
				"EEEE, MMMM d, yyyy HH:mm Z");
		final DateFormat df = new SimpleDateFormat(format);
		df.setTimeZone(getTimeZone());
		List<AnnotatedLine> lines = DiffUtils.blame(getRepository(), blobPath, objectId);
		ListDataProvider<AnnotatedLine> blameDp = new ListDataProvider<AnnotatedLine>(lines);
		DataView<AnnotatedLine> blameView = new DataView<AnnotatedLine>("annotation", blameDp) {
			private static final long serialVersionUID = 1L;
			private int count;
			private String lastCommitId = "";
			private boolean showInitials = true;

			public void populateItem(final Item<AnnotatedLine> item) {
				AnnotatedLine entry = item.getModelObject();
				item.add(new Label("line", "" + entry.lineNumber));
				item.add(new Label("data", StringUtils.escapeForHtml(entry.data, true))
						.setEscapeModelStrings(false));
				if (!lastCommitId.equals(entry.commitId)) {
					lastCommitId = entry.commitId;
					count++;
					// show the link for first line
					LinkPanel commitLink = new LinkPanel("commit", null,
							getShortObjectId(entry.commitId), CommitPage.class,
							newCommitParameter(entry.commitId));
					WicketUtils.setHtmlTooltip(commitLink,
							MessageFormat.format("{0}, {1}", entry.author, df.format(entry.when)));
					item.add(commitLink);
					showInitials = true;
				} else {
					if (showInitials) {
						showInitials = false;
						// show author initials
						item.add(new Label("commit", getInitials(entry.author)));
					} else {
						// hide the commit link until the next block
						item.add(new Label("commit").setVisible(false));
					}
				}
				if (count % 2 == 0) {
					WicketUtils.setCssClass(item, "even");
				} else {
					WicketUtils.setCssClass(item, "odd");
				}
			}
		};
		add(blameView);
	}

	private String getInitials(String author) {
		StringBuilder sb = new StringBuilder();
		String[] chunks = author.split(" ");
		for (String chunk : chunks) {
			sb.append(chunk.charAt(0));
		}
		return sb.toString().toUpperCase();
	}

	@Override
	protected String getPageName() {
		return getString("gb.blame");
	}
}
