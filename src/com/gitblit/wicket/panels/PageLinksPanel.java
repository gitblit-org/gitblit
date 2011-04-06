package com.gitblit.wicket.panels;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;

import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.pages.BranchesPage;
import com.gitblit.wicket.pages.CommitPage;
import com.gitblit.wicket.pages.ShortLogPage;
import com.gitblit.wicket.pages.SummaryPage;
import com.gitblit.wicket.pages.TagsPage;
import com.gitblit.wicket.pages.TreePage;


public class PageLinksPanel extends Panel {

	private static final long serialVersionUID = 1L;

	public PageLinksPanel(String id, String repositoryName, String pageName) {
		super(id);
		// summary
		if (pageName.equals("summary")) {
			add(new Label("summary", pageName));
		} else {
			add(new LinkPanel("summary", null, "summary", SummaryPage.class, new PageParameters("p=" + repositoryName)));
		}

		// shortlog
		if (pageName.equals("shortlog")) {
			add(new Label("shortlog", pageName));
		} else {
			add(new LinkPanel("shortlog", null, "shortlog", ShortLogPage.class, new PageParameters("p=" + repositoryName)));
		}
		
		// branches
		if (pageName.equals("branches")) {
			add(new Label("branches", pageName));
		} else {
			add(new LinkPanel("branches", null, "branches", BranchesPage.class, new PageParameters("p=" + repositoryName)));
		}
		
		// tags
		if (pageName.equals("tags")) {
			add(new Label("tags", pageName));
		} else {
			add(new LinkPanel("tags", null, "tags", TagsPage.class, new PageParameters("p=" + repositoryName)));
		}
		
		// tree
		if (pageName.equals("tree")) {
			add(new Label("tree", pageName));
		} else {
			add(new LinkPanel("tree", null, "tree", TreePage.class, new PageParameters("p=" + repositoryName + ",h=HEAD")));
		}		
	}
}