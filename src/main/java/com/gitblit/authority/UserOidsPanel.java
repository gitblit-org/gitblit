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

import java.awt.GridLayout;

import javax.swing.JPanel;
import javax.swing.JTextField;

import com.gitblit.client.Translation;

public class UserOidsPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private JTextField displayname;
	private JTextField username;
	private JTextField emailAddress;
	private JTextField organizationalUnit;
	private JTextField organization;
	private JTextField locality;
	private JTextField stateProvince;
	private JTextField countryCode;

	public UserOidsPanel() {
		super();

		displayname = new JTextField(20);
		username = new JTextField(20);
		username.setEditable(false);
		emailAddress = new JTextField(20);
		organizationalUnit = new JTextField(20);
		organization = new JTextField(20);
		locality = new JTextField(20);
		stateProvince = new JTextField(20);
		countryCode = new JTextField(20);

		setLayout(new GridLayout(0, 1, Utils.MARGIN, Utils.MARGIN));
		add(Utils.newFieldPanel(Translation.get("gb.displayName"), displayname));
		add(Utils.newFieldPanel(Translation.get("gb.username") + " (CN)", username));
		add(Utils.newFieldPanel(Translation.get("gb.emailAddress") + " (E)", emailAddress));
		add(Utils.newFieldPanel(Translation.get("gb.organizationalUnit") + " (OU)", organizationalUnit));
		add(Utils.newFieldPanel(Translation.get("gb.organization") + " (O)", organization));
		add(Utils.newFieldPanel(Translation.get("gb.locality") + " (L)", locality));
		add(Utils.newFieldPanel(Translation.get("gb.stateProvince") + " (ST)", stateProvince));
		add(Utils.newFieldPanel(Translation.get("gb.countryCode") + " (C)", countryCode));
	}

	public void setUserCertificateModel(UserCertificateModel ucm) {
		setEditable(false);
		displayname.setText(ucm == null ? "" : ucm.user.getDisplayName());
		username.setText(ucm == null ? "" : ucm.user.username);
		emailAddress.setText(ucm == null ? "" : ucm.user.emailAddress);
		organizationalUnit.setText(ucm == null ? "" : ucm.user.organizationalUnit);
		organization.setText(ucm == null ? "" : ucm.user.organization);
		locality.setText(ucm == null ? "" : ucm.user.locality);
		stateProvince.setText(ucm == null ? "" : ucm.user.stateProvince);
		countryCode.setText(ucm == null ? "" : ucm.user.countryCode);
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
	}

	protected void updateUser(UserCertificateModel ucm) {
		ucm.user.displayName = displayname.getText();
		ucm.user.username = username.getText();
		ucm.user.emailAddress = emailAddress.getText();
		ucm.user.organizationalUnit = organizationalUnit.getText();
		ucm.user.organization = organization.getText();
		ucm.user.locality = locality.getText();
		ucm.user.stateProvince = stateProvince.getText();
		ucm.user.countryCode = countryCode.getText();
	}
}
