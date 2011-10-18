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
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

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

	private GitblitModel gitblit;

	private JTabbedPane tabs;

	private JTable repositoriesTable;

	private RepositoriesModel repositoriesModel;

	private JTable usersTable;

	private UsersModel usersModel;

	private JButton createRepository;

	private JButton delRepository;

	private NameRenderer nameRenderer;

	private IndicatorsRenderer typeRenderer;

	private DefaultTableCellRenderer ownerRenderer;

	private DefaultTableCellRenderer sizeRenderer;

	private TableRowSorter<RepositoriesModel> defaultRepositoriesSorter;

	private TableRowSorter<UsersModel> defaultUsersSorter;

	private JButton editRepository;

	public GitblitPanel(GitblitRegistration reg) {
		this(reg.url, reg.account, reg.password);
	}

	public GitblitPanel(String url, String account, char[] password) {
		this.gitblit = new GitblitModel(url, account, password);

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

		repositoriesModel = new RepositoriesModel();
		defaultRepositoriesSorter = new TableRowSorter<RepositoriesModel>(repositoriesModel);
		repositoriesTable = Utils.newTable(repositoriesModel);
		repositoriesTable.setRowHeight(nameRenderer.getFont().getSize() + 8);
		repositoriesTable.setRowSorter(defaultRepositoriesSorter);
		repositoriesTable.getRowSorter().toggleSortOrder(RepositoriesModel.Columns.Name.ordinal());

		setRepositoryRenderer(RepositoriesModel.Columns.Name, nameRenderer);
		setRepositoryRenderer(RepositoriesModel.Columns.Indicators, typeRenderer);
		setRepositoryRenderer(RepositoriesModel.Columns.Owner, ownerRenderer);
		setRepositoryRenderer(RepositoriesModel.Columns.Size, sizeRenderer);

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
					RepositoryModel model = ((RepositoriesModel) repositoriesTable.getModel()).list
							.get(modelRow);
					editRepository.setEnabled(singleSelection
							&& (gitblit.allowAdmin() || gitblit.isOwner(model)));
				} else {
					editRepository.setEnabled(false);
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

		JPanel repositoryControls = new JPanel();
		repositoryControls.add(refreshRepositories);
		repositoryControls.add(browseRepository);
		repositoryControls.add(createRepository);
		repositoryControls.add(editRepository);
		repositoryControls.add(delRepository);

		JPanel repositoriesPanel = new JPanel(new BorderLayout(margin, margin));
		repositoriesPanel.add(newHeaderLabel(Translation.get("gb.repositories")),
				BorderLayout.NORTH);
		repositoriesPanel.add(repositoryTablePanel, BorderLayout.CENTER);
		repositoriesPanel.add(repositoryControls, BorderLayout.SOUTH);

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

		usersModel = new UsersModel();
		defaultUsersSorter = new TableRowSorter<UsersModel>(usersModel);
		usersTable = Utils.newTable(usersModel);
		String name = usersTable.getColumnName(UsersModel.Columns.Name.ordinal());
		usersTable.setRowHeight(nameRenderer.getFont().getSize() + 8);
		usersTable.getColumn(name).setCellRenderer(nameRenderer);
		usersTable.setRowSorter(defaultUsersSorter);
		usersTable.getRowSorter().toggleSortOrder(UsersModel.Columns.Name.ordinal());
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

		JPanel userControls = new JPanel();
		userControls.add(refreshUsers);
		userControls.add(createUser);
		userControls.add(editUser);
		userControls.add(delUser);

		JPanel usersPanel = new JPanel(new BorderLayout(margin, margin));
		usersPanel.add(newHeaderLabel(Translation.get("gb.users")), BorderLayout.NORTH);
		usersPanel.add(userTablePanel, BorderLayout.CENTER);
		usersPanel.add(userControls, BorderLayout.SOUTH);

		tabs = new JTabbedPane(JTabbedPane.BOTTOM);
		tabs.addTab(Translation.get("gb.repositories"), repositoriesPanel);
		tabs.addTab(Translation.get("gb.users"), usersPanel);
		tabs.addTab(Translation.get("gb.federation"), new JPanel());

		setLayout(new BorderLayout());
		add(tabs, BorderLayout.CENTER);
	}

	private JLabel newHeaderLabel(String text) {
		JLabel label = new JLabel(text);
		label.setOpaque(true);
		label.setForeground(Color.white);
		label.setBackground(Color.gray);
		label.setFont(label.getFont().deriveFont(14f));
		return label;
	}

	private void setRepositoryRenderer(RepositoriesModel.Columns col, TableCellRenderer renderer) {
		String name = repositoriesTable.getColumnName(col.ordinal());
		repositoriesTable.getColumn(name).setCellRenderer(renderer);
	}

	public void login() throws IOException {
		gitblit.login();

		updateRepositoriesTable();
		Utils.packColumns(repositoriesTable, 2);

		if (gitblit.allowAdmin()) {
			updateUsersTable();
		} else {
			// user does not have administrator privileges
			// hide admin repository buttons
			createRepository.setVisible(false);
			editRepository.setVisible(false);
			delRepository.setVisible(false);

			while (tabs.getTabCount() > 1) {
				// remove admin tabs
				tabs.removeTabAt(1);
			}
		}
	}

	private void updateRepositoriesTable() {
		repositoriesModel.list.clear();
		repositoriesModel.list.addAll(gitblit.getRepositories());
		repositoriesModel.fireTableDataChanged();
	}

	private void updateUsersTable() {
		usersModel.list.clear();
		usersModel.list.addAll(gitblit.getUsers());
		usersModel.fireTableDataChanged();
	}

	private void filterRepositories(final String fragment) {
		if (StringUtils.isEmpty(fragment)) {
			repositoriesTable.setRowSorter(defaultRepositoriesSorter);
			return;
		}
		RowFilter<RepositoriesModel, Object> containsFilter = new RowFilter<RepositoriesModel, Object>() {
			public boolean include(Entry<? extends RepositoriesModel, ? extends Object> entry) {
				for (int i = entry.getValueCount() - 1; i >= 0; i--) {
					if (entry.getStringValue(i).toLowerCase().contains(fragment.toLowerCase())) {
						return true;
					}
				}
				return false;
			}
		};
		TableRowSorter<RepositoriesModel> sorter = new TableRowSorter<RepositoriesModel>(
				repositoriesModel);
		sorter.setRowFilter(containsFilter);
		repositoriesTable.setRowSorter(sorter);
	}

	private void filterUsers(final String fragment) {
		if (StringUtils.isEmpty(fragment)) {
			usersTable.setRowSorter(defaultUsersSorter);
			return;
		}
		RowFilter<UsersModel, Object> containsFilter = new RowFilter<UsersModel, Object>() {
			public boolean include(Entry<? extends UsersModel, ? extends Object> entry) {
				for (int i = entry.getValueCount() - 1; i >= 0; i--) {
					if (entry.getStringValue(i).toLowerCase().contains(fragment.toLowerCase())) {
						return true;
					}
				}
				return false;
			}
		};
		TableRowSorter<UsersModel> sorter = new TableRowSorter<UsersModel>(usersModel);
		sorter.setRowFilter(containsFilter);
		usersTable.setRowSorter(sorter);
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
		dialog.setUsers(null, gitblit.getUsernames(), null);
		dialog.setRepositories(gitblit.getRepositories());
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
		List<String> usernames = gitblit.getUsernames();
		List<String> members = gitblit.getPermittedUsernames(repository);
		dialog.setUsers(repository.owner, usernames, members);
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
}