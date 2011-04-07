package com.gitblit.wicket.panels;

import org.apache.wicket.markup.html.link.BookmarkablePageLink;

import com.gitblit.wicket.pages.RepositoriesPage;

public class AdminLinksPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	public AdminLinksPanel(String id) {
		super(id);

		add(new BookmarkablePageLink<Void>("newRepository", RepositoriesPage.class));
		add(new BookmarkablePageLink<Void>("newUser", RepositoriesPage.class));
	}
}