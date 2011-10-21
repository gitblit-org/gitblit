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
import java.awt.FlowLayout;
import java.io.Serializable;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

import com.gitblit.models.RepositoryModel;

/**
 * Renders the type indicators (tickets, frozen, access restriction, etc) in a
 * single cell.
 * 
 * @author James Moger
 * 
 */
public class IndicatorsRenderer extends JPanel implements TableCellRenderer, Serializable {

	private static final long serialVersionUID = 1L;

	private final ImageIcon blankIcon;

	private final ImageIcon pushIcon;

	private final ImageIcon pullIcon;

	private final ImageIcon viewIcon;

	private final ImageIcon tixIcon;

	private final ImageIcon doxIcon;

	private final ImageIcon frozenIcon;

	private final ImageIcon federatedIcon;

	public IndicatorsRenderer() {
		super(new FlowLayout(FlowLayout.RIGHT, 1, 0));
		blankIcon = new ImageIcon(getClass().getResource("/blank.png"));
		pushIcon = new ImageIcon(getClass().getResource("/lock_go_16x16.png"));
		pullIcon = new ImageIcon(getClass().getResource("/lock_pull_16x16.png"));
		viewIcon = new ImageIcon(getClass().getResource("/shield_16x16.png"));
		tixIcon = new ImageIcon(getClass().getResource("/bug_16x16.png"));
		doxIcon = new ImageIcon(getClass().getResource("/book_16x16.png"));
		frozenIcon = new ImageIcon(getClass().getResource("/cold_16x16.png"));
		federatedIcon = new ImageIcon(getClass().getResource("/federated_16x16.png"));
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
			boolean hasFocus, int row, int column) {
		if (isSelected)
			setBackground(table.getSelectionBackground());
		else
			setBackground(table.getBackground());
		removeAll();
		if (value instanceof RepositoryModel) {
			StringBuilder tooltip = new StringBuilder();
			RepositoryModel model = (RepositoryModel) value;
			if (model.useTickets) {
				JLabel icon = new JLabel(tixIcon);
				tooltip.append(Translation.get("gb.tickets")).append("<br/>");
				add(icon);
			}
			if (model.useDocs) {
				JLabel icon = new JLabel(doxIcon);
				tooltip.append(Translation.get("gb.docs")).append("<br/>");
				add(icon);
			}
			if (model.isFrozen) {
				JLabel icon = new JLabel(frozenIcon);
				tooltip.append(Translation.get("gb.isFrozen")).append("<br/>");
				add(icon);
			}
			if (model.isFederated) {
				JLabel icon = new JLabel(federatedIcon);
				tooltip.append(Translation.get("gb.isFederated")).append("<br/>");
				add(icon);
			}

			switch (model.accessRestriction) {
			case NONE: {
				add(new JLabel(blankIcon));
				break;
			}
			case PUSH: {
				JLabel icon = new JLabel(pushIcon);
				tooltip.append(Translation.get("gb.pushRestricted")).append("<br/>");
				add(icon);
				break;
			}
			case CLONE: {
				JLabel icon = new JLabel(pullIcon);
				tooltip.append(Translation.get("gb.cloneRestricted")).append("<br/>");
				add(icon);
				break;
			}
			case VIEW: {
				JLabel icon = new JLabel(viewIcon);
				tooltip.append(Translation.get("gb.viewRestricted")).append("<br/>");
				add(icon);
				break;
			}
			default:
				add(new JLabel(blankIcon));
			}
			if (tooltip.length() > 0) {
				tooltip.insert(0, "<html><body>");
				setToolTipText(tooltip.toString().trim());
			}
		}
		return this;
	}
}