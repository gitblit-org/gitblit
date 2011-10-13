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
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.FederationStrategy;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;

/**
 * Dialog to create/edit a repository.
 * 
 * @author James Moger
 */
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
		setTitle(Translation.get("gb.newRepository"));
	}

	public EditRepositoryDialog(RepositoryModel aRepository, List<UserModel> allUsers) {
		super();
		this.repository = new RepositoryModel();
		initialize(aRepository, allUsers);
		setModal(true);
		setTitle(Translation.get("gb.edit") + ": " + aRepository.name);
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

		useTickets = new JCheckBox(Translation.get("gb.useTicketsDescription"),
				anRepository.useTickets);
		useDocs = new JCheckBox(Translation.get("gb.useDocsDescription"), anRepository.useDocs);
		showRemoteBranches = new JCheckBox(Translation.get("gb.showRemoteBranchesDescription"),
				anRepository.showRemoteBranches);
		showReadme = new JCheckBox(Translation.get("gb.showReadmeDescription"),
				anRepository.showReadme);
		isFrozen = new JCheckBox(Translation.get("gb.isFrozenDescription"), anRepository.isFrozen);

		accessRestriction = new JComboBox(AccessRestrictionType.values());
		accessRestriction.setRenderer(new AccessRestrictionRenderer());
		accessRestriction.setSelectedItem(anRepository.accessRestriction);

		federationStrategy = new JComboBox(FederationStrategy.values());
		federationStrategy.setRenderer(new FederationStrategyRenderer());
		federationStrategy.setSelectedItem(anRepository.federationStrategy);

		JPanel fieldsPanel = new JPanel(new GridLayout(0, 1));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.name"), nameField));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.description"), descriptionField));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.owner"), owner));

		fieldsPanel.add(newFieldPanel(Translation.get("gb.enableTickets"), useTickets));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.enableDocs"), useDocs));
		fieldsPanel
				.add(newFieldPanel(Translation.get("gb.showRemoteBranches"), showRemoteBranches));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.showReadme"), showReadme));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.isFrozen"), isFrozen));

		usersPalette = new JPalette<String>();
		JPanel accessPanel = new JPanel(new BorderLayout(5, 5));
		accessPanel.add(newFieldPanel(Translation.get("gb.accessRestriction"), accessRestriction),
				BorderLayout.NORTH);
		accessPanel.add(newFieldPanel(Translation.get("gb.permittedUsers"), usersPalette),
				BorderLayout.CENTER);

		setsPalette = new JPalette<String>();
		JPanel federationPanel = new JPanel(new BorderLayout(5, 5));
		federationPanel.add(
				newFieldPanel(Translation.get("gb.federationStrategy"), federationStrategy),
				BorderLayout.NORTH);
		federationPanel.add(newFieldPanel(Translation.get("gb.federationSets"), setsPalette),
				BorderLayout.CENTER);

		JPanel panel = new JPanel(new BorderLayout(5, 5));
		panel.add(fieldsPanel, BorderLayout.NORTH);
		panel.add(accessPanel, BorderLayout.CENTER);
		panel.add(federationPanel, BorderLayout.SOUTH);

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
		JOptionPane.showMessageDialog(EditRepositoryDialog.this, message,
				Translation.get("gb.error"), JOptionPane.ERROR_MESSAGE);
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

	/**
	 * ListCellRenderer to display descriptive text about the access
	 * restriction.
	 * 
	 */
	private class AccessRestrictionRenderer extends JLabel implements ListCellRenderer {

		private static final long serialVersionUID = 1L;

		@Override
		public Component getListCellRendererComponent(JList list, Object value, int index,
				boolean isSelected, boolean cellHasFocus) {
			if (value instanceof AccessRestrictionType) {
				AccessRestrictionType restriction = (AccessRestrictionType) value;
				switch (restriction) {
				case NONE:
					setText(Translation.get("gb.notRestricted"));
					break;
				case PUSH:
					setText(Translation.get("gb.pushRestricted"));
					break;
				case CLONE:
					setText(Translation.get("gb.cloneRestricted"));
					break;
				case VIEW:
					setText(Translation.get("gb.viewRestricted"));
					break;
				}
			} else {
				setText(value.toString());
			}
			return this;
		}
	}

	/**
	 * ListCellRenderer to display descriptive text about the federation
	 * strategy.
	 */
	private class FederationStrategyRenderer extends JLabel implements ListCellRenderer {

		private static final long serialVersionUID = 1L;

		@Override
		public Component getListCellRendererComponent(JList list, Object value, int index,
				boolean isSelected, boolean cellHasFocus) {
			if (value instanceof FederationStrategy) {
				FederationStrategy strategy = (FederationStrategy) value;
				switch (strategy) {
				case EXCLUDE:
					setText(Translation.get("gb.excludeFromFederation"));
					break;
				case FEDERATE_THIS:
					setText(Translation.get("gb.federateThis"));
					break;
				case FEDERATE_ORIGIN:
					setText(Translation.get("gb.federateOrigin"));
					break;
				}
			} else {
				setText(value.toString());
			}
			return this;
		}
	}
}
