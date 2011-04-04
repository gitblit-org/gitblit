package com.gitblit.wicket.panels;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.panel.Panel;

import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.models.RefModel;
import com.gitblit.wicket.pages.CommitPage;
import com.gitblit.wicket.pages.LogPage;
import com.gitblit.wicket.pages.ShortLogPage;


public class TagLinksPanel extends Panel {

	private static final long serialVersionUID = 1L;

	public TagLinksPanel(String id, String repositoryName, RefModel tag) {
		super(id);
		add(new LinkPanel("commit", null, "commit", CommitPage.class, new PageParameters("p=" + repositoryName + ",h=" + tag.getCommitId().getName())));
		add(new LinkPanel("shortlog", null, "shortlog", ShortLogPage.class, new PageParameters("p=" + repositoryName + ",h=" + tag.getName())));
		add(new LinkPanel("log", null, "log", LogPage.class, new PageParameters("p=" + repositoryName + ",h=" + tag.getName())));
	}
}