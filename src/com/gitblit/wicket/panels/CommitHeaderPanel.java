package com.gitblit.wicket.panels;

import java.util.Date;

import org.apache.wicket.markup.html.basic.Label;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.CommitPage;

public class CommitHeaderPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	public CommitHeaderPanel(String id, String repositoryName, RevCommit c) {
		super(id);
		add(new LinkPanel("shortmessage", "title", c == null ? "" : c.getShortMessage(), CommitPage.class, WicketUtils.newObjectParameter(repositoryName, c == null ? "" : c.getName())));
		add(new Label("commitid", "(" + c.getName().substring(0, 8) + ")"));		
		add(new Label("author", c == null ? "" : c.getAuthorIdent().getName()));
		add(WicketUtils.createDateLabel("date", c == null ? new Date(0) : c.getAuthorIdent().getWhen(), getTimeZone()));
	}
}