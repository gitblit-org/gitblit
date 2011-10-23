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
import java.util.Date;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import com.gitblit.models.RepositoryModel;

/**
 * Table model of a list of repositories.
 * 
 * @author James Moger
 * 
 */
public class RepositoriesTableModel extends AbstractTableModel {

	private static final long serialVersionUID = 1L;

	List<RepositoryModel> list;

	enum Columns {
		Name, Description, Owner, Indicators, Last_Change, Size;

		@Override
		public String toString() {
			return name().replace('_', ' ');
		}
	}

	public RepositoriesTableModel() {
		this(new ArrayList<RepositoryModel>());
	}

	public RepositoriesTableModel(List<RepositoryModel> repositories) {
		this.list = repositories;
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
		case Description:
			return Translation.get("gb.description");
		case Owner:
			return Translation.get("gb.owner");
		case Last_Change:
			return Translation.get("gb.lastChange");
		case Size:
			return Translation.get("gb.size");
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
		Columns col = Columns.values()[columnIndex];
		switch (col) {
		case Name:
		case Indicators:
			return RepositoryModel.class;
		case Last_Change:
			return Date.class;
		}
		return String.class;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		RepositoryModel model = list.get(rowIndex);
		Columns col = Columns.values()[columnIndex];
		switch (col) {
		case Name:
			return model;
		case Description:
			return model.description;
		case Owner:
			return model.owner;
		case Indicators:
			return model;
		case Last_Change:
			return model.lastChange;
		case Size:
			if (model.hasCommits) {
				return model.size;
			}
			return "(empty)";
		}
		return null;
	}
}
