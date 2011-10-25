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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import com.gitblit.models.ServerStatus;
import com.gitblit.utils.ByteFormat;

/**
 * This panel displays the server status.
 * 
 * @author James Moger
 */
public class StatusPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	private final Insets insets = new Insets(5, 5, 5, 5);
	private JLabel bootDate;
	private JLabel servletContainer;
	private JLabel heapMaximum;
	private JLabel heapAllocated;
	private JLabel heapUsed;
	private PropertiesTableModel model;
	private HeaderPanel headerPanel;

	public StatusPanel() {
		super();
		initialize();
	}

	public StatusPanel(ServerStatus status) {
		this();
		setStatus(status);
	}

	private void initialize() {
		bootDate = new JLabel();
		servletContainer = new JLabel();

		heapMaximum = new JLabel();
		heapAllocated = new JLabel();
		heapUsed = new JLabel();

		JPanel fieldsPanel = new JPanel(new GridLayout(0, 1));
		fieldsPanel.add(createFieldPanel("gb.bootDate", bootDate));
		fieldsPanel.add(createFieldPanel("gb.servletContainer", servletContainer));
		fieldsPanel.add(createFieldPanel("gb.heapUsed", heapUsed));
		fieldsPanel.add(createFieldPanel("gb.heapAllocated", heapAllocated));
		fieldsPanel.add(createFieldPanel("gb.heapMaximum", heapMaximum));

		model = new PropertiesTableModel();
		JTable propertiesTable = Utils.newTable(model);
		String name = propertiesTable.getColumnName(PropertiesTableModel.Columns.Name.ordinal());
		NameRenderer nameRenderer = new NameRenderer();
		propertiesTable.setRowHeight(nameRenderer.getFont().getSize() + 8);
		propertiesTable.getColumn(name).setCellRenderer(nameRenderer);

		JPanel centerPanel = new JPanel(new BorderLayout());
		centerPanel.add(fieldsPanel, BorderLayout.NORTH);
		centerPanel.add(new JScrollPane(propertiesTable), BorderLayout.CENTER);

		headerPanel = new HeaderPanel(Translation.get("gb.status"), null);
		setLayout(new BorderLayout());
		add(headerPanel, BorderLayout.NORTH);
		add(centerPanel, BorderLayout.CENTER);
	}

	private JPanel createFieldPanel(String key, JLabel valueLabel) {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
		JLabel textLabel = new JLabel(Translation.get(key));
		textLabel.setFont(textLabel.getFont().deriveFont(Font.BOLD));
		textLabel.setPreferredSize(new Dimension(120, valueLabel.getFont().getSize() + 4));
		panel.add(textLabel);
		panel.add(valueLabel);
		return panel;
	}

	@Override
	public Insets getInsets() {
		return insets;
	}

	public void setStatus(ServerStatus status) {
		headerPanel.setText(Translation.get("gb.status"));
		bootDate.setText(status.bootDate.toString());
		servletContainer.setText(status.servletContainer);
		ByteFormat byteFormat = new ByteFormat();
		heapMaximum.setText(byteFormat.format(status.heapMaximum));
		heapAllocated.setText(byteFormat.format(status.heapAllocated));
		heapUsed.setText(byteFormat.format(status.heapAllocated - status.heapFree) + " ("
				+ byteFormat.format(status.heapFree) + " " + Translation.get("gb.free") + ")");
		model.setProperties(status.systemProperties);
		model.fireTableDataChanged();
	}
}
