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
package com.gitblit.wicket.pages;

import java.util.Date;

import org.apache.wicket.PageParameters;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.extensions.markup.html.form.DateTextField;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.tickets.TicketMilestone;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;

/**
 * Page for creating a new milestone.
 *
 * @author James Moger
 *
 */
public class NewMilestonePage extends RepositoryPage {

	private IModel<String> nameModel;

	private IModel<Date> dueModel;

	public NewMilestonePage(PageParameters params) {
		super(params);

		RepositoryModel model = getRepositoryModel();
		if (!app().tickets().isAcceptingTicketUpdates(model)) {
			// ticket service is read-only
			throw new RestartResponseException(TicketsPage.class, WicketUtils.newOpenTicketsParameter(repositoryName));
		}

		UserModel currentUser = GitBlitWebSession.get().getUser();
		if (currentUser == null) {
			currentUser = UserModel.ANONYMOUS;
		}

		if (!currentUser.isAuthenticated || !currentUser.canAdmin(model)) {
			// administration prohibited
			throw new RestartResponseException(TicketsPage.class, WicketUtils.newOpenTicketsParameter(repositoryName));
		}

		setStatelessHint(false);
		setOutputMarkupId(true);

		Form<Void> form = new Form<Void>("editForm");
		add(form);

		nameModel = Model.of("");
		dueModel = Model.of(new Date(System.currentTimeMillis() + TimeUtils.ONEDAY));

		form.add(new TextField<String>("name", nameModel));
		form.add(new DateTextField("due", dueModel, "yyyy-MM-dd"));
		form.add(new Label("dueFormat", "yyyy-MM-dd"));

		form.add(new AjaxButton("create") {

			private static final long serialVersionUID = 1L;

			@Override
			protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
				String name = nameModel.getObject();
				if (StringUtils.isEmpty(name)) {
					// invalid name
					return;
				}

				TicketMilestone milestone = app().tickets().getMilestone(getRepositoryModel(), name);
				if (milestone != null) {
					// milestone already exists
					return;
				}

				Date due = dueModel.getObject();

				UserModel currentUser = GitBlitWebSession.get().getUser();
				String createdBy = currentUser.username;

				milestone = app().tickets().createMilestone(getRepositoryModel(), name, createdBy);
				if (milestone != null) {
					milestone.due = due;
					app().tickets().updateMilestone(getRepositoryModel(), milestone, createdBy);
					redirectTo(TicketsPage.class, WicketUtils.newOpenTicketsParameter(repositoryName));
				} else {
					// TODO error
				}
			}
		});

		Button cancel = new Button("cancel") {
			private static final long serialVersionUID = 1L;

			@Override
			public void onSubmit() {
				setResponsePage(TicketsPage.class, WicketUtils.newOpenTicketsParameter(repositoryName));
			}
		};
		cancel.setDefaultFormProcessing(false);
		form.add(cancel);

	}

	@Override
	protected String getPageName() {
		return getString("gb.newMilestone");
	}

	@Override
	protected Class<? extends BasePage> getRepoNavPageClass() {
		return TicketsPage.class;
	}
}
