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

import org.apache.wicket.ajax.form.AjaxFormChoiceComponentUpdatingBehavior;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Radio;
import org.apache.wicket.markup.html.form.RadioGroup;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.AuthorizationControl;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.models.RepositoryModel;
import com.gitblit.wicket.WicketUtils;

/**
 * A radio group panel of the 5 available authorization/access restriction combinations.
 *
 * @author James Moger
 *
 */
public class AccessPolicyPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	private final RepositoryModel repository;

	private final AjaxFormChoiceComponentUpdatingBehavior callback;
	
	private final boolean allowAnonymousClones;

	private RadioGroup<AccessPolicy> policiesGroup;

	public AccessPolicyPanel(String wicketId, RepositoryModel repository, boolean allowAnonymousClones) {
		this(wicketId, repository, null, allowAnonymousClones);
	}

	public AccessPolicyPanel(String wicketId, RepositoryModel repository, AjaxFormChoiceComponentUpdatingBehavior callback, boolean allowAnonymousClones) {
		super(wicketId);
		this.repository = repository;
		this.callback = callback;
		this.allowAnonymousClones = allowAnonymousClones;
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();

		AccessPolicy anonymousPolicy = new AccessPolicy(getString("gb.anonymousPolicy"),
				getString("gb.anonymousPolicyDescription"),
				"blank.png",
				AuthorizationControl.AUTHENTICATED,
				AccessRestrictionType.NONE);

		AccessPolicy authenticatedPushPolicy = new AccessPolicy(getString("gb.authenticatedPushPolicy"),
				getString("gb.authenticatedPushPolicyDescription"),
				"lock_go_16x16.png",
				AuthorizationControl.AUTHENTICATED,
				AccessRestrictionType.PUSH);

		AccessPolicy namedPushPolicy = new AccessPolicy(getString("gb.namedPushPolicy"),
				getString("gb.namedPushPolicyDescription"),
				"lock_go_16x16.png",
				AuthorizationControl.NAMED,
				AccessRestrictionType.PUSH);

		AccessPolicy clonePolicy = new AccessPolicy(getString("gb.clonePolicy"),
				getString("gb.clonePolicyDescription"),
				"lock_pull_16x16.png",
				AuthorizationControl.NAMED,
				AccessRestrictionType.CLONE);

		AccessPolicy viewPolicy = new AccessPolicy(getString("gb.viewPolicy"),
				getString("gb.viewPolicyDescription"),
				"shield_16x16.png",
				AuthorizationControl.NAMED,
				AccessRestrictionType.VIEW);

		List<AccessPolicy> policies = new ArrayList<AccessPolicy>();
		if (app().settings().getBoolean(Keys.git.allowAnonymousPushes, false)) {
			policies.add(anonymousPolicy);
		}
		if (!allowAnonymousClones) {
		    policies.add(authenticatedPushPolicy);
		    policies.add(namedPushPolicy);
		}
		policies.add(clonePolicy);
		policies.add(viewPolicy);

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

		AccessPolicy defaultPolicy = namedPushPolicy;
		for (AccessPolicy policy : policies) {
			if (policy.type == defaultRestriction && policy.control == defaultControl) {
				defaultPolicy = policy;
			}
		}

		policiesGroup = new RadioGroup<>("policiesGroup", new Model<AccessPolicy>(defaultPolicy));
		ListView<AccessPolicy> policiesList = new ListView<AccessPolicy>("policies", policies) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void populateItem(ListItem<AccessPolicy> item) {
				AccessPolicy p = item.getModelObject();
				item.add(new Radio<AccessPolicy>("radio", item.getModel()));
				item.add(WicketUtils.newImage("image",  p.image));
				item.add(new Label("name", p.name));
				item.add(new Label("description", p.description));
			}
		};
		policiesGroup.add(policiesList);
		if (callback != null) {
			policiesGroup.add(callback);
			policiesGroup.setOutputMarkupId(true);
		}
		add(policiesGroup);

		if (app().settings().getBoolean(Keys.web.allowForking, true)) {
			Fragment fragment = new Fragment("allowForks", "allowForksFragment", this);
			fragment.add(new BooleanOption("allowForks",
				getString("gb.allowForks"),
				getString("gb.allowForksDescription"),
				new PropertyModel<Boolean>(repository, "allowForks")));
			add(fragment);
		} else {
			add(new Label("allowForks").setVisible(false));
		}

		setOutputMarkupId(true);
	}

	public void updateModel(RepositoryModel repository) {
		AccessPolicy policy = policiesGroup.getModelObject();
		repository.authorizationControl = policy.control;
		repository.accessRestriction = policy.type;
	}

	@Override
	protected boolean getStatelessHint() {
		return false;
	}

	public static class AccessPolicy implements Serializable {

		private static final long serialVersionUID = 1L;

		final String name;
		final String description;
		final String image;
		final AuthorizationControl control;
		final AccessRestrictionType type;

		AccessPolicy(String name, String description, String img, AuthorizationControl control, AccessRestrictionType type) {
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
