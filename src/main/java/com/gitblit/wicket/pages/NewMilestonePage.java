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
import org.apache.wicket.extensions.markup.html.form.DateTextField;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.tickets.TicketMilestone;
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
			throw new RestartResponseException(TicketsPage.class, WicketUtils.newRepositoryParameter(repositoryName));
		}
		
		UserModel currentUser = GitBlitWebSession.get().getUser();
		if (currentUser == null) {
			currentUser = UserModel.ANONYMOUS;
		}

		if (!currentUser.isAuthenticated || !currentUser.canAdmin(model)) {
			// administration prohibited
			throw new RestartResponseException(TicketsPage.class, WicketUtils.newRepositoryParameter(repositoryName));
		}

		setStatelessHint(false);
		setOutputMarkupId(true);

		Form<Void> form = new Form<Void>("editForm") {

			private static final long serialVersionUID = 1L;

			@Override
			protected void onSubmit() {
				
				String name = nameModel.getObject();
				Date due = dueModel.getObject();

				UserModel currentUser = GitBlitWebSession.get().getUser();
				String createdBy = currentUser.username;
				
				TicketMilestone milestone = app().tickets().createMilestone(getRepositoryModel(), name, createdBy);
				if (milestone != null) {
					milestone.due = due;
					app().tickets().updateMilestone(getRepositoryModel(), milestone, createdBy);
					throw new RestartResponseException(TicketsPage.class, WicketUtils.newRepositoryParameter(getRepositoryModel().name));
				} else {
					// TODO error
				}
			}
		};
		add(form);

		nameModel = Model.of("");
		dueModel = Model.of(new Date());
		
		form.add(new TextField<String>("name", nameModel));
		form.add(new DateTextField("due", dueModel, "yyyy-MM-dd"));

		form.add(new Button("create"));
		Button cancel = new Button("cancel") {
			private static final long serialVersionUID = 1L;

			@Override
			public void onSubmit() {
				setResponsePage(TicketsPage.class, WicketUtils.newRepositoryParameter(repositoryName));
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
