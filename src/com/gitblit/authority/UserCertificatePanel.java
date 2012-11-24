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
package com.gitblit.authority;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableRowSorter;

import com.gitblit.client.HeaderPanel;
import com.gitblit.client.Translation;
import com.gitblit.models.UserModel;
import com.gitblit.utils.X509Utils.RevocationReason;
import com.gitblit.utils.X509Utils.X509Metadata;

public abstract class UserCertificatePanel extends JPanel {

	private static final long serialVersionUID = 1L;
	
	private Frame owner;
	
	private UserCertificateModel ucm;
	
	private JTextField displayname;
	private JTextField username;
	private JTextField emailAddress;
	private JTextField organizationalUnit;
	private JTextField organization;
	private JTextField locality;
	private JTextField stateProvince;
	private JTextField countryCode;

	private CertificatesTableModel tableModel;

	private JButton saveUserButton;

	private JButton editUserButton;

	private JButton newCertificateButton;
	
	private JButton revokeCertificateButton;

	private JTable table;
	
	public UserCertificatePanel(Frame owner) {
		super(new BorderLayout());
		
		this.owner = owner;
		
		displayname = new JTextField(20);
		username = new JTextField(20);
		username.setEditable(false);
		emailAddress = new JTextField(20);
		organizationalUnit = new JTextField(20);
		organization = new JTextField(20);
		locality = new JTextField(20);
		stateProvince = new JTextField(20);
		countryCode = new JTextField(20);
				
		JPanel fields = new JPanel(new GridLayout(0, 1, 5, 5));
		fields.add(newFieldPanel(Translation.get("gb.displayName"), displayname));
		fields.add(newFieldPanel(Translation.get("gb.username") + " (CN)", username));
		fields.add(newFieldPanel(Translation.get("gb.emailAddress") + " (E)", emailAddress));
		fields.add(newFieldPanel(Translation.get("gb.organizationalUnit") + " (OU)", organizationalUnit));
		fields.add(newFieldPanel(Translation.get("gb.organization") + " (O)", organization));
		fields.add(newFieldPanel(Translation.get("gb.locality") + " (L)", locality));
		fields.add(newFieldPanel(Translation.get("gb.stateProvince") + " (ST)", stateProvince));
		fields.add(newFieldPanel(Translation.get("gb.countryCode") + " (C)", countryCode));
		
		JPanel fp = new JPanel(new BorderLayout(5, 5));
		fp.add(fields, BorderLayout.NORTH);
		
		JPanel fieldsPanel = new JPanel(new BorderLayout());
		fieldsPanel.add(new HeaderPanel(Translation.get("gb.properties"), "vcard_16x16.png"), BorderLayout.NORTH);
		fieldsPanel.add(fp, BorderLayout.CENTER);
		
		saveUserButton = new JButton(Translation.get("gb.save"));
		saveUserButton.setEnabled(false);
		saveUserButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setEditable(false);
				String username = ucm.user.username;
				updateUser();
				saveUser(username, ucm);
			}
		});
		
		editUserButton = new JButton(Translation.get("gb.edit"));
		editUserButton.setEnabled(false);
		editUserButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setEditable(true);
			}
		});
		
		JPanel userControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		userControls.add(editUserButton);
		userControls.add(saveUserButton);
		fieldsPanel.add(userControls, BorderLayout.SOUTH);
		
		JPanel certificatesPanel = new JPanel(new BorderLayout());
		certificatesPanel.add(new HeaderPanel(Translation.get("gb.certificates"), "rosette_16x16.png"), BorderLayout.NORTH);		
		tableModel = new CertificatesTableModel();
		table = Utils.newTable(tableModel, Utils.DATE_FORMAT);
		table.setRowSorter(new TableRowSorter<CertificatesTableModel>(tableModel));
		table.setDefaultRenderer(CertificateStatus.class, new CertificateStatusRenderer());
		table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				if (e.getValueIsAdjusting()) {
					return;
				}
				boolean enable = false;
				int row = table.getSelectedRow();
				if (row > -1) {
					int modelIndex = table.convertRowIndexToModel(row);
					X509Certificate cert = tableModel.get(modelIndex);
					enable = !ucm.isRevoked(cert.getSerialNumber());
				}
				revokeCertificateButton.setEnabled(enable);
			}
		});
		table.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {					
					int row = table.rowAtPoint(e.getPoint());
					int modelIndex = table.convertRowIndexToModel(row);
					X509Certificate cert = tableModel.get(modelIndex);
					X509CertificateViewer viewer = new X509CertificateViewer(UserCertificatePanel.this.owner, cert);					
					viewer.setVisible(true);
				}
			}
		});
		certificatesPanel.add(new JScrollPane(table), BorderLayout.CENTER);
		
		newCertificateButton = new JButton(Translation.get("gb.newCertificate"));
		newCertificateButton.setEnabled(false);
		newCertificateButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					if (saveUserButton.isEnabled()) {
						// save changes
						String username = ucm.user.username;
						setEditable(false);
						updateUser();
						saveUser(username, ucm);
					}
					
					NewClientCertificateDialog dialog = new NewClientCertificateDialog(UserCertificatePanel.this.owner,
							ucm.user.getDisplayName(), getDefaultExpiration());
					dialog.setModal(true);
					dialog.setVisible(true);
					if (dialog.isCanceled()) {
						return;
					}
					
					setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
					UserModel user = ucm.user;
					X509Metadata metadata = new X509Metadata(user.username, dialog.getPassword());
					metadata.userDisplayname = user.getDisplayName();
					metadata.emailAddress = user.emailAddress;				
					metadata.passwordHint = dialog.getPasswordHint();
					metadata.notAfter = dialog.getExpiration();

					newCertificate(ucm, metadata, dialog.sendEmail());
				} catch (Exception x) {
					Utils.showException(UserCertificatePanel.this, x);
				} finally {
					setCursor(Cursor.getDefaultCursor());
				}
			}
		});
		
		revokeCertificateButton = new JButton(Translation.get("gb.revokeCertificate"));
		revokeCertificateButton.setEnabled(false);
		revokeCertificateButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					int row = table.getSelectedRow();
					if (row < 0) {
						return;
					}
					int modelIndex = table.convertRowIndexToModel(row);
					X509Certificate cert = tableModel.get(modelIndex);
					
					String [] choices = new String[RevocationReason.reasons.length];
					for (int i = 0; i < choices.length; i++) {
						choices[i] = Translation.get("gb." + RevocationReason.reasons[i].name());
					}
					
					Object choice = JOptionPane.showInputDialog(UserCertificatePanel.this.owner,
							Translation.get("gb.revokeCertificateReason"), Translation.get("gb.revokeCertificate"),
							JOptionPane.PLAIN_MESSAGE, new ImageIcon(getClass().getResource("/rosette_16x16.png")), choices, Translation.get("gb.unspecified"));
					if (choice == null) {
						return;
					}
					RevocationReason reason = RevocationReason.unspecified;
					for (int i = 0 ; i < choices.length; i++) {
						if (choices[i].equals(choice)) {
							reason = RevocationReason.reasons[i];
							break;
						}
					}
					if (!ucm.isRevoked(cert.getSerialNumber())) {
						if (ucm.certs.size() == 1) {
							// no other certificates
							ucm.expires = null;
						} else {
							// determine new expires date for user
							Date newExpires = null;
							for (X509Certificate c : ucm.certs) {								
								if (!c.equals(cert)) {
									if (!ucm.isRevoked(c.getSerialNumber())) {
										if (newExpires == null || c.getNotAfter().after(newExpires)) {
											newExpires = c.getNotAfter();
										}
									}
								}
							}
							ucm.expires = newExpires;
						}
						revoke(ucm, cert, reason);
					}
				} catch (Exception x) {
					Utils.showException(UserCertificatePanel.this, x);
				} finally {
					setCursor(Cursor.getDefaultCursor());
				}
			}
		});
		
		JPanel certificateControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
		certificateControls.add(newCertificateButton);
		certificateControls.add(revokeCertificateButton);
		certificatesPanel.add(certificateControls, BorderLayout.SOUTH);
				
		add(fieldsPanel, BorderLayout.NORTH);
		add(certificatesPanel, BorderLayout.CENTER);
		setEditable(false);
	}
	
	private JPanel newFieldPanel(String label, Component c) {
		JLabel jlabel = new JLabel(label);
		jlabel.setPreferredSize(new Dimension(175, 20));
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		panel.add(jlabel);
		panel.add(c);
		return panel;
	}
	
	public void setUserCertificateModel(UserCertificateModel ucm) {
		this.ucm = ucm;
		setEditable(false);
		displayname.setText(ucm.user.getDisplayName());
		username.setText(ucm.user.username);
		emailAddress.setText(ucm.user.emailAddress);
		organizationalUnit.setText(ucm.user.organizationalUnit);
		organization.setText(ucm.user.organization);
		locality.setText(ucm.user.locality);
		stateProvince.setText(ucm.user.stateProvince);
		countryCode.setText(ucm.user.countryCode);
		
		tableModel.setUserCertificateModel(ucm);
		tableModel.fireTableDataChanged();
		Utils.packColumns(table, Utils.MARGIN);
	}
	
	public void setEditable(boolean editable) {
		displayname.setEditable(editable);
//		username.setEditable(editable);
		emailAddress.setEditable(editable);
		organizationalUnit.setEditable(editable);
		organization.setEditable(editable);
		locality.setEditable(editable);
		stateProvince.setEditable(editable);
		countryCode.setEditable(editable);
		
		editUserButton.setEnabled(!editable && ucm != null);
		saveUserButton.setEnabled(editable && ucm != null);
		
		newCertificateButton.setEnabled(ucm != null);
		revokeCertificateButton.setEnabled(false);
	}
	
	private void updateUser() {
		ucm.user.displayName = displayname.getText();
		ucm.user.username = username.getText();
		ucm.user.emailAddress = emailAddress.getText();
		ucm.user.organizationalUnit = organizationalUnit.getText();
		ucm.user.organization = organization.getText();
		ucm.user.locality = locality.getText();
		ucm.user.stateProvince = stateProvince.getText();
		ucm.user.countryCode = countryCode.getText();
	}
	
	public abstract Date getDefaultExpiration();
	
	public abstract void saveUser(String username, UserCertificateModel ucm);
	public abstract void newCertificate(UserCertificateModel ucm, X509Metadata metadata, boolean sendEmail);
	public abstract void revoke(UserCertificateModel ucm, X509Certificate cert, RevocationReason reason);
}
