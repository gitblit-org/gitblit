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

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.wicket.panels.FederationProposalsPanel;
import com.gitblit.wicket.panels.FederationRegistrationsPanel;
import com.gitblit.wicket.panels.FederationTokensPanel;

public class FederationPage extends RootPage {

	public FederationPage() {
		super();
		setupPage("", "");

		boolean showFederation = showAdmin && GitBlit.canFederate();
		add(new FederationTokensPanel("federationTokensPanel", showFederation)
				.setVisible(showFederation));
		FederationProposalsPanel proposalsPanel = new FederationProposalsPanel(
				"federationProposalsPanel");
		if (showFederation) {
			proposalsPanel.hideIfEmpty();
		} else {
			proposalsPanel.setVisible(false);
		}

		boolean showRegistrations = GitBlit.getBoolean(Keys.web.showFederationRegistrations, false);
		FederationRegistrationsPanel registrationsPanel = new FederationRegistrationsPanel(
				"federationRegistrationsPanel");
		if (showAdmin || showRegistrations) {
			registrationsPanel.hideIfEmpty();
		} else {
			registrationsPanel.setVisible(false);
		}
		add(proposalsPanel);
		add(registrationsPanel);
	}
}
