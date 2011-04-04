package com.gitblit.wicket.pages;

import com.gitblit.wicket.BasePage;
import com.gitblit.wicket.panels.PageFooter;
import com.gitblit.wicket.panels.PageHeader;


public class AboutPage extends BasePage {

	public AboutPage() {
		add(new PageHeader("pageHeader"));

		add(new PageFooter("pageFooter"));
	}
}
