package com.gitblit.wicket.panels;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;

import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.models.PathModel;
import com.gitblit.wicket.pages.BlobPage;


public class PathLinksPanel extends Panel {

	private static final long serialVersionUID = 1L;

	public PathLinksPanel(String id, String repositoryName, PathModel path) {
		super(id);
		add(new Label("diff", "diff"));
		add(new LinkPanel("blob", null, "view", BlobPage.class, new PageParameters("p=" + repositoryName + ",h=" + path.commitId + ",f=" + path.path)));
		add(new Label("history", "history"));
	}
}