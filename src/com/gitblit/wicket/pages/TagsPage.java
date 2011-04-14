package com.gitblit.wicket.pages;

import org.apache.wicket.PageParameters;

import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.panels.TagsPanel;

public class TagsPage extends RepositoryPage {

	public TagsPage(PageParameters params) {
		super(params);

		add(new TagsPanel("tagsPanel", repositoryName, getRepository(), -1));

	}

	@Override
	protected String getPageName() {
		return getString("gb.tags");
	}
}
