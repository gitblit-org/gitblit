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
 * Repository name cell renderer. This renderer shows the group name in a gray
 * color and accentuates the repository name in a cornflower blue color.
 * 
 * @author James Moger
 * 
 */
public class NameRenderer extends DefaultTableCellRenderer implements ListCellRenderer {

	private static final long serialVersionUID = 1L;

	private static final Color CORNFLOWER = new Color(0x00, 0x69, 0xD6);

	private final String groupSpan;

	public NameRenderer() {
		this(Color.gray, CORNFLOWER);
	}

	private NameRenderer(Color group, Color repo) {
		groupSpan = "<span style='color:" + getHexColor(group) + "'>";
		setForeground(repo);
	}

	String getHexColor(Color c) {
		StringBuilder sb = new StringBuilder();
		sb.append(Integer.toHexString((c.getRGB() & 0x00FFFFFF)));
		while (sb.length() < 6)
			sb.insert(0, '0');
		sb.insert(0, '#');
		return sb.toString();
	}

	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
			boolean hasFocus, int row, int column) {
		super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		setValue(value == null ? "" : value, isSelected);
		return this;
	}

	@Override
	public Component getListCellRendererComponent(JList list, Object value, int index,
			boolean isSelected, boolean cellHasFocus) {
		setValue(value == null ? "" : value, isSelected);
		if (isSelected) {
			setBackground(list.getSelectionBackground());
			setForeground(list.getSelectionForeground());
		} else {
			setBackground(list.getBackground());
			setForeground(CORNFLOWER);
		}
		return this;
	}

	private void setValue(Object value, boolean isSelected) {
		String name = value.toString();
		int lastSlash = name.lastIndexOf('/');
		if (!isSelected && lastSlash > -1) {
			String group = name.substring(0, lastSlash + 1);
			String repo = name.substring(lastSlash + 1);
			setText("<html><body>" + groupSpan + group + "</span>" + repo);
		} else {
			this.setText(name);
		}
	}
}