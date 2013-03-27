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

import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.ListCellRenderer;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Branch renderer displays refs/heads and refs/remotes in a color similar to
 * the site.
 * 
 * @author James Moger
 * 
 */
public class BranchRenderer extends DefaultTableCellRenderer implements ListCellRenderer {

	private static final long serialVersionUID = 1L;

	private static final String R_HEADS = "refs/heads/";

	private static final String R_REMOTES = "refs/remotes/";

	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
			boolean hasFocus, int row, int column) {
		super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		setText(value == null ? "" : value.toString());
		if (isSelected) {
			setForeground(table.getSelectionForeground());
		}
		return this;
	}

	@Override
	public Component getListCellRendererComponent(JList list, Object value, int index,
			boolean isSelected, boolean cellHasFocus) {
		setText(value == null ? "" : value.toString());
		if (isSelected) {
			setBackground(list.getSelectionBackground());
			setForeground(list.getSelectionForeground());
		} else {
			setBackground(list.getBackground());
		}
		return this;
	}

	@Override
	public void setText(String text) {
		String name = text;
		Color fg = getForeground();
		if (name.startsWith(R_HEADS)) {
			name = name.substring(R_HEADS.length());
			fg = new Color(0, 0x80, 0);
		} else if (name.startsWith(R_REMOTES)) {
			name = name.substring(R_REMOTES.length());
			fg = Color.decode("#6C6CBF");
		}
		setForeground(fg);
		super.setText(name);
	}
}