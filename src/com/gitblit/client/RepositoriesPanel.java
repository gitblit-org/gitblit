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
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;

import com.gitblit.Constants;
import com.gitblit.Constants.RpcRequest;
import com.gitblit.Keys;
import com.gitblit.models.FeedModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.StringUtils;

/**
 * RSS Feeds Panel displays recent entries and launches the browser to view the
 * commit. commitdiff, or tree of a commit.
 * 
 * @author James Moger
 * 
 */
public abstract class RepositoriesPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private final GitblitClient gitblit;

	private HeaderPanel header;

	private JTable table;

	private RepositoriesTableModel tableModel;

	private TableRowSorter<RepositoriesTableModel> defaultSorter;

	private JButton createRepository;

	private JButton editRepository;

	private JButton delRepository;

	private JTextField filterTextfield;

	private JButton clearCache;

	public RepositoriesPanel(GitblitClient gitblit) {
		super();
		this.gitblit = gitblit;
		initialize();
	}

	private void initialize() {
		final JButton browseRepository = new JButton(Translation.get("gb.browse"));
		browseRepository.setEnabled(false);
		browseRepository.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				RepositoryModel model = getSelectedRepositories().get(0);
				String url = gitblit.getURL("summary", model.name, null);
				Utils.browse(url);
			}
		});

		JButton refreshRepositories = new JButton(Translation.get("gb.refresh"));
		refreshRepositories.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				refreshRepositories();
			}
		});
		
		clearCache = new JButton(Translation.get("gb.clearCache"));
		clearCache.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				clearCache();
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

		final JButton subscribeRepository = new JButton(Translation.get("gb.subscribe") + "...");
		subscribeRepository.setEnabled(false);
		subscribeRepository.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				List<FeedModel> feeds = gitblit.getAvailableFeeds(getSelectedRepositories().get(0));
				subscribeFeeds(feeds);
			}
		});

		final JButton logRepository = new JButton(Translation.get("gb.log") + "...");
		logRepository.setEnabled(false);
		logRepository.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				RepositoryModel model = getSelectedRepositories().get(0);
				showSearchDialog(false, model);
			}
		});

		final JButton searchRepository = new JButton(Translation.get("gb.search") + "...");
		searchRepository.setEnabled(false);
		searchRepository.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				RepositoryModel model = getSelectedRepositories().get(0);
				showSearchDialog(true, model);
			}
		});

		SubscribedRepositoryRenderer nameRenderer = new SubscribedRepositoryRenderer(gitblit);
		IndicatorsRenderer typeRenderer = new IndicatorsRenderer();

		DefaultTableCellRenderer sizeRenderer = new DefaultTableCellRenderer();
		sizeRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
		sizeRenderer.setForeground(new Color(0, 0x80, 0));

		DefaultTableCellRenderer ownerRenderer = new DefaultTableCellRenderer();
		ownerRenderer.setForeground(Color.gray);
		ownerRenderer.setHorizontalAlignment(SwingConstants.CENTER);

		tableModel = new RepositoriesTableModel();
		defaultSorter = new TableRowSorter<RepositoriesTableModel>(tableModel);
		table = Utils.newTable(tableModel, Utils.DATE_FORMAT);
		table.setRowHeight(nameRenderer.getFont().getSize() + 8);
		table.setRowSorter(defaultSorter);
		table.getRowSorter().toggleSortOrder(RepositoriesTableModel.Columns.Name.ordinal());

		setRepositoryRenderer(RepositoriesTableModel.Columns.Name, nameRenderer, -1);
		setRepositoryRenderer(RepositoriesTableModel.Columns.Indicators, typeRenderer, 100);
		setRepositoryRenderer(RepositoriesTableModel.Columns.Owner, ownerRenderer, -1);
		setRepositoryRenderer(RepositoriesTableModel.Columns.Size, sizeRenderer, 60);

		table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				if (e.getValueIsAdjusting()) {
					return;
				}
				boolean singleSelection = table.getSelectedRowCount() == 1;
				boolean selected = table.getSelectedRow() > -1;
				if (singleSelection) {
					RepositoryModel repository = getSelectedRepositories().get(0);
					browseRepository.setEnabled(repository.hasCommits);
					logRepository.setEnabled(repository.hasCommits);
					searchRepository.setEnabled(repository.hasCommits);
					subscribeRepository.setEnabled(repository.hasCommits);
				} else {
					browseRepository.setEnabled(false);
					logRepository.setEnabled(false);
					searchRepository.setEnabled(false);
					subscribeRepository.setEnabled(false);
				}
				delRepository.setEnabled(selected);
				if (selected) {
					int viewRow = table.getSelectedRow();
					int modelRow = table.convertRowIndexToModel(viewRow);
					RepositoryModel model = ((RepositoriesTableModel) table.getModel()).list
							.get(modelRow);
					editRepository.setEnabled(singleSelection
							&& (gitblit.allowManagement() || gitblit.isOwner(model)));
				} else {
					editRepository.setEnabled(false);
				}
			}
		});

		table.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && gitblit.allowManagement()) {
					editRepository(getSelectedRepositories().get(0));
				}
			}
		});

		filterTextfield = new JTextField();
		filterTextfield.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				filterRepositories(filterTextfield.getText());
			}
		});
		filterTextfield.addKeyListener(new KeyAdapter() {
			public void keyReleased(KeyEvent e) {
				filterRepositories(filterTextfield.getText());
			}
		});

		JPanel repositoryFilterPanel = new JPanel(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		repositoryFilterPanel.add(new JLabel(Translation.get("gb.filter")), BorderLayout.WEST);
		repositoryFilterPanel.add(filterTextfield, BorderLayout.CENTER);

		JPanel repositoryTablePanel = new JPanel(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		repositoryTablePanel.add(repositoryFilterPanel, BorderLayout.NORTH);
		repositoryTablePanel.add(new JScrollPane(table), BorderLayout.CENTER);

		JPanel repositoryControls = new JPanel(new FlowLayout(FlowLayout.CENTER, Utils.MARGIN, 0));
		repositoryControls.add(clearCache);
		repositoryControls.add(refreshRepositories);
		repositoryControls.add(browseRepository);
		repositoryControls.add(createRepository);
		repositoryControls.add(editRepository);
		repositoryControls.add(delRepository);
		repositoryControls.add(subscribeRepository);
		repositoryControls.add(logRepository);
		repositoryControls.add(searchRepository);

		setLayout(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		header = new HeaderPanel(Translation.get("gb.repositories"), "git-orange-16x16.png");
		add(header, BorderLayout.NORTH);
		add(repositoryTablePanel, BorderLayout.CENTER);
		add(repositoryControls, BorderLayout.SOUTH);
	}

	@Override
	public void requestFocus() {
		filterTextfield.requestFocus();
	}

	@Override
	public Insets getInsets() {
		return Utils.INSETS;
	}

	private void setRepositoryRenderer(RepositoriesTableModel.Columns col,
			TableCellRenderer renderer, int maxWidth) {
		String name = table.getColumnName(col.ordinal());
		table.getColumn(name).setCellRenderer(renderer);
		if (maxWidth > 0) {
			table.getColumn(name).setMinWidth(maxWidth);
			table.getColumn(name).setMaxWidth(maxWidth);
		}
	}

	protected abstract void subscribeFeeds(List<FeedModel> feeds);

	protected abstract void updateUsersTable();

	protected abstract void updateTeamsTable();

	protected void disableManagement() {
		clearCache.setVisible(false);
		createRepository.setVisible(false);
		editRepository.setVisible(false);
		delRepository.setVisible(false);
	}

	protected void updateTable(boolean pack) {
		tableModel.list.clear();
		tableModel.list.addAll(gitblit.getRepositories());
		tableModel.fireTableDataChanged();
		header.setText(Translation.get("gb.repositories") + " (" + gitblit.getRepositories().size()
				+ ")");
		if (pack) {
			Utils.packColumns(table, Utils.MARGIN);
		}
	}

	private void filterRepositories(final String fragment) {
		if (StringUtils.isEmpty(fragment)) {
			table.setRowSorter(defaultSorter);
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
				tableModel);
		sorter.setRowFilter(containsFilter);
		table.setRowSorter(sorter);
	}

	private List<RepositoryModel> getSelectedRepositories() {
		List<RepositoryModel> repositories = new ArrayList<RepositoryModel>();
		for (int viewRow : table.getSelectedRows()) {
			int modelRow = table.convertRowIndexToModel(viewRow);
			RepositoryModel model = tableModel.list.get(modelRow);
			repositories.add(model);
		}
		return repositories;
	}

	protected void refreshRepositories() {
		GitblitWorker worker = new GitblitWorker(RepositoriesPanel.this,
				RpcRequest.LIST_REPOSITORIES) {
			@Override
			protected Boolean doRequest() throws IOException {
				gitblit.refreshRepositories();
				return true;
			}

			@Override
			protected void onSuccess() {
				updateTable(false);
			}
		};
		worker.execute();
	}
	
	protected void clearCache() {
		GitblitWorker worker = new GitblitWorker(RepositoriesPanel.this,
				RpcRequest.CLEAR_REPOSITORY_CACHE) {
			@Override
			protected Boolean doRequest() throws IOException {
				if (gitblit.clearRepositoryCache()) {
					gitblit.refreshRepositories();
					return true;
				}
				return false;
			}

			@Override
			protected void onSuccess() {
				updateTable(false);
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
		EditRepositoryDialog dialog = new EditRepositoryDialog(gitblit.getProtocolVersion());
		dialog.setLocationRelativeTo(RepositoriesPanel.this);
		dialog.setAccessRestriction(gitblit.getDefaultAccessRestriction());
		dialog.setAuthorizationControl(gitblit.getDefaultAuthorizationControl());
		dialog.setUsers(null, gitblit.getUsernames(), null);
		dialog.setTeams(gitblit.getTeamnames(), null);
		dialog.setRepositories(gitblit.getRepositories());
		dialog.setFederationSets(gitblit.getFederationSets(), null);
		dialog.setIndexedBranches(new ArrayList<String>(Arrays.asList(Constants.DEFAULT_BRANCH)), null);
		dialog.setPreReceiveScripts(gitblit.getPreReceiveScriptsUnused(null),
				gitblit.getPreReceiveScriptsInherited(null), null);
		dialog.setPostReceiveScripts(gitblit.getPostReceiveScriptsUnused(null),
				gitblit.getPostReceiveScriptsInherited(null), null);
		dialog.setVisible(true);
		final RepositoryModel newRepository = dialog.getRepository();
		final List<String> permittedUsers = dialog.getPermittedUsers();
		final List<String> permittedTeams = dialog.getPermittedTeams();
		if (newRepository == null) {
			return;
		}

		GitblitWorker worker = new GitblitWorker(this, RpcRequest.CREATE_REPOSITORY) {

			@Override
			protected Boolean doRequest() throws IOException {
				boolean success = gitblit.createRepository(newRepository, permittedUsers,
						permittedTeams);
				if (success) {
					gitblit.refreshRepositories();
					if (permittedUsers.size() > 0) {
						gitblit.refreshUsers();
					}
					if (permittedTeams.size() > 0) {
						gitblit.refreshTeams();
					}
				}
				return success;
			}

			@Override
			protected void onSuccess() {
				updateTable(false);
				updateUsersTable();
				updateTeamsTable();
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
		EditRepositoryDialog dialog = new EditRepositoryDialog(gitblit.getProtocolVersion(),
				repository);
		dialog.setLocationRelativeTo(RepositoriesPanel.this);
		List<String> usernames = gitblit.getUsernames();
		List<String> members = gitblit.getPermittedUsernames(repository);
		dialog.setUsers(repository.owner, usernames, members);
		dialog.setTeams(gitblit.getTeamnames(), gitblit.getPermittedTeamnames(repository));
		dialog.setRepositories(gitblit.getRepositories());
		dialog.setFederationSets(gitblit.getFederationSets(), repository.federationSets);
		List<String> allLocalBranches = new ArrayList<String>();
		allLocalBranches.add(Constants.DEFAULT_BRANCH);
		allLocalBranches.addAll(repository.getLocalBranches());
		dialog.setIndexedBranches(allLocalBranches, repository.indexedBranches);
		dialog.setPreReceiveScripts(gitblit.getPreReceiveScriptsUnused(repository),
				gitblit.getPreReceiveScriptsInherited(repository), repository.preReceiveScripts);
		dialog.setPostReceiveScripts(gitblit.getPostReceiveScriptsUnused(repository),
				gitblit.getPostReceiveScriptsInherited(repository), repository.postReceiveScripts);
		if (gitblit.getSettings().hasKey(Keys.groovy.customFields)) {
			Map<String, String> map = gitblit.getSettings().get(Keys.groovy.customFields).getMap();
			dialog.setCustomFields(repository, map);
		}
		dialog.setVisible(true);
		final RepositoryModel revisedRepository = dialog.getRepository();
		final List<String> permittedUsers = dialog.getPermittedUsers();
		final List<String> permittedTeams = dialog.getPermittedTeams();
		if (revisedRepository == null) {
			return;
		}

		GitblitWorker worker = new GitblitWorker(this, RpcRequest.EDIT_REPOSITORY) {

			@Override
			protected Boolean doRequest() throws IOException {
				boolean success = gitblit.updateRepository(repository.name, revisedRepository,
						permittedUsers, permittedTeams);
				if (success) {
					gitblit.refreshRepositories();
					gitblit.refreshUsers();
					gitblit.refreshTeams();
				}
				return success;
			}

			@Override
			protected void onSuccess() {
				updateTable(false);
				updateUsersTable();
				updateTeamsTable();
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
		int result = JOptionPane.showConfirmDialog(RepositoriesPanel.this, message.toString(),
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
						gitblit.refreshTeams();
					}
					return success;
				}

				@Override
				protected void onSuccess() {
					updateTable(false);
					updateUsersTable();
					updateTeamsTable();
				}

				@Override
				protected void onFailure() {
					showFailure("Failed to delete specified repositories!");
				}
			};
			worker.execute();
		}
	}

	private void showSearchDialog(boolean isSearch, final RepositoryModel repository) {
		final SearchDialog dialog = new SearchDialog(gitblit, isSearch);
		if (repository != null) {
			dialog.selectRepository(repository);
		}
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
	}
}
