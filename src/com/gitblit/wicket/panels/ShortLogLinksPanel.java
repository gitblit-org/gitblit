package com.gitblit.wicket.panels;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;

import com.gitblit.wicket.pages.CommitPage;
import com.gitblit.wicket.pages.DiffPage;
import com.gitblit.wicket.pages.TreePage;


public class ShortLogLinksPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	public ShortLogLinksPanel(String id, String repositoryName, String commitId) {
		super(id);

		add(new BookmarkablePageLink<Void>("commit", CommitPage.class, new PageParameters("p=" + repositoryName + ",h=" + commitId)));
		add(new BookmarkablePageLink<Void>("commitdiff", DiffPage.class, new PageParameters("p=" + repositoryName + ",h=" + commitId)));
		add(new BookmarkablePageLink<Void>("tree", TreePage.class, new PageParameters("p=" + repositoryName + ",h=" + commitId)));
	}
}