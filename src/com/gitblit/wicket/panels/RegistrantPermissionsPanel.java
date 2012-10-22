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

import java.util.ArrayList;
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
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.utils.DeepCopier;
import com.gitblit.wicket.WicketUtils;

/**
 * Allows user to manipulate registrant access permissions.
 * 
 * @author James Moger
 *
 */
public class RegistrantPermissionsPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	public RegistrantPermissionsPanel(String wicketId, List<String> allRegistrants, final List<RegistrantAccessPermission> permissions, final Map<AccessPermission, String> translations) {
		super(wicketId);
		
		// update existing permissions repeater
		RefreshingView<RegistrantAccessPermission> dataView = new RefreshingView<RegistrantAccessPermission>("permissionRow") {
			private static final long serialVersionUID = 1L;
		
			@Override
            protected Iterator<IModel<RegistrantAccessPermission>> getItemModels() {
                // the iterator returns RepositoryPermission objects, but we need it to
                // return models
                return new ModelIteratorAdapter<RegistrantAccessPermission>(permissions.iterator()) {
                    @Override
                    protected IModel<RegistrantAccessPermission> model(RegistrantAccessPermission permission) {
                        return new CompoundPropertyModel<RegistrantAccessPermission>(permission);
                    }
                };
            }

            @Override
            protected Item<RegistrantAccessPermission> newItem(String id, int index, IModel<RegistrantAccessPermission> model) {
                // this item sets markup class attribute to either 'odd' or
                // 'even' for decoration
                return new OddEvenItem<RegistrantAccessPermission>(id, index, model);
            }
            
			public void populateItem(final Item<RegistrantAccessPermission> item) {
				final RegistrantAccessPermission entry = item.getModelObject();
				item.add(new Label("registrant", entry.registrant));
				if (entry.isExplicit) {
					item.add(new Label("regex", "").setVisible(false));
				} else {
					Label regex = new Label("regex", "regex");
					WicketUtils.setHtmlTooltip(regex, getString("gb.regexPermission"));
					item.add(regex);
				}

				// use ajax to get immediate update of permission level change
				// otherwise we can lose it if they change levels and then add
				// a new repository permission
				final DropDownChoice<AccessPermission> permissionChoice = new DropDownChoice<AccessPermission>(
						"permission", Arrays.asList(AccessPermission.values()), new AccessPermissionRenderer(translations));
				// only allow changing an explicitly defined permission
				// this is designed to prevent changing a regex permission in
				// a repository
				permissionChoice.setEnabled(entry.isExplicit);
				if (entry.isExplicit) {
					permissionChoice.add(new AjaxFormComponentUpdatingBehavior("onchange") {
		           
						private static final long serialVersionUID = 1L;

						protected void onUpdate(AjaxRequestTarget target) {
							target.addComponent(permissionChoice);
						}
					});
				}

				item.add(permissionChoice);
			}
		};
		add(dataView);
		setOutputMarkupId(true);

		// filter out registrants we already have permissions for
		final List<String> registrants = new ArrayList<String>(allRegistrants);
		for (RegistrantAccessPermission rp : permissions) {
			registrants.remove(rp.registrant);
		}

		// add new permission form
		IModel<RegistrantAccessPermission> addPermissionModel = new CompoundPropertyModel<RegistrantAccessPermission>(new RegistrantAccessPermission());
		Form<RegistrantAccessPermission> addPermissionForm = new Form<RegistrantAccessPermission>("addPermissionForm", addPermissionModel);
		addPermissionForm.add(new DropDownChoice<String>("registrant", registrants));
		addPermissionForm.add(new DropDownChoice<AccessPermission>("permission", Arrays
				.asList(AccessPermission.NEWPERMISSIONS), new AccessPermissionRenderer(translations)));
		AjaxButton button = new AjaxButton("addPermissionButton", addPermissionForm) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
				// add permission to our list
				RegistrantAccessPermission rp = (RegistrantAccessPermission) form.getModel().getObject();
				permissions.add(DeepCopier.copy(rp));
				
				// remove registrant from available choices
				registrants.remove(rp.registrant);
				
				// force the panel to refresh
				target.addComponent(RegistrantPermissionsPanel.this);
			}
		};
		addPermissionForm.add(button);
		
		// only show add permission form if we have a registrant choice
		add(addPermissionForm.setVisible(registrants.size() > 0));
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
