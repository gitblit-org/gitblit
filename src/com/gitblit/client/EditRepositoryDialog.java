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
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.FederationStrategy;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;

public class EditRepositoryDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	private final RepositoryModel repository;

	private boolean canceled = true;

	private JTextField nameField;

	private JTextField descriptionField;

	private JCheckBox useTickets;

	private JCheckBox useDocs;

	private JCheckBox showRemoteBranches;

	private JCheckBox showReadme;

	private JCheckBox isFrozen;

	private JComboBox accessRestriction;

	private JComboBox federationStrategy;

	private JComboBox owner;

	private JPalette<String> usersPalette;

	private JPalette<String> setsPalette;

	public EditRepositoryDialog(List<UserModel> allusers) {
		this(new RepositoryModel(), allusers);
		setTitle("Create Repository");
	}

	public EditRepositoryDialog(RepositoryModel aRepository, List<UserModel> allUsers) {
		super();
		this.repository = new RepositoryModel();
		initialize(aRepository, allUsers);
		setModal(true);
		setTitle("Edit Repository: " + aRepository.name);
		setIconImage(new ImageIcon(getClass().getResource("/gitblt-favicon.png")).getImage());
	}

	private void initialize(RepositoryModel anRepository, List<UserModel> allUsers) {
		nameField = new JTextField(anRepository.name == null ? "" : anRepository.name, 35);
		descriptionField = new JTextField(anRepository.description == null ? ""
				: anRepository.description, 35);

		owner = new JComboBox(allUsers.toArray());
		if (!StringUtils.isEmpty(anRepository.owner)) {
			UserModel currentOwner = null;
			for (UserModel user : allUsers) {
				if (user.username.equalsIgnoreCase(anRepository.owner)) {
					currentOwner = user;
					break;
				}
			}
			owner.setSelectedItem(currentOwner);
		}

		useTickets = new JCheckBox("distributed Ticgit issues", anRepository.useTickets);
		useDocs = new JCheckBox("enumerates Markdown documentation in repository",
				anRepository.useDocs);
		showRemoteBranches = new JCheckBox("show remote branches", anRepository.showRemoteBranches);
		showReadme = new JCheckBox("show a \"readme\" Markdown file on the summary page",
				anRepository.showReadme);
		isFrozen = new JCheckBox("deny push operations", anRepository.isFrozen);

		accessRestriction = new JComboBox(AccessRestrictionType.values());
		accessRestriction.setSelectedItem(anRepository.accessRestriction);

		federationStrategy = new JComboBox(FederationStrategy.values());
		federationStrategy.setSelectedItem(anRepository.federationStrategy);

		JPanel fieldsPanel = new JPanel(new GridLayout(0, 1));
		fieldsPanel.add(newFieldPanel("name", nameField));
		fieldsPanel.add(newFieldPanel("description", descriptionField));
		fieldsPanel.add(newFieldPanel("owner", owner));

		fieldsPanel.add(newFieldPanel("enable tickets", useTickets));
		fieldsPanel.add(newFieldPanel("enable docs", useDocs));
		fieldsPanel.add(newFieldPanel("show remote branches", showRemoteBranches));
		fieldsPanel.add(newFieldPanel("show readme", showReadme));
		fieldsPanel.add(newFieldPanel("is frozen", isFrozen));

		usersPalette = new JPalette<String>();
		JPanel accessPanel = new JPanel(new BorderLayout(5, 5));
		accessPanel.add(newFieldPanel("access restriction", accessRestriction), BorderLayout.NORTH);
		accessPanel.add(newFieldPanel("permitted users", usersPalette), BorderLayout.CENTER);

		setsPalette = new JPalette<String>();
		JPanel federationPanel = new JPanel(new BorderLayout(5, 5));
		federationPanel.add(newFieldPanel("federation strategy", federationStrategy),
				BorderLayout.NORTH);
		federationPanel.add(newFieldPanel("federation sets", setsPalette), BorderLayout.CENTER);

		JPanel panel = new JPanel(new BorderLayout(5, 5));
		panel.add(fieldsPanel, BorderLayout.NORTH);
		panel.add(accessPanel, BorderLayout.CENTER);
		panel.add(federationPanel, BorderLayout.SOUTH);

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
		// TODO validate input and populate model
		return true;
	}

	private void showValidationError(String message) {
		JOptionPane.showMessageDialog(EditRepositoryDialog.this, message, "Validation Error",
				JOptionPane.ERROR_MESSAGE);
	}

	public void setUsers(List<String> all, List<String> selected) {
		usersPalette.setObjects(all, selected);
	}

	public void setFederationSets(List<String> all, List<String> selected) {
		setsPalette.setObjects(all, selected);
	}

	public RepositoryModel getRepository() {
		if (canceled) {
			return null;
		}
		return repository;
	}
}
