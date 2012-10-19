/*
 * Copyright 2012 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.wicket.panels;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.OddEvenItem;
import org.apache.wicket.markup.repeater.RefreshingView;
import org.apache.wicket.markup.repeater.util.ModelIteratorAdapter;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.GitBlit;
import com.gitblit.models.UserAccessPermission;
import com.gitblit.utils.DeepCopier;

/**
 * Allows user to manipulate user access permissions.
 * 
 * @author James Moger
 *
 */
public class UserPermissionsPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	public UserPermissionsPanel(String wicketId, final List<UserAccessPermission> permissions, final Map<AccessPermission, String> translations) {
		super(wicketId);
		
		// update existing permissions repeater
		RefreshingView<UserAccessPermission> dataView = new RefreshingView<UserAccessPermission>("permissionRow") {
			private static final long serialVersionUID = 1L;
		
			@Override
            protected Iterator<IModel<UserAccessPermission>> getItemModels() {
                // the iterator returns RepositoryPermission objects, but we need it to
                // return models
                return new ModelIteratorAdapter<UserAccessPermission>(permissions.iterator()) {
                    @Override
                    protected IModel<UserAccessPermission> model(UserAccessPermission permission) {
                        return new CompoundPropertyModel<UserAccessPermission>(permission);
                    }
                };
            }

            @Override
            protected Item<UserAccessPermission> newItem(String id, int index, IModel<UserAccessPermission> model) {
                // this item sets markup class attribute to either 'odd' or
                // 'even' for decoration
                return new OddEvenItem<UserAccessPermission>(id, index, model);
            }
            
			public void populateItem(final Item<UserAccessPermission> item) {
				final UserAccessPermission entry = item.getModelObject();
				item.add(new Label("user", entry.user));

				// use ajax to get immediate update of permission level change
				// otherwise we can lose it if they change levels and then add
				// a new repository permission
				final DropDownChoice<AccessPermission> permissionChoice = new DropDownChoice<AccessPermission>(
						"permission", Arrays.asList(AccessPermission.values()), new AccessPermissionRenderer(translations));
				permissionChoice.add(new AjaxFormComponentUpdatingBehavior("onchange") {
		           
					private static final long serialVersionUID = 1L;

					protected void onUpdate(AjaxRequestTarget target) {
		                target.addComponent(permissionChoice);
		            }
		        });

				item.add(permissionChoice);
			}
		};
		add(dataView);
		setOutputMarkupId(true);

		// filter out users we already have permissions for
		final List<String> users = GitBlit.self().getAllUsernames();
		for (UserAccessPermission up : permissions) {
			users.remove(up.user);
		}

		// add new permission form
		IModel<UserAccessPermission> addPermissionModel = new CompoundPropertyModel<UserAccessPermission>(new UserAccessPermission());
		Form<UserAccessPermission> addPermissionForm = new Form<UserAccessPermission>("addPermissionForm", addPermissionModel);
		addPermissionForm.add(new DropDownChoice<String>("user", users));
		addPermissionForm.add(new DropDownChoice<AccessPermission>("permission", Arrays
				.asList(AccessPermission.NEWPERMISSIONS), new AccessPermissionRenderer(translations)));
		AjaxButton button = new AjaxButton("addPermissionButton", addPermissionForm) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
				// add permission to our list
				UserAccessPermission up = (UserAccessPermission) form.getModel().getObject();
				permissions.add(DeepCopier.copy(up));
				
				// remove user from available choices
				users.remove(up.user);
				
				// force the panel to refresh
				target.addComponent(UserPermissionsPanel.this);
			}
		};
		addPermissionForm.add(button);
		
		// only show add permission form if we have a user choice
		add(addPermissionForm.setVisible(users.size() > 0));
	}
	
	private class AccessPermissionRenderer implements IChoiceRenderer<AccessPermission> {

		private static final long serialVersionUID = 1L;

		private final Map<AccessPermission, String> map;

		public AccessPermissionRenderer(Map<AccessPermission, String> map) {
			this.map = map;
		}

		@Override
		public String getDisplayValue(AccessPermission type) {
			return map.get(type);
		}

		@Override
		public String getIdValue(AccessPermission type, int index) {
			return Integer.toString(index);
		}
	}
}
