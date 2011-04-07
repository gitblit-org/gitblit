package com.gitblit.wicket.panels;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;

import com.gitblit.wicket.models.PathModel;
import com.gitblit.wicket.pages.TreePage;


public class TreeLinksPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	public TreeLinksPanel(String id, String repositoryName, PathModel path) {
		super(id);
		add(new BookmarkablePageLink<Void>("tree", TreePage.class, new PageParameters("p=" + repositoryName + ",h=" + path.commitId + ",f=" + path.path)));
		add(new BookmarkablePageLink<Void>("history", TreePage.class, new PageParameters()).setEnabled(false));
	}
}