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

import java.util.Arrays;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.models.UserModel;
import com.gitblit.transport.ssh.SshKey;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;


/**
 * A panel that enumerates and manages SSH public keys.
 *
 * @author James Moger
 *
 */
public class SshKeysPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	private final UserModel user;

	private final Class<? extends WebPage> pageClass;

	private final PageParameters params;

	public SshKeysPanel(String wicketId, UserModel user, Class<? extends WebPage> pageClass, PageParameters params) {
		super(wicketId);

		this.user = user;
		this.pageClass = pageClass;
		this.params = params;
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();
		List<SshKey> keys = app().keys().getKeys(user.username);

		final ListDataProvider<SshKey> dp = new ListDataProvider<SshKey>(keys);
		DataView<SshKey> keysView = new DataView<SshKey>("keys", dp) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(final Item<SshKey> item) {
				final SshKey key = item.getModelObject();
				item.add(new Label("comment", key.getComment()));
				item.add(new Label("fingerprint", key.getFingerprint()));
				item.add(new Label("permission", key.getPermission().toString()));
				item.add(new Label("algorithm", key.getAlgorithm()));

				Link<Void> delete = new Link<Void>("delete") {

					private static final long serialVersionUID = 1L;

					@Override
					public void onClick() {
						if (app().keys().removeKey(user.username, key)) {
							setRedirect(true);
							setResponsePage(pageClass, params);
						}
					}
				};
				item.add(delete);
			}
		};
		add(keysView);

		Form<Void> addKeyForm = new Form<Void>("addKeyForm");

		final IModel<String> keyData = Model.of("");
		addKeyForm.add(new TextAreaOption("addKeyData",
				getString("gb.key"),
				null,
				"span5",
				keyData));

		final IModel<AccessPermission> keyPermission = Model.of(AccessPermission.PUSH);
		addKeyForm.add(new ChoiceOption<AccessPermission>("addKeyPermission",
				getString("gb.permission"),
				getString("gb.sshKeyPermissionDescription"),
				keyPermission,
				Arrays.asList(AccessPermission.SSHPERMISSIONS)));

		final IModel<String> keyComment = Model.of("");
		addKeyForm.add(new TextOption("addKeyComment",
				getString("gb.comment"),
				getString("gb.sshKeyCommentDescription"),
				"span5",
				keyComment));

		addKeyForm.add(new AjaxButton("addKeyButton") {

			private static final long serialVersionUID = 1L;

			@Override
			protected void onSubmit(AjaxRequestTarget target, Form<?> form) {

				UserModel user = GitBlitWebSession.get().getUser();
				String data = keyData.getObject();
				if (StringUtils.isEmpty(data)) {
					// do not submit empty key
					return;
				}

				SshKey key = new SshKey(data);
				try {
					key.getPublicKey();
				} catch (Exception e) {
					// failed to parse the key
					return;
				}

				AccessPermission permission = keyPermission.getObject();
				key.setPermission(permission);

				String comment  = keyComment.getObject();
				if (!StringUtils.isEmpty(comment)) {
					key.setComment(comment);
				}

				if (app().keys().addKey(user.username, key)) {
					setRedirect(true);
					setResponsePage(pageClass, params);
				}
			}
		});

		add(addKeyForm);
	}
}
