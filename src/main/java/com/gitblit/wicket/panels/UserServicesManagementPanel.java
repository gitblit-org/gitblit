package com.gitblit.wicket.panels;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

		Set<String> connectorParamsInternal = new HashSet<String>();
		Set<String> connectorParamsLdap = new HashSet<String>();
		Set<String> connectorParamsCrowd = new HashSet<String>();
		Set<String> connectorParamsRedmine = new HashSet<String>();

		connectorParamsLdap.add("realm.ldap.server");
		connectorParamsLdap.add("realm.ldap.username");
		connectorParamsLdap.add("realm.ldap.password");
		connectorParamsLdap.add("realm.ldap.accountBase");
		connectorParamsLdap.add("realm.ldap.groupMemberPattern");
		connectorParamsLdap.add("realm.ldap.displayName");
		connectorParamsLdap.add("realm.ldap.email");
		connectorParamsLdap.add("realm.ldap.ldapCachePeriod");
		connectorParamsLdap.add("realm.ldap.synchronizeUsers.enable");
		connectorParamsLdap.add("realm.ldap.synchronizeUsers.removeDeleted");
		connectorParamsLdap.add("realm.ldap.uid");

		connectorParamsRedmine.add("realm.redmine.url");

		connectorParamsCrowd.add("realm.crowd.url");
		connectorParamsCrowd.add("realm.crowd.appname");
		connectorParamsCrowd.add("realm.crowd.apppassword");

		ArrayList<Set<String>> connectorParams = new ArrayList<Set<String>>();
		connectorParams.add(connectorParamsInternal);
		connectorParams.add(connectorParamsLdap);
		connectorParams.add(connectorParamsCrowd);
		connectorParams.add(connectorParamsRedmine);

		for (int i = 0; i < userServices.length; i++) {
			List<RoleMapping> dummyRoleMappings = new ArrayList<RoleMapping>();
			dummyRoleMappings.add(new RoleMapping("dummyRole", RoleMapping.SYSTEM_USER));
			form.add(new UserServiceConfigPanel(userServices[i],
					userServicesNames[i], connectorParams.get(i), RoleMapping
					.getSystemRoles(), dummyRoleMappings));
			add(form);
		}

	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 6037038749205815615L;
	private static final String[] userServices = new String[] { "internal",
			"realm.ldap", "realm.crowd", "realm.redmine" };
	private static final String[] userServicesNames = new String[] {
			"Gitblit User and Group Management", "LDAP Directory",
			"Atlassian Crowd", "Redmine" };

}
