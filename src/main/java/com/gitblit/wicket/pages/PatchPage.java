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
import org.apache.wicket.markup.html.basic.Label;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.DiffUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.CacheControl;
import com.gitblit.wicket.CacheControl.LastModified;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;

@CacheControl(LastModified.BOOT)
public class PatchPage extends SessionPage {

	public PatchPage(final PageParameters params) {
		super(params);

		if (!params.containsKey("r")) {
			error(getString("gb.repositoryNotSpecified"));
			redirectToInterceptPage(new RepositoriesPage());
		}

		final String repositoryName = WicketUtils.getRepositoryName(params);
		final String baseObjectId = WicketUtils.getBaseObjectId(params);
		final String objectId = WicketUtils.getObject(params);
		final String blobPath = WicketUtils.getPath(params);

		GitBlitWebSession session = GitBlitWebSession.get();
		UserModel user = session.getUser();

		RepositoryModel model = app().repositories().getRepositoryModel(user, repositoryName);
		if (model == null) {
			// user does not have permission
			error(getString("gb.canNotLoadRepository") + " " + repositoryName);
			redirectToInterceptPage(new RepositoriesPage());
			return;
		}

		Repository r = app().repositories().getRepository(repositoryName);
		if (r == null) {
			error(getString("gb.canNotLoadRepository") + " " + repositoryName);
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
