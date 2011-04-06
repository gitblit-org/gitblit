package com.gitblit.wicket.panels;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;

import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.models.PathModel;
import com.gitblit.wicket.pages.BlobPage;
import com.gitblit.wicket.pages.DiffPage;


public class PathLinksPanel extends Panel {

	private static final long serialVersionUID = 1L;

	public PathLinksPanel(String id, String repositoryName, PathModel path) {
		super(id);
		add(new LinkPanel("diff", null, "diff", DiffPage.class, new PageParameters("p=" + repositoryName + ",h=" + path.commitId + ",f=" + path.path)));
		add(new LinkPanel("blob", null, "view", BlobPage.class, new PageParameters("p=" + repositoryName + ",h=" + path.commitId + ",f=" + path.path)));
		add(new Label("history", "history"));
	}
}