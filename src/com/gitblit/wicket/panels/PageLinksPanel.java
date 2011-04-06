package com.gitblit.wicket.panels;

import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.pages.BranchesPage;
import com.gitblit.wicket.pages.ShortLogPage;
import com.gitblit.wicket.pages.SummaryPage;
import com.gitblit.wicket.pages.TagsPage;
import com.gitblit.wicket.pages.TicGitPage;
import com.gitblit.wicket.pages.TreePage;

public class PageLinksPanel extends Panel {

	private static final long serialVersionUID = 1L;

	public PageLinksPanel(String id, Repository r, final String repositoryName, String pageName) {
		super(id);

		// summary
		if (pageName.equals("summary")) {
			add(new Label("summary", pageName));
		} else {
			add(new LinkPanel("summary", null, "summary", SummaryPage.class, new PageParameters("p=" + repositoryName)));
		}

		// shortlog
		if (pageName.equals("shortlog")) {
			add(new Label("shortlog", pageName));
		} else {
			add(new LinkPanel("shortlog", null, "shortlog", ShortLogPage.class, new PageParameters("p=" + repositoryName)));
		}

		// branches
		if (pageName.equals("branches")) {
			add(new Label("branches", pageName));
		} else {
			add(new LinkPanel("branches", null, "branches", BranchesPage.class, new PageParameters("p=" + repositoryName)));
		}

		// tags
		if (pageName.equals("tags")) {
			add(new Label("tags", pageName));
		} else {
			add(new LinkPanel("tags", null, "tags", TagsPage.class, new PageParameters("p=" + repositoryName)));
		}

		// tree
		if (pageName.equals("tree")) {
			add(new Label("tree", pageName));
		} else {
			add(new LinkPanel("tree", null, "tree", TreePage.class, new PageParameters("p=" + repositoryName + ",h=HEAD")));
		}

		// Add dynamic repository extras
		List<String> extras = new ArrayList<String>();
		if (JGitUtils.getTicGitBranch(r) != null) {
			extras.add("ticgit");
		}

		ListDataProvider<String> extrasDp = new ListDataProvider<String>(extras);
		DataView<String> extrasView = new DataView<String>("extra", extrasDp) {
			private static final long serialVersionUID = 1L;

			public void populateItem(final Item<String> item) {
				String extra = item.getModelObject();
				if (extra.equals("ticgit")) {
					item.add(new Label("extraSeparator", " | "));
					item.add(new LinkPanel("extraLink", null, "ticgit", TicGitPage.class, new PageParameters("p=" + repositoryName)));
				}
			}
		};
		add(extrasView);
	}
}