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

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.table.TableCellRenderer;

import org.eclipse.jgit.lib.Constants;

import com.gitblit.models.FeedEntryModel;

/**
 * Message renderer displays the short log message and then any refs in a style
 * like the site.
 * 
 * @author James Moger
 * 
 */
public class MessageRenderer extends JPanel implements TableCellRenderer, Serializable {

	private static final long serialVersionUID = 1L;

	private final GitblitClient gitblit;
	
	private final ImageIcon mergeIcon;
	
	private final ImageIcon blankIcon;
	
	private final JLabel messageLabel;

	private final JLabel headLabel;

	private final JLabel branchLabel;

	private final JLabel remoteLabel;

	private final JLabel tagLabel;

	public MessageRenderer() {
		this(null);
	}

	public MessageRenderer(GitblitClient gitblit) {
		super(new FlowLayout(FlowLayout.LEFT, Utils.MARGIN, 1));
		this.gitblit = gitblit;
	
		mergeIcon = new ImageIcon(getClass().getResource("/commit_merge_16x16.png"));
		blankIcon = new ImageIcon(getClass().getResource("/blank.png"));

		messageLabel = new JLabel();
	
		headLabel = newRefLabel();
		branchLabel = newRefLabel();
		remoteLabel = newRefLabel();
		tagLabel = newRefLabel();

		add(messageLabel);
		add(headLabel);
		add(branchLabel);
		add(remoteLabel);
		add(tagLabel);
	}

	private JLabel newRefLabel() {		
		JLabel label = new JLabel();
		label.setOpaque(true);
		Font font = label.getFont();
		label.setFont(font.deriveFont(font.getSize2D() - 1f));
		return label;
	}

	private void resetRef(JLabel label) {
		label.setText("");
		label.setBackground(messageLabel.getBackground());
		label.setBorder(null);
		label.setVisible(false);
	}

	private void showRef(String ref, JLabel label) {
		String name = ref;
		Color bg = getBackground();
		Border border = null;
		if (name.startsWith(Constants.R_HEADS)) {
			// local branch
			bg = Color.decode("#CCFFCC");
			name = name.substring(Constants.R_HEADS.length());
			border = new LineBorder(Color.decode("#00CC33"), 1);
		} else if (name.startsWith(Constants.R_REMOTES)) {
			// remote branch
			bg = Color.decode("#CAC2F5");
			name = name.substring(Constants.R_REMOTES.length());
			border = new LineBorder(Color.decode("#6C6CBF"), 1);
		} else if (name.startsWith(Constants.R_TAGS)) {
			// tag
			bg = Color.decode("#FFFFAA");
			name = name.substring(Constants.R_TAGS.length());
			border = new LineBorder(Color.decode("#FFCC00"), 1);
		} else if (name.equals(Constants.HEAD)) {
			// HEAD
			bg = Color.decode("#FFAAFF");
			border = new LineBorder(Color.decode("#FF00EE"), 1);
		} else {
		}
		label.setText(name);
		label.setBackground(bg);
		label.setBorder(border);
		label.setVisible(true);
	}

	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
			boolean hasFocus, int row, int column) {
		if (isSelected)
			setBackground(table.getSelectionBackground());
		else
			setBackground(table.getBackground());
		messageLabel.setForeground(isSelected ? table.getSelectionForeground() : table
				.getForeground());
		if (value == null) {
			return this;
		}
		FeedEntryModel entry = (FeedEntryModel) value;

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
		resetRef(headLabel);
		resetRef(branchLabel);
		resetRef(remoteLabel);
		resetRef(tagLabel);

		int parentCount = 0;
		if (entry.tags != null) {
			for (String tag : entry.tags) {
				if (tag.startsWith("ref:")) {
					// strip ref:
					tag = tag.substring("ref:".length());
				} else {
					// count parents
					if (tag.startsWith("parent:")) {
						parentCount++;
					}
				}
				if (tag.equals(entry.branch)) {
					// skip current branch label
					continue;
				}
				if (tag.startsWith(Constants.R_HEADS)) {
					// local branch
					showRef(tag, branchLabel);
				} else if (tag.startsWith(Constants.R_REMOTES)) {
					// remote branch
					showRef(tag, remoteLabel);
				} else if (tag.startsWith(Constants.R_TAGS)) {
					// tag
					showRef(tag, tagLabel);
				} else if (tag.equals(Constants.HEAD)) {
					// HEAD
					showRef(tag, headLabel);
				}
			}
		}

		if (parentCount > 1) {
			// multiple parents, show merge icon
			messageLabel.setIcon(mergeIcon);
		} else {
			messageLabel.setIcon(blankIcon);
		}
		return this;
	}
}