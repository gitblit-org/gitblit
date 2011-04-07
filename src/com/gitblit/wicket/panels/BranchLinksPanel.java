package com.gitblit.wicket.panels;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;

import com.gitblit.wicket.models.RefModel;
import com.gitblit.wicket.pages.ShortLogPage;
import com.gitblit.wicket.pages.TreePage;

public class BranchLinksPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	public BranchLinksPanel(String id, String repositoryName, RefModel tag) {
		super(id);
		add(new BookmarkablePageLink<Void>("shortlog", ShortLogPage.class, new PageParameters("p=" + repositoryName + ",h=" + tag.getName())));
		add(new BookmarkablePageLink<Void>("tree", TreePage.class, new PageParameters("p=" + repositoryName + ",h=" + tag.getName())));
	}
}