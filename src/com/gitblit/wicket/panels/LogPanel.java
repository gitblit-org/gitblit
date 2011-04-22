package com.gitblit.wicket.panels;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.image.ContextImage;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.model.StringResourceModel;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.JGitUtils.SearchType;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.CommitDiffPage;
import com.gitblit.wicket.pages.CommitPage;
import com.gitblit.wicket.pages.LogPage;
import com.gitblit.wicket.pages.SearchPage;
import com.gitblit.wicket.pages.SummaryPage;
import com.gitblit.wicket.pages.TreePage;

public class LogPanel extends BasePanel {

	private static final long serialVersionUID = 1L;
	
	private boolean hasMore = false;

	public LogPanel(String wicketId, final String repositoryName, final String objectId, Repository r, int limit, int pageOffset) {
		super(wicketId);
		boolean pageResults = limit <= 0;
		int itemsPerPage = GitBlit.self().settings().getInteger(Keys.web.logPageCommitCount, 50);
		if (itemsPerPage <= 1) {
			itemsPerPage = 50;
		}

		final Map<ObjectId, List<String>> allRefs = JGitUtils.getAllRefs(r);
		List<RevCommit> commits;
		if (pageResults) {
			// Paging result set
			commits = JGitUtils.getRevLog(r, objectId, pageOffset * itemsPerPage, itemsPerPage);
		} else {
			// Fixed size result set
			commits = JGitUtils.getRevLog(r, objectId, 0, limit);
		}

		// inaccurate way to determine if there are more commits.
		// works unless commits.size() represents the exact end. 
		hasMore = commits.size() >= itemsPerPage;

		// header
		if (pageResults) {
			// shortlog page
			// show repository summary page link
			add(new LinkPanel("header", "title", repositoryName, SummaryPage.class, WicketUtils.newRepositoryParameter(repositoryName)));
		} else {
			// summary page
			// show shortlog page link
			add(new LinkPanel("header", "title", new StringResourceModel("gb.log", this, null), LogPage.class, WicketUtils.newRepositoryParameter(repositoryName)));
		}

		ListDataProvider<RevCommit> dp = new ListDataProvider<RevCommit>(commits);
		DataView<RevCommit> logView = new DataView<RevCommit>("commit", dp) {
			private static final long serialVersionUID = 1L;
			int counter = 0;

			public void populateItem(final Item<RevCommit> item) {
				final RevCommit entry = item.getModelObject();
				final Date date = JGitUtils.getCommitDate(entry);

				item.add(WicketUtils.createDateLabel("commitDate", date, getTimeZone()));

				// author search link
				String author = entry.getAuthorIdent().getName();
				LinkPanel authorLink = new LinkPanel("commitAuthor", "list", author, SearchPage.class, WicketUtils.newSearchParameter(repositoryName, objectId, author, SearchType.AUTHOR));
				setPersonSearchTooltip(authorLink, author, SearchType.AUTHOR);
				item.add(authorLink);

				// merge icon
				if (entry.getParentCount() > 1) {
					item.add(new ContextImage("commitIcon", "/com/gitblit/wicket/resources/commit_merge_16x16.png"));
				} else {
					item.add(new ContextImage("commitIcon", "/com/gitblit/wicket/resources/blank.png"));
				}
				
				// short message
				String shortMessage = entry.getShortMessage();
				String trimmedMessage = StringUtils.trimShortLog(shortMessage);
				LinkPanel shortlog = new LinkPanel("commitShortMessage", "list subject", trimmedMessage, CommitPage.class, WicketUtils.newObjectParameter(repositoryName, entry.getName()));
				if (!shortMessage.equals(trimmedMessage)) {
					WicketUtils.setHtmlTitle(shortlog, shortMessage);
				}
				item.add(shortlog);

				item.add(new RefsPanel("commitRefs", repositoryName, entry, allRefs));

				item.add(new BookmarkablePageLink<Void>("view", CommitPage.class, WicketUtils.newObjectParameter(repositoryName, entry.getName())));
				item.add(new BookmarkablePageLink<Void>("diff", CommitDiffPage.class, WicketUtils.newObjectParameter(repositoryName, entry.getName())));
				item.add(new BookmarkablePageLink<Void>("tree", TreePage.class, WicketUtils.newObjectParameter(repositoryName, entry.getName())));

				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(logView);

		// determine to show pager, more, or neither
		if (limit <= 0) {
			// no display limit
			add(new Label("moreLogs", "").setVisible(false));
		} else {
			if (pageResults) {
				// paging
				add(new Label("moreLogs", "").setVisible(false));
			} else {
				// more
				if (commits.size() == limit) {
					// show more
					add(new LinkPanel("moreLogs", "link", new StringResourceModel("gb.moreLogs", this, null), LogPage.class, WicketUtils.newRepositoryParameter(repositoryName)));
				} else {
					// no more
					add(new Label("moreLogs", "").setVisible(false));
				}
			}
		}
	}
	
	public boolean hasMore() {
		return hasMore;
	}
}
