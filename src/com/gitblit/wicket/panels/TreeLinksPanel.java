package com.gitblit.wicket.panels;

import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.Panel;

import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.models.PathModel;
import com.gitblit.wicket.pages.TreePage;


public class TreeLinksPanel extends Panel {

	private static final long serialVersionUID = 1L;

	public TreeLinksPanel(String id, String repositoryName, PathModel path) {
		super(id);
		add(new BookmarkablePageLink<Void>("tree", TreePage.class, WicketUtils.newPathParameter(repositoryName, path.commitId, path.path)));
		add(new BookmarkablePageLink<Void>("history", TreePage.class).setEnabled(false));
	}
}