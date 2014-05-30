/*
 * Copyright 2014 gitblit.com.
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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Radio;
import org.apache.wicket.markup.html.form.RadioGroup;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.Model;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.AuthorizationControl;
import com.gitblit.Keys;
import com.gitblit.models.RepositoryModel;
import com.gitblit.wicket.WicketUtils;

/**
 * A radio group panel of the 5 available authorization/access restriction combinations.
 *
 * @author James Moger
 *
 */
public class RepositoryPermissionPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	private final RepositoryModel repository;

	private RadioGroup<Permission> permissionGroup;

	public RepositoryPermissionPanel(String wicketId, RepositoryModel repository) {
		super(wicketId);
		this.repository = repository;
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();

		Permission anonymousPermission = new Permission(getString("gb.anonymousPush"),
				getString("gb.anonymousPushDescription"),
				"blank.png",
				AuthorizationControl.AUTHENTICATED,
				AccessRestrictionType.NONE);

		Permission authenticatedPermission = new Permission(getString("gb.pushRestrictedAuthenticated"),
				getString("gb.pushRestrictedAuthenticatedDescription"),
				"lock_go_16x16.png",
				AuthorizationControl.AUTHENTICATED,
				AccessRestrictionType.PUSH);

		Permission publicPermission = new Permission(getString("gb.pushRestrictedNamed"),
				getString("gb.pushRestrictedNamedDescription"),
				"lock_go_16x16.png",
				AuthorizationControl.NAMED,
				AccessRestrictionType.PUSH);

		Permission protectedPermission = new Permission(getString("gb.cloneRestricted"),
				getString("gb.cloneRestrictedDescription"),
				"lock_pull_16x16.png",
				AuthorizationControl.NAMED,
				AccessRestrictionType.CLONE);

		Permission privatePermission = new Permission(getString("gb.private"),
				getString("gb.privateRepoDescription"),
				"shield_16x16.png",
				AuthorizationControl.NAMED,
				AccessRestrictionType.VIEW);

		List<Permission> permissions = new ArrayList<Permission>();
		if (app().settings().getBoolean(Keys.git.allowAnonymousPushes, false)) {
			permissions.add(anonymousPermission);
		}
		permissions.add(authenticatedPermission);
		permissions.add(publicPermission);
		permissions.add(protectedPermission);
		permissions.add(privatePermission);

		AccessRestrictionType defaultRestriction = repository.accessRestriction;
		if (defaultRestriction == null) {
			defaultRestriction = AccessRestrictionType.fromName(app().settings().getString(Keys.git.defaultAccessRestriction,
					AccessRestrictionType.PUSH.name()));
		}

		AuthorizationControl defaultControl = repository.authorizationControl;
		if (defaultControl == null) {
			defaultControl = AuthorizationControl.fromName(app().settings().getString(Keys.git.defaultAuthorizationControl,
					AuthorizationControl.NAMED.name()));
		}

		Permission defaultPermission = publicPermission;
		for (Permission permission : permissions) {
			if (permission.type == defaultRestriction && permission.control == defaultControl) {
				defaultPermission = permission;
			}
		}

		permissionGroup = new RadioGroup<>("permissionsGroup", new Model<Permission>(defaultPermission));
		ListView<Permission> permissionsList = new ListView<Permission>("permissions", permissions) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void populateItem(ListItem<Permission> item) {
				Permission p = item.getModelObject();
				item.add(new Radio<Permission>("radio", item.getModel()));
				item.add(WicketUtils.newImage("image",  p.image));
				item.add(new Label("name", p.name));
				item.add(new Label("description", p.description));
			}
		};
		permissionGroup.add(permissionsList);

		setOutputMarkupId(true);

		add(permissionGroup);
	}

	public void updateModel(RepositoryModel repository) {
		Permission permission = permissionGroup.getModelObject();
		repository.authorizationControl = permission.control;
		repository.accessRestriction = permission.type;
	}

	@Override
	protected boolean getStatelessHint() {
		return false;
	}

	private static class Permission implements Serializable {

		private static final long serialVersionUID = 1L;

		final String name;
		final String description;
		final String image;
		final AuthorizationControl control;
		final AccessRestrictionType type;

		Permission(String name, String description, String img, AuthorizationControl control, AccessRestrictionType type) {
			this.name = name;
			this.description = description;
			this.image = img;
			this.control = control;
			this.type = type;
		}

		@Override
		public String toString() {
			return name;
		}
	}
}
