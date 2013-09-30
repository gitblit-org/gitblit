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
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
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
import com.gitblit.Constants.PermissionType;
import com.gitblit.Constants.RegistrantType;
import com.gitblit.client.Utils.RowRenderer;
import com.gitblit.models.RegistrantAccessPermission;
import com.gitblit.utils.StringUtils;

public class RegistrantPermissionsPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private JTable permissionsTable;

	private RegistrantPermissionsTableModel tableModel;

	private DefaultComboBoxModel registrantModel;

	private JComboBox registrantSelector;

	private JComboBox permissionSelector;

	private JButton addButton;

	private JPanel addPanel;

	public RegistrantPermissionsPanel(final RegistrantType registrantType) {
		super(new BorderLayout(5, 5));
		tableModel = new RegistrantPermissionsTableModel();
		permissionsTable = Utils.newTable(tableModel, Utils.DATE_FORMAT, new RowRenderer() {
			Color clear = new Color(0, 0, 0, 0);
			Color iceGray = new Color(0xf0, 0xf0, 0xf0);

			@Override
			public void prepareRow(Component c, boolean isSelected, int row, int column) {
				if (isSelected) {
					c.setBackground(permissionsTable.getSelectionBackground());
				} else {
					if (tableModel.permissions.get(row).mutable) {
						c.setBackground(clear);
					} else {
						c.setBackground(iceGray);
					}
				}
			}
		});
		permissionsTable.setModel(tableModel);
		permissionsTable.setPreferredScrollableViewportSize(new Dimension(400, 150));
		JScrollPane jsp = new JScrollPane(permissionsTable);
		add(jsp, BorderLayout.CENTER);

		permissionsTable.getColumnModel().getColumn(RegistrantPermissionsTableModel.Columns.Registrant.ordinal())
		.setCellRenderer(new NameRenderer());
		permissionsTable.getColumnModel().getColumn(RegistrantPermissionsTableModel.Columns.Type.ordinal())
				.setCellRenderer(new PermissionTypeRenderer());
		permissionsTable.getColumnModel().getColumn(RegistrantPermissionsTableModel.Columns.Permission.ordinal())
		.setCellEditor(new AccessPermissionEditor());

		registrantModel = new DefaultComboBoxModel();
		registrantSelector = new JComboBox(registrantModel);
		permissionSelector = new JComboBox(AccessPermission.NEWPERMISSIONS);
		addButton = new JButton(Translation.get("gb.add"));
		addButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (registrantSelector.getSelectedIndex() < 0) {
					return;
				}
				if (permissionSelector.getSelectedIndex() < 0) {
					return;
				}

				RegistrantAccessPermission rp = new RegistrantAccessPermission(registrantType);
				rp.registrant = registrantSelector.getSelectedItem().toString();
				rp.permission = (AccessPermission) permissionSelector.getSelectedItem();
				if (StringUtils.findInvalidCharacter(rp.registrant) != null) {
					rp.permissionType = PermissionType.REGEX;
					rp.source = rp.registrant;
				} else {
					rp.permissionType = PermissionType.EXPLICIT;
				}

				tableModel.permissions.add(rp);
				// resort permissions after insert to convey idea of eval order
				Collections.sort(tableModel.permissions);

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
		permissionsTable.setEnabled(enabled);
		registrantSelector.setEnabled(enabled);
		permissionSelector.setEnabled(enabled);
		addButton.setEnabled(enabled);
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
			if (rp.mutable) {
				// only remove editable duplicates
				// this allows for specifying an explicit permission
				filtered.remove(rp.registrant);
			} else if (rp.isAdmin()) {
				// administrators can not have their permission changed
				filtered.remove(rp.registrant);
			} else if (rp.isOwner()) {
				// owners can not have their permission changed
				filtered.remove(rp.registrant);
			}
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

	private class PermissionTypeRenderer extends DefaultTableCellRenderer {

		private static final long serialVersionUID = 1L;

		public PermissionTypeRenderer() {
			super();
			setHorizontalAlignment(SwingConstants.CENTER);
		}

		@Override
		protected void setValue(Object value) {
			RegistrantAccessPermission ap = (RegistrantAccessPermission) value;
			switch (ap.permissionType) {
			case ADMINISTRATOR:
				setText(ap.source == null ? Translation.get("gb.administrator") : ap.source);
				setToolTipText(Translation.get("gb.administratorPermission"));
				break;
			case OWNER:
				setText(Translation.get("gb.owner"));
				setToolTipText(Translation.get("gb.ownerPermission"));
				break;
			case TEAM:
				setText(ap.source == null ? Translation.get("gb.team") : ap.source);
				setToolTipText(MessageFormat.format(Translation.get("gb.teamPermission"), ap.source));
				break;
			case REGEX:
				setText("regex");
				setToolTipText(MessageFormat.format(Translation.get("gb.regexPermission"), ap.source));
				break;
			default:
				if (ap.isMissing()) {
					setText(Translation.get("gb.missing"));
					setToolTipText(Translation.get("gb.missingPermission"));
				} else {
					setText("");
					setToolTipText(null);
				}
				break;
			}
		}
	}
}
