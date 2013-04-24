package com.gitblit.wicket.pages;

import com.gitblit.wicket.panels.UserServicesManagementPanel;

public class SystemManagementPage extends RootPage {

	public SystemManagementPage() {
		super();		
		setupPage("", "");
		add(new UserServicesManagementPanel("userServiceManagementPanel"));
	}
}
