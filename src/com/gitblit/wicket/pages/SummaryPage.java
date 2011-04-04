package com.gitblit.wicket.pages;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.GitBlitWebApp;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.models.RefModel;
import com.gitblit.wicket.panels.HeadLinksPanel;
import com.gitblit.wicket.panels.RefsPanel;
import com.gitblit.wicket.panels.ShortLogLinksPanel;
import com.gitblit.wicket.panels.TagLinksPanel;


public class SummaryPage extends RepositoryPage {

	public SummaryPage(PageParameters params) {
		super(params, "summary");

		Repository r = getRepository();
		final Map<ObjectId, List<String>> allRefs = JGitUtils.getAllRefs(r);

		String owner = JGitUtils.getRepositoryOwner(r);
		GitBlitWebSession session = GitBlitWebSession.get();
		String lastchange = session.formatDateTimeLong(JGitUtils.getLastChange(r));
		String cloneurl = GitBlitWebApp.get().getCloneUrl(repositoryName);

		// repository description
		add(new Label("repositoryDescription", description));
		add(new Label("repositoryOwner", owner));
		add(new Label("repositoryLastChange", lastchange));
		add(new Label("repositoryCloneUrl", cloneurl));

		int summaryCount = 16;

		// shortlog
		add(new LinkPanel("shortlog", "title", "shortlog", ShortLogPage.class, newRepositoryParameter()));

		List<RevCommit> commits = JGitUtils.getRevLog(r, summaryCount);
		ListDataProvider<RevCommit> dp = new ListDataProvider<RevCommit>(commits);
		DataView<RevCommit> shortlogView = new DataView<RevCommit>("commit", dp) {
			private static final long serialVersionUID = 1L;
			int counter = 0;

			public void populateItem(final Item<RevCommit> item) {
				RevCommit entry = item.getModelObject();
				Date date = JGitUtils.getCommitDate(entry);

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

				setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(shortlogView);
		add(new LinkPanel("shortlogMore", "link", "...", ShortLogPage.class, newRepositoryParameter()));

		// tags
		List<RefModel> tags = JGitUtils.getTags(r, summaryCount);
		add(new LinkPanel("tags", "title", "tags", TagsPage.class, newRepositoryParameter()));

		ListDataProvider<RefModel> tagsDp = new ListDataProvider<RefModel>(tags);
		DataView<RefModel> tagView = new DataView<RefModel>("tag", tagsDp) {
			private static final long serialVersionUID = 1L;
			int counter = 0;

			public void populateItem(final Item<RefModel> item) {
				final RefModel entry = item.getModelObject();

				item.add(createDateLabel("tagDate", entry.getDate()));

				item.add(new LinkPanel("tagName", "list name", entry.getDisplayName(), CommitPage.class, newCommitParameter(entry.getCommitId().getName())));

				if (entry.getCommitId().equals(entry.getObjectId())) {
					// lightweight tag on commit object
					item.add(new Label("tagDescription", ""));
				} else {
					// tag object
					item.add(new LinkPanel("tagDescription", "list subject", entry.getShortLog(), TagPage.class, newCommitParameter(entry.getObjectId().getName())));
				}

				item.add(new TagLinksPanel("tagLinks", repositoryName, entry));

				setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(tagView);
		add(new LinkPanel("tagsMore", "link", "...", TagsPage.class, newRepositoryParameter()));
		// heads
		List<RefModel> heads = JGitUtils.getHeads(r, summaryCount);
		add(new LinkPanel("heads", "title", "heads", HeadsPage.class, newRepositoryParameter()));

		ListDataProvider<RefModel> headsDp = new ListDataProvider<RefModel>(heads);
		DataView<RefModel> headsView = new DataView<RefModel>("head", headsDp) {
			private static final long serialVersionUID = 1L;
			int counter = 0;

			public void populateItem(final Item<RefModel> item) {
				final RefModel entry = item.getModelObject();

				item.add(createDateLabel("headDate", entry.getDate()));

				item.add(new LinkPanel("headName", "list name", entry.getDisplayName(), ShortLogPage.class, newCommitParameter(entry.getName())));

				item.add(new HeadLinksPanel("headLinks", repositoryName, entry));

				setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(headsView);

		// close the repository
		r.close();

		// footer
		addFooter();
	}
}
