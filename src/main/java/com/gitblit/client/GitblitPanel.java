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
package com.gitblit.client;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Insets;
import java.io.IOException;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.gitblit.client.ClosableTabComponent.CloseTabListener;
import com.gitblit.models.FeedModel;

/**
 * GitblitPanel is a container for the repository, users, settings, etc panels.
 * 
 * @author James Moger
 * 
 */
public class GitblitPanel extends JPanel implements CloseTabListener {

	private static final long serialVersionUID = 1L;

	private final RegistrationsDialog.RegistrationListener listener;

	private GitblitClient gitblit;

	private JTabbedPane tabs;

	private RepositoriesPanel repositoriesPanel;

	private FeedsPanel feedsPanel;

	private UsersPanel usersPanel;
	
	private TeamsPanel teamsPanel;

	private SettingsPanel settingsPanel;

	private StatusPanel statusPanel;

	public GitblitPanel(GitblitRegistration reg, RegistrationsDialog.RegistrationListener listener) {
		this.gitblit = new GitblitClient(reg);
		this.listener = listener;

		tabs = new JTabbedPane(JTabbedPane.BOTTOM);
		tabs.addTab(Translation.get("gb.repositories"), createRepositoriesPanel());
		tabs.addTab(Translation.get("gb.activity"), createFeedsPanel());
		tabs.addTab(Translation.get("gb.teams"), createTeamsPanel());
		tabs.addTab(Translation.get("gb.users"), createUsersPanel());
		tabs.addTab(Translation.get("gb.settings"), createSettingsPanel());
		tabs.addTab(Translation.get("gb.status"), createStatusPanel());
		tabs.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				tabs.getSelectedComponent().requestFocus();
			}
		});

		setLayout(new BorderLayout());
		add(tabs, BorderLayout.CENTER);
	}

	private JPanel createRepositoriesPanel() {
		repositoriesPanel = new RepositoriesPanel(gitblit) {

			private static final long serialVersionUID = 1L;

			@Override
			protected void subscribeFeeds(List<FeedModel> feeds) {
				GitblitPanel.this.subscribeFeeds(feeds);
			}

			@Override
			protected void updateUsersTable() {
				usersPanel.updateTable(false);
			}
			
			@Override
			protected void updateTeamsTable() {
				teamsPanel.updateTable(false);
			}

		};
		return repositoriesPanel;
	}

	private JPanel createFeedsPanel() {
		feedsPanel = new FeedsPanel(gitblit) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void subscribeFeeds(List<FeedModel> feeds) {
				GitblitPanel.this.subscribeFeeds(feeds);
			}
		};
		return feedsPanel;
	}

	private JPanel createUsersPanel() {
		usersPanel = new UsersPanel(gitblit) {
			
			private static final long serialVersionUID = 1L;
			
			@Override
			protected void updateTeamsTable() {
				teamsPanel.updateTable(false);
			}
		};
		return usersPanel;
	}
	
	private JPanel createTeamsPanel() {
		teamsPanel = new TeamsPanel(gitblit) {
			
			private static final long serialVersionUID = 1L;

			@Override
			protected void updateUsersTable() {
				usersPanel.updateTable(false);
			}
		};
		return teamsPanel;
	}	

	private JPanel createSettingsPanel() {
		settingsPanel = new SettingsPanel(gitblit);
		return settingsPanel;
	}

	private JPanel createStatusPanel() {
		statusPanel = new StatusPanel(gitblit);
		return statusPanel;
	}

	public void login() throws IOException {
		gitblit.login();

		repositoriesPanel.updateTable(true);
		feedsPanel.updateTable(true);

		if (gitblit.allowManagement()) {
			if (gitblit.getProtocolVersion() >= 2) {
				// refresh teams panel
				teamsPanel.updateTable(false);
			} else {
				// remove teams panel
				String teams = Translation.get("gb.teams");
				for (int i = 0; i < tabs.getTabCount(); i++) {
					if (teams.equals(tabs.getTitleAt(i))) {
						tabs.removeTabAt(i);
						break;
					}
				}
			}
			usersPanel.updateTable(false);
		} else {
			// user does not have administrator privileges
			// hide admin repository buttons
			repositoriesPanel.disableManagement();

			while (tabs.getTabCount() > 2) {
				// remove all management/administration tabs
				tabs.removeTabAt(2);
			}
		}

		if (gitblit.allowAdministration()) {
			settingsPanel.updateTable(true);
			statusPanel.updateTable(false);
		} else {
			// remove the settings and status tab
			String[] titles = { Translation.get("gb.settings"), Translation.get("gb.status") };
			for (String title : titles) {
				for (int i = 0; i < tabs.getTabCount(); i++) {
					if (tabs.getTitleAt(i).equals(title)) {
						tabs.removeTabAt(i);
						break;
					}
				}
			}
		}
	}

	@Override
	public Insets getInsets() {
		return Utils.INSETS;
	}

	@Override
	public void closeTab(Component c) {
		gitblit = null;
	}

	protected void subscribeFeeds(final List<FeedModel> feeds) {
		SubscriptionsDialog dialog = new SubscriptionsDialog(feeds) {

			private static final long serialVersionUID = 1L;

			@Override
			public void save() {
				gitblit.updateSubscribedFeeds(feeds);
				listener.saveRegistration(gitblit.reg.name, gitblit.reg);
				setVisible(false);
				repositoriesPanel.updateTable(false);
			}
		};
		dialog.setLocationRelativeTo(GitblitPanel.this);
		dialog.setVisible(true);
	}
}