package com.gitblit.wicket.pages;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.navigation.paging.PagingNavigator;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.GitBlitWebApp;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.RefsPanel;
import com.gitblit.wicket.panels.ShortLogLinksPanel;


public class ShortLogPage extends RepositoryPage {

	public ShortLogPage(PageParameters params) {
		super(params, "shortlog");

		Repository r = getRepository();
		final Map<ObjectId, List<String>> allRefs = JGitUtils.getAllRefs(r);
		List<RevCommit> commits = JGitUtils.getRevLog(r, 100);
		r.close();

		// shortlog
		add(new LinkPanel("summary", "title", repositoryName, SummaryPage.class, newRepositoryParameter()));

		ListDataProvider<RevCommit> dp = new ListDataProvider<RevCommit>(commits);
		DataView<RevCommit> shortlogView = new DataView<RevCommit>("commit", dp) {
			private static final long serialVersionUID = 1L;
			int counter = 0;

			public void populateItem(final Item<RevCommit> item) {
				final RevCommit entry = item.getModelObject();
				final Date date = JGitUtils.getCommitDate(entry);

				item.add(createShortlogDateLabel("commitDate", date));

				String author = entry.getAuthorIdent().getName();
				item.add(createAuthorLabel("commitAuthor", author));

				String shortMessage = entry.getShortMessage();
				String trimmedMessage = trimShortLog(shortMessage);
				LinkPanel shortlog = new LinkPanel("commitShortMessage", "list subject", trimmedMessage, CommitPage.class, newCommitParameter(entry.getName()));
				if (!shortMessage.equals(trimmedMessage)) {
					WicketUtils.setHtmlTitle(shortlog, shortMessage);
				}
				item.add(shortlog);
				
				item.add(new RefsPanel("commitRefs", entry, allRefs));
				
				item.add(new ShortLogLinksPanel("commitLinks", repositoryName, entry.getName()));

				String clazz = counter % 2 == 0 ? "dark" : "light";
				WicketUtils.setCssClass(item, clazz);
				counter++;
			}
		};
		shortlogView.setItemsPerPage(GitBlitWebApp.PAGING_ITEM_COUNT);
		add(shortlogView);
		add(new PagingNavigator("navigator", shortlogView));

		// footer
		addFooter();
	}
}
