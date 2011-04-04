package com.gitblit.wicket.panels;

import javax.servlet.http.HttpServletRequest;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.protocol.http.servlet.ServletWebRequest;

import com.gitblit.Constants;
import com.gitblit.StoredSettings;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.pages.SummaryPage;


public class PageHeader extends Panel {

	private static final long serialVersionUID = 1L;

	public PageHeader(String id) {
		this(id, "", "");
	}

	public PageHeader(String id, String repositoryName, String page) {
		super(id);
		if (repositoryName != null && repositoryName.trim().length() > 0) {
			add(new Label("title", getServerName() + " - " + repositoryName));
		} else {
			add(new Label("title", getServerName()));
		}
		add(new Label("siteName", StoredSettings.getString("siteName", Constants.NAME)));
		add(new LinkPanel("repositoryName", null, repositoryName, SummaryPage.class, new PageParameters("p=" + repositoryName)));
		add(new Label("pageName", page));
	}

	protected String getServerName() {
		ServletWebRequest servletWebRequest = (ServletWebRequest) getRequest();
		HttpServletRequest req = servletWebRequest.getHttpServletRequest();
		return req.getServerName();
	}
}