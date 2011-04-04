package com.gitblit.wicket.pages;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.navigation.paging.PagingNavigator;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.Utils;
import com.gitblit.wicket.GitBlitWebApp;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.RefsPanel;


public class LogPage extends RepositoryPage {

	public LogPage(PageParameters params) {
		super(params, "log");

		Repository r = getRepository();
		final Map<ObjectId, List<String>> allRefs = JGitUtils.getAllRefs(r);
		List<RevCommit> commits = JGitUtils.getRevLog(r, 100);
		r.close();

		add(new LinkPanel("summary", "title", repositoryName, SummaryPage.class, newRepositoryParameter()));

		// log
		ListDataProvider<RevCommit> dp = new ListDataProvider<RevCommit>(commits);
		DataView<RevCommit> logView = new DataView<RevCommit>("commit", dp) {
			private static final long serialVersionUID = 1L;

			public void populateItem(final Item<RevCommit> item) {
				final RevCommit entry = item.getModelObject();
				final Date date = JGitUtils.getCommitDate(entry);

				item.add(new Label("timeAgo", Utils.timeAgo(date)));

				item.add(new LinkPanel("link", "title", entry.getShortMessage(), CommitPage.class, newCommitParameter(entry.getName())));

				item.add(new RefsPanel("commitRefs", entry, allRefs));

				String author = entry.getAuthorIdent().getName();
				item.add(createAuthorLabel("commitAuthor", author));

				item.add(new Label("commitDate", GitBlitWebSession.get().formatDateTimeLong(date)));

				item.add(new Label("fullMessage", WicketUtils.breakLines(entry.getFullMessage())).setEscapeModelStrings(false));
			}
		};
		logView.setItemsPerPage(GitBlitWebApp.PAGING_ITEM_COUNT);
		add(logView);
		add(new PagingNavigator("navigator", logView));

		// footer
		addFooter();
	}
}
