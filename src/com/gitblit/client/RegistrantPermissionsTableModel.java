/*
 * Copyright 2012 gitblit.com.
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
import java.util.List;

import javax.swing.table.AbstractTableModel;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.models.RegistrantAccessPermission;

/**
 * Table model of a registrant permissions.
 * 
 * @author James Moger
 * 
 */
public class RegistrantPermissionsTableModel extends AbstractTableModel {

	private static final long serialVersionUID = 1L;

	List<RegistrantAccessPermission> permissions;

	enum Columns {
		Registrant, Permission;

		@Override
		public String toString() {
			return name().replace('_', ' ');
		}
	}

	public RegistrantPermissionsTableModel() {
		this(new ArrayList<RegistrantAccessPermission>());
	}

	public RegistrantPermissionsTableModel(List<RegistrantAccessPermission> list) {
		setPermissions(list);
	}

	public void setPermissions(List<RegistrantAccessPermission> list) {
		this.permissions = list;
	}

	@Override
	public int getRowCount() {
		return permissions.size();
	}

	@Override
	public int getColumnCount() {
		return Columns.values().length;
	}

	@Override
	public String getColumnName(int column) {
		Columns col = Columns.values()[column];
		switch (col) {
		case Registrant:
			return Translation.get("gb.name");
		case Permission:
			return Translation.get("gb.permission");
		}
		return "";
	}

	/**
	 * Returns <code>Object.class</code> regardless of <code>columnIndex</code>.
	 * 
	 * @param columnIndex
	 *            the column being queried
	 * @return the Object.class
	 */
	public Class<?> getColumnClass(int columnIndex) {
		if (columnIndex == Columns.Permission.ordinal()) {
			return AccessPermission.class;
		}
		return String.class;
	}
	
	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		return columnIndex == Columns.Permission.ordinal();
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		RegistrantAccessPermission rp = permissions.get(rowIndex);
		Columns col = Columns.values()[columnIndex];
		switch (col) {
		case Registrant:
			return rp.registrant;
		case Permission:
			return rp.permission;
		}
		return null;
	}
	
	@Override
	public void setValueAt(Object o, int rowIndex, int columnIndex) {
		RegistrantAccessPermission rp = permissions.get(rowIndex);
		if (columnIndex == Columns.Permission.ordinal()) {
			rp.permission = (AccessPermission) o;
		}
	}
}
