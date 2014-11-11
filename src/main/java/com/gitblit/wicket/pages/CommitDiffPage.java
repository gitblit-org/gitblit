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
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.Constants;
import com.gitblit.models.GitNote;
import com.gitblit.models.PathModel.PathChangeModel;
import com.gitblit.models.SubmoduleModel;
import com.gitblit.servlet.RawServlet;
import com.gitblit.utils.DiffUtils;
import com.gitblit.utils.DiffUtils.DiffOutput;
import com.gitblit.utils.DiffUtils.DiffOutputType;
import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.CacheControl;
import com.gitblit.wicket.CacheControl.LastModified;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.CommitHeaderPanel;
import com.gitblit.wicket.panels.CommitLegendPanel;
import com.gitblit.wicket.panels.DiffStatPanel;
import com.gitblit.wicket.panels.GravatarImage;
import com.gitblit.wicket.panels.LinkPanel;
import com.gitblit.wicket.panels.RefsPanel;

@CacheControl(LastModified.BOOT)
public class CommitDiffPage extends RepositoryPage {

	public CommitDiffPage(PageParameters params) {
		super(params);

		Repository r = getRepository();

		RevCommit commit = getCommit();

		final DiffOutput diff = DiffUtils.getCommitDiff(r, commit, DiffOutputType.HTML);

		List<String> parents = new ArrayList<String>();
		if (commit.getParentCount() > 0) {
			for (RevCommit parent : commit.getParents()) {
				parents.add(parent.name());
			}
		}

		// commit page links
		if (parents.size() == 0) {
			add(new Label("parentLink", getString("gb.none")));
		} else {
			add(new LinkPanel("parentLink", null, parents.get(0).substring(0, 8),
					CommitDiffPage.class, newCommitParameter(parents.get(0))));
		}
		add(new BookmarkablePageLink<Void>("patchLink", PatchPage.class,
				WicketUtils.newObjectParameter(repositoryName, objectId)));
		add(new BookmarkablePageLink<Void>("commitLink", CommitPage.class,
				WicketUtils.newObjectParameter(repositoryName, objectId)));

		add(new CommitHeaderPanel("commitHeader", repositoryName, commit));

		// add commit diffstat
		int insertions = 0;
		int deletions = 0;
		for (PathChangeModel pcm : diff.stat.paths) {
			insertions += pcm.insertions;
			deletions += pcm.deletions;
		}
		add(new DiffStatPanel("diffStat", insertions, deletions));

		addFullText("fullMessage", commit.getFullMessage());

		// git notes
		List<GitNote> notes = JGitUtils.getNotesOnCommit(r, commit);
		ListDataProvider<GitNote> notesDp = new ListDataProvider<GitNote>(notes);
		DataView<GitNote> notesView = new DataView<GitNote>("notes", notesDp) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(final Item<GitNote> item) {
				GitNote entry = item.getModelObject();
				item.add(new RefsPanel("refName", repositoryName, Arrays.asList(entry.notesRef)));
				item.add(createPersonPanel("authorName", entry.notesRef.getAuthorIdent(),
						Constants.SearchType.AUTHOR));
				item.add(new GravatarImage("noteAuthorAvatar", entry.notesRef.getAuthorIdent()));
				item.add(WicketUtils.createTimestampLabel("authorDate", entry.notesRef
						.getAuthorIdent().getWhen(), getTimeZone(), getTimeUtils()));
				item.add(new Label("noteContent", bugtraqProcessor().processPlainCommitMessage(getRepository(), repositoryName,
						entry.content)).setEscapeModelStrings(false));
			}
		};
		add(notesView.setVisible(notes.size() > 0));

		// changed paths list
		add(new CommitLegendPanel("commitLegend", diff.stat.paths));
		ListDataProvider<PathChangeModel> pathsDp = new ListDataProvider<PathChangeModel>(diff.stat.paths);
		DataView<PathChangeModel> pathsView = new DataView<PathChangeModel>("changedPath", pathsDp) {
			private static final long serialVersionUID = 1L;
			int counter;

			@Override
			public void populateItem(final Item<PathChangeModel> item) {
				final PathChangeModel entry = item.getModelObject();
				Label changeType = new Label("changeType", "");
				WicketUtils.setChangeTypeCssClass(changeType, entry.changeType);
				setChangeTypeTooltip(changeType, entry.changeType);
				item.add(changeType);
				item.add(new DiffStatPanel("diffStat", entry.insertions, entry.deletions, true));

				boolean hasSubmodule = false;
				String submodulePath = null;
				if (entry.isTree()) {
					// tree
					item.add(new LinkPanel("pathName", null, entry.path, TreePage.class,
							WicketUtils
									.newPathParameter(repositoryName, entry.commitId, entry.path)));
				} else if (entry.isSubmodule()) {
					// submodule
					String submoduleId = entry.objectId;
					SubmoduleModel submodule = getSubmodule(entry.path);
					submodulePath = submodule.gitblitPath;
					hasSubmodule = submodule.hasSubmodule;

					// add relative link
					item.add(new LinkPanel("pathName", "list", entry.path + " @ " + getShortObjectId(submoduleId), "#n" + entry.objectId));
				} else {
					// add relative link
					item.add(new LinkPanel("pathName", "list", entry.path, "#n" + entry.objectId));
				}

				// quick links
				if (entry.isSubmodule()) {
					item.add(new ExternalLink("raw", "").setEnabled(false));
					// submodule
					item.add(new ExternalLink("patch", "").setEnabled(false));
					item.add(new BookmarkablePageLink<Void>("view", CommitPage.class, WicketUtils
							.newObjectParameter(submodulePath, entry.objectId)).setEnabled(hasSubmodule));
					item.add(new ExternalLink("blame", "").setEnabled(false));
					item.add(new BookmarkablePageLink<Void>("history", HistoryPage.class, WicketUtils
							.newPathParameter(repositoryName, entry.commitId, entry.path))
							.setEnabled(!entry.changeType.equals(ChangeType.ADD)));
				} else {
					// tree or blob
					item.add(new BookmarkablePageLink<Void>("patch", PatchPage.class, WicketUtils
							.newPathParameter(repositoryName, entry.commitId, entry.path))
							.setEnabled(!entry.changeType.equals(ChangeType.ADD)
									&& !entry.changeType.equals(ChangeType.DELETE)));
					item.add(new BookmarkablePageLink<Void>("view", BlobPage.class, WicketUtils
							.newPathParameter(repositoryName, entry.commitId, entry.path))
							.setEnabled(!entry.changeType.equals(ChangeType.DELETE)));
					String rawUrl = RawServlet.asLink(getContextUrl(), repositoryName, entry.commitId, entry.path);
					item.add(new ExternalLink("raw", rawUrl)
							.setEnabled(!entry.changeType.equals(ChangeType.DELETE)));
					item.add(new BookmarkablePageLink<Void>("blame", BlamePage.class, WicketUtils
							.newPathParameter(repositoryName, entry.commitId, entry.path))
							.setEnabled(!entry.changeType.equals(ChangeType.ADD)
									&& !entry.changeType.equals(ChangeType.DELETE)));
					item.add(new BookmarkablePageLink<Void>("history", HistoryPage.class, WicketUtils
							.newPathParameter(repositoryName, entry.commitId, entry.path))
							.setEnabled(!entry.changeType.equals(ChangeType.ADD)));
				}

				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(pathsView);
		add(new Label("diffText", diff.content).setEscapeModelStrings(false));
	}

	@Override
	protected String getPageName() {
		return getString("gb.commitdiff");
	}

	@Override
	protected boolean isCommitPage() {
		return true;
	}

	@Override
	protected Class<? extends BasePage> getRepoNavPageClass() {
		return LogPage.class;
	}
}
