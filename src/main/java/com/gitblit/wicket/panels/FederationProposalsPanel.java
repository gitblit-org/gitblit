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

import java.text.MessageFormat;
import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;

import com.gitblit.models.FederationProposal;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.ReviewProposalPage;

public class FederationProposalsPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	private final boolean hasProposals;

	public FederationProposalsPanel(String wicketId) {
		super(wicketId);

		final List<FederationProposal> list = app().federation().getPendingFederationProposals();
		hasProposals = list.size() > 0;
		DataView<FederationProposal> dataView = new DataView<FederationProposal>("row",
				new ListDataProvider<FederationProposal>(list)) {
			private static final long serialVersionUID = 1L;
			private int counter;

			@Override
			protected void onBeforeRender() {
				super.onBeforeRender();
				counter = 0;
			}

			@Override
			public void populateItem(final Item<FederationProposal> item) {
				final FederationProposal entry = item.getModelObject();
				item.add(new LinkPanel("url", "list", entry.url, ReviewProposalPage.class,
						WicketUtils.newTokenParameter(entry.token)));
				item.add(WicketUtils.createDateLabel("received", entry.received, getTimeZone(), getTimeUtils()));
				item.add(new Label("tokenType", entry.tokenType.name()));
				item.add(new LinkPanel("token", "list", entry.token, ReviewProposalPage.class,
						WicketUtils.newTokenParameter(entry.token)));

				Link<Void> deleteLink = new Link<Void>("deleteProposal") {

					private static final long serialVersionUID = 1L;

					@Override
					public void onClick() {
						if (app().federation().deletePendingFederationProposal(entry)) {
							list.remove(entry);
							info(MessageFormat.format("Proposal ''{0}'' deleted.", entry.name));
						} else {
							error(MessageFormat.format("Failed to delete proposal ''{0}''!",
									entry.name));
						}
					}
				};
				deleteLink.add(new JavascriptEventConfirmation("click", MessageFormat.format(
						"Delete proposal \"{0}\"?", entry.name)));
				item.add(deleteLink);
				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(dataView);
	}

	public Component hideIfEmpty() {
		return super.setVisible(hasProposals);
	}
}
