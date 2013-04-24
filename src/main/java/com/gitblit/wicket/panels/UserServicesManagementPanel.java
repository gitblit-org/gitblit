package com.gitblit.wicket.panels;

import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.StatelessForm;

public class UserServicesManagementPanel extends BasePanel {

	public UserServicesManagementPanel(String wicketId) {
		super(wicketId);
		Button saveOrder = new Button("saveOrder");
		StatelessForm<Void> form = new StatelessForm<Void>("ugmForm") {

			private static final long serialVersionUID = 1463035789722273210L;

			@Override
			public void onSubmit() {
				System.out.println("SHB save order clicked");
			}
		};
		form.add(saveOrder);
		for (String userService : userServices) {
			form.add(new UserServiceConfigPanel(userService, userService));
		}
		add(form);		
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 6037038749205815615L;
	private static final String[] userServices = new String[] { "internal",
			"ldap", "crowd", "redmine" };

}
