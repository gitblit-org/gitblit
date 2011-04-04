package com.gitblit.wicket.panels;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;

import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.pages.CommitPage;
import com.gitblit.wicket.pages.LogPage;
import com.gitblit.wicket.pages.ShortLogPage;
import com.gitblit.wicket.pages.SummaryPage;
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

		// log
		if (pageName.equals("log")) {
			add(new Label("log", pageName));
		} else {
			add(new LinkPanel("log", null, "log", LogPage.class, new PageParameters("p=" + repositoryName)));
		}

		// commit
		if (pageName.equals("commit")) {
			add(new Label("commit", pageName));
		} else {
			add(new LinkPanel("commit", null, "commit", CommitPage.class, new PageParameters("p=" + repositoryName + ",h=HEAD")));
		}
		// commitdiff
		if (pageName.equals("commitdiff")) {
			add(new Label("commitdiff", pageName));
		} else {
			add(new Label("commitdiff", "commitdiff"));
		}
		// tree
		if (pageName.equals("tree")) {
			add(new Label("tree", pageName));
		} else {
			add(new LinkPanel("tree", null, "tree", TreePage.class, new PageParameters("p=" + repositoryName + ",h=HEAD")));
		}		
	}
}