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
import java.awt.event.KeyEvent;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
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
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ScrollPaneConstants;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.Constants.FederationStrategy;
import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.StringUtils;

/**
 * Dialog to create/edit a repository.
 * 
 * @author James Moger
 */
public class EditRepositoryDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	private final String repositoryName;

	private final RepositoryModel repository;

	private boolean isCreate;

	private boolean canceled = true;

	private JTextField nameField;

	private JTextField descriptionField;

	private JCheckBox useTickets;

	private JCheckBox useDocs;

	private JCheckBox showRemoteBranches;

	private JCheckBox showReadme;

	private JCheckBox skipSizeCalculation;

	private JCheckBox skipSummaryMetrics;

	private JCheckBox isFrozen;

	private JTextField mailingListsField;

	private JComboBox accessRestriction;

	private JComboBox federationStrategy;

	private JComboBox ownerField;

	private JComboBox headRefField;

	private JPalette<String> usersPalette;

	private JPalette<String> setsPalette;

	private JPalette<String> teamsPalette;
	
	private JPalette<String> indexedBranchesPalette;

	private JPalette<String> preReceivePalette;

	private JLabel preReceiveInherited;

	private JPalette<String> postReceivePalette;

	private JLabel postReceiveInherited;

	private Set<String> repositoryNames;
	
	private JPanel customFieldsPanel;

	public EditRepositoryDialog(int protocolVersion) {
		this(protocolVersion, new RepositoryModel());
		this.isCreate = true;
		setTitle(Translation.get("gb.newRepository"));
	}

	public EditRepositoryDialog(int protocolVersion, RepositoryModel aRepository) {
		super();
		this.repositoryName = aRepository.name;
		this.repository = new RepositoryModel();
		this.repositoryNames = new HashSet<String>();
		this.isCreate = false;
		initialize(protocolVersion, aRepository);
		setModal(true);
		setResizable(false);
		setTitle(Translation.get("gb.edit") + ": " + aRepository.name);
		setIconImage(new ImageIcon(getClass()
				.getResource("/gitblt-favicon.png")).getImage());
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

	private void initialize(int protocolVersion, RepositoryModel anRepository) {
		nameField = new JTextField(anRepository.name == null ? ""
				: anRepository.name, 35);
		descriptionField = new JTextField(anRepository.description == null ? ""
				: anRepository.description, 35);

		JTextField originField = new JTextField(
				anRepository.origin == null ? "" : anRepository.origin, 40);
		originField.setEditable(false);

		if (ArrayUtils.isEmpty(anRepository.availableRefs)) {
			headRefField = new JComboBox();
			headRefField.setEnabled(false);			
		} else {
			headRefField = new JComboBox(
					anRepository.availableRefs.toArray());
			headRefField.setSelectedItem(anRepository.HEAD);
		}

		ownerField = new JComboBox();

		useTickets = new JCheckBox(Translation.get("gb.useTicketsDescription"),
				anRepository.useTickets);
		useDocs = new JCheckBox(Translation.get("gb.useDocsDescription"),
				anRepository.useDocs);
		showRemoteBranches = new JCheckBox(
				Translation.get("gb.showRemoteBranchesDescription"),
				anRepository.showRemoteBranches);
		showReadme = new JCheckBox(Translation.get("gb.showReadmeDescription"),
				anRepository.showReadme);
		skipSizeCalculation = new JCheckBox(
				Translation.get("gb.skipSizeCalculationDescription"),
				anRepository.skipSizeCalculation);
		skipSummaryMetrics = new JCheckBox(
				Translation.get("gb.skipSummaryMetricsDescription"),
				anRepository.skipSummaryMetrics);
		isFrozen = new JCheckBox(Translation.get("gb.isFrozenDescription"),
				anRepository.isFrozen);

		mailingListsField = new JTextField(
				ArrayUtils.isEmpty(anRepository.mailingLists) ? ""
						: StringUtils.flattenStrings(anRepository.mailingLists,
								" "), 50);

		accessRestriction = new JComboBox(AccessRestrictionType.values());
		accessRestriction.setRenderer(new AccessRestrictionRenderer());
		accessRestriction.setSelectedItem(anRepository.accessRestriction);

		// federation strategies - remove ORIGIN choice if this repository has
		// no origin.
		List<FederationStrategy> federationStrategies = new ArrayList<FederationStrategy>(
				Arrays.asList(FederationStrategy.values()));
		if (StringUtils.isEmpty(anRepository.origin)) {
			federationStrategies.remove(FederationStrategy.FEDERATE_ORIGIN);
		}
		federationStrategy = new JComboBox(federationStrategies.toArray());
		federationStrategy.setRenderer(new FederationStrategyRenderer());
		federationStrategy.setSelectedItem(anRepository.federationStrategy);

		JPanel fieldsPanel = new JPanel(new GridLayout(0, 1));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.name"), nameField));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.description"),
				descriptionField));
		fieldsPanel
				.add(newFieldPanel(Translation.get("gb.origin"), originField));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.headRef"), headRefField));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.owner"), ownerField));

		fieldsPanel.add(newFieldPanel(Translation.get("gb.enableTickets"),
				useTickets));
		fieldsPanel
				.add(newFieldPanel(Translation.get("gb.enableDocs"), useDocs));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.showRemoteBranches"),
				showRemoteBranches));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.showReadme"),
				showReadme));
		fieldsPanel
				.add(newFieldPanel(Translation.get("gb.skipSizeCalculation"),
						skipSizeCalculation));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.skipSummaryMetrics"),
				skipSummaryMetrics));
		fieldsPanel
				.add(newFieldPanel(Translation.get("gb.isFrozen"), isFrozen));
		fieldsPanel.add(newFieldPanel(Translation.get("gb.mailingLists"),
				mailingListsField));

		usersPalette = new JPalette<String>();
		JPanel accessPanel = new JPanel(new BorderLayout(5, 5));
		accessPanel.add(
				newFieldPanel(Translation.get("gb.accessRestriction"),
						accessRestriction), BorderLayout.NORTH);
		accessPanel.add(
				newFieldPanel(Translation.get("gb.permittedUsers"),
						usersPalette), BorderLayout.CENTER);

		teamsPalette = new JPalette<String>();
		JPanel teamsPanel = new JPanel(new BorderLayout(5, 5));
		teamsPanel.add(
				newFieldPanel(Translation.get("gb.permittedTeams"),
						teamsPalette), BorderLayout.CENTER);

		setsPalette = new JPalette<String>();
		JPanel federationPanel = new JPanel(new BorderLayout(5, 5));
		federationPanel.add(
				newFieldPanel(Translation.get("gb.federationStrategy"),
						federationStrategy), BorderLayout.NORTH);
		federationPanel
				.add(newFieldPanel(Translation.get("gb.federationSets"),
						setsPalette), BorderLayout.CENTER);

		indexedBranchesPalette = new JPalette<String>();
		JPanel indexedBranchesPanel = new JPanel(new BorderLayout(5, 5));
		indexedBranchesPanel
				.add(newFieldPanel(Translation.get("gb.indexedBranches"),
						indexedBranchesPalette), BorderLayout.CENTER);

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
		
		customFieldsPanel = new JPanel();
		customFieldsPanel.setLayout(new BoxLayout(customFieldsPanel, BoxLayout.Y_AXIS));
		JScrollPane customFieldsScrollPane = new JScrollPane(customFieldsPanel);
		customFieldsScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		customFieldsScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

		JTabbedPane panel = new JTabbedPane(JTabbedPane.TOP);
		panel.addTab(Translation.get("gb.general"), fieldsPanel);
		panel.addTab(Translation.get("gb.accessRestriction"), accessPanel);
		if (protocolVersion >= 2) {
			panel.addTab(Translation.get("gb.teams"), teamsPanel);
		}
		panel.addTab(Translation.get("gb.federation"), federationPanel);
		if (protocolVersion >= 3) {
			panel.addTab(Translation.get("gb.indexedBranches"), indexedBranchesPanel);
		}
		panel.addTab(Translation.get("gb.preReceiveScripts"), preReceivePanel);
		panel.addTab(Translation.get("gb.postReceiveScripts"), postReceivePanel);
		
		panel.addTab(Translation.get("gb.customFields"), customFieldsScrollPane);
		

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
		nameField.requestFocus();
	}
	
	private JPanel newFieldPanel(String label, JComponent comp) {
		return newFieldPanel(label, 150, comp);
	}

	private JPanel newFieldPanel(String label, int labelSize, JComponent comp) {
		JLabel fieldLabel = new JLabel(label);
		fieldLabel.setFont(fieldLabel.getFont().deriveFont(Font.BOLD));
		fieldLabel.setPreferredSize(new Dimension(labelSize, 20));
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		panel.add(fieldLabel);
		panel.add(comp);
		return panel;
	}

	private boolean validateFields() {
		String rname = nameField.getText();
		if (StringUtils.isEmpty(rname)) {
			error("Please enter a repository name!");
			return false;
		}

		// automatically convert backslashes to forward slashes
		rname = rname.replace('\\', '/');
		// Automatically replace // with /
		rname = rname.replace("//", "/");

		// prohibit folder paths
		if (rname.startsWith("/")) {
			error("Leading root folder references (/) are prohibited.");
			return false;
		}
		if (rname.startsWith("../")) {
			error("Relative folder references (../) are prohibited.");
			return false;
		}
		if (rname.contains("/../")) {
			error("Relative folder references (../) are prohibited.");
			return false;
		}

		// confirm valid characters in repository name
		Character c = StringUtils.findInvalidCharacter(rname);
		if (c != null) {
			error(MessageFormat.format(
					"Illegal character ''{0}'' in repository name!", c));
			return false;
		}

		// verify repository name uniqueness on create
		if (isCreate) {
			// force repo names to lowercase
			// this means that repository name checking for rpc creation
			// is case-insensitive, regardless of the Gitblit server's
			// filesystem
			if (repositoryNames.contains(rname.toLowerCase())) {
				error(MessageFormat
						.format("Can not create repository ''{0}'' because it already exists.",
								rname));
				return false;
			}
		} else {
			// check rename collision
			if (!repositoryName.equalsIgnoreCase(rname)) {
				if (repositoryNames.contains(rname.toLowerCase())) {
					error(MessageFormat
							.format("Failed to rename ''{0}'' because ''{1}'' already exists.",
									repositoryName, rname));
					return false;
				}
			}
		}

		if (accessRestriction.getSelectedItem() == null) {
			error("Please select access restriction!");
			return false;
		}

		if (federationStrategy.getSelectedItem() == null) {
			error("Please select federation strategy!");
			return false;
		}

		repository.name = rname;
		repository.description = descriptionField.getText();
		repository.owner = ownerField.getSelectedItem() == null ? null
				: ownerField.getSelectedItem().toString();
		repository.HEAD = headRefField.getSelectedItem() == null ? null
				: headRefField.getSelectedItem().toString();
		repository.useTickets = useTickets.isSelected();
		repository.useDocs = useDocs.isSelected();
		repository.showRemoteBranches = showRemoteBranches.isSelected();
		repository.showReadme = showReadme.isSelected();
		repository.skipSizeCalculation = skipSizeCalculation.isSelected();
		repository.skipSummaryMetrics = skipSummaryMetrics.isSelected();
		repository.isFrozen = isFrozen.isSelected();

		String ml = mailingListsField.getText();
		if (!StringUtils.isEmpty(ml)) {
			Set<String> list = new HashSet<String>();
			for (String address : ml.split("(,|\\s)")) {
				if (StringUtils.isEmpty(address)) {
					continue;
				}
				list.add(address.toLowerCase());
			}
			repository.mailingLists = new ArrayList<String>(list);
		}

		repository.accessRestriction = (AccessRestrictionType) accessRestriction
				.getSelectedItem();
		repository.federationStrategy = (FederationStrategy) federationStrategy
				.getSelectedItem();

		if (repository.federationStrategy.exceeds(FederationStrategy.EXCLUDE)) {
			repository.federationSets = setsPalette.getSelections();
		}
		
		repository.indexedBranches = indexedBranchesPalette.getSelections();
		repository.preReceiveScripts = preReceivePalette.getSelections();
		repository.postReceiveScripts = postReceivePalette.getSelections();
		
		// Custom Fields
		repository.customFields = new HashMap<String, String>();
		
		for (Component aCustomFieldPanel : customFieldsPanel.getComponents()) {
			JTextField textField = (JTextField) ((JPanel)aCustomFieldPanel).getComponent(1);
			repository.customFields.put(textField.getName(), textField.getText());
		}
		
		return true;
	}

	private void error(String message) {
		JOptionPane.showMessageDialog(EditRepositoryDialog.this, message,
				Translation.get("gb.error"), JOptionPane.ERROR_MESSAGE);
	}

	public void setUsers(String owner, List<String> all, List<String> selected) {
		ownerField.setModel(new DefaultComboBoxModel(all.toArray()));
		if (!StringUtils.isEmpty(owner)) {
			ownerField.setSelectedItem(owner);
		}
		usersPalette.setObjects(all, selected);
	}

	public void setTeams(List<String> all, List<String> selected) {
		teamsPalette.setObjects(all, selected);
	}

	public void setRepositories(List<RepositoryModel> repositories) {
		repositoryNames.clear();
		for (RepositoryModel repository : repositories) {
			// force repo names to lowercase
			// this means that repository name checking for rpc creation
			// is case-insensitive, regardless of the Gitblit server's
			// filesystem
			repositoryNames.add(repository.name.toLowerCase());
		}
	}

	public void setFederationSets(List<String> all, List<String> selected) {
		setsPalette.setObjects(all, selected);
	}
	
	public void setIndexedBranches(List<String> all, List<String> selected) {
		indexedBranchesPalette.setObjects(all, selected);
	}

	public void setPreReceiveScripts(List<String> all, List<String> inherited,
			List<String> selected) {
		preReceivePalette.setObjects(all, selected);
		showInherited(inherited, preReceiveInherited);
	}

	public void setPostReceiveScripts(List<String> all, List<String> inherited,
			List<String> selected) {
		postReceivePalette.setObjects(all, selected);
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

	public RepositoryModel getRepository() {
		if (canceled) {
			return null;
		}
		return repository;
	}

	public List<String> getPermittedUsers() {
		return usersPalette.getSelections();
	}

	public List<String> getPermittedTeams() {
		return teamsPalette.getSelections();
	}
	
	public void setCustomFields(RepositoryModel repository, List<String> customFields) {
		customFieldsPanel.removeAll();
		
		for (String customFieldDef : customFields) {
			String[] customFieldProperty = customFieldDef.split("=");
			String fieldName = customFieldProperty[0];
			String fieldLabel = customFieldProperty[1];
			
			JTextField textField = new JTextField(repository.customFields.get(fieldName), 50);
			textField.setName(fieldName);
			
			customFieldsPanel.add(newFieldPanel(fieldLabel, 250, textField));
		}
		
		if (customFields.size() < 14) {
			customFieldsPanel.add(Box.createVerticalGlue());
			customFieldsPanel.add(Box.createRigidArea(new Dimension(300, 300 - (customFields.size() * 22))));
		}
	}

	/**
	 * ListCellRenderer to display descriptive text about the access
	 * restriction.
	 * 
	 */
	private class AccessRestrictionRenderer extends JLabel implements
			ListCellRenderer {

		private static final long serialVersionUID = 1L;

		@Override
		public Component getListCellRendererComponent(JList list, Object value,
				int index, boolean isSelected, boolean cellHasFocus) {
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
	private class FederationStrategyRenderer extends JLabel implements
			ListCellRenderer {

		private static final long serialVersionUID = 1L;

		@Override
		public Component getListCellRendererComponent(JList list, Object value,
				int index, boolean isSelected, boolean cellHasFocus) {
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
