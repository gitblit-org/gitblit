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

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.extensions.markup.html.form.DateTextField;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Status;
import com.gitblit.models.UserModel;
import com.gitblit.tickets.TicketMilestone;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;

/**
 * Page for creating a new milestone.
 *
 * @author James Moger
 *
 */
public class EditMilestonePage extends RepositoryPage {

	private final String oldName;

	private IModel<String> nameModel;

	private IModel<Date> dueModel;

	private IModel<Status> statusModel;

	private IModel<Boolean> notificationModel;

	public EditMilestonePage(PageParameters params) {
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

		oldName = WicketUtils.getObject(params);
		if (StringUtils.isEmpty(oldName)) {
			// milestone not specified
			throw new RestartResponseException(TicketsPage.class, WicketUtils.newRepositoryParameter(repositoryName));
		}

		TicketMilestone tm = app().tickets().getMilestone(getRepositoryModel(), oldName);
		if (tm == null) {
			// milestone does not exist
			throw new RestartResponseException(TicketsPage.class, WicketUtils.newRepositoryParameter(repositoryName));
		}

		setStatelessHint(false);
		setOutputMarkupId(true);

		Form<Void> form = new Form<Void>("editForm") {

			private static final long serialVersionUID = 1L;

			@Override
			protected void onSubmit() {

				String name = nameModel.getObject();
				if (StringUtils.isEmpty(name)) {
					return;
				}

				Date due = dueModel.getObject();
				Status status = statusModel.getObject();
				boolean rename = !name.equals(oldName);
				boolean notify = notificationModel.getObject();

				UserModel currentUser = GitBlitWebSession.get().getUser();
				String createdBy = currentUser.username;

				TicketMilestone tm = app().tickets().getMilestone(getRepositoryModel(), oldName);
				tm.setName(name);
				tm.setDue(due);
				tm.status = status;

				boolean success = true;
				if (rename) {
					success = app().tickets().renameMilestone(getRepositoryModel(), oldName, name, createdBy, notify);
				}

				if (success && app().tickets().updateMilestone(getRepositoryModel(), tm, createdBy)) {
					setResponsePage(TicketsPage.class, WicketUtils.newRepositoryParameter(getRepositoryModel().name));
				} else {
					// TODO error
				}
			}
		};
		add(form);

		nameModel = Model.of(tm.name);
		dueModel = Model.of(tm.due);
		statusModel = Model.of(tm.status);
		notificationModel = Model.of(true);

		form.add(new TextField<String>("name", nameModel));
		form.add(new DateTextField("due", dueModel, "yyyy-MM-dd"));

		List<Status> statusChoices = Arrays.asList(Status.Open, Status.Closed);
		form.add(new DropDownChoice<TicketModel.Status>("status", statusModel, statusChoices));

		form.add(new Button("save"));
		Button cancel = new Button("cancel") {
			private static final long serialVersionUID = 1L;

			@Override
			public void onSubmit() {
				setResponsePage(TicketsPage.class, WicketUtils.newRepositoryParameter(repositoryName));
			}
		};
		cancel.setDefaultFormProcessing(false);
		form.add(cancel);

		Button delete = new Button("delete") {
			private static final long serialVersionUID = 1L;

			@Override
			public void onSubmit() {
				UserModel currentUser = GitBlitWebSession.get().getUser();
				String createdBy = currentUser.username;

				if (app().tickets().deleteMilestone(getRepositoryModel(), oldName, createdBy)) {
					setResponsePage(TicketsPage.class, WicketUtils.newRepositoryParameter(repositoryName));
				} else {
					// TODO error processing
				}
			}
		};
		delete.setDefaultFormProcessing(false);
		form.add(delete);
	}

	@Override
	protected String getPageName() {
		return getString("gb.editMilestone");
	}

	@Override
	protected Class<? extends BasePage> getRepoNavPageClass() {
		return TicketsPage.class;
	}
}
