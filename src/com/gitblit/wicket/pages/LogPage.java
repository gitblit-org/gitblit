package com.gitblit.wicket.pages;

import org.apache.wicket.PageParameters;

import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.panels.LogPanel;


public class LogPage extends RepositoryPage {

	public LogPage(PageParameters params) {
		super(params);

		add(new LogPanel("logPanel", repositoryName, getRepository(), 100, true));
	}
	
	@Override
	protected String getPageName() {
		return getString("gb.log");
	}
}
