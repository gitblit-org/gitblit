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
package com.gitblit.authority;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import com.gitblit.client.Translation;

/**
 * Table model of a list of user certificate models.
 *
 * @author James Moger
 *
 */
public class UserCertificateTableModel extends AbstractTableModel {

	private static final long serialVersionUID = 1L;

	List<UserCertificateModel> list;

	enum Columns {
		Username, DisplayName, Status, Expires;

		@Override
		public String toString() {
			return name().replace('_', ' ');
		}
	}

	public UserCertificateTableModel() {
		this(new ArrayList<UserCertificateModel>());
	}

	public UserCertificateTableModel(List<UserCertificateModel> list) {
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
		case Username:
			return Translation.get("gb.username");
		case DisplayName:
			return Translation.get("gb.displayName");
		case Expires:
			return Translation.get("gb.expires");
		case Status:
			return Translation.get("gb.status");
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
	@Override
	public Class<?> getColumnClass(int columnIndex) {
		Columns col = Columns.values()[columnIndex];
		switch (col) {
		case Expires:
			return Date.class;
		case Status:
			return CertificateStatus.class;
		default:
			return String.class;
		}
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		Columns col = Columns.values()[columnIndex];
		switch (col) {
		default:
			return false;
		}
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		UserCertificateModel model = list.get(rowIndex);
		Columns col = Columns.values()[columnIndex];
		switch (col) {
		case Username:
			return model.user.username;
		case DisplayName:
			return model.user.getDisplayName();
		case Expires:
			return model.expires;
		case Status:
			return model.getStatus();
		}
		return null;
	}

	public UserCertificateModel get(int modelRow) {
		return list.get(modelRow);
	}
}
