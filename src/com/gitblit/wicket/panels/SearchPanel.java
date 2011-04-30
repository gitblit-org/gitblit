package com.gitblit.wicket.panels;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
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
import com.gitblit.wicket.pages.SearchPage;
import com.gitblit.wicket.pages.TreePage;

public class SearchPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	private boolean hasMore = false;

	public SearchPanel(String wicketId, final String repositoryName, final String objectId, final String value, SearchType searchType, Repository r, int limit, int pageOffset) {
		super(wicketId);
		boolean pageResults = limit <= 0;
		int itemsPerPage = GitBlit.self().settings().getInteger(Keys.web.itemsPerPage, 50);
		if (itemsPerPage <= 1) {
			itemsPerPage = 50;
		}

		RevCommit commit = JGitUtils.getCommit(r, objectId);

		final Map<ObjectId, List<String>> allRefs = JGitUtils.getAllRefs(r);
		List<RevCommit> commits;
		if (pageResults) {
			// Paging result set
			commits = JGitUtils.searchRevlogs(r, objectId, value, searchType, pageOffset * itemsPerPage, itemsPerPage);
		} else {
			// Fixed size result set
			commits = JGitUtils.searchRevlogs(r, objectId, value, searchType, 0, limit);
		}

		// inaccurate way to determine if there are more commits.
		// works unless commits.size() represents the exact end.
		hasMore = commits.size() >= itemsPerPage;

		// header
		add(new LinkPanel("header", "title", commit == null ? "":commit.getShortMessage(), CommitPage.class, WicketUtils.newObjectParameter(repositoryName, commit == null ? "":commit.getName())));

		ListDataProvider<RevCommit> dp = new ListDataProvider<RevCommit>(commits);
		DataView<RevCommit> searchView = new DataView<RevCommit>("commit", dp) {
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
					item.add(WicketUtils.newImage("commitIcon", "commit_merge_16x16.png"));
				} else {
					item.add(WicketUtils.newBlankImage("commitIcon"));
				}

				String shortMessage = entry.getShortMessage();
				String trimmedMessage = StringUtils.trimShortLog(shortMessage);
				// TODO highlight matches
				LinkPanel shortlog = new LinkPanel("commitShortMessage", "list subject", trimmedMessage, CommitPage.class, WicketUtils.newObjectParameter(repositoryName, entry.getName()));
				if (!shortMessage.equals(trimmedMessage)) {
					WicketUtils.setHtmlTooltip(shortlog, shortMessage);
				}
				item.add(shortlog);

				item.add(new RefsPanel("commitRefs", repositoryName, entry, allRefs));

				item.add(new BookmarkablePageLink<Void>("commit", CommitPage.class, WicketUtils.newObjectParameter(repositoryName, entry.getName())));
				item.add(new BookmarkablePageLink<Void>("commitdiff", CommitDiffPage.class, WicketUtils.newObjectParameter(repositoryName, entry.getName())));
				item.add(new BookmarkablePageLink<Void>("tree", TreePage.class, WicketUtils.newObjectParameter(repositoryName, entry.getName())));

				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(searchView);
	}

	public boolean hasMore() {
		return hasMore;
	}
}
