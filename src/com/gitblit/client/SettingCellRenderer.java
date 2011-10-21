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

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import com.gitblit.models.SettingModel;

/**
 * SettingModel cell renderer that indicates if a setting is the default or
 * modified.
 * 
 * @author James Moger
 * 
 */
public class SettingCellRenderer extends DefaultTableCellRenderer {

	private static final long serialVersionUID = 1L;

	private final Font defaultFont;

	private final Font modifiedFont;

	public SettingCellRenderer() {
		defaultFont = getFont();
		modifiedFont = defaultFont.deriveFont(Font.BOLD);
	}

	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
			boolean hasFocus, int row, int column) {
		super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		if (value instanceof SettingModel) {
			SettingModel setting = (SettingModel) value;
			if (setting.isDefaultValue()) {
				this.setFont(defaultFont);
				if (!isSelected) {
					this.setForeground(Color.BLACK);
				}
			} else {
				this.setFont(modifiedFont);
				if (!isSelected) {
					this.setForeground(Color.BLUE);
				}
			}
			this.setText(setting.getString(""));
		}
		return this;
	}
}