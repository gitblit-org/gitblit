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
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.models.PathModel.PathChangeModel;
import com.gitblit.models.SubmoduleModel;
import com.gitblit.utils.DiffUtils;
import com.gitblit.utils.DiffUtils.DiffOutputType;
import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.CommitHeaderPanel;
import com.gitblit.wicket.panels.CommitLegendPanel;
import com.gitblit.wicket.panels.LinkPanel;

public class CommitDiffPage extends RepositoryPage {

	public CommitDiffPage(PageParameters params) {
		super(params);

		Repository r = getRepository();
		RevCommit commit = getCommit();
		DiffOutputType diffType = DiffOutputType.forName(GitBlit.getString(Keys.web.diffStyle,
				DiffOutputType.GITBLIT.name()));
		String diff = DiffUtils.getCommitDiff(r, commit, diffType);

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

		// changed paths list
		List<PathChangeModel> paths = JGitUtils.getFilesInCommit(r, commit);
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
					
					item.add(new LinkPanel("pathName", "list", entry.path + " @ " +
							getShortObjectId(submoduleId), TreePage.class,
							WicketUtils
									.newPathParameter(submodulePath, submoduleId, "")).setEnabled(hasSubmodule));
				} else {
					// blob
					item.add(new LinkPanel("pathName", "list", entry.path, BlobPage.class,
							WicketUtils
									.newPathParameter(repositoryName, entry.commitId, entry.path)));
				}

				// quick links
				if (entry.isSubmodule()) {
					// submodule					
					item.add(new BookmarkablePageLink<Void>("patch", PatchPage.class, WicketUtils
							.newPathParameter(submodulePath, entry.objectId, entry.path)).setEnabled(false));
					item.add(new BookmarkablePageLink<Void>("view", CommitPage.class, WicketUtils
							.newObjectParameter(submodulePath, entry.objectId)).setEnabled(hasSubmodule));
					item.add(new BookmarkablePageLink<Void>("blame", BlamePage.class, WicketUtils
							.newPathParameter(submodulePath, entry.objectId, entry.path)).setEnabled(false));
					item.add(new BookmarkablePageLink<Void>("history", HistoryPage.class, WicketUtils
							.newPathParameter(submodulePath, entry.objectId, entry.path))
							.setEnabled(hasSubmodule));
				} else {
					// tree or blob
					item.add(new BookmarkablePageLink<Void>("patch", PatchPage.class, WicketUtils
							.newPathParameter(repositoryName, entry.commitId, entry.path)));
					item.add(new BookmarkablePageLink<Void>("view", BlobPage.class, WicketUtils
							.newPathParameter(repositoryName, entry.commitId, entry.path)));
					item.add(new BookmarkablePageLink<Void>("blame", BlamePage.class, WicketUtils
							.newPathParameter(repositoryName, entry.commitId, entry.path)));
					item.add(new BookmarkablePageLink<Void>("history", HistoryPage.class, WicketUtils
							.newPathParameter(repositoryName, entry.commitId, entry.path))
							.setEnabled(!entry.changeType.equals(ChangeType.ADD)));
				}
				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(pathsView);
		add(new Label("diffText", diff).setEscapeModelStrings(false));
	}

	@Override
	protected String getPageName() {
		return getString("gb.commitdiff");
	}
}
