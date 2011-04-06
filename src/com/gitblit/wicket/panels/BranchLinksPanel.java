package com.gitblit.wicket.panels;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.panel.Panel;

import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.models.RefModel;
import com.gitblit.wicket.pages.ShortLogPage;
import com.gitblit.wicket.pages.TreePage;


public class BranchLinksPanel extends Panel {

	private static final long serialVersionUID = 1L;

	public BranchLinksPanel(String id, String repositoryName, RefModel tag) {
		super(id);
		add(new LinkPanel("shortlog", null, "shortlog", ShortLogPage.class, new PageParameters("p=" + repositoryName + ",h=" + tag.getName())));
		add(new LinkPanel("tree", null, "tree", TreePage.class, new PageParameters("p=" + repositoryName + ",h=" + tag.getName())));
	}
}