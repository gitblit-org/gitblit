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

import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.swing.table.AbstractTableModel;

/**
 * Table model of a list of Gitblit server registrations.
 * 
 * @author James Moger
 * 
 */
public class RegistrationsTableModel extends AbstractTableModel {

	private static final long serialVersionUID = 1L;

	List<GitblitRegistration> list;

	enum Columns {
		Name, URL, Last_Login;

		@Override
		public String toString() {
			return name().replace('_', ' ');
		}
	}

	public RegistrationsTableModel(List<GitblitRegistration> list) {
		this.list = list;
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
		case URL:
			return Translation.get("gb.url");
		case Last_Login:
			return Translation.get("gb.lastLogin");
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
		if (columnIndex == Columns.Last_Login.ordinal()) {
			return Date.class;
		}
		return String.class;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		GitblitRegistration model = list.get(rowIndex);
		Columns col = Columns.values()[columnIndex];
		switch (col) {
		case Name:
			return model.name;
		case URL:
			return model.url;
		case Last_Login:
			return model.lastLogin;
		}
		return null;
	}
}
