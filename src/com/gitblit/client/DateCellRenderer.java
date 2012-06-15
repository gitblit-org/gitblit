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
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;

import com.gitblit.utils.TimeUtils;

/**
 * Time ago cell renderer with real date tooltip.
 * 
 * @author James Moger
 * 
 */
public class DateCellRenderer extends DefaultTableCellRenderer {

	private static final long serialVersionUID = 1L;

	private final String pattern;
	
	public DateCellRenderer(String pattern, Color foreground) {
		this.pattern = (pattern == null ? "yyyy-MM-dd HH:mm" : pattern);
		setForeground(foreground);
		setHorizontalAlignment(SwingConstants.CENTER);
	}

	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
			boolean hasFocus, int row, int column) {
		super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		if (value instanceof Date) {
			Date date = (Date) value;
			String title;
			String dateString;
			if (date.getTime() == 0) {
				title = "--";
				dateString = "never";
			} else {
				title = Translation.getTimeUtils().timeAgo(date);
				dateString = new SimpleDateFormat(pattern).format((Date) value);
			}

			if ((System.currentTimeMillis() - date.getTime()) > 10 * 24 * 60 * 60 * 1000L) {
				String tmp = dateString;
				dateString = title;
				title = tmp;
			}
			this.setText(title);
			this.setToolTipText(dateString);
		}
		return this;
	}
}