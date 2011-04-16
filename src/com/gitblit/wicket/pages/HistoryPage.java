package com.gitblit.wicket.pages;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;

import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.HistoryPanel;

public class HistoryPage extends RepositoryPage {

	public HistoryPage(PageParameters params) {
		super(params);

		String path = WicketUtils.getPath(params);
		int pageNumber = WicketUtils.getPage(params);
		int prevPage = Math.max(0, pageNumber - 1);
		int nextPage = pageNumber + 1;

		HistoryPanel history = new HistoryPanel("historyPanel", repositoryName, objectId, path, getRepository(), -1, pageNumber - 1);
		boolean hasMore = history.hasMore();
		add(history);

		add(new BookmarkablePageLink<Void>("firstPageTop", HistoryPage.class, WicketUtils.newPathParameter(repositoryName, objectId, path)).setEnabled(pageNumber > 1));
		add(new BookmarkablePageLink<Void>("prevPageTop", HistoryPage.class, WicketUtils.newHistoryPageParameter(repositoryName, objectId, path, prevPage)).setEnabled(pageNumber > 1));
		add(new BookmarkablePageLink<Void>("nextPageTop", HistoryPage.class, WicketUtils.newHistoryPageParameter(repositoryName, objectId, path, nextPage)).setEnabled(hasMore));

		add(new BookmarkablePageLink<Void>("firstPageBottom", HistoryPage.class, WicketUtils.newPathParameter(repositoryName, objectId, path)).setEnabled(pageNumber > 1));
		add(new BookmarkablePageLink<Void>("prevPageBottom", HistoryPage.class, WicketUtils.newHistoryPageParameter(repositoryName, objectId, path, prevPage)).setEnabled(pageNumber > 1));
		add(new BookmarkablePageLink<Void>("nextPageBottom", HistoryPage.class, WicketUtils.newHistoryPageParameter(repositoryName, objectId, path, nextPage)).setEnabled(hasMore));

	}

	@Override
	protected String getPageName() {
		return getString("gb.history");
	}
}
