package com.gitblit.wicket.panels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.model.StringResourceModel;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.models.RefModel;
import com.gitblit.wicket.pages.BranchesPage;
import com.gitblit.wicket.pages.LogPage;
import com.gitblit.wicket.pages.SummaryPage;
import com.gitblit.wicket.pages.TreePage;

public class BranchesPanel extends Panel {

	private static final long serialVersionUID = 1L;

	public BranchesPanel(String wicketId, final String repositoryName, Repository r, final int maxCount) {
		super(wicketId);

		// branches
		List<RefModel> branches = new ArrayList<RefModel>();
		branches.addAll(JGitUtils.getLocalBranches(r, maxCount));
		branches.addAll(JGitUtils.getRemoteBranches(r, maxCount));
		Collections.sort(branches);
		Collections.reverse(branches);
		if (maxCount > 0 && branches.size() > maxCount) {
			branches = new ArrayList<RefModel>(branches.subList(0, maxCount));
		}

		if (maxCount > 0) {
			// summary page
			// show branches page link
			add(new LinkPanel("branches", "title", new StringResourceModel("gb.branches", this, null), BranchesPage.class, WicketUtils.newRepositoryParameter(repositoryName)));
		} else {
			// branches page
			// show repository summary page link
			add(new LinkPanel("branches", "title", repositoryName, SummaryPage.class, WicketUtils.newRepositoryParameter(repositoryName)));
		}
		
		ListDataProvider<RefModel> branchesDp = new ListDataProvider<RefModel>(branches);
		DataView<RefModel> branchesView = new DataView<RefModel>("branch", branchesDp) {
			private static final long serialVersionUID = 1L;
			int counter = 0;

			public void populateItem(final Item<RefModel> item) {
				final RefModel entry = item.getModelObject();

				item.add(WicketUtils.createDateLabel("branchDate", entry.getDate(), GitBlitWebSession.get().getTimezone()));

				item.add(new LinkPanel("branchName", "list name", WicketUtils.trimString(entry.getDisplayName(), 28), LogPage.class, WicketUtils.newObjectParameter(repositoryName, entry.getName())));
				
				// only show branch type on the branches page
				boolean remote = entry.getName().startsWith(Constants.R_REMOTES);
				item.add(new Label("branchType", remote ? getString("gb.remote"):getString("gb.local")).setVisible(maxCount <= 0));
				
				item.add(new BookmarkablePageLink<Void>("log", LogPage.class, WicketUtils.newObjectParameter(repositoryName, entry.getName())));
				item.add(new BookmarkablePageLink<Void>("tree", TreePage.class, WicketUtils.newObjectParameter(repositoryName, entry.getName())));

				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(branchesView);
		if (branches.size() < maxCount || maxCount <= 0) {
			add(new Label("allBranches", "").setVisible(false));
		} else {
			add(new LinkPanel("allBranches", "link", new StringResourceModel("gb.allBranches", this, null), BranchesPage.class, WicketUtils.newRepositoryParameter(repositoryName)));
		}
	}
}
