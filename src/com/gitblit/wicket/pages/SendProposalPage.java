/*
 * Copyright 2011 gitblit.com.
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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.CompoundPropertyModel;

import com.gitblit.Constants.FederationProposalResult;
import com.gitblit.GitBlit;
import com.gitblit.models.FederationProposal;
import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.FederationUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.RequiresAdminRole;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.RepositoriesPanel;

@RequiresAdminRole
public class SendProposalPage extends BasePage {

	public String myUrl;

	public String destinationUrl;

	public String message;

	public SendProposalPage(PageParameters params) {
		super(params);

		setupPage("", getString("gb.sendProposal"));
		setStatelessHint(true);

		final String token = WicketUtils.getToken(params);

		myUrl = WicketUtils.getGitblitURL(getRequest());
		destinationUrl = "https://";

		// temporary proposal
		FederationProposal proposal = GitBlit.self().createFederationProposal(myUrl, token);
		if (proposal == null) {
			error("Could not create federation proposal!", true);
		}

		CompoundPropertyModel<SendProposalPage> model = new CompoundPropertyModel<SendProposalPage>(
				this);

		Form<SendProposalPage> form = new Form<SendProposalPage>("editForm", model) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void onSubmit() {
				// confirm a repository name was entered
				if (StringUtils.isEmpty(myUrl)) {
					error("Please enter your Gitblit url!");
					return;
				}
				if (StringUtils.isEmpty(destinationUrl)) {
					error("Please enter a destination url for your proposal!");
					return;
				}

				// build new proposal
				FederationProposal proposal = GitBlit.self().createFederationProposal(myUrl, token);
				proposal.url = myUrl;
				proposal.message = message;
				try {
					FederationProposalResult res = FederationUtils
							.propose(destinationUrl, proposal);
					switch (res) {
					case ACCEPTED:
						info(MessageFormat.format("Proposal successfully received by {0}.",
								destinationUrl));
						setResponsePage(RepositoriesPage.class);
						break;
					case NO_POKE:
						error(MessageFormat.format(
								"Sorry, {0} could not find a Gitblit instance at {1}.",
								destinationUrl, myUrl));
						break;
					case NO_PROPOSALS:
						error(MessageFormat.format(
								"Sorry, {0} is not accepting proposals at this time.",
								destinationUrl));
						break;
					case FEDERATION_DISABLED:
						error(MessageFormat
								.format("Sorry, {0} is not configured to federate with any Gitblit instances.",
										destinationUrl));
						break;
					case MISSING_DATA:
						error(MessageFormat.format("Sorry, {0} did not receive any proposal data!",
								destinationUrl));
						break;
					case ERROR:
						error(MessageFormat.format(
								"Sorry, {0} reports that an unexpected error occurred!",
								destinationUrl));
						break;
					}
				} catch (Exception e) {
					if (!StringUtils.isEmpty(e.getMessage())) {
						error(e.getMessage());
					} else {
						error("Failed to send proposal!");
					}
				}
			}
		};
		form.add(new TextField<String>("myUrl"));
		form.add(new TextField<String>("destinationUrl"));
		form.add(new TextField<String>("message"));
		form.add(new Label("tokenType", proposal.tokenType.name()));
		form.add(new Label("token", proposal.token));

		form.add(new Button("save"));
		Button cancel = new Button("cancel") {
			private static final long serialVersionUID = 1L;

			@Override
			public void onSubmit() {
				setResponsePage(RepositoriesPage.class);
			}
		};
		cancel.setDefaultFormProcessing(false);
		form.add(cancel);
		add(form);

		List<RepositoryModel> repositories = new ArrayList<RepositoryModel>(
				proposal.repositories.values());
		RepositoriesPanel repositoriesPanel = new RepositoriesPanel("repositories", false,
				repositories, getAccessRestrictions());
		add(repositoriesPanel);
	}
}
