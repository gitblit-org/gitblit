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
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRootPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Keys;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.ServerSettings;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;

public class EditUserDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	private final String username;

	private final UserModel user;

	private final ServerSettings settings;

	private boolean isCreate;

	private boolean canceled = true;

	private JTextField usernameField;

	private JPasswordField passwordField;

	private JPasswordField confirmPasswordField;
	
	private JTextField displayNameField;
	
	private JTextField emailAddressField;

	private JCheckBox canAdminCheckbox;

	private JCheckBox notFederatedCheckbox;

	private JPalette<String> repositoryPalette;

	private JPalette<TeamModel> teamsPalette;

	private Set<String> usernames;

	public EditUserDialog(int protocolVersion, ServerSettings settings) {
		this(protocolVersion, new UserModel(""), settings);
		this.isCreate = true;
		setTitle(Translation.get("gb.newUser"));
	}

	public EditUserDialog(int protocolVersion, UserModel anUser, ServerSettings settings) {
		super();
		this.username = anUser.username;
		this.user = new UserModel("");
		this.settings = settings;
		this.usernames = new HashSet<String>();
		this.isCreate = false;
		initialize(protocolVersion, anUser);
		setModal(true);
		setTitle(Translation.get("gb.edit") + ": " + anUser.username);
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

	private void initialize(int protocolVersion, UserModel anUser) {
		usernameField = new JTextField(anUser.username == null ? "" : anUser.username, 25);
		passwordField = new JPasswordField(anUser.password == null ? "" : anUser.password, 25);
		confirmPasswordField = new JPasswordField(anUser.password == null ? "" : anUser.password,
				25);
		displayNameField = new JTextField(anUser.displayName == null ? "" : anUser.displayName, 25);
		emailAddressField = new JTextField(anUser.emailAddress == null ? "" : anUser.emailAddress, 25);
		canAdminCheckbox = new JCheckBox(Translation.get("gb.canAdminDescription"), anUser.canAdmin);		
		notFederatedCheckbox = new JCheckBox(
				Translation.get("gb.excludeFromFederationDescription"),
				anUser.excludeFromFederation);
		
		// credentials are optionally controlled by 3rd-party authentication
		usernameField.setEnabled(settings.supportsCredentialChanges);
		passwordField.setEnabled(settings.supportsCredentialChanges);
		confirmPasswordField.setEnabled(settings.supportsCredentialChanges);

		displayNameField.setEnabled(settings.supportsDisplayNameChanges);
		emailAddressField.setEnabled(settings.supportsEmailAddressChanges);

		JPanel fieldsPanel = new JPanel(new GridLayout(0, 1));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.username"), usernameField));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.password"), passwordField));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.confirmPassword"), confirmPasswordField));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.displayName"), displayNameField));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.emailAddress"), emailAddressField));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.canAdmin"), canAdminCheckbox));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.excludeFromFederation"),
				notFederatedCheckbox));

		final Insets _insets = new Insets(5, 5, 5, 5);
		repositoryPalette = new JPalette<String>();
		teamsPalette = new JPalette<TeamModel>();
		teamsPalette.setEnabled(settings.supportsTeamMembershipChanges);

		JPanel fieldsPanelTop = new JPanel(new BorderLayout());
		fieldsPanelTop.add(fieldsPanel, BorderLayout.NORTH);

		JPanel repositoriesPanel = new JPanel(new BorderLayout()) {

			private static final long serialVersionUID = 1L;

			public Insets getInsets() {
				return _insets;
			}
		};
		repositoriesPanel.add(repositoryPalette, BorderLayout.CENTER);

		JPanel teamsPanel = new JPanel(new BorderLayout()) {

			private static final long serialVersionUID = 1L;

			public Insets getInsets() {
				return _insets;
			}
		};
		teamsPanel.add(teamsPalette, BorderLayout.CENTER);

		JTabbedPane panel = new JTabbedPane(JTabbedPane.TOP);
		panel.addTab(Translation.get("gb.general"), fieldsPanelTop);
		if (protocolVersion > 1) {
			panel.addTab(Translation.get("gb.teamMemberships"), teamsPanel);
		}
		panel.addTab(Translation.get("gb.restrictedRepositories"), repositoriesPanel);

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
		if (StringUtils.isEmpty(usernameField.getText())) {
			error("Please enter a username!");
			return false;
		}
		String uname = usernameField.getText().toLowerCase();
		boolean rename = false;
		// verify username uniqueness on create
		if (isCreate) {
			if (usernames.contains(uname)) {
				error(MessageFormat.format("Username ''{0}'' is unavailable.", uname));
				return false;
			}
		} else {
			// check rename collision
			rename = !StringUtils.isEmpty(username) && !username.equalsIgnoreCase(uname);
			if (rename) {
				if (usernames.contains(uname)) {
					error(MessageFormat.format(
							"Failed to rename ''{0}'' because ''{1}'' already exists.", username,
							uname));
					return false;
				}
			}
		}
		user.username = uname;

		int minLength = settings.get(Keys.realm.minPasswordLength).getInteger(5);
		if (minLength < 4) {
			minLength = 4;
		}

		String password = new String(passwordField.getPassword());
		if (StringUtils.isEmpty(password) || password.length() < minLength) {
			error(MessageFormat.format("Password is too short. Minimum length is {0} characters.",
					minLength));
			return false;
		}
		if (!password.toUpperCase().startsWith(StringUtils.MD5_TYPE)
				&& !password.toUpperCase().startsWith(StringUtils.COMBINED_MD5_TYPE)) {
			String cpw = new String(confirmPasswordField.getPassword());
			if (cpw == null || cpw.length() != password.length()) {
				error("Please confirm the password!");
				return false;
			}
			if (!password.equals(cpw)) {
				error("Passwords do not match!");
				return false;
			}

			String type = settings.get(Keys.realm.passwordStorage).getString("md5");
			if (type.equalsIgnoreCase("md5")) {
				// store MD5 digest of password
				user.password = StringUtils.MD5_TYPE + StringUtils.getMD5(password);
			} else if (type.equalsIgnoreCase("combined-md5")) {
				// store MD5 digest of username+password
				user.password = StringUtils.COMBINED_MD5_TYPE
						+ StringUtils.getMD5(user.username + password);
			} else {
				// plain-text password
				user.password = password;
			}
		} else if (rename && password.toUpperCase().startsWith(StringUtils.COMBINED_MD5_TYPE)) {
			error("Gitblit is configured for combined-md5 password hashing. You must enter a new password on account rename.");
			return false;
		} else {
			// no change in password
			user.password = password;
		}
		
		user.displayName = displayNameField.getText().trim();
		user.emailAddress = emailAddressField.getText().trim();

		user.canAdmin = canAdminCheckbox.isSelected();
		user.excludeFromFederation = notFederatedCheckbox.isSelected();

		user.repositories.clear();
		user.repositories.addAll(repositoryPalette.getSelections());

		user.teams.clear();
		user.teams.addAll(teamsPalette.getSelections());
		return true;
	}

	private void error(String message) {
		JOptionPane.showMessageDialog(EditUserDialog.this, message, Translation.get("gb.error"),
				JOptionPane.ERROR_MESSAGE);
	}

	public void setUsers(List<UserModel> users) {
		usernames.clear();
		for (UserModel user : users) {
			usernames.add(user.username.toLowerCase());
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

	public void setTeams(List<TeamModel> teams, List<TeamModel> selected) {
		Collections.sort(teams);
		if (selected != null) {
			Collections.sort(selected);
		}
		teamsPalette.setObjects(teams, selected);
	}
	
	public UserModel getUser() {
		if (canceled) {
			return null;
		}
		return user;
	}
}
