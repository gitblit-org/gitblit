/*
 * Copyright 2011 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.wicket.pages;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Comment;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TicgitUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;

public class TicketPage extends RepositoryPage {

	public TicketPage(PageParameters params) {
		super(params);

		final String ticketFolder = WicketUtils.getPath(params);

		Repository r = getRepository();
		TicketModel t = TicgitUtils.getTicket(r, ticketFolder);

		add(new Label("ticketTitle", t.title));
		add(new Label("ticketId", t.id));
		add(new Label("ticketHandler", t.handler.toLowerCase()));
		add(WicketUtils.createTimestampLabel("ticketOpenDate", t.date, getTimeZone(), getTimeUtils()));
		Label stateLabel = new Label("ticketState", t.state);
		WicketUtils.setTicketCssClass(stateLabel, t.state);
		add(stateLabel);
		add(new Label("ticketTags", StringUtils.flattenStrings(t.tags)));

		ListDataProvider<Comment> commentsDp = new ListDataProvider<Comment>(t.comments);
		DataView<Comment> commentsView = new DataView<Comment>("comment", commentsDp) {
			private static final long serialVersionUID = 1L;
			int counter;

			public void populateItem(final Item<Comment> item) {
				final Comment entry = item.getModelObject();
				item.add(WicketUtils.createDateLabel("commentDate", entry.date, GitBlitWebSession
						.get().getTimezone(), getTimeUtils()));
				item.add(new Label("commentAuthor", entry.author.toLowerCase()));
				item.add(new Label("commentText", prepareComment(entry.text))
						.setEscapeModelStrings(false));
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
		String html = StringUtils.escapeForHtml(comment, false);
		html = StringUtils.breakLinesForHtml(comment).trim();
		return html.replaceAll("\\bcommit\\s*([A-Za-z0-9]*)\\b", "<a href=\"/commit/"
				+ repositoryName + "/$1\">commit $1</a>");
	}
}
