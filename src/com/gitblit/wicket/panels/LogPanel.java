package com.gitblit.wicket.panels;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.wicket.ajax.markup.html.navigation.paging.AjaxPagingNavigator;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.model.StringResourceModel;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.GitBlitWebApp;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.CommitPage;
import com.gitblit.wicket.pages.DiffPage;
import com.gitblit.wicket.pages.LogPage;
import com.gitblit.wicket.pages.SummaryPage;
import com.gitblit.wicket.pages.TreePage;

public class LogPanel extends Panel {

	private static final long serialVersionUID = 1L;

	public LogPanel(String wicketId, final String repositoryName, String objectId, Repository r, int limit, int pageOffset) {
		super(wicketId);
		boolean pageResults = limit <= 0;	
		setOutputMarkupId(pageResults);
		
		final Map<ObjectId, List<String>> allRefs = JGitUtils.getAllRefs(r);
		List<RevCommit> commits;
		if (pageResults) {
			// Paging result set
			commits = JGitUtils.getRevLog(r, objectId, pageOffset*GitBlitWebApp.PAGING_ITEM_COUNT, GitBlitWebApp.PAGING_ITEM_COUNT);
		} else {
			// Fixed size result set
			commits = JGitUtils.getRevLog(r, objectId, 0, limit);
		}

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

				item.add(WicketUtils.createDateLabel("commitDate", date, GitBlitWebSession.get().getTimezone()));

				String author = entry.getAuthorIdent().getName();
				item.add(WicketUtils.createAuthorLabel("commitAuthor", author));

				String shortMessage = entry.getShortMessage();
				String trimmedMessage = WicketUtils.trimShortLog(shortMessage);
				LinkPanel shortlog = new LinkPanel("commitShortMessage", "list subject", trimmedMessage, CommitPage.class, WicketUtils.newObjectParameter(repositoryName, entry.getName()));
				if (!shortMessage.equals(trimmedMessage)) {
					WicketUtils.setHtmlTitle(shortlog, shortMessage);
				}
				item.add(shortlog);

				item.add(new RefsPanel("commitRefs", entry, allRefs));

				item.add(new BookmarkablePageLink<Void>("view", CommitPage.class, WicketUtils.newObjectParameter(repositoryName, entry.getName())));
				item.add(new BookmarkablePageLink<Void>("diff", DiffPage.class, WicketUtils.newObjectParameter(repositoryName, entry.getName())));
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
			add(new Label("pageLogs", "").setVisible(false));
		} else {			
			if (pageResults) {
				// paging
				add(new Label("moreLogs", "").setVisible(false));
				if (commits.size() == limit) {
					// show pager
					logView.setItemsPerPage(GitBlitWebApp.PAGING_ITEM_COUNT);
					add(new AjaxPagingNavigator("pageLogs", logView));
				} else {
					// nothing to page
					add(new Label("pageLogs", "").setVisible(false));
				}
			} else {
				// more
				add(new Label("pageLogs", "").setVisible(false));
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
}
