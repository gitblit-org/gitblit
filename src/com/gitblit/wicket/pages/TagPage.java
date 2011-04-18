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
		List<RefModel> tags = JGitUtils.getTags(r, -1);
		RevCommit c = JGitUtils.getCommit(r, objectId);

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
			add(new LinkPanel("commit", "title", c.getShortMessage(), CommitPage.class, newCommitParameter()));
			add(new LinkPanel("tagId", "list", c.getName(), CommitPage.class, newCommitParameter(c.getName())));
		} else {
			// TODO commit or tree or blob?
			add(new LinkPanel("commit", "title", tagRef.getDisplayName(), CommitPage.class, newCommitParameter()));
			add(new LinkPanel("tagId", "list", c.getName(), CommitPage.class, newCommitParameter(c.getName())));
		}

		add(createPersonPanel("tagAuthor", c.getAuthorIdent(), SearchType.AUTHOR));
		add(WicketUtils.createTimestampLabel("tagDate", c.getAuthorIdent().getWhen(), getTimeZone()));

		addFullText("fullMessage", c.getFullMessage(), true);
	}

	@Override
	protected String getPageName() {
		return getString("gb.tag");
	}
}
