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
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;

/**
 * Users panel displays a list of user accounts and allows management of those accounts.
 *
 * @author James Moger
 *
 */
public abstract class UsersPanel extends JPanel {

  private static final long serialVersionUID = 1L;

  private final GitblitClient gitblit;

  private HeaderPanel header;

  private JTable table;

  private UsersTableModel tableModel;

  private TableRowSorter<UsersTableModel> defaultSorter;

  private JTextField filterTextfield;

  public UsersPanel(GitblitClient gitblit) {
    super();
    this.gitblit = gitblit;
    initialize();
  }

  private void initialize() {
    JButton refreshUsers = new JButton(Translation.get("gb.refresh"));
    refreshUsers.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        refreshUsers();
      }
    });

    JButton createUser = new JButton(Translation.get("gb.create"));
    createUser.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        createUser();
      }
    });

    final JButton editUser = new JButton(Translation.get("gb.edit"));
    editUser.setEnabled(false);
    editUser.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        editUser(getSelectedUsers().get(0));
      }
    });

    final JButton delUser = new JButton(Translation.get("gb.delete"));
    delUser.setEnabled(false);
    delUser.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        deleteUsers(getSelectedUsers());
      }
    });

    NameRenderer nameRenderer = new NameRenderer();
    tableModel = new UsersTableModel();
    defaultSorter = new TableRowSorter<UsersTableModel>(tableModel);
    table = Utils.newTable(tableModel, Utils.DATE_FORMAT);
    String name = table.getColumnName(UsersTableModel.Columns.Name.ordinal());
    table.getColumn(name).setCellRenderer(nameRenderer);

    int w = 130;
    name = table.getColumnName(UsersTableModel.Columns.Type.ordinal());
    table.getColumn(name).setMinWidth(w);
    table.getColumn(name).setMaxWidth(w);
    name = table.getColumnName(UsersTableModel.Columns.Teams.ordinal());
    table.getColumn(name).setMinWidth(w);
    table.getColumn(name).setMaxWidth(w);
    name = table.getColumnName(UsersTableModel.Columns.Repositories.ordinal());
    table.getColumn(name).setMinWidth(w);
    table.getColumn(name).setMaxWidth(w);

    table.setRowSorter(defaultSorter);
    table.getRowSorter().toggleSortOrder(UsersTableModel.Columns.Name.ordinal());
    table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }
        boolean selected = table.getSelectedRow() > -1;
        boolean singleSelection = table.getSelectedRows().length == 1;
        editUser.setEnabled(singleSelection && selected);
        delUser.setEnabled(selected);
      }
    });

    table.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          editUser(getSelectedUsers().get(0));
        }
      }
    });

    filterTextfield = new JTextField();
    filterTextfield.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        filterUsers(filterTextfield.getText());
      }
    });
    filterTextfield.addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        filterUsers(filterTextfield.getText());
      }
    });

    JPanel userFilterPanel = new JPanel(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
    userFilterPanel.add(new JLabel(Translation.get("gb.filter")), BorderLayout.WEST);
    userFilterPanel.add(filterTextfield, BorderLayout.CENTER);

    JPanel userTablePanel = new JPanel(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
    userTablePanel.add(userFilterPanel, BorderLayout.NORTH);
    userTablePanel.add(new JScrollPane(table), BorderLayout.CENTER);

    JPanel userControls = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
    userControls.add(refreshUsers);
    userControls.add(createUser);
    userControls.add(editUser);
    userControls.add(delUser);

    setLayout(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
    header = new HeaderPanel(Translation.get("gb.users"), "user_16x16.png");
    add(header, BorderLayout.NORTH);
    add(userTablePanel, BorderLayout.CENTER);
    add(userControls, BorderLayout.SOUTH);
  }

  @Override
  public void requestFocus() {
    filterTextfield.requestFocus();
  }

  @Override
  public Insets getInsets() {
    return Utils.INSETS;
  }

  protected abstract void updateTeamsTable();

  protected void updateTable(boolean pack) {
    tableModel.list.clear();
    tableModel.list.addAll(gitblit.getUsers());
    tableModel.fireTableDataChanged();
    header.setText(Translation.get("gb.users") + " (" + gitblit.getUsers().size() + ")");
    if (pack) {
      Utils.packColumns(table, Utils.MARGIN);
    }
  }

  private void filterUsers(final String fragment) {
    if (StringUtils.isEmpty(fragment)) {
      table.setRowSorter(defaultSorter);
      return;
    }
    RowFilter<UsersTableModel, Object> containsFilter = new RowFilter<UsersTableModel, Object>() {
      @Override
      public boolean include(Entry<? extends UsersTableModel, ? extends Object> entry) {
        for (int i = entry.getValueCount() - 1; i >= 0; i--) {
          if (entry.getStringValue(i).toLowerCase().contains(fragment.toLowerCase())) {
            return true;
          }
        }
        return false;
      }
    };
    TableRowSorter<UsersTableModel> sorter = new TableRowSorter<UsersTableModel>(tableModel);
    sorter.setRowFilter(containsFilter);
    table.setRowSorter(sorter);
  }

  private List<UserModel> getSelectedUsers() {
    List<UserModel> users = new ArrayList<UserModel>();
    for (int viewRow : table.getSelectedRows()) {
      int modelRow = table.convertRowIndexToModel(viewRow);
      UserModel model = tableModel.list.get(modelRow);
      users.add(model);
    }
    return users;
  }

  protected void refreshUsers() {
    GitblitWorker worker = new GitblitWorker(UsersPanel.this, RpcRequest.LIST_USERS) {
      @Override
      protected Boolean doRequest() throws IOException {
        gitblit.refreshUsers();
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
   * Displays the create user dialog and fires a SwingWorker to update the server, if appropriate.
   *
   */
  protected void createUser() {
    EditUserDialog dialog = new EditUserDialog(gitblit.getProtocolVersion(), gitblit.getSettings());
    dialog.setLocationRelativeTo(UsersPanel.this);
    dialog.setUsers(gitblit.getUsers());
    dialog.setRepositories(gitblit.getRepositories(), null);
    dialog.setTeams(gitblit.getTeams(), null);
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
          if (newUser.getTeamSize() > 0) {
            gitblit.refreshTeams();
          }
        }
        return success;
      }

      @Override
      protected void onSuccess() {
        updateTable(false);
        if (newUser.getTeamSize() > 0) {
          updateTeamsTable();
        }
      }

      @Override
      protected void onFailure() {
        showFailure("Failed to execute request \"{0}\" for user \"{1}\".", getRequestType(), newUser.username);
      }
    };
    worker.execute();
  }

  /**
   * Displays the edit user dialog and fires a SwingWorker to update the server, if appropriate.
   *
   * @param user
   */
  protected void editUser(final UserModel user) {
    EditUserDialog dialog = new EditUserDialog(gitblit.getProtocolVersion(), user, gitblit.getSettings());
    dialog.setLocationRelativeTo(UsersPanel.this);
    dialog.setUsers(gitblit.getUsers());
    dialog.setRepositories(gitblit.getRepositories(), gitblit.getUserAccessPermissions(user));
    dialog.setTeams(gitblit.getTeams(), user.getTeams() == null ? null : user.getTeams() );
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
          gitblit.refreshTeams();
        }
        return success;
      }

      @Override
      protected void onSuccess() {
        updateTable(false);
        updateTeamsTable();
      }

      @Override
      protected void onFailure() {
        showFailure("Failed to execute request \"{0}\" for user \"{1}\".", getRequestType(), user.username);
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
    int result = JOptionPane.showConfirmDialog(UsersPanel.this, message.toString(), "Delete Users?", JOptionPane.YES_NO_OPTION);
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
            gitblit.refreshTeams();
          }
          return success;
        }

        @Override
        protected void onSuccess() {
          updateTable(false);
          updateTeamsTable();
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
