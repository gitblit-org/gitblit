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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.ServerSettings;
import com.gitblit.models.TeamModel;
import com.gitblit.utils.StringUtils;

public class EditTeamDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	private final String teamname;

	private final TeamModel team;

	private final ServerSettings settings;

	private boolean isCreate;

	private boolean canceled = true;

	private JTextField teamnameField;

	private JTextField mailingListsField;

	private JPalette<String> repositoryPalette;

	private JPalette<String> userPalette;

	private JPalette<String> preReceivePalette;

	private JLabel preReceiveInherited;

	private JPalette<String> postReceivePalette;

	private JLabel postReceiveInherited;

	private Set<String> teamnames;

	public EditTeamDialog(int protocolVersion, ServerSettings settings) {
		this(protocolVersion, new TeamModel(""), settings);
		this.isCreate = true;
		setTitle(Translation.get("gb.newTeam"));
	}

	public EditTeamDialog(int protocolVersion, TeamModel aTeam, ServerSettings settings) {
		super();
		this.teamname = aTeam.name;
		this.team = new TeamModel("");
		this.settings = settings;
		this.teamnames = new HashSet<String>();
		this.isCreate = false;
		initialize(protocolVersion, aTeam);
		setModal(true);
		setTitle(Translation.get("gb.edit") + ": " + aTeam.name);
		setIconImage(new ImageIcon(getClass().getResource("/gitblt-favicon.png")).getImage());
	}

	@Override
	protected JRootPane createRootPane() {
		KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
		JRootPane rootPane = new JRootPane();
		rootPane.registerKeyboardAction(new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				setVisible(false);
			}
		}, stroke, JComponent.WHEN_IN_FOCUSED_WINDOW);
		return rootPane;
	}

	private void initialize(int protocolVersion, TeamModel aTeam) {
		teamnameField = new JTextField(aTeam.name == null ? "" : aTeam.name, 25);

		mailingListsField = new JTextField(aTeam.mailingLists == null ? ""
				: StringUtils.flattenStrings(aTeam.mailingLists, " "), 50);

		JPanel fieldsPanel = new JPanel(new GridLayout(0, 1));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.teamName"), teamnameField));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.mailingLists"), mailingListsField));

		final Insets _insets = new Insets(5, 5, 5, 5);
		repositoryPalette = new JPalette<String>();
		userPalette = new JPalette<String>();
		userPalette.setEnabled(settings.supportsTeamMembershipChanges);
		
		JPanel fieldsPanelTop = new JPanel(new BorderLayout());
		fieldsPanelTop.add(fieldsPanel, BorderLayout.NORTH);

		JPanel repositoriesPanel = new JPanel(new BorderLayout()) {

			private static final long serialVersionUID = 1L;

			public Insets getInsets() {
				return _insets;
			}
		};
		repositoriesPanel.add(repositoryPalette, BorderLayout.CENTER);

		JPanel usersPanel = new JPanel(new BorderLayout()) {

			private static final long serialVersionUID = 1L;

			public Insets getInsets() {
				return _insets;
			}
		};
		usersPanel.add(userPalette, BorderLayout.CENTER);

		preReceivePalette = new JPalette<String>(true);
		preReceiveInherited = new JLabel();
		JPanel preReceivePanel = new JPanel(new BorderLayout(5, 5));
		preReceivePanel.add(preReceivePalette, BorderLayout.CENTER);
		preReceivePanel.add(preReceiveInherited, BorderLayout.WEST);
		
		postReceivePalette = new JPalette<String>(true);
		postReceiveInherited = new JLabel();
		JPanel postReceivePanel = new JPanel(new BorderLayout(5, 5));
		postReceivePanel.add(postReceivePalette, BorderLayout.CENTER);		
		postReceivePanel.add(postReceiveInherited, BorderLayout.WEST);

		JTabbedPane panel = new JTabbedPane(JTabbedPane.TOP);
		panel.addTab(Translation.get("gb.general"), fieldsPanelTop);
		panel.addTab(Translation.get("gb.teamMembers"), usersPanel);
		panel.addTab(Translation.get("gb.restrictedRepositories"), repositoriesPanel);
		panel.addTab(Translation.get("gb.preReceiveScripts"), preReceivePanel);
		panel.addTab(Translation.get("gb.postReceiveScripts"), postReceivePanel);

		JButton createButton = new JButton(Translation.get("gb.save"));
		createButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				if (validateFields()) {
					canceled = false;
					setVisible(false);
				}
			}
		});

		JButton cancelButton = new JButton(Translation.get("gb.cancel"));
		cancelButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				canceled = true;
				setVisible(false);
			}
		});

		JPanel controls = new JPanel();
		controls.add(cancelButton);
		controls.add(createButton);

		JPanel centerPanel = new JPanel(new BorderLayout(5, 5)) {

			private static final long serialVersionUID = 1L;

			@Override
			public Insets getInsets() {
				return _insets;
			}
		};
		centerPanel.add(panel, BorderLayout.CENTER);
		centerPanel.add(controls, BorderLayout.SOUTH);

		getContentPane().setLayout(new BorderLayout(5, 5));
		getContentPane().add(centerPanel, BorderLayout.CENTER);
		pack();
	}

	private JPanel newFieldPanel(String label, JComponent comp) {
		JLabel fieldLabel = new JLabel(label);
		fieldLabel.setFont(fieldLabel.getFont().deriveFont(Font.BOLD));
		fieldLabel.setPreferredSize(new Dimension(150, 20));
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		panel.add(fieldLabel);
		panel.add(comp);
		return panel;
	}

	private boolean validateFields() {
		String tname = teamnameField.getText();
		if (StringUtils.isEmpty(tname)) {
			error("Please enter a team name!");
			return false;
		}

		boolean rename = false;
		// verify teamname uniqueness on create
		if (isCreate) {
			if (teamnames.contains(tname.toLowerCase())) {
				error(MessageFormat.format("Team name ''{0}'' is unavailable.", tname));
				return false;
			}
		} else {
			// check rename collision
			rename = !StringUtils.isEmpty(teamname) && !teamname.equalsIgnoreCase(tname);
			if (rename) {
				if (teamnames.contains(tname.toLowerCase())) {
					error(MessageFormat.format(
							"Failed to rename ''{0}'' because ''{1}'' already exists.", teamname,
							tname));
					return false;
				}
			}
		}
		team.name = tname;

		String ml = mailingListsField.getText();
		if (!StringUtils.isEmpty(ml)) {
			Set<String> list = new HashSet<String>();
			for (String address : ml.split("(,|\\s)")) {
				if (StringUtils.isEmpty(address)) {
					continue;
				}
				list.add(address.toLowerCase());
			}
			team.mailingLists.clear();
			team.mailingLists.addAll(list);
		}

		team.repositories.clear();
		team.repositories.addAll(repositoryPalette.getSelections());

		team.users.clear();
		team.users.addAll(userPalette.getSelections());

		team.preReceiveScripts.clear();
		team.preReceiveScripts.addAll(preReceivePalette.getSelections());

		team.postReceiveScripts.clear();
		team.postReceiveScripts.addAll(postReceivePalette.getSelections());

		return true;
	}

	private void error(String message) {
		JOptionPane.showMessageDialog(EditTeamDialog.this, message, Translation.get("gb.error"),
				JOptionPane.ERROR_MESSAGE);
	}

	public void setTeams(List<TeamModel> teams) {
		teamnames.clear();
		for (TeamModel team : teams) {
			teamnames.add(team.name.toLowerCase());
		}
	}

	public void setRepositories(List<RepositoryModel> repositories, List<String> selected) {
		List<String> restricted = new ArrayList<String>();
		for (RepositoryModel repo : repositories) {
			if (repo.accessRestriction.exceeds(AccessRestrictionType.NONE)) {
				restricted.add(repo.name);
			}
		}
		StringUtils.sortRepositorynames(restricted);
		if (selected != null) {
			StringUtils.sortRepositorynames(selected);
		}
		repositoryPalette.setObjects(restricted, selected);
	}

	public void setUsers(List<String> users, List<String> selected) {
		Collections.sort(users);
		if (selected != null) {
			Collections.sort(selected);
		}
		userPalette.setObjects(users, selected);
	}

	public void setPreReceiveScripts(List<String> unused, List<String> inherited,
			List<String> selected) {
		Collections.sort(unused);
		if (selected != null) {
			Collections.sort(selected);
		}
		preReceivePalette.setObjects(unused, selected);
		showInherited(inherited, preReceiveInherited);
	}

	public void setPostReceiveScripts(List<String> unused, List<String> inherited,
			List<String> selected) {
		Collections.sort(unused);
		if (selected != null) {
			Collections.sort(selected);
		}
		postReceivePalette.setObjects(unused, selected);
		showInherited(inherited, postReceiveInherited);
	}

	private void showInherited(List<String> list, JLabel label) {
		StringBuilder sb = new StringBuilder();
		if (list != null && list.size() > 0) {
			sb.append("<html><body><b>INHERITED</b><ul style=\"margin-left:5px;list-style-type: none;\">");
			for (String script : list) {
				sb.append("<li>").append(script).append("</li>");
			}
			sb.append("</ul></body></html>");
		}
		label.setText(sb.toString());
	}

	public TeamModel getTeam() {
		if (canceled) {
			return null;
		}
		return team;
	}
}
