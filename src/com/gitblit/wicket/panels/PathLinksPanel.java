package com.gitblit.wicket.panels;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;

import com.gitblit.wicket.models.PathModel;
import com.gitblit.wicket.pages.BlobPage;
import com.gitblit.wicket.pages.DiffPage;


public class PathLinksPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	public PathLinksPanel(String id, String repositoryName, PathModel path) {
		super(id);
		add(new BookmarkablePageLink<Void>("diff", DiffPage.class, new PageParameters("p=" + repositoryName + ",h=" + path.commitId + ",f=" + path.path)));
		add(new BookmarkablePageLink<Void>("view", BlobPage.class, new PageParameters("p=" + repositoryName + ",h=" + path.commitId + ",f=" + path.path)));
		add(new BookmarkablePageLink<Void>("history", BlobPage.class, new PageParameters()).setEnabled(false));
	}
}