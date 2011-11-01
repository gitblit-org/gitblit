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

import javax.swing.ImageIcon;
import javax.swing.JTable;

import com.gitblit.models.RepositoryModel;

/**
 * Displays a subscribed icon on the left of the repository name, if there is at
 * least one subscribed branch.
 * 
 * @author James Moger
 * 
 */
public class SubscribedRepositoryRenderer extends NameRenderer {

	private static final long serialVersionUID = 1L;

	private final GitblitClient gitblit;

	private final ImageIcon blankIcon;

	private final ImageIcon subscribedIcon;

	public SubscribedRepositoryRenderer(GitblitClient gitblit) {
		super();
		this.gitblit = gitblit;
		blankIcon = new ImageIcon(getClass().getResource("/blank.png"));
		subscribedIcon = new ImageIcon(getClass().getResource("/bullet_feed.png"));
	}

	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
			boolean hasFocus, int row, int column) {
		super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		if (value instanceof RepositoryModel) {
			RepositoryModel model = (RepositoryModel) value;
			if (gitblit.isSubscribed(model)) {
				setIcon(subscribedIcon);
			} else {
				setIcon(blankIcon);
			}
		}
		return this;
	}
}
