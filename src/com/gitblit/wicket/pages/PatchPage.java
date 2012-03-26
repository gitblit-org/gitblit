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

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.GitBlit;
import com.gitblit.utils.DiffUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;

public class PatchPage extends WebPage {

	public PatchPage(PageParameters params) {
		super(params);

		if (!params.containsKey("r")) {
			GitBlitWebSession.get().cacheErrorMessage(getString("gb.repositoryNotSpecified"));
			redirectToInterceptPage(new RepositoriesPage());
			return;
		}

		final String repositoryName = WicketUtils.getRepositoryName(params);
		final String baseObjectId = WicketUtils.getBaseObjectId(params);
		final String objectId = WicketUtils.getObject(params);
		final String blobPath = WicketUtils.getPath(params);

		Repository r = GitBlit.self().getRepository(repositoryName);
		if (r == null) {
			GitBlitWebSession.get().cacheErrorMessage(getString("gb.canNotLoadRepository") + " " + repositoryName);
			redirectToInterceptPage(new RepositoriesPage());
			return;
		}

		RevCommit commit = JGitUtils.getCommit(r, objectId);
		if (commit == null) {
			GitBlitWebSession.get().cacheErrorMessage(getString("gb.commitIsNull"));
			redirectToInterceptPage(new RepositoriesPage());
			return;
		}

		RevCommit baseCommit = null;
		if (!StringUtils.isEmpty(baseObjectId)) {
			baseCommit = JGitUtils.getCommit(r, baseObjectId);
		}
		String patch = DiffUtils.getCommitPatch(r, baseCommit, commit, blobPath);
		add(new Label("patchText", patch));
		r.close();
	}
}
