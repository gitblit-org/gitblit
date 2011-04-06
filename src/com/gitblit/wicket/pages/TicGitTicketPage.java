package com.gitblit.wicket.pages;

import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.TicGitTicket;
import com.gitblit.utils.TicGitTicket.Comment;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.WicketUtils;

public class TicGitTicketPage extends RepositoryPage {

	public TicGitTicketPage(PageParameters params) {
		super(params, "ticket");

		final String ticketFolder = params.getString("f", "");

		Repository r = getRepository();
		TicGitTicket t = JGitUtils.getTicGitTicket(r, ticketFolder);
		r.close();

		add(new Label("ticketTitle", t.title));
		add(new Label("ticketId", t.id));
		add(new Label("ticketHandler", t.handler));
		String openDate = GitBlitWebSession.get().formatDateTimeLong(t.date);
		add(new Label("ticketOpenDate", openDate));
		Label stateLabel = new Label("ticketState", t.state);
		String css = null;
		if (t.state.equals("open")) {
			css = "bug_open";
		} else if (t.state.equals("hold")) {
			css = "bug_hold";
		} else if (t.state.equals("resolved")) {
			css = "bug_resolved";
		} else if (t.state.equals("invalid")) {
			css = "bug_invalid";
		}
		if (css != null) {
			WicketUtils.setCssClass(stateLabel, css);
		}
		add(stateLabel);
		add(new Label("ticketTags", flattenStrings(t.tags)));

		ListDataProvider<Comment> commentsDp = new ListDataProvider<Comment>(t.comments);
		DataView<Comment> commentsView = new DataView<Comment>("comment", commentsDp) {
			private static final long serialVersionUID = 1L;
			int counter = 0;

			public void populateItem(final Item<Comment> item) {
				final Comment entry = item.getModelObject();
				item.add(createDateLabel("commentDate", entry.date));
				item.add(new Label("commentAuthor", entry.author));
				item.add(new Label("commentText", prepareComment(entry.text)).setEscapeModelStrings(false));
				setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(commentsView);

		// footer
		addFooter();
	}

	private String prepareComment(String comment) {
		String html = WicketUtils.breakLines(comment).trim();
		return html.replaceAll("\\bcommit\\s*([A-Za-z0-9]*)\\b", "<a href=\"/commit/" + repositoryName + "/$1\">commit $1</a>");
	}

	private String flattenStrings(List<String> values) {
		StringBuilder sb = new StringBuilder();
		for (String value : values) {
			sb.append(value).append(" ");
		}
		return sb.toString().trim();
	}
}
