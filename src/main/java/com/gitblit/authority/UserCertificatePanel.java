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
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.Date;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
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

	private UserOidsPanel oidsPanel;

	private CertificatesTableModel tableModel;

	private JButton saveUserButton;

	private JButton editUserButton;

	private JButton newCertificateButton;

	private JButton revokeCertificateButton;

	private JTable table;

	public UserCertificatePanel(Frame owner) {
		super(new BorderLayout());

		this.owner = owner;
		oidsPanel = new UserOidsPanel();

		JPanel fp = new JPanel(new BorderLayout(Utils.MARGIN, Utils.MARGIN));
		fp.add(oidsPanel, BorderLayout.NORTH);

		JPanel fieldsPanel = new JPanel(new BorderLayout());
		fieldsPanel.add(new HeaderPanel(Translation.get("gb.properties"), "vcard_16x16.png"), BorderLayout.NORTH);
		fieldsPanel.add(fp, BorderLayout.CENTER);

		saveUserButton = new JButton(Translation.get("gb.save"));
		saveUserButton.setEnabled(false);
		saveUserButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				setEditable(false);
				String username = ucm.user.username;
				oidsPanel.updateUser(ucm);
				saveUser(username, ucm);
			}
		});

		editUserButton = new JButton(Translation.get("gb.edit"));
		editUserButton.setEnabled(false);
		editUserButton.addActionListener(new ActionListener() {
			@Override
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
			@Override
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
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					if (saveUserButton.isEnabled()) {
						// save changes
						String username = ucm.user.username;
						setEditable(false);
						oidsPanel.updateUser(ucm);
						saveUser(username, ucm);
					}

					NewClientCertificateDialog dialog = new NewClientCertificateDialog(UserCertificatePanel.this.owner,
							ucm.user.getDisplayName(), getDefaultExpiration(), isAllowEmail());
					dialog.setModal(true);
					dialog.setVisible(true);
					if (dialog.isCanceled()) {
						return;
					}

					final boolean sendEmail = dialog.sendEmail();
					final UserModel user = ucm.user;
					final X509Metadata metadata = new X509Metadata(user.username, dialog.getPassword());
					metadata.userDisplayname = user.getDisplayName();
					metadata.emailAddress = user.emailAddress;
					metadata.passwordHint = dialog.getPasswordHint();
					metadata.notAfter = dialog.getExpiration();

					AuthorityWorker worker = new AuthorityWorker(UserCertificatePanel.this.owner) {
						@Override
						protected Boolean doRequest() throws IOException {
							return newCertificate(ucm, metadata, sendEmail);
						}

						@Override
						protected void onSuccess() {
							JOptionPane.showMessageDialog(UserCertificatePanel.this.owner,
									MessageFormat.format(Translation.get("gb.clientCertificateGenerated"), user.getDisplayName()),
									Translation.get("gb.newCertificate"), JOptionPane.INFORMATION_MESSAGE);
						}
					};
					worker.execute();
				} catch (Exception x) {
					Utils.showException(UserCertificatePanel.this, x);
				}
			}
		});

		revokeCertificateButton = new JButton(Translation.get("gb.revokeCertificate"));
		revokeCertificateButton.setEnabled(false);
		revokeCertificateButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					int row = table.getSelectedRow();
					if (row < 0) {
						return;
					}
					int modelIndex = table.convertRowIndexToModel(row);
					final X509Certificate cert = tableModel.get(modelIndex);

					String [] choices = new String[RevocationReason.reasons.length];
					for (int i = 0; i < choices.length; i++) {
						choices[i] = Translation.get("gb." + RevocationReason.reasons[i].name());
					}

					Object choice = JOptionPane.showInputDialog(UserCertificatePanel.this.owner,
							Translation.get("gb.revokeCertificateReason"), Translation.get("gb.revokeCertificate"),
							JOptionPane.PLAIN_MESSAGE, new ImageIcon(getClass().getResource("/rosette_32x32.png")), choices, Translation.get("gb.unspecified"));
					if (choice == null) {
						return;
					}
					RevocationReason selection = RevocationReason.unspecified;
					for (int i = 0 ; i < choices.length; i++) {
						if (choices[i].equals(choice)) {
							selection = RevocationReason.reasons[i];
							break;
						}
					}
					final RevocationReason reason = selection;
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

						AuthorityWorker worker = new AuthorityWorker(UserCertificatePanel.this.owner) {

							@Override
							protected Boolean doRequest() throws IOException {
								return revoke(ucm, cert, reason);
							}

							@Override
							protected void onSuccess() {
								JOptionPane.showMessageDialog(UserCertificatePanel.this.owner,
										MessageFormat.format(Translation.get("gb.certificateRevoked"), cert.getSerialNumber(), cert.getIssuerDN().getName()),
										Translation.get("gb.revokeCertificate"), JOptionPane.INFORMATION_MESSAGE);
							}

						};
						worker.execute();
					}
				} catch (Exception x) {
					Utils.showException(UserCertificatePanel.this, x);
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

	public void setUserCertificateModel(UserCertificateModel ucm) {
		this.ucm = ucm;
		setEditable(false);
		oidsPanel.setUserCertificateModel(ucm);

		tableModel.setUserCertificateModel(ucm);
		tableModel.fireTableDataChanged();
		Utils.packColumns(table, Utils.MARGIN);
	}

	public void setEditable(boolean editable) {
		oidsPanel.setEditable(editable);

		editUserButton.setEnabled(!editable && ucm != null);
		saveUserButton.setEnabled(editable && ucm != null);

		newCertificateButton.setEnabled(ucm != null);
		revokeCertificateButton.setEnabled(false);
	}

	public abstract Date getDefaultExpiration();
	public abstract boolean isAllowEmail();

	public abstract boolean saveUser(String username, UserCertificateModel ucm);
	public abstract boolean newCertificate(UserCertificateModel ucm, X509Metadata metadata, boolean sendEmail);
	public abstract boolean revoke(UserCertificateModel ucm, X509Certificate cert, RevocationReason reason);
}
