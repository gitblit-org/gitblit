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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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
import com.gitblit.models.TicketModel.Type;
import com.gitblit.models.UserModel;
import com.gitblit.tickets.TicketMilestone;
import com.gitblit.tickets.TicketNotifier;
import com.gitblit.tickets.TicketResponsible;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.MarkdownTextArea;

/**
 * Page for editing a ticket.
 *
 * @author James Moger
 *
 */
public class EditTicketPage extends RepositoryPage {

	static final String NIL = "<nil>";

	static final String ESC_NIL = StringUtils.escapeForHtml(NIL,  false);

	private IModel<TicketModel.Type> typeModel;

	private IModel<String> titleModel;

	private MarkdownTextArea descriptionEditor;

	private IModel<String> topicModel;

	private IModel<TicketResponsible> responsibleModel;

	private IModel<TicketMilestone> milestoneModel;

	private Label descriptionPreview;

	public EditTicketPage(PageParameters params) {
		super(params);

		UserModel currentUser = GitBlitWebSession.get().getUser();
		if (currentUser == null) {
			currentUser = UserModel.ANONYMOUS;
		}

		if (!currentUser.isAuthenticated || !app().tickets().isAcceptingTicketUpdates(getRepositoryModel())) {
			// tickets prohibited
			setResponsePage(TicketsPage.class, WicketUtils.newRepositoryParameter(repositoryName));
		}

		long ticketId = 0L;
		try {
			String h = WicketUtils.getObject(params);
			ticketId = Long.parseLong(h);
		} catch (Exception e) {
			setResponsePage(TicketsPage.class, WicketUtils.newRepositoryParameter(repositoryName));
		}

		TicketModel ticket = app().tickets().getTicket(getRepositoryModel(), ticketId);
		if (ticket == null) {
			setResponsePage(TicketsPage.class, WicketUtils.newRepositoryParameter(repositoryName));
		}

		typeModel = Model.of(ticket.type);
		titleModel = Model.of(ticket.title);
		topicModel = Model.of(ticket.topic == null ? "" : ticket.topic);
		responsibleModel = Model.of();
		milestoneModel = Model.of();

		setStatelessHint(false);
		setOutputMarkupId(true);

		Form<Void> form = new Form<Void>("editForm") {

			private static final long serialVersionUID = 1L;

			@Override
			protected void onSubmit() {
				long ticketId = 0L;
				try {
					String h = WicketUtils.getObject(getPageParameters());
					ticketId = Long.parseLong(h);
				} catch (Exception e) {
					setResponsePage(TicketsPage.class, WicketUtils.newRepositoryParameter(repositoryName));
				}

				TicketModel ticket = app().tickets().getTicket(getRepositoryModel(), ticketId);

				String createdBy = GitBlitWebSession.get().getUsername();
				Change change = new Change(createdBy);

				String title = titleModel.getObject();
				if (!ticket.title.equals(title)) {
					// title change
					change.setField(Field.title, title);
				}

				String description = descriptionEditor.getText();
				if (!ticket.body.equals(description)) {
					// description change
					change.setField(Field.body, description);
				}

				Type type = typeModel.getObject();
				if (!ticket.type.equals(type)) {
					// type change
					change.setField(Field.type, type);
				}

				String topic = topicModel.getObject();
				if ((StringUtils.isEmpty(ticket.topic) && !StringUtils.isEmpty(topic))
						|| (!StringUtils.isEmpty(topic) && !topic.equals(ticket.topic))) {
					// topic change
					change.setField(Field.topic, topic);
				}

				TicketResponsible responsible = responsibleModel == null ? null : responsibleModel.getObject();
				if (responsible != null && !responsible.username.equals(ticket.responsible)) {
					// responsible change
					change.setField(Field.responsible, responsible.username);
					if (!StringUtils.isEmpty(responsible.username)) {
						if (!ticket.isWatching(responsible.username)) {
							change.watch(responsible.username);
						}
					}
				}

				TicketMilestone milestone = milestoneModel == null ? null : milestoneModel.getObject();
				if (milestone != null && !milestone.name.equals(ticket.milestone)) {
					// milestone change
					if (NIL.equals(milestone.name)) {
						change.setField(Field.milestone, "");
					} else {
						change.setField(Field.milestone, milestone.name);
					}
				}

				if (change.hasFieldChanges()) {
					if (!ticket.isWatching(createdBy)) {
						change.watch(createdBy);
					}
					ticket = app().tickets().updateTicket(getRepositoryModel(), ticket.number, change);
					if (ticket != null) {
						TicketNotifier notifier = app().tickets().createNotifier();
						notifier.sendMailing(ticket);
						setResponsePage(TicketsPage.class, WicketUtils.newObjectParameter(getRepositoryModel().name, "" + ticket.number));
					} else {
						// TODO error
					}
				} else {
					// nothing to change?!
					setResponsePage(TicketsPage.class, WicketUtils.newObjectParameter(getRepositoryModel().name, "" + ticket.number));
				}
			}
		};
		add(form);

		List<Type> typeChoices;
		if (ticket.isProposal()) {
			typeChoices = Arrays.asList(Type.Proposal);
		} else {
			typeChoices = Arrays.asList(TicketModel.Type.choices());
		}
		form.add(new DropDownChoice<TicketModel.Type>("type", typeModel, typeChoices));
		form.add(new TextField<String>("title", titleModel));
		form.add(new TextField<String>("topic", topicModel));

		final IModel<String> markdownPreviewModel = new Model<String>();
		descriptionPreview = new Label("descriptionPreview", markdownPreviewModel);
		descriptionPreview.setEscapeModelStrings(false);
		descriptionPreview.setOutputMarkupId(true);
		form.add(descriptionPreview);

		descriptionEditor = new MarkdownTextArea("description", markdownPreviewModel, descriptionPreview);
		descriptionEditor.setRepository(repositoryName);
		descriptionEditor.setText(ticket.body);
		form.add(descriptionEditor);

		if (currentUser != null && currentUser.isAuthenticated && currentUser.canPush(getRepositoryModel())) {
			// responsible
			Set<String> userlist = new TreeSet<String>(ticket.getParticipants());

			for (RegistrantAccessPermission rp : app().repositories().getUserAccessPermissions(getRepositoryModel())) {
				if (rp.permission.atLeast(AccessPermission.PUSH) && !rp.isTeam()) {
					userlist.add(rp.registrant);
				}
			}

			List<TicketResponsible> responsibles = new ArrayList<TicketResponsible>();
			for (String username : userlist) {
				UserModel user = app().users().getUserModel(username);
				if (user != null) {
					TicketResponsible responsible = new TicketResponsible(user);
					responsibles.add(responsible);
					if (user.username.equals(ticket.responsible)) {
						responsibleModel.setObject(responsible);
					}
				}
			}
			Collections.sort(responsibles);
			responsibles.add(new TicketResponsible(NIL, "", ""));
			Fragment responsible = new Fragment("responsible", "responsibleFragment", this);
			responsible.add(new DropDownChoice<TicketResponsible>("responsible", responsibleModel, responsibles));
			form.add(responsible.setVisible(!responsibles.isEmpty()));

			// milestone
			List<TicketMilestone> milestones = app().tickets().getMilestones(getRepositoryModel(), Status.Open);
			for (TicketMilestone milestone : milestones) {
				if (milestone.name.equals(ticket.milestone)) {
					milestoneModel.setObject(milestone);
					break;
				}
			}
			if (!milestones.isEmpty()) {
				milestones.add(new TicketMilestone(NIL));
			}

			Fragment milestone = new Fragment("milestone", "milestoneFragment", this);

			milestone.add(new DropDownChoice<TicketMilestone>("milestone", milestoneModel, milestones));
			form.add(milestone.setVisible(!milestones.isEmpty()));
		} else {
			// user does not have permission to assign milestone or responsible
			form.add(new Label("responsible").setVisible(false));
			form.add(new Label("milestone").setVisible(false));
		}

		form.add(new Button("update"));
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
		return getString("gb.editTicket");
	}

	@Override
	protected Class<? extends BasePage> getRepoNavPageClass() {
		return TicketsPage.class;
	}
}
