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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import com.gitblit.client.HeaderPanel;
import com.gitblit.client.Translation;
import com.gitblit.utils.StringUtils;

public class X509CertificateViewer extends JDialog {

	private static final long serialVersionUID = 1L;

	public X509CertificateViewer(Frame owner, X509Certificate cert) {
		super(owner);

		setTitle(Translation.get("gb.viewCertificate"));

		JPanel content = new JPanel(new BorderLayout(Utils.MARGIN, Utils.MARGIN)) {
			private static final long serialVersionUID = 1L;

			@Override
			public Insets getInsets() {

				return Utils.INSETS;
			}
		};

		DateFormat df = DateFormat.getDateTimeInstance();

		int l1 = 15;
		int l2 = 25;
		int l3 = 45;
		JPanel panel = new JPanel(new GridLayout(0, 1, 0, 2*Utils.MARGIN));
		panel.add(newField(Translation.get("gb.version"), "" + cert.getVersion(), 3));
		panel.add(newField(Translation.get("gb.subject"), cert.getSubjectDN().getName(), l3));
		panel.add(newField(Translation.get("gb.issuer"), cert.getIssuerDN().getName(), l3));
		panel.add(newField(Translation.get("gb.serialNumber"), "0x" + cert.getSerialNumber().toString(16), l2));
		panel.add(newField(Translation.get("gb.serialNumber"), cert.getSerialNumber().toString(), l2));
		panel.add(newField(Translation.get("gb.validFrom"), df.format(cert.getNotBefore()), l2));
		panel.add(newField(Translation.get("gb.validUntil"), df.format(cert.getNotAfter()), l2));
		panel.add(newField(Translation.get("gb.publicKey"), cert.getPublicKey().getAlgorithm(), l1));
		panel.add(newField(Translation.get("gb.signatureAlgorithm"), cert.getSigAlgName(), l1));
		try {
			panel.add(newField(Translation.get("gb.sha1FingerPrint"), fingerprint(StringUtils.getSHA1(cert.getEncoded())), l3));
		} catch (CertificateEncodingException e1) {
		}
		try {
			panel.add(newField(Translation.get("gb.md5FingerPrint"), fingerprint(StringUtils.getMD5(cert.getEncoded())), l3));
		} catch (CertificateEncodingException e1) {
		}

		content.add(panel, BorderLayout.CENTER);

		JButton ok = new JButton(Translation.get("gb.ok"));
		ok.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				setVisible(false);
			}
		});

		JPanel controls = new JPanel();
		controls.add(ok);

		content.add(controls, BorderLayout.SOUTH);

		getContentPane().add(new HeaderPanel(Translation.get("gb.certificate"), "rosette_16x16.png"), BorderLayout.NORTH);
		getContentPane().add(content, BorderLayout.CENTER);
		pack();

		setLocationRelativeTo(owner);
	}

	private JPanel newField(String label, String value, int cols) {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2*Utils.MARGIN, 0));
		JLabel lbl = new JLabel(label);
		lbl.setHorizontalAlignment(SwingConstants.RIGHT);
		lbl.setPreferredSize(new Dimension(125, 20));
		panel.add(lbl);
		JTextField tf = new JTextField(value, cols);
		tf.setCaretPosition(0);
		tf.setEditable(false);
		panel.add(tf);
		return panel;
	}

	private String fingerprint(String value) {
		value = value.toUpperCase();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < value.length(); i += 2) {
			sb.append(value.charAt(i));
			sb.append(value.charAt(i + 1));
			sb.append(':');
		}
		sb.setLength(sb.length() - 1);
		return sb.toString();
	}
}
