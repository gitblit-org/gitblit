package com.gitblit.wicket.panels;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.panel.Panel;

import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.pages.CommitPage;
import com.gitblit.wicket.pages.DiffPage;
import com.gitblit.wicket.pages.TreePage;


public class ShortLogLinksPanel extends Panel {

	private static final long serialVersionUID = 1L;

	public ShortLogLinksPanel(String id, String repositoryName, String commitId) {
		super(id);

		add(new LinkPanel("commit", null, "commit", CommitPage.class, new PageParameters("p=" + repositoryName + ",h=" + commitId)));
		add(new LinkPanel("commitdiff", null, "commitdiff", DiffPage.class, new PageParameters("p=" + repositoryName + ",h=" + commitId)));
		add(new LinkPanel("tree", null, "tree", TreePage.class, new PageParameters("p=" + repositoryName + ",h=" + commitId)));
	}
}