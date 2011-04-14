package com.gitblit.wicket.pages;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.Utils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.models.TicGitTicket;
import com.gitblit.wicket.models.TicGitTicket.Comment;

public class TicGitTicketPage extends RepositoryPage {

	public TicGitTicketPage(PageParameters params) {
		super(params);

		final String ticketFolder = WicketUtils.getPath(params);

		Repository r = getRepository();
		TicGitTicket t = JGitUtils.getTicGitTicket(r, ticketFolder);

		add(new Label("ticketTitle", t.title));
		add(new Label("ticketId", t.id));
		add(new Label("ticketHandler", t.handler.toLowerCase()));
		add(WicketUtils.createTimestampLabel("ticketOpenDate", t.date, getTimeZone()));
		Label stateLabel = new Label("ticketState", t.state);
		WicketUtils.setTicketCssClass(stateLabel, t.state);
		add(stateLabel);
		add(new Label("ticketTags", WicketUtils.flattenStrings(t.tags)));

		ListDataProvider<Comment> commentsDp = new ListDataProvider<Comment>(t.comments);
		DataView<Comment> commentsView = new DataView<Comment>("comment", commentsDp) {
			private static final long serialVersionUID = 1L;
			int counter = 0;

			public void populateItem(final Item<Comment> item) {
				final Comment entry = item.getModelObject();
				item.add(WicketUtils.createDateLabel("commentDate", entry.date, GitBlitWebSession.get().getTimezone()));
				item.add(new Label("commentAuthor", entry.author.toLowerCase()));
				item.add(new Label("commentText", prepareComment(entry.text)).setEscapeModelStrings(false));
				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(commentsView);
	}

	@Override
	protected String getPageName() {
		return getString("gb.ticket");
	}

	private String prepareComment(String comment) {
		String html = Utils.escapeForHtml(comment, false);
		html = WicketUtils.breakLines(comment).trim();
		return html.replaceAll("\\bcommit\\s*([A-Za-z0-9]*)\\b", "<a href=\"/commit/" + repositoryName + "/$1\">commit $1</a>");
	}
}
