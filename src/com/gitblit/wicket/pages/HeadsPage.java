package com.gitblit.wicket.pages;

import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.Utils;
import com.gitblit.wicket.GitBlitWebApp;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.models.RefModel;


public class HeadsPage extends RepositoryPage {

	public HeadsPage(PageParameters params) {
		super(params, "heads");

		Repository r = getRepository();
		List<RefModel> tags = JGitUtils.getHeads(r, -1);
		r.close();

		// shortlog
		add(new LinkPanel("summary", "title", repositoryName, SummaryPage.class, newRepositoryParameter()));

		ListDataProvider<RefModel> tagsDp = new ListDataProvider<RefModel>(tags);
		DataView<RefModel> tagView = new DataView<RefModel>("head", tagsDp) {
			private static final long serialVersionUID = 1L;
			int counter = 0;

			public void populateItem(final Item<RefModel> item) {
				final RefModel entry = item.getModelObject();
				String date;
				if (entry.getDate() != null) {
					date = Utils.timeAgo(entry.getDate());
				} else {
					date = "";
				}
				Label headDateLabel = new Label("headDate", date);
				item.add(headDateLabel);
				WicketUtils.setCssClass(headDateLabel, Utils.timeAgoCss(entry.getDate()));

				item.add(new LinkPanel("headName", "list name", entry.getDisplayName(), ShortLogPage.class, newCommitParameter(entry.getName())));

				String clazz = counter % 2 == 0 ? "dark" : "light";
				WicketUtils.setCssClass(item, clazz);
				counter++;
			}
		};
		tagView.setItemsPerPage(GitBlitWebApp.PAGING_ITEM_COUNT);
		add(tagView);

		// footer
		addFooter();
	}
}
