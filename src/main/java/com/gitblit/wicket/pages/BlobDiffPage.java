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

import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.Keys;
import com.gitblit.utils.DiffUtils;
import com.gitblit.utils.DiffUtils.DiffComparator;
import com.gitblit.utils.DiffUtils.DiffOutputType;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.CacheControl;
import com.gitblit.wicket.CacheControl.LastModified;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.CommitHeaderPanel;
import com.gitblit.wicket.panels.LinkPanel;
import com.gitblit.wicket.panels.PathBreadcrumbsPanel;

@CacheControl(LastModified.BOOT)
public class BlobDiffPage extends RepositoryPage {

	public BlobDiffPage(PageParameters params) {
		super(params);

		final String blobPath = WicketUtils.getPath(params);
		final String baseObjectId = WicketUtils.getBaseObjectId(params);
		final DiffComparator diffComparator = WicketUtils.getDiffComparator(params);

		Repository r = getRepository();
		RevCommit commit = getCommit();

		final List<String> imageExtensions = app().settings().getStrings(Keys.web.imageExtensions);

		String diff;
		if (StringUtils.isEmpty(baseObjectId)) {
			// use first parent
			RevCommit parent = commit.getParentCount() == 0 ? null : commit.getParent(0);
			ImageDiffHandler handler = new ImageDiffHandler(this, repositoryName,
					parent.getName(), commit.getName(), imageExtensions);
			diff = DiffUtils.getDiff(r, commit, blobPath, diffComparator, DiffOutputType.HTML, handler, 3).content;
			if (handler.getImgDiffCount() > 0) {
				addBottomScript("scripts/imgdiff.js"); // Tiny support script for image diffs
			}
			add(new BookmarkablePageLink<Void>("patchLink", PatchPage.class,
					WicketUtils.newPathParameter(repositoryName, objectId, blobPath)));
		} else {
			// base commit specified
			RevCommit baseCommit = JGitUtils.getCommit(r, baseObjectId);
			ImageDiffHandler handler = new ImageDiffHandler(this, repositoryName,
					baseCommit.getName(), commit.getName(), imageExtensions);
			diff = DiffUtils.getDiff(r, baseCommit, commit, blobPath, diffComparator, DiffOutputType.HTML, handler, 3).content;
			if (handler.getImgDiffCount() > 0) {
				addBottomScript("scripts/imgdiff.js"); // Tiny support script for image diffs
			}
			add(new BookmarkablePageLink<Void>("patchLink", PatchPage.class,
					WicketUtils.newBlobDiffParameter(repositoryName, baseObjectId, objectId,
							blobPath)));
		}

		add(new BookmarkablePageLink<Void>("commitLink", CommitPage.class,
				WicketUtils.newObjectParameter(repositoryName, objectId)));
		add(new BookmarkablePageLink<Void>("commitDiffLink", CommitDiffPage.class,
				WicketUtils.newObjectParameter(repositoryName, objectId)));
		add(new LinkPanel("whitespaceLink", null, getString(diffComparator.getOpposite().getTranslationKey()),
				BlobDiffPage.class, WicketUtils.newDiffParameter(repositoryName, objectId, diffComparator.getOpposite(), blobPath)));

		// diff page links
		add(new BookmarkablePageLink<Void>("blameLink", BlamePage.class,
				WicketUtils.newPathParameter(repositoryName, objectId, blobPath)));
		add(new BookmarkablePageLink<Void>("historyLink", HistoryPage.class,
				WicketUtils.newPathParameter(repositoryName, objectId, blobPath)));

		add(new CommitHeaderPanel("commitHeader", repositoryName, commit));

		add(new PathBreadcrumbsPanel("breadcrumbs", repositoryName, blobPath, objectId));

		add(new Label("diffText", diff).setEscapeModelStrings(false));
	}

	@Override
	protected String getPageName() {
		return getString("gb.diff");
	}

	@Override
	protected boolean isCommitPage() {
		return true;
	}

	@Override
	protected Class<? extends BasePage> getRepoNavPageClass() {
		return TreePage.class;
	}
}
