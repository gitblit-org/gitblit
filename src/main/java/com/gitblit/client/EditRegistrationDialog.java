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
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import com.gitblit.utils.StringUtils;

/**
 * Dialog to create or edit a Gitblit registration.
 * 
 * @author James Moger
 * 
 */
public class EditRegistrationDialog extends JDialog {

	private static final long serialVersionUID = 1L;
	private JTextField urlField;
	private JTextField nameField;
	private JTextField accountField;
	private JPasswordField passwordField;
	private JCheckBox savePassword;
	private boolean canceled;
	private HeaderPanel headerPanel;

	public EditRegistrationDialog(Window owner) {
		this(owner, null, false);
	}

	public EditRegistrationDialog(Window owner, GitblitRegistration reg, boolean isLogin) {
		super(owner);
		initialize(reg, isLogin);
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

	private void initialize(GitblitRegistration reg, boolean isLogin) {
		setIconImage(new ImageIcon(getClass().getResource("/gitblt-favicon.png")).getImage());
		canceled = true;
		urlField = new JTextField(reg == null ? "" : reg.url, 30);
		nameField = new JTextField(reg == null ? "" : reg.name);
		accountField = new JTextField(reg == null ? "" : reg.account);
		passwordField = new JPasswordField(reg == null ? "" : new String(reg.password));
		savePassword = new JCheckBox("save password (passwords are NOT encrypted!)");
		savePassword.setSelected(reg == null ? false
				: (reg.password != null && reg.password.length > 0));

		JPanel panel = new JPanel(new GridLayout(0, 1, 5, 5));
		panel.add(newLabelPanel(Translation.get("gb.name"), nameField));
		panel.add(newLabelPanel(Translation.get("gb.url"), urlField));
		panel.add(newLabelPanel(Translation.get("gb.username"), accountField));
		panel.add(newLabelPanel(Translation.get("gb.password"), passwordField));
		panel.add(newLabelPanel("", savePassword));

		JButton cancel = new JButton(Translation.get("gb.cancel"));
		cancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				setVisible(false);
			}
		});

		final JButton save = new JButton(Translation.get(isLogin ? "gb.login" : "gb.save"));
		save.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				if (validateFields()) {
					canceled = false;
					setVisible(false);
				}
			}
		});

		// on enter in password field, save or login
		passwordField.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				save.doClick();
			}
		});

		JPanel controls = new JPanel();
		controls.add(cancel);
		controls.add(save);

		if (reg == null) {
			this.setTitle(Translation.get("gb.create"));
			headerPanel = new HeaderPanel(Translation.get("gb.create"), null);
		} else {
			this.setTitle(Translation.get(isLogin ? "gb.login" : "gb.edit"));
			headerPanel = new HeaderPanel(reg.name, null);
		}

		final Insets insets = new Insets(5, 5, 5, 5);
		JPanel centerPanel = new JPanel(new BorderLayout(5, 5)) {

			private static final long serialVersionUID = 1L;

			@Override
			public Insets getInsets() {
				return insets;
			}
		};
		centerPanel.add(headerPanel, BorderLayout.NORTH);
		centerPanel.add(panel, BorderLayout.CENTER);
		centerPanel.add(controls, BorderLayout.SOUTH);

		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(centerPanel, BorderLayout.CENTER);
		pack();
		setModal(true);
		if (isLogin) {
			passwordField.requestFocus();
		}
	}

	private JPanel newLabelPanel(String text, JComponent field) {
		JLabel label = new JLabel(text);
		label.setFont(label.getFont().deriveFont(Font.BOLD));
		label.setPreferredSize(new Dimension(75, 10));
		JPanel jpanel = new JPanel(new BorderLayout());
		jpanel.add(label, BorderLayout.WEST);
		jpanel.add(field, BorderLayout.CENTER);
		return jpanel;
	}

	private boolean validateFields() {
		String name = nameField.getText();
		if (StringUtils.isEmpty(name)) {
			error("Please enter a name for this registration!");
			return false;
		}
		String url = urlField.getText();
		if (StringUtils.isEmpty(url)) {
			error("Please enter a url for this registration!");
			return false;
		}
		return true;
	}

	private void error(String message) {
		JOptionPane.showMessageDialog(EditRegistrationDialog.this, message,
				Translation.get("gb.error"), JOptionPane.ERROR_MESSAGE);
	}

	public GitblitRegistration getRegistration() {
		if (canceled) {
			return null;
		}
		GitblitRegistration reg = new GitblitRegistration(nameField.getText(), urlField.getText(),
				accountField.getText(), passwordField.getPassword());
		reg.savePassword = savePassword.isSelected();
		return reg;
	}
}
