package com.gitblit.wicket.panels;

import org.apache.wicket.ajax.AjaxEventBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.StatelessForm;
import org.apache.wicket.markup.html.panel.Panel;

public class UserServiceConfigPanel extends BasePanel {
    private class GroupMapToggle extends AjaxEventBehavior {
    	private Panel panelUnderControl;
		private CheckBox controller;
    	
    	public GroupMapToggle(CheckBox controller, Panel panelUnderControl) {
    		super("onchange");
    		this.panelUnderControl=panelUnderControl;
    		this.controller=controller;
    	}
		protected void onUpdate(AjaxRequestTarget target) {
			// enable/disable permissions panel based on access restriction
			System.out.println("SHB update");
			updateVisibility();
		}
		
		protected void updateVisibility() {
			boolean isSelected = false;
			
			if (this.controller != null && this.controller.getModelObject() != null) {
				isSelected = this.controller.getModelObject().booleanValue();
			}
			System.out.println("SHB "+isSelected+" input: "+this.controller.getInput()+" value: "+this.controller.getValue());
			System.out.println("SHB "+this.controller.toString());
			if (isSelected) {
				this.panelUnderControl.setVisible(true);
				this.panelUnderControl.setEnabled(true);
			} else {
				this.panelUnderControl.setVisible(false);
				this.panelUnderControl.setEnabled(false);
			}
//			this.panelUnderControl.render();
		}
		@Override
		protected void onEvent(AjaxRequestTarget target) {
			// TODO Auto-generated method stub
			System.out.println("SHB on event");
			updateVisibility();
		}
    }
	public UserServiceConfigPanel(String wicketId, String title) {
		super(wicketId);
		
//		Label titleLabel = new Label("titleLabel",title);
//		CheckBox authUsersBox = new CheckBox("authenticateUsers");
//		CheckBox mapGroupsBox = new CheckBox("mapGroups");
//		TextField remoteGroupToSystemAdminField = new TextField("remoteGroupToSystemAdmin");
//		TextField remoteGroupToRepoAdminField = new TextField("remoteGroupToRepoAdmin");
//		TextField remoteGroupToRepoCreatorField = new TextField("remoteGroupToRepoCreator");
//		CheckBox groupsForPermissionManagementBox = new CheckBox("groupsForPermissionManagement");
////		Label prefixLabel = new Label("prefixLabel","prefix");
//		TextField prefixField = new TextField("prefix"); 
////		CheckGroup options = new CheckGroup("userServiceOptions");
////		options.add(mapGroupsBox);
//		
////		MapGroupsPanel mapGroupsPanel = new MapGroupsPanel("mapGroupsSection");
////		GroupMapToggle groupMapToggle = new GroupMapToggle(mapGroupsBox,mapGroupsPanel);
////		groupMapToggle.updateVisibility();
////		mapGroupsBox.add(groupMapToggle);
//		
//		add(titleLabel);
//		add(authUsersBox);
//		add(mapGroupsBox);
//		add(remoteGroupToSystemAdminField);
//		add(remoteGroupToRepoAdminField);
//		add(remoteGroupToRepoCreatorField);
//		add(groupsForPermissionManagementBox);
////		add(prefixLabel);
//		add(prefixField);
////		add(options);
//		add(mapGroupsPanel);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = -8466354580570112415L;

}
