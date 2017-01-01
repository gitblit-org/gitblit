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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

import com.gitblit.Constants;
import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.AuthorizationControl;
import com.gitblit.Constants.PermissionType;
import com.gitblit.Constants.RegistrantType;
import com.gitblit.Keys;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.ServerSettings;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.SecurePasswordHashUtils;
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

	private JCheckBox canForkCheckbox;

	private JCheckBox canCreateCheckbox;

	private JCheckBox notFederatedCheckbox;

	private JCheckBox disabledCheckbox;

	private JTextField organizationalUnitField;

	private JTextField organizationField;

	private JTextField localityField;

	private JTextField stateProvinceField;

	private JTextField countryCodeField;

	private RegistrantPermissionsPanel repositoryPalette;

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
			@Override
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
		canForkCheckbox = new JCheckBox(Translation.get("gb.canForkDescription"), anUser.canFork);
		canCreateCheckbox = new JCheckBox(Translation.get("gb.canCreateDescription"), anUser.canCreate);
		notFederatedCheckbox = new JCheckBox(
				Translation.get("gb.excludeFromFederationDescription"),
				anUser.excludeFromFederation);
		disabledCheckbox = new JCheckBox(Translation.get("gb.disableUserDescription"), anUser.disabled);

		organizationalUnitField = new JTextField(anUser.organizationalUnit == null ? "" : anUser.organizationalUnit, 25);
		organizationField = new JTextField(anUser.organization == null ? "" : anUser.organization, 25);
		localityField = new JTextField(anUser.locality == null ? "" : anUser.locality, 25);
		stateProvinceField = new JTextField(anUser.stateProvince == null ? "" : anUser.stateProvince, 25);
		countryCodeField = new JTextField(anUser.countryCode == null ? "" : anUser.countryCode, 15);

		// credentials are optionally controlled by 3rd-party authentication
		usernameField.setEnabled(anUser.isLocalAccount());
		passwordField.setEnabled(anUser.isLocalAccount());
		confirmPasswordField.setEnabled(anUser.isLocalAccount());

		JPanel fieldsPanel = new JPanel(new GridLayout(0, 1));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.username"), usernameField));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.password"), passwordField));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.confirmPassword"), confirmPasswordField));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.displayName"), displayNameField));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.emailAddress"), emailAddressField));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.canAdmin"), canAdminCheckbox));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.canFork"), canForkCheckbox));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.canCreate"), canCreateCheckbox));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.excludeFromFederation"),
				notFederatedCheckbox));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.disableUser"), disabledCheckbox));

		JPanel attributesPanel = new JPanel(new GridLayout(0, 1, 5, 2));
		attributesPanel.add(newFieldPanel(Translation.get("gb.organizationalUnit") + " (OU)", organizationalUnitField));
		attributesPanel.add(newFieldPanel(Translation.get("gb.organization") + " (O)", organizationField));
		attributesPanel.add(newFieldPanel(Translation.get("gb.locality") + " (L)", localityField));
		attributesPanel.add(newFieldPanel(Translation.get("gb.stateProvince") + " (ST)", stateProvinceField));
		attributesPanel.add(newFieldPanel(Translation.get("gb.countryCode") + " (C)", countryCodeField));

		final Insets _insets = new Insets(5, 5, 5, 5);
		repositoryPalette = new RegistrantPermissionsPanel(RegistrantType.REPOSITORY);
		teamsPalette = new JPalette<TeamModel>();

		JPanel fieldsPanelTop = new JPanel(new BorderLayout());
		fieldsPanelTop.add(fieldsPanel, BorderLayout.NORTH);

		JPanel attributesPanelTop = new JPanel(new BorderLayout());
		attributesPanelTop.add(attributesPanel, BorderLayout.NORTH);

		JPanel repositoriesPanel = new JPanel(new BorderLayout()) {

			private static final long serialVersionUID = 1L;

			@Override
			public Insets getInsets() {
				return _insets;
			}
		};
		repositoriesPanel.add(repositoryPalette, BorderLayout.CENTER);

		JPanel teamsPanel = new JPanel(new BorderLayout()) {

			private static final long serialVersionUID = 1L;

			@Override
			public Insets getInsets() {
				return _insets;
			}
		};
		teamsPanel.add(teamsPalette, BorderLayout.CENTER);

		JTabbedPane panel = new JTabbedPane(JTabbedPane.TOP);
		panel.addTab(Translation.get("gb.general"), fieldsPanelTop);
		panel.addTab(Translation.get("gb.attributes"), attributesPanelTop);
		if (protocolVersion > 1) {
			panel.addTab(Translation.get("gb.teamMemberships"), teamsPanel);
		}
		panel.addTab(Translation.get("gb.restrictedRepositories"), repositoriesPanel);

		JButton createButton = new JButton(Translation.get("gb.save"));
		createButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				if (validateFields()) {
					canceled = false;
					setVisible(false);
				}
			}
		});

		JButton cancelButton = new JButton(Translation.get("gb.cancel"));
		cancelButton.addActionListener(new ActionListener() {
			@Override
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
				&& !password.toUpperCase().startsWith(StringUtils.COMBINED_MD5_TYPE)
				&& !password.startsWith(SecurePasswordHashUtils.PBKDF2WITHHMACSHA256_TYPE)) {
			String cpw = new String(confirmPasswordField.getPassword());
			if (cpw == null || cpw.length() != password.length()) {
				error("Please confirm the password!");
				return false;
			}
			if (!password.equals(cpw)) {
				error("Passwords do not match!");
				return false;
			}

			// change the cookie
			user.cookie = user.createCookie();

			String type = settings.get(Keys.realm.passwordStorage).getString("PBKDF2WithHmacSHA256");
			if (type.equalsIgnoreCase("md5")) {
				// store MD5 digest of password
				user.password = StringUtils.MD5_TYPE + StringUtils.getMD5(password);
			} else if (type.equalsIgnoreCase("combined-md5")) {
				// store MD5 digest of username+password
				user.password = StringUtils.COMBINED_MD5_TYPE
						+ StringUtils.getMD5(user.username + password);
			} else if (type.equalsIgnoreCase("PBKDF2WithHmacSHA256")) {
				// store PBKDF2WithHmacSHA256 digest of password
				user.password  = SecurePasswordHashUtils.get().createStoredPasswordFromPassword(password);
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
		user.canFork = canForkCheckbox.isSelected();
		user.canCreate = canCreateCheckbox.isSelected();
		user.excludeFromFederation = notFederatedCheckbox.isSelected();
		user.disabled = disabledCheckbox.isSelected();

		user.organizationalUnit = organizationalUnitField.getText().trim();
		user.organization = organizationField.getText().trim();
		user.locality = localityField.getText().trim();
		user.stateProvince = stateProvinceField.getText().trim();
		user.countryCode = countryCodeField.getText().trim();

		for (RegistrantAccessPermission rp : repositoryPalette.getPermissions()) {
			user.setRepositoryPermission(rp.registrant, rp.permission);
		}

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

	public void setRepositories(List<RepositoryModel> repositories, List<RegistrantAccessPermission> permissions) {
		Map<String, RepositoryModel> repoMap = new HashMap<String, RepositoryModel>();
		List<String> restricted = new ArrayList<String>();
		for (RepositoryModel repo : repositories) {
			// exclude Owner or personal repositories
			if (!repo.isOwner(username) && !repo.isUsersPersonalRepository(username)) {
				if (repo.accessRestriction.exceeds(AccessRestrictionType.NONE)
						&& repo.authorizationControl.equals(AuthorizationControl.NAMED)) {
					restricted.add(repo.name);
				}
			}
			repoMap.put(repo.name.toLowerCase(), repo);
		}
		StringUtils.sortRepositorynames(restricted);

		List<String> list = new ArrayList<String>();
		// repositories
		list.add(".*");

		String prefix;
		if (settings.hasKey(Keys.git.userRepositoryPrefix)) {
			prefix = settings.get(Keys.git.userRepositoryPrefix).currentValue;
			if (StringUtils.isEmpty(prefix)) {
				prefix = Constants.DEFAULT_USER_REPOSITORY_PREFIX;
			}
		} else {
			prefix = Constants.DEFAULT_USER_REPOSITORY_PREFIX;
		}

		if (prefix.length() == 1) {
			// all repositories excluding personal repositories
			list.add("[^" + prefix + "].*");
		}

		String lastProject = null;
		for (String repo : restricted) {
			String projectPath = StringUtils.getFirstPathElement(repo).toLowerCase();
			if (lastProject == null || !lastProject.equalsIgnoreCase(projectPath)) {
				lastProject = projectPath;
				if (!StringUtils.isEmpty(projectPath)) {
					// regex for all repositories within a project
					list.add(projectPath + "/.*");
				}
			}
			list.add(repo);
		}

		// remove repositories for which user already has a permission
		if (permissions == null) {
			permissions = new ArrayList<RegistrantAccessPermission>();
		} else {
			for (RegistrantAccessPermission rp : permissions) {
				list.remove(rp.registrant.toLowerCase());
			}
		}

		// update owner and missing permissions for editing
		for (RegistrantAccessPermission permission : permissions) {
			if (permission.mutable && PermissionType.EXPLICIT.equals(permission.permissionType)) {
				// Ensure this is NOT an owner permission - which is non-editable
				// We don't know this from within the usermodel, ownership is a
				// property of a repository.
				RepositoryModel rm = repoMap.get(permission.registrant.toLowerCase());
				if (rm == null) {
					permission.permissionType = PermissionType.MISSING;
					permission.mutable = false;
					continue;
				}
				boolean isOwner = rm.isOwner(username);
				if (isOwner) {
					permission.permissionType = PermissionType.OWNER;
					permission.mutable = false;
				}
			}
		}

		repositoryPalette.setObjects(list, permissions);
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
