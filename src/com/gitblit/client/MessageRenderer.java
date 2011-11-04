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
import java.awt.Font;
import java.io.Serializable;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.table.TableCellRenderer;

import com.gitblit.models.SyndicatedEntryModel;

/**
 * Message renderer displays the short log message and then any refs in a style
 * like the site.
 * 
 * @author James Moger
 * 
 */
public class MessageRenderer extends JPanel implements TableCellRenderer, Serializable {

	private static final long serialVersionUID = 1L;

	private static final String R_TAGS = "refs/tags/";

	private static final String R_HEADS = "refs/heads/";

	private static final String R_REMOTES = "refs/remotes/";

	private final GitblitClient gitblit;

	private final JLabel messageLabel;

	private final JLabel branchLabel;

	public MessageRenderer() {
		this(null);
	}

	public MessageRenderer(GitblitClient gitblit) {
		super(new FlowLayout(FlowLayout.LEFT, 10, 1));
		this.gitblit = gitblit;

		messageLabel = new JLabel();
		branchLabel = new JLabel();
		branchLabel.setOpaque(true);
		Font font = branchLabel.getFont();
		branchLabel.setFont(font.deriveFont(font.getSize2D() - 1f));
		add(messageLabel);
		add(branchLabel);
	}

	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
			boolean hasFocus, int row, int column) {
		if (isSelected)
			setBackground(table.getSelectionBackground());
		else
			setBackground(table.getBackground());
		messageLabel.setForeground(isSelected ? table.getSelectionForeground() : table
				.getForeground());
		SyndicatedEntryModel entry = (SyndicatedEntryModel) value;

		if (gitblit == null) {
			// no gitblit client, just display message
			messageLabel.setText(entry.title);
		} else {
			// show message in BOLD if its a new entry
			if (entry.published.after(gitblit.getLastFeedRefresh(entry.repository, entry.branch))) {
				messageLabel.setText("<html><body><b>" + entry.title);
			} else {
				messageLabel.setText(entry.title);
			}
		}

		// reset ref label
		branchLabel.setText("");
		branchLabel.setBackground(messageLabel.getBackground());
		branchLabel.setBorder(null);

		if (entry.tags != null) {
			for (String tag : entry.tags) {
				if (tag.equals(entry.branch)) {
					continue;
				}
				String name = tag;
				Color bg = getBackground();
				Border border = null;
				if (name.startsWith(R_HEADS)) {
					// local branch
					bg = Color.decode("#CCFFCC");
					name = name.substring(R_HEADS.length());
					border = new LineBorder(Color.decode("#00CC33"), 1);
				} else if (name.startsWith(R_REMOTES)) {
					// origin branch
					bg = Color.decode("#CAC2F5");
					name = name.substring(R_REMOTES.length());
					border = new LineBorder(Color.decode("#6C6CBF"), 1);
				} else if (name.startsWith(R_TAGS)) {
					// tag
					bg = Color.decode("#FFFFAA");
					name = name.substring(R_TAGS.length());
					border = new LineBorder(Color.decode("#FFCC00"), 1);
				} else if (name.equals("HEAD")) {
					// HEAD
					bg = Color.decode("#FFAAFF");
					border = new LineBorder(Color.decode("#FF00EE"), 1);
				} else {

				}
				branchLabel.setText(" " + name + " ");
				branchLabel.setBackground(bg);
				branchLabel.setBorder(border);
			}
		}

		return this;
	}
}