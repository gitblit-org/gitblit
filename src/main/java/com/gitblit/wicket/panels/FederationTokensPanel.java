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
package com.gitblit.wicket.panels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;

import com.gitblit.Constants.FederationRequest;
import com.gitblit.Constants.FederationToken;
import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.utils.FederationUtils;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.SendProposalPage;

public class FederationTokensPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	public FederationTokensPanel(String wicketId, final boolean showFederation) {
		super(wicketId);

		final String baseUrl = WicketUtils.getGitblitURL(getRequest());
		add(new ExternalLink("federatedUsers", FederationUtils.asLink(baseUrl, GitBlit.self()
				.getFederationToken(FederationToken.USERS_AND_REPOSITORIES),
				FederationRequest.PULL_USERS)));

		add(new ExternalLink("federatedSettings", FederationUtils.asLink(baseUrl, GitBlit
				.self().getFederationToken(FederationToken.ALL), FederationRequest.PULL_SETTINGS)));

		final List<String[]> data = new ArrayList<String[]>();
		for (FederationToken token : FederationToken.values()) {
			data.add(new String[] { token.name(), GitBlit.self().getFederationToken(token), null });
		}
		List<String> sets = GitBlit.getStrings(Keys.federation.sets);
		Collections.sort(sets);
		for (String set : sets) {
			data.add(new String[] { FederationToken.REPOSITORIES.name(),
					GitBlit.self().getFederationToken(set), set });
		}

		DataView<String[]> dataView = new DataView<String[]>("row", new ListDataProvider<String[]>(
				data)) {
			private static final long serialVersionUID = 1L;
			private int counter;

			@Override
			protected void onBeforeRender() {
				super.onBeforeRender();
				counter = 0;
			}

			public void populateItem(final Item<String[]> item) {
				final String[] entry = item.getModelObject();
				final FederationToken token = FederationToken.fromName(entry[0]);
				if (entry[2] == null) {
					// standard federation token
					item.add(new Label("description", describeToken(token)));
				} else {
					// federation set token
					item.add(new Label("description", entry[2]));
				}
				item.add(new Label("value", entry[1]));

				item.add(new ExternalLink("repositoryDefinitions", FederationUtils.asLink(
						baseUrl, entry[1], FederationRequest.PULL_REPOSITORIES)));

				item.add(new BookmarkablePageLink<Void>("send",
						SendProposalPage.class, WicketUtils.newTokenParameter(entry[1])));

				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(dataView.setVisible(showFederation));
	}

	private String describeToken(FederationToken token) {
		switch (token) {
		case ALL:
			return getString("gb.tokenAllDescription");
		case USERS_AND_REPOSITORIES:
			return getString("gb.tokenUnrDescription");
		case REPOSITORIES:
		default:
			return getString("gb.tokenJurDescription");
		}
	}
}
