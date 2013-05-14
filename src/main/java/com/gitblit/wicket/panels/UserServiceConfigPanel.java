package com.gitblit.wicket.panels;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.wicket.ajax.AjaxEventBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.HiddenField;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.html.form.StatelessForm;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.RefreshingView;
import org.apache.wicket.markup.repeater.util.ModelIteratorAdapter;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;

public class UserServiceConfigPanel extends BasePanel {
	
	private boolean connectorIsVisible = false, mappingIsVisible = false;
	public static final String CSS_INVISIBLE_UGM = "invisibleUgmForm";
	public static final String CSS_VISIBLE_UGM = "visibleUgmForm";

	public UserServiceConfigPanel(String wicketId, String title,
			Set<String> connectorParams, Set<String> systemRoles,
			final List<RoleMapping> alreadyDefinedRoleModels) {
		super(wicketId);

		Label titleLabel = new Label("titleLabel", title);
		Label connectorTitle = new Label("ugmConnectorDetailsTitle",
				"Connector Settings");
		Label mappingTitle = new Label("ugmMappingRulesTitle",
				"Mapping Rules Form");

		StatelessForm<Void> form = new StatelessForm<Void>("ugmPanelForm");

		Button connectorButton = new Button("connector");

		Button mappingButton = new Button("mapping");
	
		form.add(titleLabel);
		form.add(connectorButton);
		form.add(mappingButton);
		form.add(connectorTitle);

		ArrayList<String> params = new ArrayList<String>();
		for (String string : connectorParams) {
			params.add(string.replace(wicketId + ".", ""));
		}
		ListView<String> listview = new ListView<String>("listview", params) {
			protected void populateItem(ListItem<String> item) {
				item.add(new HiddenField("paramField", item.getModel()));
				Label label = new Label("label", item.getModelObject().replace(
						"synchronize", "sync"));
				item.add(label);
				IModel<String> valueModel = new IModel<String>() {

					private static final long serialVersionUID = 1L;
					private String value = "";

					@Override
					public void detach() {
						// TODO Auto-generated method stub

					}

					@Override
					public String getObject() {
						return this.value;
					}

					@Override
					public void setObject(String object) {
						this.value = object;

					}
				};
				item.add(new TextField("valueField", valueModel));
			}
		};
		form.add(listview);
		final ArrayList<String> roles = new ArrayList<String>();
		for (String string : systemRoles) {
			roles.add(string);
		}
		form.add(mappingTitle);
		RefreshingView<RoleMapping> listRolesview = new RefreshingView<RoleMapping>(
				"listRolesview") {

			private static final long serialVersionUID = 1L;

			@Override
			protected Iterator<IModel<RoleMapping>> getItemModels() {
				return new ModelIteratorAdapter<RoleMapping>(
						alreadyDefinedRoleModels.iterator()) {
					@Override
					protected IModel<RoleMapping> model(RoleMapping mapping) {
						return new CompoundPropertyModel<RoleMapping>(mapping);
					}
				};
			}

			@Override
			protected void populateItem(Item<RoleMapping> item) {
				IModel<String> remoteRoleModel = new IModel<String>() {

					private static final long serialVersionUID = 1L;
					private String value = "";

					@Override
					public void detach() {
						// TODO Auto-generated method stub

					}

					@Override
					public String getObject() {
						return this.value;
					}

					@Override
					public void setObject(String object) {
						this.value = object;

					}
				};
				remoteRoleModel.setObject(((RoleMapping) item.getModelObject()).getRemoteRole()); 
				item.add(new TextField("remoteRole", remoteRoleModel));
				
				
				
				DropDownChoice<String> systemRolesChoice = new DropDownChoice<String>(
						"systemRoleSelection", roles,new DropDownTranslationRenderer());
				item.add(systemRolesChoice);
			}
		};
		
		
		form.add(listRolesview);

		add(form);		
	}

	private class DropDownTranslationRenderer implements IChoiceRenderer<String> {
		/**
		 * 
		 */
		private static final long serialVersionUID = 4282172447769549480L;
		
		public DropDownTranslationRenderer() {
		}

		@Override
		public Object getDisplayValue(String object) {
			return getString("gb.realm.ugm.role."+object);
		}

		@Override
		public String getIdValue(String object, int index) {
			return object;
		}
	}
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -8466354580570112415L;

}
