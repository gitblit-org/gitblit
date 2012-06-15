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

import org.apache.wicket.Component;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;

import com.gitblit.GitBlit;
import com.gitblit.models.FederationModel;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.FederationRegistrationPage;

public class FederationRegistrationsPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	private final boolean hasRegistrations;

	public FederationRegistrationsPanel(String wicketId) {
		super(wicketId);

		final List<FederationModel> list = new ArrayList<FederationModel>(GitBlit.self()
				.getFederationRegistrations());
		list.addAll(GitBlit.self().getFederationResultRegistrations());
		Collections.sort(list);
		hasRegistrations = list.size() > 0;
		DataView<FederationModel> dataView = new DataView<FederationModel>("row",
				new ListDataProvider<FederationModel>(list)) {
			private static final long serialVersionUID = 1L;
			private int counter;

			@Override
			protected void onBeforeRender() {
				super.onBeforeRender();
				counter = 0;
			}

			public void populateItem(final Item<FederationModel> item) {
				final FederationModel entry = item.getModelObject();
				item.add(new LinkPanel("url", "list", entry.url, FederationRegistrationPage.class,
						WicketUtils.newRegistrationParameter(entry.url, entry.name)));
				item.add(WicketUtils.getPullStatusImage("statusIcon", entry.getLowestStatus()));
				item.add(new LinkPanel("name", "list", entry.name,
						FederationRegistrationPage.class, WicketUtils.newRegistrationParameter(
								entry.url, entry.name)));

				item.add(WicketUtils.getRegistrationImage("typeIcon", entry, this));

				item.add(WicketUtils.createDateLabel("lastPull", entry.lastPull, getTimeZone(), getTimeUtils()));
				item.add(WicketUtils
						.createTimestampLabel("nextPull", entry.nextPull, getTimeZone(), getTimeUtils()));
				item.add(new Label("frequency", entry.frequency));
				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(dataView);
	}

	public Component hideIfEmpty() {
		return super.setVisible(hasRegistrations);
	}
}
