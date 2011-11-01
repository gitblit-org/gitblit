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
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableRowSorter;

import com.gitblit.Constants.RpcRequest;
import com.gitblit.models.SettingModel;
import com.gitblit.utils.StringUtils;

/**
 * Settings panel displays a list of server settings and their associated
 * metadata. This panel also allows editing of a setting.
 * 
 * @author James Moger
 * 
 */
public class SettingsPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private final GitblitClient gitblit;

	private HeaderPanel header;

	private JTable table;

	private SettingsTableModel tableModel;

	private TableRowSorter<SettingsTableModel> defaultSorter;

	private JTextField filterTextfield;

	public SettingsPanel(GitblitClient gitblit) {
		super();
		this.gitblit = gitblit;
		initialize();
	}

	private void initialize() {
		JButton refreshSettings = new JButton(Translation.get("gb.refresh"));
		refreshSettings.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				refreshSettings();
			}
		});

		final JButton editSetting = new JButton(Translation.get("gb.edit"));
		editSetting.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int viewRow = table.getSelectedRow();
				int modelRow = table.convertRowIndexToModel(viewRow);
				String key = tableModel.keys.get(modelRow);
				SettingModel setting = tableModel.settings.get(key);
				editSetting(setting);
			}
		});

		NameRenderer nameRenderer = new NameRenderer();
		final SettingPanel settingPanel = new SettingPanel();
		tableModel = new SettingsTableModel();
		defaultSorter = new TableRowSorter<SettingsTableModel>(tableModel);
		table = Utils.newTable(tableModel, Utils.DATE_FORMAT);
		table.setDefaultRenderer(SettingModel.class, new SettingCellRenderer());
		String name = table.getColumnName(UsersTableModel.Columns.Name.ordinal());
		table.setRowHeight(nameRenderer.getFont().getSize() + 8);
		table.getColumn(name).setCellRenderer(nameRenderer);
		table.setRowSorter(defaultSorter);
		table.getRowSorter().toggleSortOrder(SettingsTableModel.Columns.Name.ordinal());
		table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

			@Override
			public void valueChanged(ListSelectionEvent e) {
				if (e.getValueIsAdjusting()) {
					return;
				}
				boolean singleSelection = table.getSelectedRows().length == 1;
				editSetting.setEnabled(singleSelection);
				if (singleSelection) {
					int viewRow = table.getSelectedRow();
					int modelRow = table.convertRowIndexToModel(viewRow);
					SettingModel setting = tableModel.get(modelRow);
					settingPanel.setSetting(setting);
				} else {
					settingPanel.clear();
				}
			}
		});
		table.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					int viewRow = table.getSelectedRow();
					int modelRow = table.convertRowIndexToModel(viewRow);
					SettingModel setting = tableModel.get(modelRow);
					editSetting(setting);
				}
			}
		});

		filterTextfield = new JTextField();
		filterTextfield.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				filterSettings(filterTextfield.getText());
			}
		});
		filterTextfield.addKeyListener(new KeyAdapter() {
			public void keyReleased(KeyEvent e) {
				filterSettings(filterTextfield.getText());
			}
		});

		JPanel settingFilterPanel = new JPanel(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		settingFilterPanel.add(new JLabel(Translation.get("gb.filter")), BorderLayout.WEST);
		settingFilterPanel.add(filterTextfield, BorderLayout.CENTER);

		JPanel settingsTablePanel = new JPanel(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		settingsTablePanel.add(settingFilterPanel, BorderLayout.NORTH);
		settingsTablePanel.add(new JScrollPane(table), BorderLayout.CENTER);
		settingsTablePanel.add(settingPanel, BorderLayout.SOUTH);

		JPanel settingsControls = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
		settingsControls.add(refreshSettings);
		settingsControls.add(editSetting);

		setLayout(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		header = new HeaderPanel(Translation.get("gb.settings"), "settings_16x16.png");
		add(header, BorderLayout.NORTH);
		add(settingsTablePanel, BorderLayout.CENTER);
		add(settingsControls, BorderLayout.SOUTH);
	}
	
	@Override
	public void requestFocus() {
		filterTextfield.requestFocus();
	}

	@Override
	public Insets getInsets() {
		return Utils.INSETS;
	}

	protected void updateTable(boolean pack) {
		tableModel.setSettings(gitblit.getSettings());
		tableModel.fireTableDataChanged();
		header.setText(Translation.get("gb.settings"));
		if (pack) {
			Utils.packColumns(table, Utils.MARGIN);
		}
	}

	private void filterSettings(final String fragment) {
		if (StringUtils.isEmpty(fragment)) {
			table.setRowSorter(defaultSorter);
			return;
		}
		RowFilter<SettingsTableModel, Object> containsFilter = new RowFilter<SettingsTableModel, Object>() {
			public boolean include(Entry<? extends SettingsTableModel, ? extends Object> entry) {
				for (int i = entry.getValueCount() - 1; i >= 0; i--) {
					if (entry.getStringValue(i).toLowerCase().contains(fragment.toLowerCase())) {
						return true;
					}
				}
				return false;
			}
		};
		TableRowSorter<SettingsTableModel> sorter = new TableRowSorter<SettingsTableModel>(
				tableModel);
		sorter.setRowFilter(containsFilter);
		table.setRowSorter(sorter);
	}

	protected void refreshSettings() {
		GitblitWorker worker = new GitblitWorker(SettingsPanel.this, RpcRequest.LIST_SETTINGS) {
			@Override
			protected Boolean doRequest() throws IOException {
				gitblit.refreshSettings();
				return true;
			}

			@Override
			protected void onSuccess() {
				updateTable(false);
			}
		};
		worker.execute();
	}

	protected void editSetting(final SettingModel settingModel) {
		final JTextField textField = new JTextField(settingModel.currentValue);
		JPanel editPanel = new JPanel(new GridLayout(0, 1));
		editPanel.add(new JLabel("New Value"));
		editPanel.add(textField);

		JPanel settingPanel = new JPanel(new BorderLayout());
		settingPanel.add(new SettingPanel(settingModel), BorderLayout.CENTER);
		settingPanel.add(editPanel, BorderLayout.SOUTH);
		settingPanel.setPreferredSize(new Dimension(800, 200));

		String[] options;
		if (settingModel.currentValue.equals(settingModel.defaultValue)) {
			options = new String[] { Translation.get("gb.cancel"), Translation.get("gb.save") };
		} else {
			options = new String[] { Translation.get("gb.cancel"),
					Translation.get("gb.setDefault"), Translation.get("gb.save") };
		}
		String defaultOption = options[0];
		int selection = JOptionPane.showOptionDialog(SettingsPanel.this, settingPanel,
				settingModel.name, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
				new ImageIcon(getClass().getResource("/settings_16x16.png")), options,
				defaultOption);
		if (selection <= 0) {
			return;
		}
		if (options[selection].equals(Translation.get("gb.setDefault"))) {
			textField.setText(settingModel.defaultValue);
		}
		final Map<String, String> newSettings = new HashMap<String, String>();
		newSettings.put(settingModel.name, textField.getText().trim());
		GitblitWorker worker = new GitblitWorker(SettingsPanel.this, RpcRequest.EDIT_SETTINGS) {
			@Override
			protected Boolean doRequest() throws IOException {
				boolean success = gitblit.updateSettings(newSettings);
				if (success) {
					gitblit.refreshSettings();
				}
				return success;
			}

			@Override
			protected void onSuccess() {
				updateTable(false);
			}
		};
		worker.execute();
	}
}
