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
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;

import com.gitblit.Constants.RpcRequest;
import com.gitblit.GitBlitException.ForbiddenException;
import com.gitblit.GitBlitException.UnauthorizedException;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.client.ClosableTabComponent.CloseTabListener;
import com.gitblit.models.FederationModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.RpcUtils;
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

	private final String url;

	private final String account;

	private final char[] password;

	private volatile boolean isAdmin;

	private volatile List<UserModel> allUsers;

	private volatile IStoredSettings settings;

	private JTabbedPane tabs;

	private JTable repositoriesTable;

	private RepositoriesModel repositoriesModel;

	private JList usersList;

	private JPanel usersPanel;

	private JButton createRepository;

	private JButton delRepository;

	private NameRenderer nameRenderer;

	private TypeRenderer typeRenderer;

	private DefaultTableCellRenderer ownerRenderer;

	private DefaultTableCellRenderer sizeRenderer;

	private TableRowSorter<RepositoriesModel> defaultSorter;

	private List<RepositoryModel> allRepositories;

	public GitblitPanel(GitblitRegistration reg) {
		this(reg.url, reg.account, reg.password);
	}

	public GitblitPanel(String url, String account, char[] password) {
		this.url = url;
		this.account = account;
		this.password = password;

		final JButton browseRepository = new JButton("Browse");
		browseRepository.setEnabled(false);
		browseRepository.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				RepositoryModel model = getSelectedRepositories().get(0);
				String u = MessageFormat.format("{0}/summary/{1}", GitblitPanel.this.url,
						StringUtils.encodeURL(model.name));
				try {
					Desktop.getDesktop().browse(new URI(u));
				} catch (Exception x) {
					x.printStackTrace();
				}
			}
		});

		JButton refreshRepositories = new JButton("Refresh");
		refreshRepositories.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					refreshRepositoriesTable();
				} catch (ForbiddenException x) {
					explainForbidden(RpcRequest.LIST_REPOSITORIES);
				} catch (UnauthorizedException x) {
					explainUnauthorized(RpcRequest.LIST_REPOSITORIES);
				} catch (Throwable t) {
					showException(t);
				}
			}
		});

		createRepository = new JButton("Create");
		createRepository.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				createRepository();
			}
		});

		final JButton editRepository = new JButton("Edit");
		editRepository.setEnabled(false);
		editRepository.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				editRepository(getSelectedRepositories().get(0));
			}
		});

		delRepository = new JButton("Delete");
		delRepository.setEnabled(false);
		delRepository.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				deleteRepositories(getSelectedRepositories());
			}
		});

		final JButton cloneRepository = new JButton("Clone");
		cloneRepository.setEnabled(false);
		cloneRepository.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				for (RepositoryModel model : getSelectedRepositories()) {
					System.out.println("TODO Clone " + model);
				}
			}
		});

		nameRenderer = new NameRenderer();
		typeRenderer = new TypeRenderer();

		sizeRenderer = new DefaultTableCellRenderer();
		sizeRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
		sizeRenderer.setForeground(new Color(0, 0x80, 0));

		ownerRenderer = new DefaultTableCellRenderer();
		ownerRenderer.setForeground(Color.gray);
		ownerRenderer.setHorizontalAlignment(SwingConstants.CENTER);

		repositoriesModel = new RepositoriesModel();
		defaultSorter = new TableRowSorter<RepositoriesModel>(repositoriesModel);
		repositoriesTable = new JTable(repositoriesModel);
		repositoriesTable.setRowSorter(defaultSorter);
		repositoriesTable.getRowSorter().toggleSortOrder(RepositoriesModel.Columns.Name.ordinal());

		repositoriesTable.setCellSelectionEnabled(false);
		repositoriesTable.setRowSelectionAllowed(true);
		repositoriesTable.setRowHeight(nameRenderer.getFont().getSize() + 8);
		repositoriesTable.getTableHeader().setReorderingAllowed(false);
		repositoriesTable.setGridColor(new Color(0xd9d9d9));
		repositoriesTable.setBackground(Color.white);
		repositoriesTable.setDefaultRenderer(Date.class,
				new DateCellRenderer(null, Color.orange.darker()));
		setRenderer(RepositoriesModel.Columns.Name, nameRenderer);
		setRenderer(RepositoriesModel.Columns.Type, typeRenderer);
		setRenderer(RepositoriesModel.Columns.Owner, ownerRenderer);
		setRenderer(RepositoriesModel.Columns.Size, sizeRenderer);

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
				cloneRepository.setEnabled(selected);
				if (selected) {
					int viewRow = repositoriesTable.getSelectedRow();
					int modelRow = repositoriesTable.convertRowIndexToModel(viewRow);
					RepositoryModel model = ((RepositoriesModel) repositoriesTable.getModel()).list
							.get(modelRow);
					editRepository.setEnabled(singleSelection
							&& (isAdmin || model.owner.equalsIgnoreCase(GitblitPanel.this.account)));
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

		JPanel filterPanel = new JPanel(new BorderLayout(margin, margin));
		filterPanel.add(new JLabel("Filter"), BorderLayout.WEST);
		filterPanel.add(repositoryFilter, BorderLayout.CENTER);

		JPanel tablePanel = new JPanel(new BorderLayout(margin, margin));
		tablePanel.add(filterPanel, BorderLayout.NORTH);
		tablePanel.add(new JScrollPane(repositoriesTable), BorderLayout.CENTER);

		JPanel repositoryControls = new JPanel();
		repositoryControls.add(browseRepository);
		repositoryControls.add(cloneRepository);
		repositoryControls.add(createRepository);
		repositoryControls.add(editRepository);
		repositoryControls.add(delRepository);

		JPanel repositoriesPanel = new JPanel(new BorderLayout(margin, margin));
		repositoriesPanel.add(newHeaderLabel("Repositories"), BorderLayout.NORTH);
		repositoriesPanel.add(tablePanel, BorderLayout.CENTER);
		repositoriesPanel.add(repositoryControls, BorderLayout.SOUTH);

		JButton refreshUsers = new JButton("Refresh");
		refreshUsers.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					refreshUsersTable();
				} catch (ForbiddenException x) {
					explainForbidden(RpcRequest.LIST_USERS);
				} catch (UnauthorizedException x) {
					explainUnauthorized(RpcRequest.LIST_USERS);
				} catch (Throwable t) {
					showException(t);
				}
			}
		});

		JButton createUser = new JButton("Create");
		createUser.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				createUser();
			}
		});

		final JButton editUser = new JButton("Edit");
		editUser.setEnabled(false);
		editUser.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				editUser(getSelectedUsers().get(0));
			}
		});

		final JButton delUser = new JButton("Delete");
		delUser.setEnabled(false);
		delUser.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				deleteUsers(getSelectedUsers());
			}
		});

		usersList = new JList();
		usersList.addListSelectionListener(new ListSelectionListener() {

			@Override
			public void valueChanged(ListSelectionEvent e) {
				if (e.getValueIsAdjusting()) {
					return;
				}
				boolean selected = usersList.getSelectedIndex() > -1;
				boolean singleSelection = usersList.getSelectedIndices().length == 1;
				editUser.setEnabled(singleSelection && selected);
				delUser.setEnabled(selected);
			}
		});

		JPanel userControls = new JPanel(new GridLayout(0, 2));
		userControls.add(refreshUsers);
		userControls.add(createUser);
		userControls.add(editUser);
		userControls.add(delUser);

		usersPanel = new JPanel(new BorderLayout(margin, margin));
		usersPanel.add(newHeaderLabel("Users"), BorderLayout.NORTH);
		usersPanel.add(new JScrollPane(usersList), BorderLayout.CENTER);
		usersPanel.add(userControls, BorderLayout.SOUTH);

		/*
		 * Assemble the main panel
		 */
		JPanel mainPanel = new JPanel(new BorderLayout(margin, margin));
		mainPanel.add(repositoriesPanel, BorderLayout.CENTER);
		mainPanel.add(usersPanel, BorderLayout.EAST);

		tabs = new JTabbedPane(JTabbedPane.BOTTOM);
		tabs.addTab("Main", mainPanel);
		tabs.addTab("Federation", new JPanel());

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

	public void login() throws IOException {
		refreshRepositoriesTable();

		try {
			refreshUsersTable();
			refreshSettings();
			isAdmin = true;
			refreshFederationPanel();
		} catch (ForbiddenException e) {
			// user does not have administrator privileges
			// hide admin repository buttons
			createRepository.setVisible(false);
			delRepository.setVisible(false);

			// hide users panel
			usersPanel.setVisible(false);

			// remove federation tab
			tabs.removeTabAt(1);
		} catch (IOException e) {
			System.err.println(e.getMessage());
		}
	}

	private void refreshRepositoriesTable() throws IOException {
		Map<String, RepositoryModel> repositories = RpcUtils
				.getRepositories(url, account, password);
		allRepositories = new ArrayList<RepositoryModel>(repositories.values());
		Collections.sort(allRepositories);
		repositoriesModel.list.clear();
		repositoriesModel.list.addAll(allRepositories);
		repositoriesModel.fireTableDataChanged();
		packColumns(repositoriesTable, 2);
	}

	private void setRenderer(RepositoriesModel.Columns col, TableCellRenderer renderer) {
		String name = repositoriesTable.getColumnName(col.ordinal());
		repositoriesTable.getColumn(name).setCellRenderer(renderer);
	}

	private void refreshUsersTable() throws IOException {
		allUsers = RpcUtils.getUsers(url, account, password);
		usersList.setListData(allUsers.toArray());
	}

	private void refreshSettings() throws IOException {
		settings = RpcUtils.getSettings(url, account, password);
	}

	private void refreshFederationPanel() throws IOException {
		List<FederationModel> registrations = RpcUtils.getFederationRegistrations(url, account,
				password);
	}

	private void filterRepositories(final String fragment) {
		if (StringUtils.isEmpty(fragment)) {
			repositoriesTable.setRowSorter(defaultSorter);
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
		RepositoriesModel model = (RepositoriesModel) repositoriesTable.getModel();
		TableRowSorter<RepositoriesModel> sorter = new TableRowSorter<RepositoriesModel>(model);
		sorter.setRowFilter(containsFilter);
		repositoriesTable.setRowSorter(sorter);
	}

	private List<RepositoryModel> getSelectedRepositories() {
		List<RepositoryModel> repositories = new ArrayList<RepositoryModel>();
		for (int viewRow : repositoriesTable.getSelectedRows()) {
			int modelRow = repositoriesTable.convertRowIndexToModel(viewRow);
			RepositoryModel model = ((RepositoriesModel) repositoriesTable.getModel()).list
					.get(modelRow);
			repositories.add(model);
		}
		return repositories;
	}

	private List<UserModel> getSelectedUsers() {
		List<UserModel> users = new ArrayList<UserModel>();
		for (int viewRow : usersList.getSelectedIndices()) {
			UserModel model = (UserModel) usersList.getModel().getElementAt(viewRow);
			users.add(model);
		}
		return users;
	}

	private void packColumns(JTable table, int margin) {
		for (int c = 0; c < table.getColumnCount(); c++) {
			packColumn(table, c, 4);
		}
	}

	// Sets the preferred width of the visible column specified by vColIndex.
	// The column will be just wide enough to show the column head and the
	// widest cell in the column. margin pixels are added to the left and right
	// (resulting in an additional width of 2*margin pixels).
	private void packColumn(JTable table, int vColIndex, int margin) {
		DefaultTableColumnModel colModel = (DefaultTableColumnModel) table.getColumnModel();
		TableColumn col = colModel.getColumn(vColIndex);
		int width = 0;

		// Get width of column header
		TableCellRenderer renderer = col.getHeaderRenderer();
		if (renderer == null) {
			renderer = table.getTableHeader().getDefaultRenderer();
		}
		Component comp = renderer.getTableCellRendererComponent(table, col.getHeaderValue(), false,
				false, 0, 0);
		width = comp.getPreferredSize().width;

		// Get maximum width of column data
		for (int r = 0; r < table.getRowCount(); r++) {
			renderer = table.getCellRenderer(r, vColIndex);
			comp = renderer.getTableCellRendererComponent(table, table.getValueAt(r, vColIndex),
					false, false, r, vColIndex);
			width = Math.max(width, comp.getPreferredSize().width);
		}

		// Add margin
		width += 2 * margin;

		// Set the width
		col.setPreferredWidth(width);
	}

	@Override
	public Insets getInsets() {
		return insets;
	}

	@Override
	public Dimension getPreferredSize() {
		if (isAdmin) {
			return new Dimension(950, 550);
		}
		return new Dimension(775, 450);
	}

	@Override
	public void closeTab(Component c) {
	}

	/**
	 * Displays the create repository dialog and fires a SwingWorker to update
	 * the server, if appropriate.
	 * 
	 */
	protected void createRepository() {
		EditRepositoryDialog dialog = new EditRepositoryDialog(allUsers);
		dialog.setVisible(true);
		final RepositoryModel newRepository = dialog.getRepository();
		if (newRepository == null) {
			return;
		}

		final RpcRequest request = RpcRequest.CREATE_REPOSITORY;
		SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {

			@Override
			protected Boolean doInBackground() throws IOException {
				return RpcUtils.createRepository(newRepository, url, account, password);
			}

			@Override
			protected void done() {
				try {
					boolean success = get();
					if (success) {
						refreshRepositoriesTable();
					} else {
						String msg = MessageFormat.format(
								"Failed to execute request \"{0}\" for repository \"{1}\".",
								request.name(), newRepository.name);
						JOptionPane.showMessageDialog(GitblitPanel.this, msg, "Error!",
								JOptionPane.ERROR_MESSAGE);
					}
				} catch (ForbiddenException e) {
					explainForbidden(request);
				} catch (UnauthorizedException e) {
					explainUnauthorized(request);
				} catch (Throwable t) {
					showException(t);
				}
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
		EditRepositoryDialog dialog = new EditRepositoryDialog(repository, allUsers);
		List<String> usernames = new ArrayList<String>();
		for (UserModel user : this.allUsers) {
			usernames.add(user.username);
		}
		Collections.sort(usernames);
		dialog.setUsers(usernames, null);
		dialog.setFederationSets(settings.getStrings(Keys.federation.sets),
				repository.federationSets);
		dialog.setVisible(true);
		final RepositoryModel revisedRepository = dialog.getRepository();
		if (revisedRepository == null) {
			return;
		}

		final RpcRequest request = RpcRequest.EDIT_REPOSITORY;
		SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {

			@Override
			protected Boolean doInBackground() throws IOException {
				return RpcUtils.updateRepository(repository.name, revisedRepository, url, account,
						password);
			}

			@Override
			protected void done() {
				try {
					boolean success = get();
					if (success) {
						refreshRepositoriesTable();
					} else {
						String msg = MessageFormat.format(
								"Failed to execute request \"{0}\" for repository \"{1}\".",
								request.name(), repository.name);
						JOptionPane.showMessageDialog(GitblitPanel.this, msg, "Error!",
								JOptionPane.ERROR_MESSAGE);
					}
				} catch (ForbiddenException e) {
					explainForbidden(request);
				} catch (UnauthorizedException e) {
					explainUnauthorized(request);
				} catch (Throwable t) {
					showException(t);
				}
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
			final RpcRequest request = RpcRequest.DELETE_REPOSITORY;
			SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
				@Override
				protected Boolean doInBackground() throws Exception {
					boolean success = true;
					for (RepositoryModel repository : repositories) {
						success &= RpcUtils.deleteRepository(repository, url, account, password);
					}
					return success;
				}

				@Override
				protected void done() {
					try {
						boolean success = get();
						if (success) {
							refreshRepositoriesTable();
						} else {
							String msg = "Failed to delete specified repositories!";
							JOptionPane.showMessageDialog(GitblitPanel.this, msg, "Error!",
									JOptionPane.ERROR_MESSAGE);
						}
					} catch (ForbiddenException e) {
						explainForbidden(request);
					} catch (UnauthorizedException e) {
						explainUnauthorized(request);
					} catch (Throwable t) {
						showException(t);
					}
				}
			};
			worker.execute();
		}
	}

	/**
	 * Displays the create user dialog and fires a SwingWorker to update the
	 * server, if appropriate.
	 * 
	 */
	protected void createUser() {
		EditUserDialog dialog = new EditUserDialog(settings);
		dialog.setRepositories(allRepositories, null);
		dialog.setVisible(true);
		final UserModel newUser = dialog.getUser();
		if (newUser == null) {
			return;
		}

		final RpcRequest request = RpcRequest.CREATE_USER;
		SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {

			@Override
			protected Boolean doInBackground() throws IOException {
				return RpcUtils.createUser(newUser, url, account, password);
			}

			@Override
			protected void done() {
				try {
					boolean success = get();
					if (success) {
						refreshUsersTable();
					} else {
						String msg = MessageFormat.format(
								"Failed to execute request \"{0}\" for user \"{1}\".",
								request.name(), newUser.username);
						JOptionPane.showMessageDialog(GitblitPanel.this, msg, "Error!",
								JOptionPane.ERROR_MESSAGE);
					}
				} catch (ForbiddenException e) {
					explainForbidden(request);
				} catch (UnauthorizedException e) {
					explainUnauthorized(request);
				} catch (Throwable t) {
					showException(t);
				}
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
		EditUserDialog dialog = new EditUserDialog(user, settings);
		dialog.setRepositories(allRepositories, new ArrayList<String>(user.repositories));
		dialog.setVisible(true);
		final UserModel revisedUser = dialog.getUser();
		if (revisedUser == null) {
			return;
		}

		final RpcRequest request = RpcRequest.EDIT_USER;
		SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {

			@Override
			protected Boolean doInBackground() throws IOException {
				return RpcUtils.updateUser(user.username, revisedUser, url, account, password);
			}

			@Override
			protected void done() {
				try {
					boolean success = get();
					if (success) {
						refreshUsersTable();
					} else {
						String msg = MessageFormat.format(
								"Failed to execute request \"{0}\" for user \"{1}\".",
								request.name(), user.username);
						JOptionPane.showMessageDialog(GitblitPanel.this, msg, "Error!",
								JOptionPane.ERROR_MESSAGE);
					}
				} catch (ForbiddenException e) {
					explainForbidden(request);
				} catch (UnauthorizedException e) {
					explainUnauthorized(request);
				} catch (Throwable t) {
					showException(t);
				}
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
			final RpcRequest request = RpcRequest.DELETE_USER;
			SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
				@Override
				protected Boolean doInBackground() throws Exception {
					boolean success = true;
					for (UserModel user : users) {
						success &= RpcUtils.deleteUser(user, url, account, password);
					}
					return success;
				}

				@Override
				protected void done() {
					try {
						boolean success = get();
						if (success) {
							refreshUsersTable();
						} else {
							String msg = "Failed to delete specified users!";
							JOptionPane.showMessageDialog(GitblitPanel.this, msg, "Error!",
									JOptionPane.ERROR_MESSAGE);
						}
					} catch (ForbiddenException e) {
						explainForbidden(request);
					} catch (UnauthorizedException e) {
						explainUnauthorized(request);
					} catch (Throwable t) {
						showException(t);
					}
				}
			};
			worker.execute();
		}
	}

	private void explainForbidden(RpcRequest request) {
		String msg = MessageFormat.format(
				"The request \"{0}\" has been forbidden by the Gitblit server @ {1}.",
				request.name(), url);
		JOptionPane.showMessageDialog(GitblitPanel.this, msg, "Forbidden",
				JOptionPane.ERROR_MESSAGE);
	}

	private void explainUnauthorized(RpcRequest request) {
		String msg = MessageFormat.format(
				"The account \"{0}\" is not authorized to execute the request \"{1}\".", account,
				request.name());
		JOptionPane.showMessageDialog(GitblitPanel.this, msg, "Unauthorized",
				JOptionPane.ERROR_MESSAGE);
	}

	private void showException(Throwable t) {
		// TODO show the unexpected exception
	}
}
