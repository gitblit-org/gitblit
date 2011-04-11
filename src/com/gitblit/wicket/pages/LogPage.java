package com.gitblit.wicket.pages;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;

import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.LogPanel;

public class LogPage extends RepositoryPage {

	public LogPage(PageParameters params) {
		super(params);

		int pageNumber = params.getInt("page", 1); // index from 1
		int prevPage = Math.max(0, pageNumber - 1);
		int nextPage = pageNumber + 1;

		add(new BookmarkablePageLink<Void>("firstPageTop", LogPage.class, WicketUtils.newObjectParameter(repositoryName, objectId)));
		add(new BookmarkablePageLink<Void>("prevPageTop", LogPage.class, WicketUtils.newLogPageParameter(repositoryName, objectId, prevPage)).setEnabled(pageNumber > 1));
		add(new BookmarkablePageLink<Void>("nextPageTop", LogPage.class, WicketUtils.newLogPageParameter(repositoryName, objectId, nextPage)));

		add(new LogPanel("logPanel", repositoryName, objectId, getRepository(), -1, pageNumber - 1));

		add(new BookmarkablePageLink<Void>("firstPageBottom", LogPage.class, WicketUtils.newObjectParameter(repositoryName, objectId)));
		add(new BookmarkablePageLink<Void>("prevPageBottom", LogPage.class, WicketUtils.newLogPageParameter(repositoryName, objectId, prevPage)).setEnabled(pageNumber > 1));
		add(new BookmarkablePageLink<Void>("nextPageBottom", LogPage.class, WicketUtils.newLogPageParameter(repositoryName, objectId, nextPage)));
	}

	@Override
	protected String getPageName() {
		return getString("gb.log");
	}
}
