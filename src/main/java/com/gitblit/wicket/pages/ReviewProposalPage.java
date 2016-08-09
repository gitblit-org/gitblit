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

import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.markup.html.basic.Label;

import com.gitblit.Constants.FederationToken;
import com.gitblit.Keys;
import com.gitblit.models.FederationProposal;
import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.RequiresAdminRole;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.RepositoriesPanel;

@RequiresAdminRole
public class ReviewProposalPage extends RootSubPage {

	private final String PROPS_PATTERN = "{0} = {1}\n";

	public ReviewProposalPage(PageParameters params) {
		super(params);

		final String token = WicketUtils.getToken(params);

		FederationProposal proposal = app().federation().getPendingFederationProposal(token);
		if (proposal == null) {
			error(getString("gb.couldNotFindFederationProposal"), true);
		}

		setupPage(getString("gb.proposals"), proposal.url);


		add(new Label("url", proposal.url));
		add(new Label("message", proposal.message));
		add(WicketUtils.createTimestampLabel("received", proposal.received, getTimeZone(), getTimeUtils()));
		add(new Label("token", proposal.token));
		add(new Label("tokenType", proposal.tokenType.name()));

		String p = PROPS_PATTERN;

		// build proposed definition
		StringBuilder sb = new StringBuilder();
		sb.append(asParam(p, proposal.name, "url", proposal.url));
		sb.append(asParam(p, proposal.name, "token", proposal.token));

		if (FederationToken.USERS_AND_REPOSITORIES.equals(proposal.tokenType)
				|| FederationToken.ALL.equals(proposal.tokenType)) {
			sb.append(asParam(p, proposal.name, "mergeAccounts", "false"));
		}
		sb.append(asParam(p, proposal.name, "frequency",
				app().settings().getString(Keys.federation.defaultFrequency, "60 mins")));
		sb.append(asParam(p, proposal.name, "folder", proposal.name));
		sb.append(asParam(p, proposal.name, "bare", "true"));
		sb.append(asParam(p, proposal.name, "mirror", "true"));
		sb.append(asParam(p, proposal.name, "sendStatus", "true"));
		sb.append(asParam(p, proposal.name, "notifyOnError", "true"));
		sb.append(asParam(p, proposal.name, "exclude", ""));
		sb.append(asParam(p, proposal.name, "include", ""));

		final int tabLength = app().settings().getInteger(Keys.web.tabLength, 4);
		add(new Label("definition", StringUtils.breakLinesForHtml(StringUtils.escapeForHtml(sb
				.toString().trim(), true, tabLength))).setEscapeModelStrings(false));

		List<RepositoryModel> repositories = new ArrayList<RepositoryModel>(
				proposal.repositories.values());
		RepositoriesPanel repositoriesPanel = new RepositoriesPanel("repositoriesPanel", false,
				false, repositories, false, getAccessRestrictions());
		add(repositoriesPanel);
	}

	private String asParam(String pattern, String name, String key, String value) {
		return MessageFormat.format(pattern, Keys.federation._ROOT + "." + name + "." + key, value);
	}
}
