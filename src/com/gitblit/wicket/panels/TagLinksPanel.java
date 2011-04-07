package com.gitblit.wicket.panels;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;

import com.gitblit.wicket.models.RefModel;
import com.gitblit.wicket.pages.CommitPage;
import com.gitblit.wicket.pages.ShortLogPage;


public class TagLinksPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	public TagLinksPanel(String id, String repositoryName, RefModel tag) {
		super(id);
		add(new BookmarkablePageLink<Void>("commit", CommitPage.class, new PageParameters("p=" + repositoryName + ",h=" + tag.getCommitId().getName())));
		add(new BookmarkablePageLink<Void>("shortlog", ShortLogPage.class, new PageParameters("p=" + repositoryName + ",h=" + tag.getName())));
	}
}