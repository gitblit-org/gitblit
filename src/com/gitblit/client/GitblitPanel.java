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
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;

import com.gitblit.Constants.RpcRequest;
import com.gitblit.client.ClosableTabComponent.CloseTabListener;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.SettingModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;

/**
 * GitblitPanel performs the login, all business logic, and contains all widgets
 * to represent the state of a repository for the given account credentials.
 * 
 * @author James Moger
 * 
 */
public class GitblitPanel extends JPanel implements CloseTabListener {

	private static final long serialVersionUID = 1L;

	private final int margin = 5;

	private final Insets insets = new Insets(margin, margin, margin, margin);

	private GitblitClient gitblit;

	private JTabbedPane tabs;

	private JTable repositoriesTable;

	private RepositoriesTableModel repositoriesModel;

	private JTable usersTable;

	private UsersTableModel usersModel;

	private JTable settingsTable;

	private SettingsTableModel settingsModel;

	private JButton createRepository;

	private JButton delRepository;

	private NameRenderer nameRenderer;

	private IndicatorsRenderer typeRenderer;

	private DefaultTableCellRenderer ownerRenderer;

	private DefaultTableCellRenderer sizeRenderer;

	private TableRowSorter<RepositoriesTableModel> defaultRepositoriesSorter;

	private TableRowSorter<UsersTableModel> defaultUsersSorter;

	private TableRowSorter<SettingsTableModel> defaultSettingsSorter;

	private JButton editRepository;

	private HeaderPanel repositoriesHeader;

	private HeaderPanel usersHeader;

	private HeaderPanel settingsHeader;

	private StatusPanel statusPanel;

	public GitblitPanel(GitblitRegistration reg) {
		this(reg.url, reg.account, reg.password);
	}

	public GitblitPanel(String url, String account, char[] password) {
		this.gitblit = new GitblitClient(url, account, password);

		tabs = new JTabbedPane(JTabbedPane.BOTTOM);
		tabs.addTab(Translation.get("gb.repositories"), createRepositoriesPanel());
		tabs.addTab(Translation.get("gb.users"), createUsersPanel());
		tabs.addTab(Translation.get("gb.settings"), createSettingsPanel());
		tabs.addTab(Translation.get("gb.status"), createStatusPanel());

		setLayout(new BorderLayout());
		add(tabs, BorderLayout.CENTER);
	}

	private JPanel createRepositoriesPanel() {
		final JButton browseRepository = new JButton(Translation.get("gb.browse"));
		browseRepository.setEnabled(false);
		browseRepository.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				RepositoryModel model = getSelectedRepositories().get(0);
				String u = MessageFormat.format("{0}/summary/{1}", gitblit.url,
						StringUtils.encodeURL(model.name));
				try {
					Desktop.getDesktop().browse(new URI(u));
				} catch (Exception x) {
					x.printStackTrace();
				}
			}
		});

		JButton refreshRepositories = new JButton(Translation.get("gb.refresh"));
		refreshRepositories.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				refreshRepositories();
			}
		});

		createRepository = new JButton(Translation.get("gb.create"));
		createRepository.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				createRepository();
			}
		});

		editRepository = new JButton(Translation.get("gb.edit"));
		editRepository.setEnabled(false);
		editRepository.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				editRepository(getSelectedRepositories().get(0));
			}
		});

		delRepository = new JButton(Translation.get("gb.delete"));
		delRepository.setEnabled(false);
		delRepository.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				deleteRepositories(getSelectedRepositories());
			}
		});

		nameRenderer = new NameRenderer();
		typeRenderer = new IndicatorsRenderer();

		sizeRenderer = new DefaultTableCellRenderer();
		sizeRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
		sizeRenderer.setForeground(new Color(0, 0x80, 0));

		ownerRenderer = new DefaultTableCellRenderer();
		ownerRenderer.setForeground(Color.gray);
		ownerRenderer.setHorizontalAlignment(SwingConstants.CENTER);

		repositoriesModel = new RepositoriesTableModel();
		defaultRepositoriesSorter = new TableRowSorter<RepositoriesTableModel>(repositoriesModel);
		repositoriesTable = Utils.newTable(repositoriesModel);
		repositoriesTable.setRowHeight(nameRenderer.getFont().getSize() + 8);
		repositoriesTable.setRowSorter(defaultRepositoriesSorter);
		repositoriesTable.getRowSorter().toggleSortOrder(
				RepositoriesTableModel.Columns.Name.ordinal());

		setRepositoryRenderer(RepositoriesTableModel.Columns.Name, nameRenderer, -1);
		setRepositoryRenderer(RepositoriesTableModel.Columns.Indicators, typeRenderer, 100);
		setRepositoryRenderer(RepositoriesTableModel.Columns.Owner, ownerRenderer, -1);
		setRepositoryRenderer(RepositoriesTableModel.Columns.Size, sizeRenderer, 60);

		repositoriesTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				if (e.getValueIsAdjusting()) {
					return;
				}
				boolean singleSelection = repositoriesTable.getSelectedRowCount() == 1;
				boolean selected = repositoriesTable.getSelectedRow() > -1;
				browseRepository.setEnabled(singleSelection);
				delRepository.setEnabled(selected);
				if (selected) {
					int viewRow = repositoriesTable.getSelectedRow();
					int modelRow = repositoriesTable.convertRowIndexToModel(viewRow);
					RepositoryModel model = ((RepositoriesTableModel) repositoriesTable.getModel()).list
							.get(modelRow);
					editRepository.setEnabled(singleSelection
							&& (gitblit.allowManagement() || gitblit.isOwner(model)));
				} else {
					editRepository.setEnabled(false);
				}
			}
		});

		repositoriesTable.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && gitblit.allowManagement()) {
					editRepository(getSelectedRepositories().get(0));
				}
			}
		});

		final JTextField repositoryFilter = new JTextField();
		repositoryFilter.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				filterRepositories(repositoryFilter.getText());
			}
		});
		repositoryFilter.addKeyListener(new KeyAdapter() {
			public void keyReleased(KeyEvent e) {
				filterRepositories(repositoryFilter.getText());
			}
		});

		JPanel repositoryFilterPanel = new JPanel(new BorderLayout(margin, margin));
		repositoryFilterPanel.add(new JLabel(Translation.get("gb.filter")), BorderLayout.WEST);
		repositoryFilterPanel.add(repositoryFilter, BorderLayout.CENTER);

		JPanel repositoryTablePanel = new JPanel(new BorderLayout(margin, margin));
		repositoryTablePanel.add(repositoryFilterPanel, BorderLayout.NORTH);
		repositoryTablePanel.add(new JScrollPane(repositoriesTable), BorderLayout.CENTER);

		JPanel repositoryControls = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
		repositoryControls.add(refreshRepositories);
		repositoryControls.add(browseRepository);
		repositoryControls.add(createRepository);
		repositoryControls.add(editRepository);
		repositoryControls.add(delRepository);

		JPanel repositoriesPanel = new JPanel(new BorderLayout(margin, margin)) {

			private static final long serialVersionUID = 1L;

			public Insets getInsets() {
				return insets;
			}
		};
		repositoriesHeader = new HeaderPanel(Translation.get("gb.repositories"),
				"gitweb-favicon.png");
		repositoriesPanel.add(repositoriesHeader, BorderLayout.NORTH);
		repositoriesPanel.add(repositoryTablePanel, BorderLayout.CENTER);
		repositoriesPanel.add(repositoryControls, BorderLayout.SOUTH);

		return repositoriesPanel;
	}

	private void setRepositoryRenderer(RepositoriesTableModel.Columns col,
			TableCellRenderer renderer, int maxWidth) {
		String name = repositoriesTable.getColumnName(col.ordinal());
		repositoriesTable.getColumn(name).setCellRenderer(renderer);
		if (maxWidth > 0) {
			repositoriesTable.getColumn(name).setMinWidth(maxWidth);
			repositoriesTable.getColumn(name).setMaxWidth(maxWidth);
		}
	}

	private JPanel createUsersPanel() {
		JButton refreshUsers = new JButton(Translation.get("gb.refresh"));
		refreshUsers.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				refreshUsers();
			}
		});

		JButton createUser = new JButton(Translation.get("gb.create"));
		createUser.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				createUser();
			}
		});

		final JButton editUser = new JButton(Translation.get("gb.edit"));
		editUser.setEnabled(false);
		editUser.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				editUser(getSelectedUsers().get(0));
			}
		});

		final JButton delUser = new JButton(Translation.get("gb.delete"));
		delUser.setEnabled(false);
		delUser.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				deleteUsers(getSelectedUsers());
			}
		});

		usersModel = new UsersTableModel();
		defaultUsersSorter = new TableRowSorter<UsersTableModel>(usersModel);
		usersTable = Utils.newTable(usersModel);
		String name = usersTable.getColumnName(UsersTableModel.Columns.Name.ordinal());
		usersTable.setRowHeight(nameRenderer.getFont().getSize() + 8);
		usersTable.getColumn(name).setCellRenderer(nameRenderer);
		usersTable.setRowSorter(defaultUsersSorter);
		usersTable.getRowSorter().toggleSortOrder(UsersTableModel.Columns.Name.ordinal());
		usersTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

			@Override
			public void valueChanged(ListSelectionEvent e) {
				if (e.getValueIsAdjusting()) {
					return;
				}
				boolean selected = usersTable.getSelectedRow() > -1;
				boolean singleSelection = usersTable.getSelectedRows().length == 1;
				editUser.setEnabled(singleSelection && selected);
				delUser.setEnabled(selected);
			}
		});

		usersTable.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					editUser(getSelectedUsers().get(0));
				}
			}
		});

		final JTextField userFilter = new JTextField();
		userFilter.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				filterUsers(userFilter.getText());
			}
		});
		userFilter.addKeyListener(new KeyAdapter() {
			public void keyReleased(KeyEvent e) {
				filterUsers(userFilter.getText());
			}
		});

		JPanel userFilterPanel = new JPanel(new BorderLayout(margin, margin));
		userFilterPanel.add(new JLabel(Translation.get("gb.filter")), BorderLayout.WEST);
		userFilterPanel.add(userFilter, BorderLayout.CENTER);

		JPanel userTablePanel = new JPanel(new BorderLayout(margin, margin));
		userTablePanel.add(userFilterPanel, BorderLayout.NORTH);
		userTablePanel.add(new JScrollPane(usersTable), BorderLayout.CENTER);

		JPanel userControls = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
		userControls.add(refreshUsers);
		userControls.add(createUser);
		userControls.add(editUser);
		userControls.add(delUser);

		JPanel usersPanel = new JPanel(new BorderLayout(margin, margin)) {

			private static final long serialVersionUID = 1L;

			public Insets getInsets() {
				return insets;
			}
		};
		usersHeader = new HeaderPanel(Translation.get("gb.users"), "user_16x16.png");
		usersPanel.add(usersHeader, BorderLayout.NORTH);
		usersPanel.add(userTablePanel, BorderLayout.CENTER);
		usersPanel.add(userControls, BorderLayout.SOUTH);

		return usersPanel;
	}

	private JPanel createSettingsPanel() {
		JButton refreshSettings = new JButton(Translation.get("gb.refresh"));
		refreshSettings.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				refreshSettings();
			}
		});

		final JButton editSetting = new JButton(Translation.get("gb.edit"));
		editSetting.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int viewRow = settingsTable.getSelectedRow();
				int modelRow = settingsTable.convertRowIndexToModel(viewRow);
				String key = settingsModel.keys.get(modelRow);
				SettingModel setting = settingsModel.settings.get(key);
				editSetting(setting);
			}
		});

		final SettingPanel settingPanel = new SettingPanel();
		settingsModel = new SettingsTableModel();
		defaultSettingsSorter = new TableRowSorter<SettingsTableModel>(settingsModel);
		settingsTable = Utils.newTable(settingsModel);
		settingsTable.setDefaultRenderer(SettingModel.class, new SettingCellRenderer());
		String name = settingsTable.getColumnName(UsersTableModel.Columns.Name.ordinal());
		settingsTable.setRowHeight(nameRenderer.getFont().getSize() + 8);
		settingsTable.getColumn(name).setCellRenderer(nameRenderer);
		settingsTable.setRowSorter(defaultSettingsSorter);
		settingsTable.getRowSorter().toggleSortOrder(SettingsTableModel.Columns.Name.ordinal());
		settingsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

			@Override
			public void valueChanged(ListSelectionEvent e) {
				if (e.getValueIsAdjusting()) {
					return;
				}
				boolean singleSelection = settingsTable.getSelectedRows().length == 1;
				editSetting.setEnabled(singleSelection);
				if (singleSelection) {
					int viewRow = settingsTable.getSelectedRow();
					int modelRow = settingsTable.convertRowIndexToModel(viewRow);
					SettingModel setting = settingsModel.get(modelRow);
					settingPanel.setSetting(setting);
				} else {
					settingPanel.clear();
				}
			}
		});

		final JTextField settingFilter = new JTextField();
		settingFilter.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				filterSettings(settingFilter.getText());
			}
		});
		settingFilter.addKeyListener(new KeyAdapter() {
			public void keyReleased(KeyEvent e) {
				filterSettings(settingFilter.getText());
			}
		});

		JPanel settingFilterPanel = new JPanel(new BorderLayout(margin, margin));
		settingFilterPanel.add(new JLabel(Translation.get("gb.filter")), BorderLayout.WEST);
		settingFilterPanel.add(settingFilter, BorderLayout.CENTER);

		JPanel settingsTablePanel = new JPanel(new BorderLayout(margin, margin));
		settingsTablePanel.add(settingFilterPanel, BorderLayout.NORTH);
		settingsTablePanel.add(new JScrollPane(settingsTable), BorderLayout.CENTER);
		settingsTablePanel.add(settingPanel, BorderLayout.SOUTH);

		JPanel settingsControls = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
		settingsControls.add(refreshSettings);
		settingsControls.add(editSetting);

		JPanel settingsPanel = new JPanel(new BorderLayout(margin, margin)) {

			private static final long serialVersionUID = 1L;

			public Insets getInsets() {
				return insets;
			}
		};
		settingsHeader = new HeaderPanel(Translation.get("gb.settings"), "settings_16x16.png");
		settingsPanel.add(settingsHeader, BorderLayout.NORTH);
		settingsPanel.add(settingsTablePanel, BorderLayout.CENTER);
		settingsPanel.add(settingsControls, BorderLayout.SOUTH);

		return settingsPanel;
	}

	private JPanel createStatusPanel() {
		JButton refreshStatus = new JButton(Translation.get("gb.refresh"));
		refreshStatus.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				refreshStatus();
			}
		});

		JPanel controls = new JPanel();
		controls.add(refreshStatus);

		JPanel panel = new JPanel(new BorderLayout());
		statusPanel = new StatusPanel();
		panel.add(statusPanel, BorderLayout.CENTER);
		panel.add(controls, BorderLayout.SOUTH);
		return panel;
	}

	public void login() throws IOException {
		gitblit.login();

		updateRepositoriesTable();
		Utils.packColumns(repositoriesTable, 5);

		if (gitblit.allowManagement()) {
			updateUsersTable();
		} else {
			// user does not have administrator privileges
			// hide admin repository buttons
			createRepository.setVisible(false);
			editRepository.setVisible(false);
			delRepository.setVisible(false);

			while (tabs.getTabCount() > 1) {
				// remove all management/administration tabs
				tabs.removeTabAt(1);
			}
		}

		if (gitblit.allowAdministration()) {
			updateSettingsTable();
			updateStatusPanel();
			Utils.packColumns(settingsTable, 5);
		} else {
			// remove the settings tab
			String settingsTitle = Translation.get("gb.settings");
			for (int i = 0; i < tabs.getTabCount(); i++) {
				if (tabs.getTitleAt(i).equals(settingsTitle)) {
					tabs.removeTabAt(i);
					break;
				}
			}
		}
	}

	private void updateRepositoriesTable() {
		repositoriesModel.list.clear();
		repositoriesModel.list.addAll(gitblit.getRepositories());
		repositoriesModel.fireTableDataChanged();
		repositoriesHeader.setText(Translation.get("gb.repositories") + " ("
				+ gitblit.getRepositories().size() + ")");
	}

	private void updateUsersTable() {
		usersModel.list.clear();
		usersModel.list.addAll(gitblit.getUsers());
		usersModel.fireTableDataChanged();
		usersHeader.setText(Translation.get("gb.users") + " (" + gitblit.getUsers().size() + ")");
	}

	private void updateSettingsTable() {
		settingsModel.setSettings(gitblit.getSettings());
		settingsModel.fireTableDataChanged();
		settingsHeader.setText(Translation.get("gb.settings"));
	}

	private void updateStatusPanel() {
		statusPanel.setStatus(gitblit.getStatus());
	}

	private void filterRepositories(final String fragment) {
		if (StringUtils.isEmpty(fragment)) {
			repositoriesTable.setRowSorter(defaultRepositoriesSorter);
			return;
		}
		RowFilter<RepositoriesTableModel, Object> containsFilter = new RowFilter<RepositoriesTableModel, Object>() {
			public boolean include(Entry<? extends RepositoriesTableModel, ? extends Object> entry) {
				for (int i = entry.getValueCount() - 1; i >= 0; i--) {
					if (entry.getStringValue(i).toLowerCase().contains(fragment.toLowerCase())) {
						return true;
					}
				}
				return false;
			}
		};
		TableRowSorter<RepositoriesTableModel> sorter = new TableRowSorter<RepositoriesTableModel>(
				repositoriesModel);
		sorter.setRowFilter(containsFilter);
		repositoriesTable.setRowSorter(sorter);
	}

	private void filterUsers(final String fragment) {
		if (StringUtils.isEmpty(fragment)) {
			usersTable.setRowSorter(defaultUsersSorter);
			return;
		}
		RowFilter<UsersTableModel, Object> containsFilter = new RowFilter<UsersTableModel, Object>() {
			public boolean include(Entry<? extends UsersTableModel, ? extends Object> entry) {
				for (int i = entry.getValueCount() - 1; i >= 0; i--) {
					if (entry.getStringValue(i).toLowerCase().contains(fragment.toLowerCase())) {
						return true;
					}
				}
				return false;
			}
		};
		TableRowSorter<UsersTableModel> sorter = new TableRowSorter<UsersTableModel>(usersModel);
		sorter.setRowFilter(containsFilter);
		usersTable.setRowSorter(sorter);
	}

	private void filterSettings(final String fragment) {
		if (StringUtils.isEmpty(fragment)) {
			settingsTable.setRowSorter(defaultSettingsSorter);
			return;
		}
		RowFilter<SettingsTableModel, Object> containsFilter = new RowFilter<SettingsTableModel, Object>() {
			public boolean include(Entry<? extends SettingsTableModel, ? extends Object> entry) {
				for (int i = entry.getValueCount() - 1; i >= 0; i--) {
					if (entry.getStringValue(i).toLowerCase().contains(fragment.toLowerCase())) {
						return true;
					}
				}
				return false;
			}
		};
		TableRowSorter<SettingsTableModel> sorter = new TableRowSorter<SettingsTableModel>(
				settingsModel);
		sorter.setRowFilter(containsFilter);
		settingsTable.setRowSorter(sorter);
	}

	private List<RepositoryModel> getSelectedRepositories() {
		List<RepositoryModel> repositories = new ArrayList<RepositoryModel>();
		for (int viewRow : repositoriesTable.getSelectedRows()) {
			int modelRow = repositoriesTable.convertRowIndexToModel(viewRow);
			RepositoryModel model = repositoriesModel.list.get(modelRow);
			repositories.add(model);
		}
		return repositories;
	}

	private List<UserModel> getSelectedUsers() {
		List<UserModel> users = new ArrayList<UserModel>();
		for (int viewRow : usersTable.getSelectedRows()) {
			int modelRow = usersTable.convertRowIndexToModel(viewRow);
			UserModel model = usersModel.list.get(modelRow);
			users.add(model);
		}
		return users;
	}

	@Override
	public Insets getInsets() {
		return insets;
	}

	@Override
	public void closeTab(Component c) {
		gitblit = null;
	}

	protected void refreshRepositories() {
		GitblitWorker worker = new GitblitWorker(GitblitPanel.this, RpcRequest.LIST_REPOSITORIES) {
			@Override
			protected Boolean doRequest() throws IOException {
				gitblit.refreshRepositories();
				return true;
			}

			@Override
			protected void onSuccess() {
				updateRepositoriesTable();
			}
		};
		worker.execute();
	}

	/**
	 * Displays the create repository dialog and fires a SwingWorker to update
	 * the server, if appropriate.
	 * 
	 */
	protected void createRepository() {
		EditRepositoryDialog dialog = new EditRepositoryDialog();
		dialog.setLocationRelativeTo(GitblitPanel.this);
		dialog.setUsers(null, gitblit.getUsernames(), null);
		dialog.setRepositories(gitblit.getRepositories());
		dialog.setFederationSets(gitblit.getFederationSets(), null);
		dialog.setVisible(true);
		final RepositoryModel newRepository = dialog.getRepository();
		final List<String> permittedUsers = dialog.getPermittedUsers();
		if (newRepository == null) {
			return;
		}

		GitblitWorker worker = new GitblitWorker(this, RpcRequest.CREATE_REPOSITORY) {

			@Override
			protected Boolean doRequest() throws IOException {
				boolean success = gitblit.createRepository(newRepository, permittedUsers);
				if (success) {
					gitblit.refreshRepositories();
					if (permittedUsers.size() > 0) {
						gitblit.refreshUsers();
					}
				}
				return success;
			}

			@Override
			protected void onSuccess() {
				updateRepositoriesTable();
				updateUsersTable();
			}

			@Override
			protected void onFailure() {
				showFailure("Failed to execute request \"{0}\" for repository \"{1}\".",
						getRequestType(), newRepository.name);
			}
		};
		worker.execute();
	}

	/**
	 * Displays the edit repository dialog and fires a SwingWorker to update the
	 * server, if appropriate.
	 * 
	 * @param repository
	 */
	protected void editRepository(final RepositoryModel repository) {
		EditRepositoryDialog dialog = new EditRepositoryDialog(repository);
		dialog.setLocationRelativeTo(GitblitPanel.this);
		List<String> usernames = gitblit.getUsernames();
		List<String> members = gitblit.getPermittedUsernames(repository);
		dialog.setUsers(repository.owner, usernames, members);
		dialog.setRepositories(gitblit.getRepositories());
		dialog.setFederationSets(gitblit.getFederationSets(), repository.federationSets);
		dialog.setVisible(true);
		final RepositoryModel revisedRepository = dialog.getRepository();
		final List<String> permittedUsers = dialog.getPermittedUsers();
		if (revisedRepository == null) {
			return;
		}

		GitblitWorker worker = new GitblitWorker(this, RpcRequest.EDIT_REPOSITORY) {

			@Override
			protected Boolean doRequest() throws IOException {
				boolean success = gitblit.updateRepository(repository.name, revisedRepository,
						permittedUsers);
				if (success) {
					gitblit.refreshRepositories();
					gitblit.refreshUsers();
				}
				return success;
			}

			@Override
			protected void onSuccess() {
				updateRepositoriesTable();
				updateUsersTable();
			}

			@Override
			protected void onFailure() {
				showFailure("Failed to execute request \"{0}\" for repository \"{1}\".",
						getRequestType(), repository.name);
			}
		};
		worker.execute();
	}

	protected void deleteRepositories(final List<RepositoryModel> repositories) {
		if (repositories == null || repositories.size() == 0) {
			return;
		}
		StringBuilder message = new StringBuilder("Delete the following repositories?\n\n");
		for (RepositoryModel repository : repositories) {
			message.append(repository.name).append("\n");
		}
		int result = JOptionPane.showConfirmDialog(GitblitPanel.this, message.toString(),
				"Delete Repositories?", JOptionPane.YES_NO_OPTION);
		if (result == JOptionPane.YES_OPTION) {
			GitblitWorker worker = new GitblitWorker(this, RpcRequest.DELETE_REPOSITORY) {
				@Override
				protected Boolean doRequest() throws IOException {
					boolean success = true;
					for (RepositoryModel repository : repositories) {
						success &= gitblit.deleteRepository(repository);
					}
					if (success) {
						gitblit.refreshRepositories();
						gitblit.refreshUsers();
					}
					return success;
				}

				@Override
				protected void onSuccess() {
					updateRepositoriesTable();
					updateUsersTable();
				}

				@Override
				protected void onFailure() {
					showFailure("Failed to delete specified repositories!");
				}
			};
			worker.execute();
		}
	}

	protected void refreshUsers() {
		GitblitWorker worker = new GitblitWorker(GitblitPanel.this, RpcRequest.LIST_USERS) {
			@Override
			protected Boolean doRequest() throws IOException {
				gitblit.refreshUsers();
				return true;
			}

			@Override
			protected void onSuccess() {
				updateUsersTable();
			}
		};
		worker.execute();
	}

	/**
	 * Displays the create user dialog and fires a SwingWorker to update the
	 * server, if appropriate.
	 * 
	 */
	protected void createUser() {
		EditUserDialog dialog = new EditUserDialog(gitblit.getSettings());
		dialog.setLocationRelativeTo(GitblitPanel.this);
		dialog.setUsers(gitblit.getUsers());
		dialog.setRepositories(gitblit.getRepositories(), null);
		dialog.setVisible(true);
		final UserModel newUser = dialog.getUser();
		if (newUser == null) {
			return;
		}

		GitblitWorker worker = new GitblitWorker(this, RpcRequest.CREATE_USER) {

			@Override
			protected Boolean doRequest() throws IOException {
				boolean success = gitblit.createUser(newUser);
				if (success) {
					gitblit.refreshUsers();
				}
				return success;
			}

			@Override
			protected void onSuccess() {
				updateUsersTable();
			}

			@Override
			protected void onFailure() {
				showFailure("Failed to execute request \"{0}\" for user \"{1}\".",
						getRequestType(), newUser.username);
			}
		};
		worker.execute();
	}

	/**
	 * Displays the edit user dialog and fires a SwingWorker to update the
	 * server, if appropriate.
	 * 
	 * @param user
	 */
	protected void editUser(final UserModel user) {
		EditUserDialog dialog = new EditUserDialog(user, gitblit.getSettings());
		dialog.setLocationRelativeTo(GitblitPanel.this);
		dialog.setUsers(gitblit.getUsers());
		dialog.setRepositories(gitblit.getRepositories(), new ArrayList<String>(user.repositories));
		dialog.setVisible(true);
		final UserModel revisedUser = dialog.getUser();
		if (revisedUser == null) {
			return;
		}

		GitblitWorker worker = new GitblitWorker(this, RpcRequest.EDIT_USER) {
			@Override
			protected Boolean doRequest() throws IOException {
				boolean success = gitblit.updateUser(user.username, revisedUser);
				if (success) {
					gitblit.refreshUsers();
				}
				return success;
			}

			@Override
			protected void onSuccess() {
				updateUsersTable();
			}

			@Override
			protected void onFailure() {
				showFailure("Failed to execute request \"{0}\" for user \"{1}\".",
						getRequestType(), user.username);
			}
		};
		worker.execute();
	}

	protected void deleteUsers(final List<UserModel> users) {
		if (users == null || users.size() == 0) {
			return;
		}
		StringBuilder message = new StringBuilder("Delete the following users?\n\n");
		for (UserModel user : users) {
			message.append(user.username).append("\n");
		}
		int result = JOptionPane.showConfirmDialog(GitblitPanel.this, message.toString(),
				"Delete Users?", JOptionPane.YES_NO_OPTION);
		if (result == JOptionPane.YES_OPTION) {
			GitblitWorker worker = new GitblitWorker(this, RpcRequest.DELETE_USER) {
				@Override
				protected Boolean doRequest() throws IOException {
					boolean success = true;
					for (UserModel user : users) {
						success &= gitblit.deleteUser(user);
					}
					if (success) {
						gitblit.refreshUsers();
					}
					return success;
				}

				@Override
				protected void onSuccess() {
					updateUsersTable();
				}

				@Override
				protected void onFailure() {
					showFailure("Failed to delete specified users!");
				}
			};
			worker.execute();
		}
	}

	protected void refreshSettings() {
		GitblitWorker worker = new GitblitWorker(GitblitPanel.this, RpcRequest.LIST_SETTINGS) {
			@Override
			protected Boolean doRequest() throws IOException {
				gitblit.refreshSettings();
				return true;
			}

			@Override
			protected void onSuccess() {
				updateSettingsTable();
			}
		};
		worker.execute();
	}

	protected void refreshStatus() {
		GitblitWorker worker = new GitblitWorker(GitblitPanel.this, RpcRequest.LIST_STATUS) {
			@Override
			protected Boolean doRequest() throws IOException {
				gitblit.refreshStatus();
				return true;
			}

			@Override
			protected void onSuccess() {
				updateStatusPanel();
			}
		};
		worker.execute();
	}

	protected void editSetting(final SettingModel settingModel) {
		final JTextField textField = new JTextField(settingModel.currentValue);
		JPanel editPanel = new JPanel(new GridLayout(0, 1));
		editPanel.add(new JLabel("New Value"));
		editPanel.add(textField);

		JPanel settingPanel = new JPanel(new BorderLayout());
		settingPanel.add(new SettingPanel(settingModel), BorderLayout.CENTER);
		settingPanel.add(editPanel, BorderLayout.SOUTH);
		settingPanel.setPreferredSize(new Dimension(800, 200));

		String[] options;
		if (settingModel.currentValue.equals(settingModel.defaultValue)) {
			options = new String[] { Translation.get("gb.cancel"), Translation.get("gb.save") };
		} else {
			options = new String[] { Translation.get("gb.cancel"),
					Translation.get("gb.setDefault"), Translation.get("gb.save") };
		}
		String defaultOption = options[0];
		int selection = JOptionPane.showOptionDialog(GitblitPanel.this, settingPanel,
				settingModel.name, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
				new ImageIcon(getClass().getResource("/settings_16x16.png")), options,
				defaultOption);
		if (selection <= 0) {
			return;
		}
		if (options[selection].equals(Translation.get("gb.setDefault"))) {
			textField.setText(settingModel.defaultValue);
		}
		final Map<String, String> newSettings = new HashMap<String, String>();
		newSettings.put(settingModel.name, textField.getText().trim());
		GitblitWorker worker = new GitblitWorker(GitblitPanel.this, RpcRequest.EDIT_SETTINGS) {
			@Override
			protected Boolean doRequest() throws IOException {
				boolean success = gitblit.updateSettings(newSettings);
				if (success) {
					gitblit.refreshSettings();
				}
				return success;
			}

			@Override
			protected void onSuccess() {
				updateSettingsTable();
			}
		};
		worker.execute();
	}
}