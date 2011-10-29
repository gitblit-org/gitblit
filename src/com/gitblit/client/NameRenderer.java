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

import javax.swing.ImageIcon;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import com.gitblit.models.RepositoryModel;

/**
 * Repository name cell renderer. This renderer shows the group name in a gray
 * color and accentuates the repository name in a cornflower blue color.
 * 
 * @author James Moger
 * 
 */
public class NameRenderer extends DefaultTableCellRenderer {

	private static final long serialVersionUID = 1L;

	final String groupSpan;

	private final boolean displayIcon;

	private final ImageIcon blankIcon;

	private final ImageIcon subscribedIcon;

	public NameRenderer() {
		this(false);
	}

	public NameRenderer(boolean showIcon) {
		this(Color.gray, new Color(0x00, 0x69, 0xD6), showIcon);
	}

	private NameRenderer(Color group, Color repo, boolean showIcon) {
		blankIcon = new ImageIcon(getClass().getResource("/blank.png"));
		subscribedIcon = new ImageIcon(getClass().getResource("/bullet_feed.png"));
		displayIcon = showIcon;
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
		if (value instanceof RepositoryModel) {
			RepositoryModel model = (RepositoryModel) value;
			String name = value.toString();
			int lastSlash = name.lastIndexOf('/');
			if (!isSelected && lastSlash > -1) {
				String group = name.substring(0, lastSlash + 1);
				String repo = name.substring(lastSlash + 1);
				setText("<html><body>" + groupSpan + group + "</span>" + repo);
			} else {
				this.setText(name);
			}
			if (displayIcon) {
				if (model.subscribed) {
					setIcon(subscribedIcon);
				} else {
					setIcon(blankIcon);
				}
			}
		} else {
			this.setText(value.toString());
		}
		return this;
	}
}