package com.gitblit.wicket.panels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.wicket.Component;
import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
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

public class PageLinksPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	private final Map<String, String> knownPages = new HashMap<String, String>() {

		private static final long serialVersionUID = 1L;

		{
			put("summary", "gb.summary");
			put("shortlog", "gb.shortlog");
			put("branches", "gb.branches");
			put("tags", "gb.tags");
			put("tree", "gb.tree");
			put("ticgit", "gb.ticgit");
		}
	};

	public PageLinksPanel(String id, Repository r, final String repositoryName, String pageName) {
		super(id);

		// summary
		add(new BookmarkablePageLink<Void>("summary", SummaryPage.class, new PageParameters("p=" + repositoryName)));
		add(new BookmarkablePageLink<Void>("shortlog", ShortLogPage.class, new PageParameters("p=" + repositoryName)));
		add(new BookmarkablePageLink<Void>("branches", BranchesPage.class, new PageParameters("p=" + repositoryName)));
		add(new BookmarkablePageLink<Void>("tags", TagsPage.class, new PageParameters("p=" + repositoryName)));
		add(new BookmarkablePageLink<Void>("tree", TreePage.class, new PageParameters("p=" + repositoryName + ",h=HEAD")));

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

	public void disablePageLink(String pageName) {
		for (String wicketId : knownPages.keySet()) {
			String key = knownPages.get(wicketId);
			String linkName = getString(key);
			if (linkName.equals(pageName)) {
				Component c = get(wicketId);
				c.setEnabled(false);
				break;
			}
		}
	}
}