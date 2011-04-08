package com.gitblit.wicket.panels;

import java.util.List;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.model.StringResourceModel;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.models.RefModel;
import com.gitblit.wicket.pages.CommitPage;
import com.gitblit.wicket.pages.LogPage;
import com.gitblit.wicket.pages.SummaryPage;
import com.gitblit.wicket.pages.TagPage;
import com.gitblit.wicket.pages.TagsPage;

public class TagsPanel extends Panel {

	private static final long serialVersionUID = 1L;

	public TagsPanel(String wicketId, final String repositoryName, Repository r, final int maxCount) {
		super(wicketId);

		// header
		List<RefModel> tags = JGitUtils.getTags(r, maxCount);
		if (maxCount > 0) {
			// summary page
			// show tags page link
			add(new LinkPanel("header", "title", new StringResourceModel("gb.tags", this, null), TagsPage.class, WicketUtils.newRepositoryParameter(repositoryName)));
		} else {
			// tags page
			// show repository summary page link
			add(new LinkPanel("header", "title", repositoryName, SummaryPage.class, WicketUtils.newRepositoryParameter(repositoryName)));
		}

		ListDataProvider<RefModel> tagsDp = new ListDataProvider<RefModel>(tags);
		DataView<RefModel> tagView = new DataView<RefModel>("tag", tagsDp) {
			private static final long serialVersionUID = 1L;
			int counter = 0;

			public void populateItem(final Item<RefModel> item) {
				RefModel entry = item.getModelObject();

				item.add(WicketUtils.createDateLabel("tagDate", entry.getDate(), GitBlitWebSession.get().getTimezone()));

				item.add(new LinkPanel("tagName", "list name", entry.getDisplayName(), CommitPage.class, WicketUtils.newCommitParameter(repositoryName, entry.getCommitId().getName())));
				String message;
				if (maxCount > 0) {
					message = WicketUtils.trimString(entry.getShortLog(), 40);
				} else {
					message = entry.getShortLog();
				}
				if (entry.isAnnotatedTag()) {
					item.add(new LinkPanel("tagDescription", "list subject", message, TagPage.class, WicketUtils.newCommitParameter(repositoryName, entry.getObjectId().getName())));
				} else {
					item.add(new LinkPanel("tagDescription", "list subject", message, CommitPage.class, WicketUtils.newCommitParameter(repositoryName, entry.getObjectId().getName())));
				}
				item.add(new BookmarkablePageLink<Void>("view", TagPage.class, WicketUtils.newCommitParameter(repositoryName, entry.getObjectId().getName())).setEnabled(entry.isAnnotatedTag()));
				item.add(new BookmarkablePageLink<Void>("commit", CommitPage.class, WicketUtils.newCommitParameter(repositoryName, entry.getCommitId().getName())));
				item.add(new BookmarkablePageLink<Void>("log", LogPage.class, WicketUtils.newCommitParameter(repositoryName, entry.getName())));

				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(tagView);
		if (tags.size() < maxCount || maxCount <= 0) {
			add(new Label("allTags", "").setVisible(false));
		} else {
			add(new LinkPanel("allTags", "link", new StringResourceModel("gb.allTags", this, null), TagsPage.class, WicketUtils.newRepositoryParameter(repositoryName)));
		}
	}
}
