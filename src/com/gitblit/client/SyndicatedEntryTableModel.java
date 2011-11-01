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

import com.gitblit.models.SyndicatedEntryModel;

/**
 * Table model for a list of retrieved feed entries.
 * 
 * @author James Moger
 * 
 */
public class SyndicatedEntryTableModel extends AbstractTableModel {

	private static final long serialVersionUID = 1L;

	List<SyndicatedEntryModel> entries;

	enum Columns {
		Date, Repository,Author, Message, Branch;

		@Override
		public String toString() {
			return name().replace('_', ' ');
		}
	}

	public SyndicatedEntryTableModel() {
		this(new ArrayList<SyndicatedEntryModel>());
	}

	public SyndicatedEntryTableModel(List<SyndicatedEntryModel> entries) {
		setEntries(entries);
	}

	public void setEntries(List<SyndicatedEntryModel> entries) {
		this.entries = entries;
		Collections.sort(entries);
	}

	@Override
	public int getRowCount() {
		return entries.size();
	}

	@Override
	public int getColumnCount() {
		return Columns.values().length;
	}

	@Override
	public String getColumnName(int column) {
		Columns col = Columns.values()[column];
		switch (col) {
		case Date:
			return Translation.get("gb.date");
		case Repository:
			return Translation.get("gb.repository");
		case Branch:
			return Translation.get("gb.branch");
		case Author:
			return Translation.get("gb.author");
		case Message:
			return Translation.get("gb.message");
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
		if (Columns.Date.ordinal() == columnIndex) {
			return Date.class;
		}
		return String.class;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		SyndicatedEntryModel entry = entries.get(rowIndex);
		Columns col = Columns.values()[columnIndex];
		switch (col) {
		case Date:
			return entry.published;
		case Repository:
			return entry.repository;
		case Branch:
			return entry.branch;
		case Author:
			return entry.author;
		case Message:
			return entry.title;
		}
		return null;
	}

	public SyndicatedEntryModel get(int modelRow) {
		return entries.get(modelRow);
	}
}
