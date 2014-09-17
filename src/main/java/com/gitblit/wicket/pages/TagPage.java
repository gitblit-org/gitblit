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
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.models.RefModel;
import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.CacheControl;
import com.gitblit.wicket.CacheControl.LastModified;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.GravatarImage;
import com.gitblit.wicket.panels.LinkPanel;
import com.gitblit.wicket.panels.RefsPanel;

@CacheControl(LastModified.BOOT)
public class TagPage extends RepositoryPage {

	public TagPage(PageParameters params) {
		super(params);

		Repository r = getRepository();

		// Find tag in repository
		List<RefModel> tags = JGitUtils.getTags(r, true, -1);
		RefModel tagRef = null;
		for (RefModel tag : tags) {
			if (tag.getName().equals(objectId) || tag.getObjectId().getName().equals(objectId)) {
				tagRef = tag;
				break;
			}
		}

		// Failed to find tag!
		if (tagRef == null) {
			error(MessageFormat.format(getString("gb.couldNotFindTag"), objectId), true);
		}

		// Display tag.
		Class<? extends WebPage> linkClass;
		PageParameters linkParameters = newCommitParameter(tagRef.getReferencedObjectId().getName());
		String typeKey;
		switch (tagRef.getReferencedObjectType()) {
		case Constants.OBJ_BLOB:
			typeKey = "gb.blob";
			linkClass = BlobPage.class;
			break;
		case Constants.OBJ_TREE:
			typeKey = "gb.tree";
			linkClass = TreePage.class;
			break;
		case Constants.OBJ_COMMIT:
		default:
			typeKey = "gb.commit";
			linkClass = CommitPage.class;
			break;
		}
		add(new GravatarImage("taggerAvatar", tagRef.getAuthorIdent()));

		add(new RefsPanel("tagName", repositoryName, Arrays.asList(tagRef)));
		add(new Label("tagId", tagRef.getObjectId().getName()));
		add(new LinkPanel("taggedObject", "list", tagRef.getReferencedObjectId().getName(),
				linkClass, linkParameters));
		add(new Label("taggedObjectType", getString(typeKey)));

		add(createPersonPanel("tagger", tagRef.getAuthorIdent(), com.gitblit.Constants.SearchType.AUTHOR));
		Date when = new Date(0);
		if (tagRef.getAuthorIdent() != null) {
			when = tagRef.getAuthorIdent().getWhen();
		}
		add(WicketUtils.createTimestampLabel("tagDate", when, getTimeZone(), getTimeUtils()));

		addFullText("fullMessage", tagRef.getFullMessage());
	}

	@Override
	protected String getPageName() {
		return getString("gb.tag");
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
