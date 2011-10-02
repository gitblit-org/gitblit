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

import java.awt.BorderLayout;
import java.awt.Component;
import java.io.IOException;
import java.util.Date;
import java.util.Map;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.RpcUtils;

public class GitblitPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	String url;
	String account;
	char[] password;

	JTabbedPane tabs;

	private JTable repositoriesTable;

	public GitblitPanel(String url, String account, char[] password) {
		this.url = url;
		this.account = account;
		this.password = password;

		tabs = new JTabbedPane(JTabbedPane.TOP);
		repositoriesTable = new JTable();
		repositoriesTable.setDefaultRenderer(Date.class, new DateCellRenderer(null));

		tabs.addTab("Repositories", new JScrollPane(repositoriesTable));

		setLayout(new BorderLayout());
		add(tabs, BorderLayout.CENTER);
	}

	public void login() throws IOException {
		refreshRepositoriesTable();
	}

	private void refreshRepositoriesTable() throws IOException {
		Map<String, RepositoryModel> repositories = RpcUtils
				.getRepositories(url, account, password);
		repositoriesTable.setModel(new RepositoriesModel(repositories));

		packColumns(repositoriesTable, 2);
	}

	private void packColumns(JTable table, int margin) {
		for (int c = 0; c < table.getColumnCount(); c++) {
			packColumn(table, c, 4);
		}
	}

	// Sets the preferred width of the visible column specified by vColIndex.
	// The column will be just wide enough to show the column head and the
	// widest cell in the column. margin pixels are added to the left and right
	// (resulting in an additional width of 2*margin pixels).
	private void packColumn(JTable table, int vColIndex, int margin) {
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
