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
import java.awt.FlowLayout;
import java.io.Serializable;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.table.TableCellRenderer;

/**
 * Branch renderer displays refs/heads and refs/remotes in a style like the
 * site.
 * 
 * @author James Moger
 * 
 */
public class BranchRenderer extends JPanel implements TableCellRenderer, Serializable {

	private static final long serialVersionUID = 1L;

	private static final String R_HEADS = "refs/heads/";

	private static final String R_REMOTES = "refs/remotes/";

	private JLabel branchLabel;

	public BranchRenderer() {
		super(new FlowLayout(FlowLayout.CENTER, 0, 1));
		branchLabel = new JLabel();
		branchLabel.setOpaque(true);
		add(branchLabel);
	}

	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
			boolean hasFocus, int row, int column) {
		if (isSelected)
			setBackground(table.getSelectionBackground());
		else
			setBackground(table.getBackground());

		String name = value.toString();
		Color bg = getBackground();
		Border border = null;
		if (name.startsWith(R_HEADS)) {
			bg = Color.decode("#CCFFCC");
			name = name.substring(R_HEADS.length());
			border = new LineBorder(Color.decode("#00CC33"), 1);
		} else if (name.startsWith(R_REMOTES)) {
			bg = Color.decode("#CAC2F5");
			name = name.substring(R_REMOTES.length());
			border = new LineBorder(Color.decode("#6C6CBF"), 1);
		}
		branchLabel.setText(" " + name + " ");
		branchLabel.setBackground(bg);
		branchLabel.setBorder(border);
		return this;
	}
}