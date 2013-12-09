/*
 * Copyright 2013 gitblit.com.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.models.TicketModel.Field;
import com.gitblit.models.TicketModel.Status;
import com.gitblit.models.UserModel;
import com.gitblit.tickets.TicketMilestone;
import com.gitblit.tickets.TicketNotifier;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.MarkdownTextArea;

public class NewTicketPage extends RepositoryPage {

	private IModel<TicketModel.Type> typeModel;

	private IModel<String> titleModel;

	private MarkdownTextArea descriptionEditor;

	private IModel<UserModel> assigntoModel;

	private IModel<TicketMilestone> milestoneModel;

	private Label descriptionPreview;

	public NewTicketPage(PageParameters params) {
		super(params);

		if (!app().tickets().isReady(repositoryName)) {
			// tickets prohibited
			setResponsePage(SummaryPage.class, WicketUtils.newRepositoryParameter(repositoryName));
		}

		UserModel currentUser = GitBlitWebSession.get().getUser();

		typeModel = Model.of(TicketModel.Type.Request);
		titleModel = Model.of();
		assigntoModel = Model.of();
		milestoneModel = Model.of();

		setStatelessHint(false);
		setOutputMarkupId(true);

		Form<Void> form = new Form<Void>("editForm") {

			private static final long serialVersionUID = 1L;

			@Override
			protected void onSubmit() {
				String createdBy = GitBlitWebSession.get().getUsername();
				Change change = new Change(createdBy);
				change.setField(Field.repository, getRepositoryModel().name);
				change.setField(Field.title, titleModel.getObject());
				change.setField(Field.body, descriptionEditor.getText());

				// type
				TicketModel.Type type = TicketModel.Type.Request;
				if (typeModel.getObject() != null) {
					type = typeModel.getObject();
				}
				change.setField(Field.type, type);

				// assigned to
				UserModel assignedTo = assigntoModel == null ? null : assigntoModel.getObject();
				if (assignedTo != null) {
					change.setField(Field.assignedTo, assignedTo.username);
				}

				// milestone
				TicketMilestone milestone = milestoneModel == null ? null : milestoneModel.getObject();
				if (milestone != null) {
					change.setField(Field.milestone, milestone.name);
				}

				TicketModel ticket = app().tickets().createTicket(change);
				if (ticket != null) {
					TicketNotifier notifier = app().tickets().createNotifier();
					notifier.sendMailing(ticket);
					setResponsePage(TicketsPage.class, WicketUtils.newObjectParameter(getRepositoryModel().name, "" + ticket.number));
				} else {
					// TODO error
				}
			}
		};
		add(form);

		form.add(new DropDownChoice<TicketModel.Type>("type", typeModel, Arrays.asList(TicketModel.Type.choices())));
		form.add(new TextField<String>("title", titleModel));

		final IModel<String> markdownPreviewModel = new Model<String>();
		descriptionPreview = new Label("descriptionPreview", markdownPreviewModel);
		descriptionPreview.setEscapeModelStrings(false);
		descriptionPreview.setOutputMarkupId(true);
		form.add(descriptionPreview);

		descriptionEditor = new MarkdownTextArea("description", markdownPreviewModel, descriptionPreview);
		descriptionEditor.setRepository(repositoryName);
		form.add(descriptionEditor);

		if (currentUser != null && currentUser.isAuthenticated && currentUser.canPush(getRepositoryModel())) {
			// assigned to
			List<UserModel> assignees = new ArrayList<UserModel>();
			for (RegistrantAccessPermission rp : app().repositories().getUserAccessPermissions(getRepositoryModel())) {
				if (rp.permission.atLeast(AccessPermission.PUSH) && !rp.isTeam()) {
					UserModel user = app().users().getUserModel(rp.registrant);
					if (user != null) {
						assignees.add(user);
					}
				}
			}
			Fragment assignTo = new Fragment("assignedTo", "assignedToFragment", this);
			assignTo.add(new DropDownChoice<UserModel>("assignto", assigntoModel, assignees));
			form.add(assignTo.setVisible(!assignees.isEmpty()));

			// milestone
			List<TicketMilestone> milestones = app().tickets().getMilestones(getRepositoryModel().name, Status.Open);
			Fragment milestone = new Fragment("milestone", "milestoneFragment", this);
			milestone.add(new DropDownChoice<TicketMilestone>("milestone", milestoneModel, milestones));
			form.add(milestone.setVisible(!milestones.isEmpty()));
		} else {
			// user does not have permission to assign milestones or handlers
			form.add(new Label("assignedTo").setVisible(false));
			form.add(new Label("milestone").setVisible(false));
		}

		form.add(new Button("create"));
		Button cancel = new Button("cancel") {
			private static final long serialVersionUID = 1L;

			@Override
			public void onSubmit() {
				setResponsePage(TicketsPage.class, getPageParameters());
			}
		};
		cancel.setDefaultFormProcessing(false);
		form.add(cancel);

	}

	@Override
	protected String getPageName() {
		return getString("gb.new");
	}

	@Override
	protected Class<? extends BasePage> getRepoNavPageClass() {
		return TicketsPage.class;
	}
}
