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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.model.StringResourceModel;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.DownloadZipServlet;
import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.models.GitNote;
import com.gitblit.models.PathModel.PathChangeModel;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.JGitUtils.SearchType;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.CommitHeaderPanel;
import com.gitblit.wicket.panels.CommitLegendPanel;
import com.gitblit.wicket.panels.LinkPanel;
import com.gitblit.wicket.panels.RefsPanel;

public class CommitPage extends RepositoryPage {

	public CommitPage(PageParameters params) {
		super(params);

		Repository r = getRepository();
		RevCommit c = getCommit();

		List<String> parents = new ArrayList<String>();
		if (c.getParentCount() > 0) {
			for (RevCommit parent : c.getParents()) {
				parents.add(parent.name());
			}
		}

		// commit page links
		if (parents.size() == 0) {
			add(new Label("parentLink", "none"));
			add(new Label("commitdiffLink", getString("gb.commitdiff")));
		} else {
			add(new LinkPanel("parentLink", null, getShortObjectId(parents.get(0)),
					CommitPage.class, newCommitParameter(parents.get(0))));
			add(new LinkPanel("commitdiffLink", null, new StringResourceModel("gb.commitdiff",
					this, null), CommitDiffPage.class, WicketUtils.newObjectParameter(
					repositoryName, objectId)));
		}
		add(new BookmarkablePageLink<Void>("patchLink", PatchPage.class,
				WicketUtils.newObjectParameter(repositoryName, objectId)));

		add(new CommitHeaderPanel("commitHeader", repositoryName, c));

		addRefs(r, c);

		// author
		add(createPersonPanel("commitAuthor", c.getAuthorIdent(), SearchType.AUTHOR));
		add(WicketUtils.createTimestampLabel("commitAuthorDate", c.getAuthorIdent().getWhen(),
				getTimeZone()));

		// committer
		add(createPersonPanel("commitCommitter", c.getCommitterIdent(), SearchType.COMMITTER));
		add(WicketUtils.createTimestampLabel("commitCommitterDate",
				c.getCommitterIdent().getWhen(), getTimeZone()));

		add(new Label("commitId", c.getName()));

		add(new LinkPanel("commitTree", "list", c.getTree().getName(), TreePage.class,
				newCommitParameter()));
		add(new BookmarkablePageLink<Void>("treeLink", TreePage.class, newCommitParameter()));
		final String baseUrl = WicketUtils.getGitblitURL(getRequest());
		add(new ExternalLink("zipLink", DownloadZipServlet.asLink(baseUrl, repositoryName,
				objectId, null)).setVisible(GitBlit.getBoolean(Keys.web.allowZipDownloads, true)));

		// Parent Commits
		ListDataProvider<String> parentsDp = new ListDataProvider<String>(parents);
		DataView<String> parentsView = new DataView<String>("commitParents", parentsDp) {
			private static final long serialVersionUID = 1L;

			public void populateItem(final Item<String> item) {
				String entry = item.getModelObject();
				item.add(new LinkPanel("commitParent", "list", entry, CommitPage.class,
						newCommitParameter(entry)));
				item.add(new BookmarkablePageLink<Void>("view", CommitPage.class,
						newCommitParameter(entry)));
				item.add(new BookmarkablePageLink<Void>("diff", CommitDiffPage.class,
						newCommitParameter(entry)));
			}
		};
		add(parentsView);

		addFullText("fullMessage", c.getFullMessage(), true);

		// git notes
		List<GitNote> notes = JGitUtils.getNotesOnCommit(r, c);
		ListDataProvider<GitNote> notesDp = new ListDataProvider<GitNote>(notes);
		DataView<GitNote> notesView = new DataView<GitNote>("notes", notesDp) {
			private static final long serialVersionUID = 1L;

			public void populateItem(final Item<GitNote> item) {
				GitNote entry = item.getModelObject();
				item.add(new RefsPanel("refName", repositoryName, Arrays.asList(entry.notesRef)));
				item.add(createPersonPanel("authorName", entry.notesRef.getAuthorIdent(),
						SearchType.AUTHOR));
				item.add(WicketUtils.createTimestampLabel("authorDate", entry.notesRef
						.getAuthorIdent().getWhen(), getTimeZone()));
				item.add(new Label("noteContent", GitBlit.self().processCommitMessage(
						repositoryName, entry.content)).setEscapeModelStrings(false));
			}
		};
		add(notesView.setVisible(notes.size() > 0));

		// changed paths list
		List<PathChangeModel> paths = JGitUtils.getFilesInCommit(r, c);
		add(new CommitLegendPanel("commitLegend", paths));
		ListDataProvider<PathChangeModel> pathsDp = new ListDataProvider<PathChangeModel>(paths);
		DataView<PathChangeModel> pathsView = new DataView<PathChangeModel>("changedPath", pathsDp) {
			private static final long serialVersionUID = 1L;
			int counter;

			public void populateItem(final Item<PathChangeModel> item) {
				final PathChangeModel entry = item.getModelObject();
				Label changeType = new Label("changeType", "");
				WicketUtils.setChangeTypeCssClass(changeType, entry.changeType);
				setChangeTypeTooltip(changeType, entry.changeType);
				item.add(changeType);
				if (entry.isTree()) {
					item.add(new LinkPanel("pathName", null, entry.path, TreePage.class,
							WicketUtils
									.newPathParameter(repositoryName, entry.commitId, entry.path)));
				} else {
					item.add(new LinkPanel("pathName", "list", entry.path, BlobPage.class,
							WicketUtils
									.newPathParameter(repositoryName, entry.commitId, entry.path)));
				}

				item.add(new BookmarkablePageLink<Void>("diff", BlobDiffPage.class, WicketUtils
						.newPathParameter(repositoryName, entry.commitId, entry.path))
						.setEnabled(!entry.changeType.equals(ChangeType.ADD)
								&& !entry.changeType.equals(ChangeType.DELETE)));
				item.add(new BookmarkablePageLink<Void>("view", BlobPage.class, WicketUtils
						.newPathParameter(repositoryName, entry.commitId, entry.path)));
				item.add(new BookmarkablePageLink<Void>("blame", BlamePage.class, WicketUtils
						.newPathParameter(repositoryName, entry.commitId, entry.path)));
				item.add(new BookmarkablePageLink<Void>("history", HistoryPage.class, WicketUtils
						.newPathParameter(repositoryName, entry.commitId, entry.path))
						.setEnabled(!entry.changeType.equals(ChangeType.ADD)));

				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(pathsView);
	}

	@Override
	protected String getPageName() {
		return getString("gb.commit");
	}
}
