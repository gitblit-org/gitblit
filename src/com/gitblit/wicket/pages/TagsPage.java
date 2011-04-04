package com.gitblit.wicket.pages;

import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.models.RefModel;
import com.gitblit.wicket.panels.TagLinksPanel;


public class TagsPage extends RepositoryPage {

	public TagsPage(PageParameters params) {
		super(params, "tags");
		Repository r = getRepository();
		List<RefModel> tags = JGitUtils.getTags(r, -1);
		r.close();

		// shortlog
		add(new LinkPanel("summary", "title", repositoryName, SummaryPage.class, newRepositoryParameter()));

		ListDataProvider<RefModel> tagsDp = new ListDataProvider<RefModel>(tags);
		DataView<RefModel> tagView = new DataView<RefModel>("tag", tagsDp) {
			private static final long serialVersionUID = 1L;
			int counter = 0;

			public void populateItem(final Item<RefModel> item) {
				final RefModel entry = item.getModelObject();
				item.add(createDateLabel("tagDate", entry.getDate()));

				item.add(new LinkPanel("tagName", "list name", entry.getDisplayName(), CommitPage.class, newCommitParameter(entry.getObjectId().getName())));

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

		// footer
		addFooter();
	}
}
