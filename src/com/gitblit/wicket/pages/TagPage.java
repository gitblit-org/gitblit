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
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.JGitUtils.SearchType;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.models.RefModel;

public class TagPage extends RepositoryPage {

	public TagPage(PageParameters params) {
		super(params);

		Repository r = getRepository();
		RevCommit c = getCommit();
		List<RefModel> tags = JGitUtils.getTags(r, -1);

		RefModel tagRef = null;
		// determine tag
		for (RefModel tag : tags) {
			if (tag.getName().equals(objectId) || tag.getObjectId().getName().equals(objectId)) {
				tagRef = tag;
				break;
			}
		}

		if (tagRef == null) {
			// point to commit
			add(new LinkPanel("commit", "title", c.getShortMessage(), CommitPage.class,
					newCommitParameter()));
			add(new LinkPanel("tagId", "list", c.getName(), CommitPage.class,
					newCommitParameter(c.getName())));
		} else {
			// TODO commit or tree or blob?
			add(new LinkPanel("commit", "title", tagRef.displayName, CommitPage.class,
					newCommitParameter()));
			add(new LinkPanel("tagId", "list", c.getName(), CommitPage.class,
					newCommitParameter(c.getName())));
		}

		add(createPersonPanel("tagAuthor", c.getAuthorIdent(), SearchType.AUTHOR));
		add(WicketUtils
				.createTimestampLabel("tagDate", c.getAuthorIdent().getWhen(), getTimeZone()));

		addFullText("fullMessage", c.getFullMessage(), true);
	}

	@Override
	protected String getPageName() {
		return getString("gb.tag");
	}
}
