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
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * Displays a list of registrations and allows management of server
 * registrations.
 * 
 * @author James Moger
 * 
 */
public class RegistrationsDialog extends JDialog {

	interface RegistrationListener {

		void login(GitblitRegistration reg);

		boolean saveRegistration(String name, GitblitRegistration reg);

		boolean deleteRegistrations(List<GitblitRegistration> list);
	}

	private static final long serialVersionUID = 1L;

	private final List<GitblitRegistration> registrations;

	private final RegistrationListener listener;

	private JTable registrationsTable;

	private RegistrationsTableModel model;

	public RegistrationsDialog(List<GitblitRegistration> registrations,
			RegistrationListener listener) {
		super();
		this.registrations = registrations;
		this.listener = listener;
		setTitle(Translation.get("gb.manage"));
		setIconImage(new ImageIcon(getClass().getResource("/gitblt-favicon.png")).getImage());
		initialize();
		setSize(600, 400);
	}

	@Override
	protected JRootPane createRootPane() {
		KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
		JRootPane rootPane = new JRootPane();
		rootPane.registerKeyboardAction(new ActionListener() {
			public void actionPerformed(ActionEvent actionEvent) {
				setVisible(false);
			}
		}, stroke, JComponent.WHEN_IN_FOCUSED_WINDOW);
		return rootPane;
	}

	private void initialize() {
		NameRenderer nameRenderer = new NameRenderer();
		model = new RegistrationsTableModel(registrations);
		registrationsTable = Utils.newTable(model, Utils.DATE_FORMAT);
		registrationsTable.setRowHeight(nameRenderer.getFont().getSize() + 8);

		String id = registrationsTable
				.getColumnName(RegistrationsTableModel.Columns.Name.ordinal());
		registrationsTable.getColumn(id).setCellRenderer(nameRenderer);
		registrationsTable.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					login();
				}
			}
		});

		final JButton create = new JButton(Translation.get("gb.create"));
		create.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				create();
			}
		});

		final JButton login = new JButton(Translation.get("gb.login"));
		login.setEnabled(false);
		login.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				login();
			}
		});

		final JButton edit = new JButton(Translation.get("gb.edit"));
		edit.setEnabled(false);
		edit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				edit();
			}
		});

		final JButton delete = new JButton(Translation.get("gb.delete"));
		delete.setEnabled(false);
		delete.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				delete();
			}
		});

		registrationsTable.getSelectionModel().addListSelectionListener(
				new ListSelectionListener() {
					@Override
					public void valueChanged(ListSelectionEvent e) {
						if (e.getValueIsAdjusting()) {
							return;
						}
						boolean singleSelection = registrationsTable.getSelectedRowCount() == 1;
						boolean selected = registrationsTable.getSelectedRow() > -1;
						login.setEnabled(singleSelection);
						edit.setEnabled(singleSelection);
						delete.setEnabled(selected);
					}
				});

		JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
		controls.add(create);
		controls.add(login);
		controls.add(edit);
		controls.add(delete);

		final Insets insets = new Insets(5, 5, 5, 5);
		JPanel centerPanel = new JPanel(new BorderLayout(5, 5)) {

			private static final long serialVersionUID = 1L;

			public Insets getInsets() {
				return insets;
			}
		};
		centerPanel.add(new HeaderPanel(Translation.get("gb.servers"), null), BorderLayout.NORTH);
		centerPanel.add(new JScrollPane(registrationsTable), BorderLayout.CENTER);
		centerPanel.add(controls, BorderLayout.SOUTH);

		getContentPane().setLayout(new BorderLayout(5, 5));
		getContentPane().add(centerPanel, BorderLayout.CENTER);
	}

	private void login() {
		int viewRow = registrationsTable.getSelectedRow();
		int modelRow = registrationsTable.convertRowIndexToModel(viewRow);
		GitblitRegistration reg = registrations.get(modelRow);
		RegistrationsDialog.this.setVisible(false);
		listener.login(reg);
	}

	private void create() {
		EditRegistrationDialog dialog = new EditRegistrationDialog(getOwner());
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
		GitblitRegistration reg = dialog.getRegistration();
		if (reg == null) {
			return;
		}
		if (listener.saveRegistration(reg.name, reg)) {
			model.list.add(reg);
			model.fireTableDataChanged();
		}
	}

	private void edit() {
		int viewRow = registrationsTable.getSelectedRow();
		int modelRow = registrationsTable.convertRowIndexToModel(viewRow);
		GitblitRegistration reg = registrations.get(modelRow);
		String originalName = reg.name;
		EditRegistrationDialog dialog = new EditRegistrationDialog(getOwner(), reg, false);
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
		reg = dialog.getRegistration();
		if (reg == null) {
			return;
		}
		if (listener.saveRegistration(originalName, reg)) {
			model.fireTableDataChanged();
		}
	}

	private void delete() {
		List<GitblitRegistration> list = new ArrayList<GitblitRegistration>();
		for (int i : registrationsTable.getSelectedRows()) {
			int model = registrationsTable.convertRowIndexToModel(i);
			GitblitRegistration reg = registrations.get(model);
			list.add(reg);
		}
		if (listener.deleteRegistrations(list)) {
			registrations.removeAll(list);
			model.fireTableDataChanged();
		}
	}
}
