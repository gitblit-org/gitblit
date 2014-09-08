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
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.Constants;
import com.gitblit.Constants.AccessPermission;
import com.gitblit.Constants.AuthorizationControl;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.models.TicketModel.Field;
import com.gitblit.models.TicketModel.Status;
import com.gitblit.models.UserModel;
import com.gitblit.tickets.TicketMilestone;
import com.gitblit.tickets.TicketNotifier;
import com.gitblit.tickets.TicketResponsible;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.SafeTextModel;
import com.gitblit.wicket.SafeTextModel.Mode;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.MarkdownTextArea;

/**
 * Page for creating a new ticket.
 *
 * @author James Moger
 *
 */
public class NewTicketPage extends RepositoryPage {

	private IModel<TicketModel.Type> typeModel;

	private IModel<String> titleModel;

	private MarkdownTextArea descriptionEditor;

	private IModel<String> topicModel;

	private IModel<String> mergeToModel;

	private IModel<TicketResponsible> responsibleModel;

	private IModel<TicketMilestone> milestoneModel;

	private Label descriptionPreview;

	public NewTicketPage(PageParameters params) {
		super(params);

		UserModel currentUser = GitBlitWebSession.get().getUser();
		if (currentUser == null) {
			currentUser = UserModel.ANONYMOUS;
		}

		if (!currentUser.isAuthenticated || !app().tickets().isAcceptingNewTickets(getRepositoryModel())) {
			// tickets prohibited
			setResponsePage(TicketsPage.class, WicketUtils.newRepositoryParameter(repositoryName));
		}

		typeModel = Model.of(TicketModel.Type.defaultType);
		titleModel = SafeTextModel.none();
		topicModel = SafeTextModel.none();
		mergeToModel = Model.of(Repository.shortenRefName(getRepositoryModel().mergeTo));
		responsibleModel = Model.of();
		milestoneModel = Model.of();

		setStatelessHint(false);
		setOutputMarkupId(true);

		Form<Void> form = new Form<Void>("editForm");
		add(form);

		form.add(new DropDownChoice<TicketModel.Type>("type", typeModel, Arrays.asList(TicketModel.Type.choices())));
		form.add(new TextField<String>("title", titleModel));
		form.add(new TextField<String>("topic", topicModel));

		final SafeTextModel markdownPreviewModel = new SafeTextModel(Mode.none);
		descriptionPreview = new Label("descriptionPreview", markdownPreviewModel);
		descriptionPreview.setEscapeModelStrings(false);
		descriptionPreview.setOutputMarkupId(true);
		form.add(descriptionPreview);

		descriptionEditor = new MarkdownTextArea("description", markdownPreviewModel, descriptionPreview);
		descriptionEditor.setRepository(repositoryName);
		form.add(descriptionEditor);

		if (currentUser.canAdmin(null, getRepositoryModel())) {
			// responsible
			Set<String> userlist = new TreeSet<String>();

			if (UserModel.ANONYMOUS.canPush(getRepositoryModel())
					|| AuthorizationControl.AUTHENTICATED == getRepositoryModel().authorizationControl) {
				// 	authorization is ANONYMOUS or AUTHENTICATED (i.e. all users can be set responsible)
				userlist.addAll(app().users().getAllUsernames());
			} else {
				// authorization is by NAMED users (users with PUSH permission can be set responsible)
				for (RegistrantAccessPermission rp : app().repositories().getUserAccessPermissions(getRepositoryModel())) {
					if (rp.permission.atLeast(AccessPermission.PUSH)) {
						userlist.add(rp.registrant);
					}
				}
			}

			List<TicketResponsible> responsibles = new ArrayList<TicketResponsible>();
			for (String username : userlist) {
				UserModel user = app().users().getUserModel(username);
				if (user != null && !user.disabled) {
					TicketResponsible responsible = new TicketResponsible(user);
					responsibles.add(responsible);
				}
			}
			Collections.sort(responsibles);
			Fragment responsible = new Fragment("responsible", "responsibleFragment", this);
			responsible.add(new DropDownChoice<TicketResponsible>("responsible", responsibleModel, responsibles));
			form.add(responsible.setVisible(!responsibles.isEmpty()));

			// milestone
			List<TicketMilestone> milestones = app().tickets().getMilestones(getRepositoryModel(), Status.Open);
			Fragment milestone = new Fragment("milestone", "milestoneFragment", this);
			milestone.add(new DropDownChoice<TicketMilestone>("milestone", milestoneModel, milestones));
			form.add(milestone.setVisible(!milestones.isEmpty()));

			// integration branch
			List<String> branches = new ArrayList<String>();
			for (String branch : getRepositoryModel().getLocalBranches()) {
				// exclude ticket branches
				if (!branch.startsWith(Constants.R_TICKET)) {
					branches.add(Repository.shortenRefName(branch));
				}
			}
			branches.remove(Repository.shortenRefName(getRepositoryModel().HEAD));
			branches.add(0, Repository.shortenRefName(getRepositoryModel().HEAD));

			Fragment mergeto = new Fragment("mergeto", "mergeToFragment", this);
			mergeto.add(new DropDownChoice<String>("mergeto", mergeToModel, branches));
			form.add(mergeto.setVisible(!branches.isEmpty()));
		} else {
			// user does not have permission to assign milestone, responsible, or mergeto
			form.add(new Label("responsible").setVisible(false));
			form.add(new Label("milestone").setVisible(false));
			form.add(new Label("mergeto").setVisible(false));
		}

		form.add(new AjaxButton("create") {

			private static final long serialVersionUID = 1L;

			@Override
			protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
				String title = titleModel.getObject();
				if (StringUtils.isEmpty(title)) {
					return;
				}

				String createdBy = GitBlitWebSession.get().getUsername();
				Change change = new Change(createdBy);
				change.setField(Field.title, title);
				change.setField(Field.body, descriptionEditor.getText());
				String topic = topicModel.getObject();
				if (!StringUtils.isEmpty(topic)) {
					change.setField(Field.topic, topic);
				}

				// type
				TicketModel.Type type = TicketModel.Type.defaultType;
				if (typeModel.getObject() != null) {
					type = typeModel.getObject();
				}
				change.setField(Field.type, type);

				// responsible
				TicketResponsible responsible = responsibleModel == null ? null : responsibleModel.getObject();
				if (responsible != null) {
					change.setField(Field.responsible, responsible.username);
				}

				// milestone
				TicketMilestone milestone = milestoneModel == null ? null : milestoneModel.getObject();
				if (milestone != null) {
					change.setField(Field.milestone, milestone.name);
				}

				// integration branch
				String mergeTo = mergeToModel.getObject();
				if (!StringUtils.isEmpty(mergeTo)) {
					change.setField(Field.mergeTo, mergeTo);
				}

				TicketModel ticket = app().tickets().createTicket(getRepositoryModel(), 0L, change);
				if (ticket != null) {
					TicketNotifier notifier = app().tickets().createNotifier();
					notifier.sendMailing(ticket);
					setResponsePage(TicketsPage.class, WicketUtils.newObjectParameter(getRepositoryModel().name, "" + ticket.number));
				} else {
					// TODO error
				}
			}
		});

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
		return getString("gb.newTicket");
	}

	@Override
	protected Class<? extends BasePage> getRepoNavPageClass() {
		return TicketsPage.class;
	}
}
