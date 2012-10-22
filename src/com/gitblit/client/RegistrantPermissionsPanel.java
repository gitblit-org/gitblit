/*
 * Copyright 2012 gitblit.com.
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;

import com.gitblit.Constants.AccessPermission;
import com.gitblit.models.RegistrantAccessPermission;

public class RegistrantPermissionsPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	
	private JTable permissionsTable;

	private RegistrantPermissionsTableModel tableModel;

	private DefaultComboBoxModel registrantModel;

	private JComboBox registrantSelector;

	private JComboBox permissionSelector;

	private JButton addButton;

	private JPanel addPanel;

	public RegistrantPermissionsPanel() {
		super(new BorderLayout(5, 5));
		tableModel = new RegistrantPermissionsTableModel();
		permissionsTable = new JTable(tableModel);
		permissionsTable.setPreferredScrollableViewportSize(new Dimension(400, 150));
		JScrollPane jsp = new JScrollPane(permissionsTable);
		add(jsp, BorderLayout.CENTER);
		
		permissionsTable.getColumnModel().getColumn(RegistrantPermissionsTableModel.Columns.Type.ordinal())
				.setCellRenderer(new RegexRenderer());
		permissionsTable.getColumnModel().getColumn(RegistrantPermissionsTableModel.Columns.Permission.ordinal())
		.setCellEditor(new AccessPermissionEditor());
		
		registrantModel = new DefaultComboBoxModel();
		registrantSelector = new JComboBox(registrantModel);
		permissionSelector = new JComboBox(AccessPermission.NEWPERMISSIONS);
		addButton = new JButton(Translation.get("gb.add"));
		addButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (registrantSelector.getSelectedIndex() < 0) {
					return;
				}
				if (permissionSelector.getSelectedIndex() < 0) {
					return;
				}
				
				RegistrantAccessPermission rp = new RegistrantAccessPermission();
				rp.registrant = registrantSelector.getSelectedItem().toString();
				rp.permission = (AccessPermission) permissionSelector.getSelectedItem();
				tableModel.permissions.add(rp);
				
				registrantModel.removeElement(rp.registrant);
				registrantSelector.setSelectedIndex(-1);
				registrantSelector.invalidate();
				addPanel.setVisible(registrantModel.getSize() > 0);
				
				tableModel.fireTableDataChanged();
			}
		});
		
		addPanel = new JPanel();
		addPanel.add(registrantSelector);
		addPanel.add(permissionSelector);
		addPanel.add(addButton);
		add(addPanel, BorderLayout.SOUTH);
	}

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		permissionsTable.setEnabled(false);
	}

	public void setObjects(List<String> registrants, List<RegistrantAccessPermission> permissions) {
		List<String> filtered;
		if (registrants == null) {
			filtered = new ArrayList<String>();
		} else {
			filtered = new ArrayList<String>(registrants);
		}
		if (permissions == null) {
			permissions = new ArrayList<RegistrantAccessPermission>();
		}
		for (RegistrantAccessPermission rp : permissions) {
			filtered.remove(rp.registrant);
		}
		for (String registrant : filtered) {
			registrantModel.addElement(registrant);
		}
		tableModel.setPermissions(permissions);
		
		registrantSelector.setSelectedIndex(-1);
		permissionSelector.setSelectedIndex(-1);
		addPanel.setVisible(filtered.size() > 0);
	}

	public List<RegistrantAccessPermission> getPermissions() {
		return tableModel.permissions;
	}
	
	private class AccessPermissionEditor extends DefaultCellEditor {
	    
		private static final long serialVersionUID = 1L;

		public AccessPermissionEditor() {
	        super(new JComboBox(AccessPermission.values()));
	    }
	}
	
	private class RegexRenderer extends DefaultTableCellRenderer {

		private static final long serialVersionUID = 1L;

		public RegexRenderer() {
			super();
			setHorizontalAlignment(SwingConstants.CENTER);
		}

		@Override
		protected void setValue(Object value) {
			boolean isExplicit = (Boolean) value;
			if (isExplicit) {
				// explicit permission
				setText("");
				setToolTipText(null);
			} else {
				// regex matched permission
				setText("regex");
				setToolTipText(Translation.get("gb.regexPermission"));
			}
		}
	}
}
