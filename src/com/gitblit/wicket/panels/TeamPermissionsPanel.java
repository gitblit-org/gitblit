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
import com.gitblit.models.TeamAccessPermission;
import com.gitblit.utils.DeepCopier;

/**
 * Allows user to manipulate user access permissions.
 * 
 * @author James Moger
 *
 */
public class TeamPermissionsPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	public TeamPermissionsPanel(String wicketId, final List<TeamAccessPermission> permissions, final Map<AccessPermission, String> translations) {
		super(wicketId);
		
		// update existing permissions repeater
		RefreshingView<TeamAccessPermission> dataView = new RefreshingView<TeamAccessPermission>("permissionRow") {
			private static final long serialVersionUID = 1L;
		
			@Override
            protected Iterator<IModel<TeamAccessPermission>> getItemModels() {
                // the iterator returns RepositoryPermission objects, but we need it to
                // return models
                return new ModelIteratorAdapter<TeamAccessPermission>(permissions.iterator()) {
                    @Override
                    protected IModel<TeamAccessPermission> model(TeamAccessPermission permission) {
                        return new CompoundPropertyModel<TeamAccessPermission>(permission);
                    }
                };
            }

            @Override
            protected Item<TeamAccessPermission> newItem(String id, int index, IModel<TeamAccessPermission> model) {
                // this item sets markup class attribute to either 'odd' or
                // 'even' for decoration
                return new OddEvenItem<TeamAccessPermission>(id, index, model);
            }
            
			public void populateItem(final Item<TeamAccessPermission> item) {
				final TeamAccessPermission entry = item.getModelObject();
				item.add(new Label("team", entry.team));

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

		// filter out teams we already have permissions for
		final List<String> teams = GitBlit.self().getAllTeamnames();
		for (TeamAccessPermission tp : permissions) {
			teams.remove(tp.team);
		}

		// add new permission form
		IModel<TeamAccessPermission> addPermissionModel = new CompoundPropertyModel<TeamAccessPermission>(new TeamAccessPermission());
		Form<TeamAccessPermission> addPermissionForm = new Form<TeamAccessPermission>("addPermissionForm", addPermissionModel);
		addPermissionForm.add(new DropDownChoice<String>("team", teams));
		addPermissionForm.add(new DropDownChoice<AccessPermission>("permission", Arrays
				.asList(AccessPermission.NEWPERMISSIONS), new AccessPermissionRenderer(translations)));
		AjaxButton button = new AjaxButton("addPermissionButton", addPermissionForm) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
				// add permission to our list
				TeamAccessPermission tp = (TeamAccessPermission) form.getModel().getObject();
				permissions.add(DeepCopier.copy(tp));
				
				// remove team from available choices
				teams.remove(tp.team);
				
				// force the panel to refresh
				target.addComponent(TeamPermissionsPanel.this);
			}
		};
		addPermissionForm.add(button);
		
		// only show add permission form if we have a team choice
		add(addPermissionForm.setVisible(teams.size() > 0));
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
