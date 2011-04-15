package com.gitblit.wicket;

import org.apache.wicket.markup.html.WebPage;

public class LogoutPage extends WebPage {

	public LogoutPage() {
		getSession().invalidate();
		setRedirect(true);
		setResponsePage(getApplication().getHomePage());
	}
}