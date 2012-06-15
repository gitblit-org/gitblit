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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import com.gitblit.Constants;
import com.gitblit.Constants.RpcRequest;
import com.gitblit.models.ServerStatus;
import com.gitblit.utils.ByteFormat;

/**
 * This panel displays the server status.
 * 
 * @author James Moger
 */
public class StatusPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	private final GitblitClient gitblit;
	private JLabel bootDate;
	private JLabel url;
	private JLabel servletContainer;
	private JLabel heapMaximum;
	private JLabel heapAllocated;
	private JLabel heapUsed;
	private PropertiesTableModel tableModel;
	private HeaderPanel header;
	private JLabel version;
	private JLabel releaseDate;

	public StatusPanel(GitblitClient gitblit) {
		super();
		this.gitblit = gitblit;
		initialize();
	}

	private void initialize() {
		JButton refreshStatus = new JButton(Translation.get("gb.refresh"));
		refreshStatus.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				refreshStatus();
			}
		});

		version = new JLabel();
		releaseDate = new JLabel();
		bootDate = new JLabel();
		url = new JLabel();
		servletContainer = new JLabel();

		heapMaximum = new JLabel();
		heapAllocated = new JLabel();
		heapUsed = new JLabel();

		JPanel fieldsPanel = new JPanel(new GridLayout(0, 1, 0, Utils.MARGIN)) {

			private static final long serialVersionUID = 1L;

			@Override
			public Insets getInsets() {
				return Utils.INSETS;
			}
		};
		fieldsPanel.add(createFieldPanel("gb.version", version));
		fieldsPanel.add(createFieldPanel("gb.releaseDate", releaseDate));
		fieldsPanel.add(createFieldPanel("gb.bootDate", bootDate));
		fieldsPanel.add(createFieldPanel("gb.url", url));
		fieldsPanel.add(createFieldPanel("gb.servletContainer", servletContainer));
		fieldsPanel.add(createFieldPanel("gb.heapUsed", heapUsed));
		fieldsPanel.add(createFieldPanel("gb.heapAllocated", heapAllocated));
		fieldsPanel.add(createFieldPanel("gb.heapMaximum", heapMaximum));

		tableModel = new PropertiesTableModel();
		JTable propertiesTable = Utils.newTable(tableModel, Utils.DATE_FORMAT);
		String name = propertiesTable.getColumnName(PropertiesTableModel.Columns.Name.ordinal());
		NameRenderer nameRenderer = new NameRenderer();
		propertiesTable.setRowHeight(nameRenderer.getFont().getSize() + 8);
		propertiesTable.getColumn(name).setCellRenderer(nameRenderer);

		JPanel centerPanel = new JPanel(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		centerPanel.add(fieldsPanel, BorderLayout.NORTH);
		centerPanel.add(new JScrollPane(propertiesTable), BorderLayout.CENTER);

		JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, Utils.MARGIN, 0));
		controls.add(refreshStatus);

		header = new HeaderPanel(Translation.get("gb.status"), "health_16x16.png");
		setLayout(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		add(header, BorderLayout.NORTH);
		add(centerPanel, BorderLayout.CENTER);
		add(controls, BorderLayout.SOUTH);
	}

	private JPanel createFieldPanel(String key, JLabel valueLabel) {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, Utils.MARGIN, 0));
		JLabel textLabel = new JLabel(Translation.get(key));
		textLabel.setFont(textLabel.getFont().deriveFont(Font.BOLD));
		textLabel.setPreferredSize(new Dimension(120, 10));
		panel.add(textLabel);
		panel.add(valueLabel);
		return panel;
	}

	@Override
	public Insets getInsets() {
		return Utils.INSETS;
	}

	protected void refreshStatus() {
		GitblitWorker worker = new GitblitWorker(StatusPanel.this, RpcRequest.LIST_STATUS) {
			@Override
			protected Boolean doRequest() throws IOException {
				gitblit.refreshStatus();
				return true;
			}

			@Override
			protected void onSuccess() {
				updateTable(false);
			}
		};
		worker.execute();
	}

	protected void updateTable(boolean pack) {
		ServerStatus status = gitblit.getStatus();
		header.setText(Translation.get("gb.status"));
		version.setText(Constants.NAME + (status.isGO ? " GO v" : " WAR v") + status.version);
		releaseDate.setText(status.releaseDate);		
		bootDate.setText(status.bootDate.toString() + " (" + Translation.getTimeUtils().timeAgo(status.bootDate)
				+ ")");
		url.setText(gitblit.url);
		servletContainer.setText(status.servletContainer);
		ByteFormat byteFormat = new ByteFormat();
		heapMaximum.setText(byteFormat.format(status.heapMaximum));
		heapAllocated.setText(byteFormat.format(status.heapAllocated));
		heapUsed.setText(byteFormat.format(status.heapAllocated - status.heapFree) + " ("
				+ byteFormat.format(status.heapFree) + " " + Translation.get("gb.free") + ")");
		tableModel.setProperties(status.systemProperties);
		tableModel.fireTableDataChanged();
	}
}
