package com.gitblit.wicket.pages;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;

import com.gitblit.utils.JGitUtils.SearchType;
import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.SearchPanel;

public class SearchPage extends RepositoryPage {
	
	public SearchPage(PageParameters params) {
		super(params);

		String value = WicketUtils.getSearchString(params);
		String type = WicketUtils.getSearchType(params);
		SearchType searchType = SearchType.forName(type);
		
		int pageNumber = WicketUtils.getPage(params);
		int prevPage = Math.max(0, pageNumber - 1);
		int nextPage = pageNumber + 1;

		SearchPanel search = new SearchPanel("searchPanel", repositoryName, objectId, value, searchType, getRepository(), -1, pageNumber - 1);
		boolean hasMore = search.hasMore();
		add(search);

		add(new BookmarkablePageLink<Void>("firstPageTop", SearchPage.class, WicketUtils.newSearchParameter(repositoryName, objectId, value, searchType)).setEnabled(pageNumber > 1));
		add(new BookmarkablePageLink<Void>("prevPageTop", SearchPage.class, WicketUtils.newSearchParameter(repositoryName, objectId, value, searchType, prevPage)).setEnabled(pageNumber > 1));
		add(new BookmarkablePageLink<Void>("nextPageTop", SearchPage.class, WicketUtils.newSearchParameter(repositoryName, objectId, value, searchType, nextPage)).setEnabled(hasMore));

		add(new BookmarkablePageLink<Void>("firstPageBottom", SearchPage.class, WicketUtils.newSearchParameter(repositoryName, objectId, value, searchType)).setEnabled(pageNumber > 1));
		add(new BookmarkablePageLink<Void>("prevPageBottom", SearchPage.class, WicketUtils.newSearchParameter(repositoryName, objectId, value, searchType, prevPage)).setEnabled(pageNumber > 1));
		add(new BookmarkablePageLink<Void>("nextPageBottom", SearchPage.class, WicketUtils.newSearchParameter(repositoryName, objectId, value, searchType, nextPage)).setEnabled(hasMore));

	}

	@Override
	protected String getPageName() {
		return getString("gb.search");
	}
}
