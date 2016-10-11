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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import com.gitblit.models.UserModel;

/**
 * Table model of a list of users.
 *
 * @author James Moger
 *
 */
public class UsersTableModel extends AbstractTableModel {

  private static final long serialVersionUID = 1L;

  List<UserModel> list;

  enum Columns {
    Name, Display_Name, Type, Teams, Repositories;

    @Override
    public String toString() {
      return name().replace('_', ' ');
    }
  }

  public UsersTableModel() {
    this(new ArrayList<UserModel>());
  }

  public UsersTableModel(List<UserModel> users) {
    this.list = users;
    Collections.sort(this.list);
  }

  @Override
  public int getRowCount() {
    return list.size();
  }

  @Override
  public int getColumnCount() {
    return Columns.values().length;
  }

  @Override
  public String getColumnName(int column) {
    Columns col = Columns.values()[column];
    switch (col) {
    case Name:
      return Translation.get("gb.name");
    case Display_Name:
      return Translation.get("gb.displayName");
    case Type:
      return Translation.get("gb.type");
    case Teams:
      return Translation.get("gb.teamMemberships");
    case Repositories:
      return Translation.get("gb.repositories");
    }
    return "";
  }

  /**
   * Returns <code>Object.class</code> regardless of <code>columnIndex</code>.
   *
   * @param columnIndex the column being queried
   * @return the Object.class
   */
  @Override
  public Class<?> getColumnClass(int columnIndex) {
    return String.class;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    UserModel model = list.get(rowIndex);
    Columns col = Columns.values()[columnIndex];
    switch (col) {
    case Name:
      return model.username;
    case Display_Name:
      return model.displayName;
    case Type:
      StringBuilder sb = new StringBuilder();
      if (model.accountType != null) {
        sb.append(model.accountType.name());
      }
      if (model.canAdmin()) {
        if (sb.length() > 0) {
          sb.append(", ");
        }
        sb.append("admin");
      }
      return sb.toString();
    case Teams:
      return (model.getTeams() == null || model.getTeams().size() == 0) ? "" : String.valueOf(model.getTeamSize());
    case Repositories:
      return (model.permissions == null || model.permissions.size() == 0) ? "" : String.valueOf(model.permissions.size());
    }
    return null;
  }
}
