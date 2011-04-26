package com.gitblit.wicket.panels;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.wicket.Component;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.StatelessForm;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.JGitUtils.SearchType;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.BranchesPage;
import com.gitblit.wicket.pages.LogPage;
import com.gitblit.wicket.pages.SearchPage;
import com.gitblit.wicket.pages.SummaryPage;
import com.gitblit.wicket.pages.TagsPage;
import com.gitblit.wicket.pages.TicketsPage;
import com.gitblit.wicket.pages.TreePage;

public class PageLinksPanel extends Panel {

	private static final long serialVersionUID = 1L;

	private final Map<String, String> knownPages = new HashMap<String, String>() {

		private static final long serialVersionUID = 1L;

		{
			put("summary", "gb.summary");
			put("log", "gb.log");
			put("branches", "gb.branches");
			put("tags", "gb.tags");
			put("tree", "gb.tree");
			put("tickets", "gb.tickets");
		}
	};

	public PageLinksPanel(String id, Repository r, final String repositoryName, String pageName) {
		super(id);

		// summary
		add(new BookmarkablePageLink<Void>("summary", SummaryPage.class, WicketUtils.newRepositoryParameter(repositoryName)));
		add(new BookmarkablePageLink<Void>("log", LogPage.class, WicketUtils.newRepositoryParameter(repositoryName)));
		add(new BookmarkablePageLink<Void>("branches", BranchesPage.class, WicketUtils.newRepositoryParameter(repositoryName)));
		add(new BookmarkablePageLink<Void>("tags", TagsPage.class, WicketUtils.newRepositoryParameter(repositoryName)));
		add(new BookmarkablePageLink<Void>("tree", TreePage.class, WicketUtils.newRepositoryParameter(repositoryName)));

		List<String> extras = new ArrayList<String>();

		// Get the repository tickets setting
		boolean checkTickets = JGitUtils.getRepositoryUseTickets(r);
		if (checkTickets && JGitUtils.getTicketsBranch(r) != null) {
			extras.add("tickets");
		}

		// Get the repository docs setting
		boolean checkDocs = JGitUtils.getRepositoryUseDocs(r);
		if (checkDocs && JGitUtils.getDocumentsBranch(r) != null) {
			extras.add("docs");
		}

		ListDataProvider<String> extrasDp = new ListDataProvider<String>(extras);
		DataView<String> extrasView = new DataView<String>("extra", extrasDp) {
			private static final long serialVersionUID = 1L;

			public void populateItem(final Item<String> item) {
				String extra = item.getModelObject();
				if (extra.equals("tickets")) {
					item.add(new Label("extraSeparator", " | "));
					item.add(new LinkPanel("extraLink", null, getString("gb.tickets"), TicketsPage.class, WicketUtils.newRepositoryParameter(repositoryName)));
				} else if (extra.equals("docs")) {
					item.add(new Label("extraSeparator", " | "));
					item.add(new LinkPanel("extraLink", null, getString("gb.docs"), TicketsPage.class, WicketUtils.newRepositoryParameter(repositoryName)));
				}
			}
		};
		add(extrasView);

		add(new SearchForm("searchForm", repositoryName));
	}

	public void disablePageLink(String pageName) {
		for (String wicketId : knownPages.keySet()) {
			String key = knownPages.get(wicketId);
			String linkName = getString(key);
			if (linkName.equals(pageName)) {
				Component c = get(wicketId);
				if (c != null) {
					c.setEnabled(false);
				}
				break;
			}
		}
	}

	class SearchForm extends StatelessForm<Void> {
		private static final long serialVersionUID = 1L;

		private final String repositoryName;

		private final IModel<String> searchBoxModel = new Model<String>("");

		private final IModel<SearchType> searchTypeModel = new Model<SearchType>(SearchType.COMMIT);

		public SearchForm(String id, String repositoryName) {
			super(id);
			this.repositoryName = repositoryName;
			DropDownChoice<SearchType> searchType = new DropDownChoice<SearchType>("searchType", Arrays.asList(SearchType.values()));
			searchType.setModel(searchTypeModel);
			WicketUtils.setHtmlTooltip(searchType, getString("gb.searchTypeTooltip"));
			add(searchType.setVisible(GitBlit.self().settings().getBoolean(Keys.web.showSearchTypeSelection, false)));
			TextField<String> searchBox = new TextField<String>("searchBox", searchBoxModel);
			add(searchBox);
			WicketUtils.setHtmlTooltip(searchBox, getString("gb.searchTooltip"));
			WicketUtils.setInputPlaceholder(searchBox, getString("gb.search"));
		}

		@Override
		public void onSubmit() {
			SearchType searchType = searchTypeModel.getObject();
			String searchString = searchBoxModel.getObject();
			for (SearchType type : SearchType.values()) {
				if (searchString.toLowerCase().startsWith(type.name().toLowerCase() + ":")) {
					searchType = type;
					searchString = searchString.substring(type.name().toLowerCase().length() + 1).trim();
					break;
				}
			}
			setResponsePage(SearchPage.class, WicketUtils.newSearchParameter(repositoryName, null, searchString, searchType));
		}
	}
}