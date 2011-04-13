package com.gitblit.wicket.pages;

import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.utils.JGitUtils;
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
		
		String name = c.getName();
		for (RefModel tag:tags) {
			if (tag.getName().equals(objectId)) {
				name = tag.getDisplayName();
			}
		}

		add(new LinkPanel("commit", "title", name, CommitPage.class, newCommitParameter()));

		add(new LinkPanel("tagId", "list", c.getName(), CommitPage.class, newCommitParameter(c.getName())));
		add(new Label("tagAuthor", JGitUtils.getDisplayName(c.getAuthorIdent())));
		add(WicketUtils.createTimestampLabel("tagDate", c.getAuthorIdent().getWhen(), getTimeZone()));

		addFullText("fullMessage", c.getFullMessage(), true);
	}
	
	@Override
	protected String getPageName() {
		return getString("gb.tag");
	}
}
