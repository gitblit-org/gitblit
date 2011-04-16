package com.gitblit.wicket.panels;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.wicket.markup.html.basic.Label;
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
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.BlobDiffPage;
import com.gitblit.wicket.pages.CommitDiffPage;
import com.gitblit.wicket.pages.CommitPage;
import com.gitblit.wicket.pages.HistoryPage;
import com.gitblit.wicket.pages.LogPage;

public class HistoryPanel extends BasePanel {

	private static final long serialVersionUID = 1L;
	
	private boolean hasMore = false;

	public HistoryPanel(String wicketId, final String repositoryName, String objectId, final String path, Repository r, int limit, int pageOffset) {
		super(wicketId);
		boolean pageResults = limit <= 0;
		int itemsPerPage = GitBlit.self().settings().getInteger(Keys.web.logPageCommitCount, 50);
		if (itemsPerPage <= 1) {
			itemsPerPage = 50;
		}
		
		RevCommit commit = JGitUtils.getCommit(r, objectId);		
		final Map<ObjectId, List<String>> allRefs = JGitUtils.getAllRefs(r);
		List<RevCommit> commits;
		if (pageResults) {
			// Paging result set
			commits = JGitUtils.getRevLog(r, objectId, path, pageOffset * itemsPerPage, itemsPerPage);
		} else {
			// Fixed size result set
			commits = JGitUtils.getRevLog(r, objectId, path, 0, limit);
		}
		
		// inaccurate way to determine if there are more commits.
		// works unless commits.size() represents the exact end. 
		hasMore = commits.size() >= itemsPerPage;

		// header
		if (pageResults) {
			// history page
			// show commit page link
			add(new LinkPanel("header", "title", commit.getShortMessage(), CommitPage.class, WicketUtils.newObjectParameter(repositoryName, commit.getName())));
		} else {
			// summary page
			// show history page link
			add(new LinkPanel("header", "title", new StringResourceModel("gb.history", this, null), LogPage.class, WicketUtils.newRepositoryParameter(repositoryName)));
		}

		// breadcrumbs
		add(new PathBreadcrumbsPanel("breadcrumbs", repositoryName, path, objectId));

		ListDataProvider<RevCommit> dp = new ListDataProvider<RevCommit>(commits);
		DataView<RevCommit> logView = new DataView<RevCommit>("commit", dp) {
			private static final long serialVersionUID = 1L;
			int counter = 0;

			public void populateItem(final Item<RevCommit> item) {
				final RevCommit entry = item.getModelObject();
				final Date date = JGitUtils.getCommitDate(entry);

				item.add(WicketUtils.createDateLabel("commitDate", date, getTimeZone()));

				String author = entry.getAuthorIdent().getName();
				item.add(WicketUtils.createAuthorLabel("commitAuthor", author));

				String shortMessage = entry.getShortMessage();
				String trimmedMessage = StringUtils.trimShortLog(shortMessage);
				LinkPanel shortlog = new LinkPanel("commitShortMessage", "list subject", trimmedMessage, CommitPage.class, WicketUtils.newObjectParameter(repositoryName, entry.getName()));
				if (!shortMessage.equals(trimmedMessage)) {
					WicketUtils.setHtmlTitle(shortlog, shortMessage);
				}
				item.add(shortlog);

				item.add(new RefsPanel("commitRefs", repositoryName, entry, allRefs));

				item.add(new BookmarkablePageLink<Void>("view", CommitPage.class, WicketUtils.newObjectParameter(repositoryName, entry.getName())));
				item.add(new BookmarkablePageLink<Void>("commitdiff", CommitDiffPage.class, WicketUtils.newObjectParameter(repositoryName, entry.getName())));
				item.add(new BookmarkablePageLink<Void>("difftocurrent", BlobDiffPage.class, WicketUtils.newPathParameter(repositoryName, entry.getName(), path)).setEnabled(counter > 0));

				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(logView);

		// determine to show pager, more, or neither
		if (limit <= 0) {
			// no display limit
			add(new Label("moreHistory", "").setVisible(false));
		} else {
			if (pageResults) {
				// paging
				add(new Label("moreHistory", "").setVisible(false));
			} else {
				// more
				if (commits.size() == limit) {
					// show more
					add(new LinkPanel("moreHistory", "link", new StringResourceModel("gb.moreHistory", this, null), HistoryPage.class, WicketUtils.newPathParameter(repositoryName, objectId, path)));
				} else {
					// no more
					add(new Label("moreHistory", "").setVisible(false));
				}
			}
		}
	}
	
	public boolean hasMore() {
		return hasMore;
	}
}
