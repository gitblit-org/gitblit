package com.gitblit.wicket.panels;

import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.Panel;

import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.models.PathModel;
import com.gitblit.wicket.pages.BlobPage;


public class TreeBlobLinksPanel extends Panel {

	private static final long serialVersionUID = 1L;

	public TreeBlobLinksPanel(String id, String repositoryName, PathModel path) {
		super(id);
		add(new BookmarkablePageLink<Void>("view", BlobPage.class, WicketUtils.newPathParameter(repositoryName, path.commitId, path.path)));
		add(new BookmarkablePageLink<Void>("raw", BlobPage.class).setEnabled(false));
		add(new BookmarkablePageLink<Void>("history", BlobPage.class).setEnabled(false));
	}
}