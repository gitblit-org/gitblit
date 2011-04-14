package com.gitblit.wicket.pages;

import org.apache.wicket.PageParameters;

import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.panels.BranchesPanel;

public class BranchesPage extends RepositoryPage {

	public BranchesPage(PageParameters params) {
		super(params);

		add(new BranchesPanel("branchesPanel", repositoryName, getRepository(), -1));
	}

	@Override
	protected String getPageName() {
		return getString("gb.branches");
	}
}
