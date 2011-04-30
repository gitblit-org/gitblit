package com.gitblit.wicket.panels;

import org.apache.wicket.markup.html.basic.Label;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.CommitPage;

public class CommitHeaderPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	public CommitHeaderPanel(String id, String repositoryName, RevCommit c) {
		super(id);
		add(new LinkPanel("shortmessage", "title", c.getShortMessage(), CommitPage.class, WicketUtils.newObjectParameter(repositoryName, c.getName())));
		add(new Label("commitid", "(" + c.getName().substring(0, 8) + ")"));		
		add(new Label("author", c.getAuthorIdent().getName()));
		add(WicketUtils.createDateLabel("date", c.getAuthorIdent().getWhen(), getTimeZone()));
	}
}