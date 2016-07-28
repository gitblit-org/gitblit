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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.OddEvenItem;
import org.apache.wicket.markup.repeater.RefreshingView;
import org.apache.wicket.markup.repeater.util.ModelIteratorAdapter;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.eclipse.jgit.lib.PersonIdent;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.PermissionType;
import com.gitblit.Constants.RegistrantType;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.UserModel;
import com.gitblit.utils.DeepCopier;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.WicketUtils;

/**
 * Allows user to manipulate registrant access permissions.
 *
 * @author James Moger
 *
 */
public class RegistrantPermissionsPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	public enum Show {
		specified, mutable, effective;

		public boolean show(RegistrantAccessPermission ap) {
			switch (this) {
			case specified:
				return ap.mutable || ap.isOwner();
			case mutable:
				return ap.mutable;
			case effective:
				return true;
			default:
				return true;
			}
		}
	}

	private Show activeState = Show.mutable;

	public RegistrantPermissionsPanel(String wicketId, RegistrantType registrantType, List<String> allRegistrants, final List<RegistrantAccessPermission> permissions, final Map<AccessPermission, String> translations) {
		super(wicketId);
		setOutputMarkupId(true);

		/*
		 * Permission view toggle buttons
		 */
		Form<Void> permissionToggleForm = new Form<Void>("permissionToggleForm");
		permissionToggleForm.add(new ShowStateButton("showSpecified", Show.specified));
		permissionToggleForm.add(new ShowStateButton("showMutable", Show.mutable));
		permissionToggleForm.add(new ShowStateButton("showEffective", Show.effective));
		add(permissionToggleForm);

		/*
		 * Permission repeating display
		 */
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

			@Override
			public void populateItem(final Item<RegistrantAccessPermission> item) {
				final RegistrantAccessPermission entry = item.getModelObject();
				if (RegistrantType.REPOSITORY.equals(entry.registrantType)) {
					String repoName = StringUtils.stripDotGit(entry.registrant);
					if (!entry.isMissing() && StringUtils.findInvalidCharacter(repoName) == null) {
						// repository, strip .git and show swatch
						Fragment repositoryFragment = new Fragment("registrant", "repositoryRegistrant", RegistrantPermissionsPanel.this);
						Component swatch = new Label("repositorySwatch", "&nbsp;").setEscapeModelStrings(false);
						WicketUtils.setCssBackground(swatch, entry.toString());
						repositoryFragment.add(swatch);
						Label registrant = new Label("repositoryName", repoName);
						repositoryFragment.add(registrant);
						item.add(repositoryFragment);
					} else {
						// regex or missing
						Label label = new Label("registrant", entry.registrant);
						WicketUtils.setCssStyle(label, "font-weight: bold;");
						item.add(label);
					}
				} else if (RegistrantType.USER.equals(entry.registrantType)) {
					// user
					PersonIdent ident = new PersonIdent(entry.registrant, "");
					UserModel user = app().users().getUserModel(entry.registrant);
					if (user != null) {
						ident = new PersonIdent(user.getDisplayName(), user.emailAddress == null ? user.getDisplayName() : user.emailAddress);
					}

					Fragment userFragment = new Fragment("registrant", "userRegistrant", RegistrantPermissionsPanel.this);
					userFragment.add(new AvatarImage("userAvatar", ident, 20));
					userFragment.add(new Label("userName", entry.registrant));
					item.add(userFragment);
				} else {
					// team
					Fragment teamFragment = new Fragment("registrant", "teamRegistrant", RegistrantPermissionsPanel.this);
					teamFragment.add(new Label("teamName", entry.registrant));
					item.add(teamFragment);
				}
				switch (entry.permissionType) {
				case ADMINISTRATOR:
					Label administrator = new Label("pType", entry.source == null ? getString("gb.administrator") : entry.source);
					WicketUtils.setHtmlTooltip(administrator, getString("gb.administratorPermission"));
					WicketUtils.setCssClass(administrator, "label label-inverse");
					item.add(administrator);
					break;
				case OWNER:
					Label owner = new Label("pType", getString("gb.owner"));
					WicketUtils.setHtmlTooltip(owner, getString("gb.ownerPermission"));
					WicketUtils.setCssClass(owner, "label label-info");
					item.add(owner);
					break;
				case TEAM:
					Label team = new Label("pType", entry.source == null ? getString("gb.team") : entry.source);
					WicketUtils.setHtmlTooltip(team, MessageFormat.format(getString("gb.teamPermission"), entry.source));
					WicketUtils.setCssClass(team, "label label-success");
					item.add(team);
					break;
				case REGEX:
					Label regex = new Label("pType", "regex");
					if (!StringUtils.isEmpty(entry.source)) {
						WicketUtils.setHtmlTooltip(regex, MessageFormat.format(getString("gb.regexPermission"), entry.source));
					}
					WicketUtils.setCssClass(regex, "label");
					item.add(regex);
					break;
				default:
					if (entry.isMissing()) {
						// repository is missing, this permission will be removed on save
						Label missing = new Label("pType", getString("gb.missing"));
						WicketUtils.setCssClass(missing, "label label-important");
						WicketUtils.setHtmlTooltip(missing, getString("gb.missingPermission"));
						item.add(missing);
					} else {
						// standard permission
						item.add(new Label("pType", "").setVisible(false));
					}
					break;
				}

				item.setVisible(activeState.show(entry));

				// use ajax to get immediate update of permission level change
				// otherwise we can lose it if they change levels and then add
				// a new repository permission
				final DropDownChoice<AccessPermission> permissionChoice = new DropDownChoice<AccessPermission>(
						"permission", Arrays.asList(AccessPermission.values()), new AccessPermissionRenderer(translations));
				// only allow changing an explicitly defined permission
				// this is designed to prevent changing a regex permission in
				// a repository
				permissionChoice.setEnabled(entry.mutable);
				permissionChoice.setOutputMarkupId(true);
				if (entry.mutable) {
					permissionChoice.add(new AjaxFormComponentUpdatingBehavior("onchange") {

						private static final long serialVersionUID = 1L;

						@Override
						protected void onUpdate(AjaxRequestTarget target) {
							target.add(permissionChoice);
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
			if (rp.mutable) {
				// remove editable duplicates
				// this allows for specifying an explicit permission
				registrants.remove(rp.registrant);
			} else if (rp.isAdmin()) {
				// administrators can not have their permission changed
				registrants.remove(rp.registrant);
			} else if (rp.isOwner()) {
				// owners can not have their permission changed
				registrants.remove(rp.registrant);
			}
		}

		/*
		 * Add permission form
		 */
		IModel<RegistrantAccessPermission> addPermissionModel = new CompoundPropertyModel<RegistrantAccessPermission>(new RegistrantAccessPermission(registrantType));
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
				if (rp.permission == null) {
					return;
				}
				if (rp.registrant == null) {
					return;
				}
				RegistrantAccessPermission copy = DeepCopier.copy(rp);
				if (StringUtils.findInvalidCharacter(copy.registrant) != null) {
					copy.permissionType = PermissionType.REGEX;
					copy.source = copy.registrant;
				}
				permissions.add(copy);

				// resort permissions after insert to convey idea of eval order
				Collections.sort(permissions);

				// remove registrant from available choices
				registrants.remove(rp.registrant);

				// force the panel to refresh
				target.add(RegistrantPermissionsPanel.this);
			}
		};
		addPermissionForm.add(button);

		// only show add permission form if we have a registrant choice
		add(addPermissionForm.setVisible(registrants.size() > 0));
	}

	@Override
	protected boolean getStatelessHint()
	{
		return false;
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

	private class ShowStateButton extends AjaxButton {
		private static final long serialVersionUID = 1L;

		Show buttonState;

		public ShowStateButton(String wicketId, Show state) {
			super(wicketId);
			this.buttonState = state;
			setOutputMarkupId(true);
		}

		@Override
		protected void onBeforeRender()
		{
			String cssClass = "btn";
			if (buttonState.equals(RegistrantPermissionsPanel.this.activeState)) {
				cssClass = "btn btn-info active";
			}
			WicketUtils.setCssClass(this, cssClass);
			super.onBeforeRender();
		}

		@Override
		protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
			RegistrantPermissionsPanel.this.activeState = buttonState;
			target.add(RegistrantPermissionsPanel.this);
		}
	};
}
