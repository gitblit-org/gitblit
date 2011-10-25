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

import com.gitblit.models.ServerSettings;
import com.gitblit.models.SettingModel;

/**
 * Table model of Map<String, SettingModel>.
 * 
 * @author James Moger
 * 
 */
public class SettingsTableModel extends AbstractTableModel {

	private static final long serialVersionUID = 1L;

	ServerSettings settings;

	List<String> keys;

	enum Columns {
		Name, Value, Since;

		@Override
		public String toString() {
			return name().replace('_', ' ');
		}
	}

	public SettingsTableModel() {
		this(null);
	}

	public SettingsTableModel(ServerSettings settings) {
		setSettings(settings);
	}

	public void setSettings(ServerSettings settings) {
		this.settings = settings;
		if (settings == null) {
			keys = new ArrayList<String>();
		} else {
			keys = new ArrayList<String>(settings.getKeys());
			Collections.sort(keys);
		}
	}

	@Override
	public int getRowCount() {
		return keys.size();
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
		case Since:
			return Translation.get("gb.since");
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
		if (Columns.Value.ordinal() == columnIndex) {
			return SettingModel.class;
		}
		return String.class;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		String key = keys.get(rowIndex);
		SettingModel setting = settings.get(key);
		Columns col = Columns.values()[columnIndex];
		switch (col) {
		case Name:
			return key;
		case Value:
			return setting;
		case Since:
			return setting.since;
		}
		return null;
	}

	public SettingModel get(int modelRow) {
		String key = keys.get(modelRow);
		return settings.get(key);
	}
}
