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
package com.gitblit.authority;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

import com.gitblit.client.DateCellRenderer;
import com.gitblit.client.Translation;
import com.gitblit.utils.StringUtils;

public class Utils {

	public final static int LABEL_WIDTH = 175;

	public final static int MARGIN = 5;

	public final static Insets INSETS = new Insets(MARGIN, MARGIN, MARGIN, MARGIN);

	public final static String TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm";

	public final static String DATE_FORMAT = "yyyy-MM-dd";

	public static JTable newTable(TableModel model, String datePattern) {
		JTable table = new JTable(model);
		table.setRowHeight(table.getFont().getSize() + 8);
		table.setCellSelectionEnabled(false);
		table.setRowSelectionAllowed(true);
		table.getTableHeader().setReorderingAllowed(false);
		table.setGridColor(new Color(0xd9d9d9));
		table.setBackground(Color.white);
		table.setDefaultRenderer(Date.class,
				new DateCellRenderer(datePattern, Color.orange.darker()));
		return table;
	}

	public static JPanel newFieldPanel(String label, Component c) {
		return newFieldPanel(label, c, null);
	}

	public static JPanel newFieldPanel(String label, Component c, String trailingLabel) {
		JLabel jlabel = new JLabel(label);
		jlabel.setPreferredSize(new Dimension(Utils.LABEL_WIDTH, 20));
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		panel.add(jlabel);
		panel.add(c);
		if (!StringUtils.isEmpty(trailingLabel)) {
			panel.add(new JLabel(trailingLabel));
		}
		return panel;
	}

	public static void showException(Component c, Throwable t) {
		StringWriter writer = new StringWriter();
		t.printStackTrace(new PrintWriter(writer));
		String stacktrace = writer.toString();
		try {
			writer.close();
		} catch (Throwable x) {
		}
		JTextArea textArea = new JTextArea(stacktrace);
		textArea.setFont(new Font("monospaced", Font.PLAIN, 11));
		JScrollPane jsp = new JScrollPane(textArea);
		jsp.setPreferredSize(new Dimension(800, 400));
		JOptionPane.showMessageDialog(c, jsp, Translation.get("gb.error"),
				JOptionPane.ERROR_MESSAGE);
	}

	public static void packColumns(JTable table, int margin) {
		for (int c = 0; c < table.getColumnCount(); c++) {
			packColumn(table, c, 4);
		}
	}

	// Sets the preferred width of the visible column specified by vColIndex.
	// The column will be just wide enough to show the column head and the
	// widest cell in the column. margin pixels are added to the left and right
	// (resulting in an additional width of 2*margin pixels).
	private static void packColumn(JTable table, int vColIndex, int margin) {
		DefaultTableColumnModel colModel = (DefaultTableColumnModel) table.getColumnModel();
		TableColumn col = colModel.getColumn(vColIndex);
		int width = 0;

		// Get width of column header
		TableCellRenderer renderer = col.getHeaderRenderer();
		if (renderer == null) {
			renderer = table.getTableHeader().getDefaultRenderer();
		}
		Component comp = renderer.getTableCellRendererComponent(table, col.getHeaderValue(), false,
				false, 0, 0);
		width = comp.getPreferredSize().width;

		// Get maximum width of column data
		for (int r = 0; r < table.getRowCount(); r++) {
			renderer = table.getCellRenderer(r, vColIndex);
			comp = renderer.getTableCellRendererComponent(table, table.getValueAt(r, vColIndex),
					false, false, r, vColIndex);
			width = Math.max(width, comp.getPreferredSize().width);
		}

		// Add margin
		width += 2 * margin;

		// Set the width
		col.setPreferredWidth(width);
	}
}
