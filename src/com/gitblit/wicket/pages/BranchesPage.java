package com.gitblit.wicket.pages;

import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.Utils;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.models.RefModel;
import com.gitblit.wicket.panels.BranchLinksPanel;


public class BranchesPage extends RepositoryPage {

	public BranchesPage(PageParameters params) {
		super(params);

		Repository r = getRepository();
		List<RefModel> branches = new ArrayList<RefModel>();
		branches.addAll(JGitUtils.getLocalBranches(r, -1));
		branches.addAll(JGitUtils.getRemoteBranches(r, -1));

		// shortlog
		add(new LinkPanel("summary", "title", repositoryName, SummaryPage.class, newRepositoryParameter()));

		ListDataProvider<RefModel> branchesDp = new ListDataProvider<RefModel>(branches);
		DataView<RefModel> branchView = new DataView<RefModel>("branch", branchesDp) {
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
				Label branchDateLabel = new Label("branchDate", date);
				item.add(branchDateLabel);
				WicketUtils.setCssClass(branchDateLabel, Utils.timeAgoCss(entry.getDate()));

				item.add(new LinkPanel("branchName", "list name", entry.getDisplayName(), ShortLogPage.class, newCommitParameter(entry.getName())));

				boolean remote = entry.getName().startsWith(Constants.R_REMOTES);
				item.add(new Label("branchType", remote ? getString("gb.remote"):getString("gb.local")));
				
				item.add(new BranchLinksPanel("branchLinks", repositoryName, entry));
				
				String clazz = counter % 2 == 0 ? "dark" : "light";
				WicketUtils.setCssClass(item, clazz);
				counter++;
			}
		};
		add(branchView);
	}
	
	@Override
	protected String getPageName() {
		return getString("gb.branches");
	}
}
