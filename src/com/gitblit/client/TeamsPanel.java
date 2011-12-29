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
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableRowSorter;

import com.gitblit.Constants.RpcRequest;
import com.gitblit.models.TeamModel;
import com.gitblit.utils.StringUtils;

/**
 * Users panel displays a list of user accounts and allows management of those
 * accounts.
 * 
 * @author James Moger
 * 
 */
public abstract class TeamsPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private final GitblitClient gitblit;

	private HeaderPanel header;

	private JTable table;

	private TeamsTableModel tableModel;

	private TableRowSorter<TeamsTableModel> defaultSorter;

	private JTextField filterTextfield;

	public TeamsPanel(GitblitClient gitblit) {
		super();
		this.gitblit = gitblit;
		initialize();
	}

	private void initialize() {
		JButton refreshTeams = new JButton(Translation.get("gb.refresh"));
		refreshTeams.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				refreshTeams();
			}
		});

		JButton createTeam = new JButton(Translation.get("gb.create"));
		createTeam.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				createTeam();
			}
		});

		final JButton editTeam = new JButton(Translation.get("gb.edit"));
		editTeam.setEnabled(false);
		editTeam.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				editTeam(getSelectedTeams().get(0));
			}
		});

		final JButton delTeam = new JButton(Translation.get("gb.delete"));
		delTeam.setEnabled(false);
		delTeam.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				deleteTeams(getSelectedTeams());
			}
		});

		NameRenderer nameRenderer = new NameRenderer();
		tableModel = new TeamsTableModel();
		defaultSorter = new TableRowSorter<TeamsTableModel>(tableModel);
		table = Utils.newTable(tableModel, Utils.DATE_FORMAT);
		String name = table.getColumnName(TeamsTableModel.Columns.Name.ordinal());
		table.setRowHeight(nameRenderer.getFont().getSize() + 8);
		table.getColumn(name).setCellRenderer(nameRenderer);

		int w = 125;
		name = table.getColumnName(TeamsTableModel.Columns.Members.ordinal());
		table.getColumn(name).setMinWidth(w);
		table.getColumn(name).setMaxWidth(w);
		name = table.getColumnName(TeamsTableModel.Columns.Repositories.ordinal());
		table.getColumn(name).setMinWidth(w);
		table.getColumn(name).setMaxWidth(w);

		table.setRowSorter(defaultSorter);
		table.getRowSorter().toggleSortOrder(TeamsTableModel.Columns.Name.ordinal());
		table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

			@Override
			public void valueChanged(ListSelectionEvent e) {
				if (e.getValueIsAdjusting()) {
					return;
				}
				boolean selected = table.getSelectedRow() > -1;
				boolean singleSelection = table.getSelectedRows().length == 1;
				editTeam.setEnabled(singleSelection && selected);
				delTeam.setEnabled(selected);
			}
		});

		table.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					editTeam(getSelectedTeams().get(0));
				}
			}
		});

		filterTextfield = new JTextField();
		filterTextfield.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				filterTeams(filterTextfield.getText());
			}
		});
		filterTextfield.addKeyListener(new KeyAdapter() {
			public void keyReleased(KeyEvent e) {
				filterTeams(filterTextfield.getText());
			}
		});

		JPanel teamFilterPanel = new JPanel(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		teamFilterPanel.add(new JLabel(Translation.get("gb.filter")), BorderLayout.WEST);
		teamFilterPanel.add(filterTextfield, BorderLayout.CENTER);

		JPanel teamTablePanel = new JPanel(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		teamTablePanel.add(teamFilterPanel, BorderLayout.NORTH);
		teamTablePanel.add(new JScrollPane(table), BorderLayout.CENTER);

		JPanel teamControls = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
		teamControls.add(refreshTeams);
		teamControls.add(createTeam);
		teamControls.add(editTeam);
		teamControls.add(delTeam);

		setLayout(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		header = new HeaderPanel(Translation.get("gb.teams"), "users_16x16.png");
		add(header, BorderLayout.NORTH);
		add(teamTablePanel, BorderLayout.CENTER);
		add(teamControls, BorderLayout.SOUTH);
	}

	@Override
	public void requestFocus() {
		filterTextfield.requestFocus();
	}

	@Override
	public Insets getInsets() {
		return Utils.INSETS;
	}

	protected abstract void updateUsersTable();

	protected void updateTable(boolean pack) {
		tableModel.list.clear();
		tableModel.list.addAll(gitblit.getTeams());
		tableModel.fireTableDataChanged();
		header.setText(Translation.get("gb.teams") + " (" + gitblit.getTeams().size() + ")");
		if (pack) {
			Utils.packColumns(table, Utils.MARGIN);
		}
	}

	private void filterTeams(final String fragment) {
		if (StringUtils.isEmpty(fragment)) {
			table.setRowSorter(defaultSorter);
			return;
		}
		RowFilter<TeamsTableModel, Object> containsFilter = new RowFilter<TeamsTableModel, Object>() {
			public boolean include(Entry<? extends TeamsTableModel, ? extends Object> entry) {
				for (int i = entry.getValueCount() - 1; i >= 0; i--) {
					if (entry.getStringValue(i).toLowerCase().contains(fragment.toLowerCase())) {
						return true;
					}
				}
				return false;
			}
		};
		TableRowSorter<TeamsTableModel> sorter = new TableRowSorter<TeamsTableModel>(tableModel);
		sorter.setRowFilter(containsFilter);
		table.setRowSorter(sorter);
	}

	private List<TeamModel> getSelectedTeams() {
		List<TeamModel> teams = new ArrayList<TeamModel>();
		for (int viewRow : table.getSelectedRows()) {
			int modelRow = table.convertRowIndexToModel(viewRow);
			TeamModel model = tableModel.list.get(modelRow);
			teams.add(model);
		}
		return teams;
	}

	protected void refreshTeams() {
		GitblitWorker worker = new GitblitWorker(TeamsPanel.this, RpcRequest.LIST_TEAMS) {
			@Override
			protected Boolean doRequest() throws IOException {
				gitblit.refreshTeams();
				return true;
			}

			@Override
			protected void onSuccess() {
				updateTable(false);
			}
		};
		worker.execute();
	}

	/**
	 * Displays the create team dialog and fires a SwingWorker to update the
	 * server, if appropriate.
	 * 
	 */
	protected void createTeam() {
		EditTeamDialog dialog = new EditTeamDialog(gitblit.getProtocolVersion(),
				gitblit.getSettings());
		dialog.setLocationRelativeTo(TeamsPanel.this);
		dialog.setTeams(gitblit.getTeams());
		dialog.setRepositories(gitblit.getRepositories(), null);
		dialog.setUsers(gitblit.getUsernames(), null);
		dialog.setPreReceiveScripts(gitblit.getPreReceiveScriptsUnused(null),
				gitblit.getPreReceiveScriptsInherited(null), null);
		dialog.setPostReceiveScripts(gitblit.getPostReceiveScriptsUnused(null),
				gitblit.getPostReceiveScriptsInherited(null), null);
		dialog.setVisible(true);
		final TeamModel newTeam = dialog.getTeam();
		if (newTeam == null) {
			return;
		}

		GitblitWorker worker = new GitblitWorker(this, RpcRequest.CREATE_TEAM) {

			@Override
			protected Boolean doRequest() throws IOException {
				boolean success = gitblit.createTeam(newTeam);
				if (success) {
					gitblit.refreshTeams();
					gitblit.refreshUsers();
				}
				return success;
			}

			@Override
			protected void onSuccess() {
				updateTable(false);
				updateUsersTable();
			}

			@Override
			protected void onFailure() {
				showFailure("Failed to execute request \"{0}\" for team \"{1}\".",
						getRequestType(), newTeam.name);
			}
		};
		worker.execute();
	}

	/**
	 * Displays the edit team dialog and fires a SwingWorker to update the
	 * server, if appropriate.
	 * 
	 * @param user
	 */
	protected void editTeam(final TeamModel team) {
		EditTeamDialog dialog = new EditTeamDialog(gitblit.getProtocolVersion(), team,
				gitblit.getSettings());
		dialog.setLocationRelativeTo(TeamsPanel.this);
		dialog.setTeams(gitblit.getTeams());
		dialog.setRepositories(gitblit.getRepositories(), new ArrayList<String>(team.repositories));
		dialog.setUsers(gitblit.getUsernames(), team.users == null ? null : new ArrayList<String>(
				team.users));
		dialog.setPreReceiveScripts(gitblit.getPreReceiveScriptsUnused(null),
				gitblit.getPreReceiveScriptsInherited(null), team.preReceiveScripts);
		dialog.setPostReceiveScripts(gitblit.getPostReceiveScriptsUnused(null),
				gitblit.getPostReceiveScriptsInherited(null), team.postReceiveScripts);
		dialog.setVisible(true);
		final TeamModel revisedTeam = dialog.getTeam();
		if (revisedTeam == null) {
			return;
		}

		GitblitWorker worker = new GitblitWorker(this, RpcRequest.EDIT_TEAM) {
			@Override
			protected Boolean doRequest() throws IOException {
				boolean success = gitblit.updateTeam(team.name, revisedTeam);
				if (success) {
					gitblit.refreshTeams();
					gitblit.refreshUsers();
				}
				return success;
			}

			@Override
			protected void onSuccess() {
				updateTable(false);
				updateUsersTable();
			}

			@Override
			protected void onFailure() {
				showFailure("Failed to execute request \"{0}\" for team \"{1}\".",
						getRequestType(), team.name);
			}
		};
		worker.execute();
	}

	protected void deleteTeams(final List<TeamModel> teams) {
		if (teams == null || teams.size() == 0) {
			return;
		}
		StringBuilder message = new StringBuilder("Delete the following teams?\n\n");
		for (TeamModel team : teams) {
			message.append(team.name).append("\n");
		}
		int result = JOptionPane.showConfirmDialog(TeamsPanel.this, message.toString(),
				"Delete Teams?", JOptionPane.YES_NO_OPTION);
		if (result == JOptionPane.YES_OPTION) {
			GitblitWorker worker = new GitblitWorker(this, RpcRequest.DELETE_TEAM) {
				@Override
				protected Boolean doRequest() throws IOException {
					boolean success = true;
					for (TeamModel team : teams) {
						success &= gitblit.deleteTeam(team);
					}
					if (success) {
						gitblit.refreshTeams();
						gitblit.refreshUsers();
					}
					return success;
				}

				@Override
				protected void onSuccess() {
					updateTable(false);
					updateUsersTable();
				}

				@Override
				protected void onFailure() {
					showFailure("Failed to delete specified teams!");
				}
			};
			worker.execute();
		}
	}
}
