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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;

public class EditUserDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	private final UserModel user;

	private final IStoredSettings settings;

	private boolean canceled = true;

	private JTextField usernameField;

	private JPasswordField passwordField;

	private JPasswordField confirmPasswordField;

	private JCheckBox canAdminCheckbox;

	private JCheckBox notFederatedCheckbox;

	private JPalette<String> repositoryPalette;

	public EditUserDialog(IStoredSettings settings) {
		this(new UserModel(""), settings);
		setTitle("Create User");
	}

	public EditUserDialog(UserModel anUser, IStoredSettings settings) {
		super();
		this.user = new UserModel("");
		this.settings = settings;
		initialize(anUser);
		setModal(true);
		setTitle("Edit User: " + anUser.username);
		setIconImage(new ImageIcon(getClass().getResource("/gitblt-favicon.png")).getImage());
	}

	private void initialize(UserModel anUser) {
		usernameField = new JTextField(anUser.username == null ? "" : anUser.username, 25);
		passwordField = new JPasswordField(anUser.password == null ? "" : anUser.password, 25);
		confirmPasswordField = new JPasswordField(anUser.password == null ? "" : anUser.password,
				25);
		canAdminCheckbox = new JCheckBox("can administer Gitblit server", anUser.canAdmin);
		notFederatedCheckbox = new JCheckBox(
				"block federated Gitblit instances from pulling this account",
				anUser.excludeFromFederation);

		JPanel fieldsPanel = new JPanel(new GridLayout(0, 1));
		fieldsPanel.add(newFieldPanel("username", usernameField));
		fieldsPanel.add(newFieldPanel("password", passwordField));
		fieldsPanel.add(newFieldPanel("confirm password", confirmPasswordField));
		fieldsPanel.add(newFieldPanel("can admin", canAdminCheckbox));
		fieldsPanel.add(newFieldPanel("exclude from federation", notFederatedCheckbox));

		repositoryPalette = new JPalette<String>();
		JPanel panel = new JPanel(new BorderLayout());
		panel.add(fieldsPanel, BorderLayout.NORTH);
		panel.add(newFieldPanel("restricted repositories", repositoryPalette), BorderLayout.CENTER);

		JButton createButton = new JButton("Save");
		createButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				if (validateFields()) {
					canceled = false;
					setVisible(false);
				}
			}
		});

		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				canceled = true;
				setVisible(false);
			}
		});

		JPanel controls = new JPanel();
		controls.add(cancelButton);
		controls.add(createButton);

		final Insets _insets = new Insets(5, 5, 5, 5);
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
		setLocationRelativeTo(null);
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
		String uname = usernameField.getText();
		if (StringUtils.isEmpty(uname)) {
			showValidationError("Please enter a username!");
			return false;
		}

		// TODO verify username uniqueness on create
		
		// if (isCreate) {
		// UserModel model = GitBlit.self().getUserModel(username);
		// if (model != null) {
		// error(MessageFormat.format("Username ''{0}'' is unavailable.",
		// username));
		// return;
		// }
		// }

		int minLength = settings.getInteger(Keys.realm.minPasswordLength, 5);
		if (minLength < 4) {
			minLength = 4;
		}
		char[] pw = passwordField.getPassword();
		if (pw == null || pw.length < minLength) {
			showValidationError(MessageFormat.format(
					"Password is too short. Minimum length is {0} characters.", minLength));
			return false;
		}
		char[] cpw = confirmPasswordField.getPassword();
		if (cpw == null || cpw.length != pw.length) {
			showValidationError("Please confirm the password!");
			return false;
		}
		if (!Arrays.equals(pw, cpw)) {
			showValidationError("Passwords do not match!");
			return false;
		}
		user.username = uname;
		String type = settings.getString(Keys.realm.passwordStorage, "md5");
		if (type.equalsIgnoreCase("md5")) {
			// store MD5 digest of password
			user.password = StringUtils.MD5_TYPE + StringUtils.getMD5(new String(pw));
		} else {
			user.password = new String(pw);
		}
		user.canAdmin = canAdminCheckbox.isSelected();
		user.excludeFromFederation = notFederatedCheckbox.isSelected();

		user.repositories.clear();
		user.repositories.addAll(repositoryPalette.getSelections());
		return true;
	}

	private void showValidationError(String message) {
		JOptionPane.showMessageDialog(EditUserDialog.this, message, "Validation Error",
				JOptionPane.ERROR_MESSAGE);
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

	public UserModel getUser() {
		if (canceled) {
			return null;
		}
		return user;
	}
}
