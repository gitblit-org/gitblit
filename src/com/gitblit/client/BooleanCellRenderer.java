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

import java.awt.Component;
import java.io.Serializable;

import javax.swing.JCheckBox;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellRenderer;

/**
 * Boolean checkbox cell renderer.
 * 
 * @author James Moger
 * 
 */
public class BooleanCellRenderer extends JCheckBox implements TableCellRenderer, Serializable {

	private static final long serialVersionUID = 1L;

	public BooleanCellRenderer() {
		super();
		setOpaque(false);
		setHorizontalAlignment(SwingConstants.CENTER);
	}

	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
			boolean hasFocus, int row, int column) {
		if (value instanceof Boolean) {
			boolean checked = (Boolean) value;
			this.setSelected(checked);
		}
		return this;
	}
}