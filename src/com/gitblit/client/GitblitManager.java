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
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.UIManager;

import com.gitblit.Constants;
import com.gitblit.utils.StringUtils;

/**
 * Sample RPC application.
 * 
 * @author James Moger
 * 
 */
public class GitblitManager extends JFrame {

	private static final long serialVersionUID = 1L;
	private JTabbedPane serverTabs;
	private GitblitRegistration localhost = new GitblitRegistration("default",
			"https://localhost:8443", "admin", "admin".toCharArray());

	private List<GitblitRegistration> registrations = new ArrayList<GitblitRegistration>();
	private JMenu recentMenu;

	private GitblitManager() {
		super();
	}

	private void initialize() {
		setContentPane(getCenterPanel());
		setIconImage(new ImageIcon(getClass().getResource("/gitblt-favicon.png")).getImage());

		setTitle("Gitblit Manager v" + Constants.VERSION + " (" + Constants.VERSION_DATE + ")");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(800, 500);
	}

	public void setVisible(boolean value) {
		if (value) {
			if (registrations.size() == 0) {
				// default prompt
				loginPrompt(localhost);
			} else if (registrations.size() == 1) {
				// single registration prompt
				loginPrompt(registrations.get(0));
			}
			super.setVisible(value);
			setLocationRelativeTo(null);
		}
	}

	private JMenuBar setupMenu() {
		JMenuBar menuBar = new JMenuBar();
		JMenu serversMenu = new JMenu(Translation.get("gb.servers"));
		menuBar.add(serversMenu);
		recentMenu = new JMenu(Translation.get("gb.recent"));
		serversMenu.add(recentMenu);
		JMenuItem login = new JMenuItem(Translation.get("gb.login") + "...");
		login.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK, false));
		login.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				loginPrompt(localhost);
			}
		});
		serversMenu.add(login);
		return menuBar;
	}

	private JPanel newLabelPanel(String text, JTextField field) {
		JLabel label = new JLabel(text);
		label.setFont(label.getFont().deriveFont(Font.BOLD));
		label.setPreferredSize(new Dimension(75, 10));
		JPanel jpanel = new JPanel(new BorderLayout());
		jpanel.add(label, BorderLayout.WEST);
		jpanel.add(field, BorderLayout.CENTER);
		return jpanel;
	}

	private JPanel getCenterPanel() {
		serverTabs = new JTabbedPane(JTabbedPane.TOP);
		JMenuBar menubar = setupMenu();
		JPanel panel = new JPanel(new BorderLayout());
		panel.add(menubar, BorderLayout.NORTH);
		panel.add(serverTabs, BorderLayout.CENTER);
		return panel;
	}

	private boolean loginPrompt(GitblitRegistration reg) {
		JTextField urlField = new JTextField(reg.url, 30);
		JTextField nameField = new JTextField(reg.name);
		JTextField accountField = new JTextField(reg.account);
		JPasswordField passwordField = new JPasswordField(new String(reg.password));

		JPanel panel = new JPanel(new GridLayout(0, 1, 5, 5));
		panel.add(newLabelPanel(Translation.get("gb.name"), nameField));
		panel.add(newLabelPanel(Translation.get("gb.url"), urlField));
		panel.add(newLabelPanel(Translation.get("gb.username"), accountField));
		panel.add(newLabelPanel(Translation.get("gb.password"), passwordField));

		int result = JOptionPane.showConfirmDialog(GitblitManager.this, panel,
				Translation.get("gb.login"), JOptionPane.OK_CANCEL_OPTION);
		if (result != JOptionPane.OK_OPTION) {
			return false;
		}
		String url = urlField.getText();
		if (StringUtils.isEmpty(url)) {
			return false;
		}
		reg = new GitblitRegistration(nameField.getText(), url, accountField.getText(),
				passwordField.getPassword());
		boolean success = login(reg);
		registrations.add(0, reg);
		rebuildRecentMenu();
		return success;
	}

	private boolean login(GitblitRegistration reg) {
		try {
			GitblitPanel panel = new GitblitPanel(reg);
			panel.login();
			serverTabs.addTab(reg.name, panel);
			int idx = serverTabs.getTabCount() - 1;
			serverTabs.setSelectedIndex(idx);
			serverTabs.setTabComponentAt(idx, new ClosableTabComponent(reg.name, null, serverTabs,
					panel));
			return true;
		} catch (IOException e) {
			JOptionPane.showMessageDialog(GitblitManager.this, e.getMessage(),
					Translation.get("gb.error"), JOptionPane.ERROR_MESSAGE);
		}
		return false;
	}

	private void rebuildRecentMenu() {
		recentMenu.removeAll();
		for (final GitblitRegistration reg : registrations) {
			JMenuItem item = new JMenuItem(reg.name);
			item.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					login(reg);
				}
			});
			recentMenu.add(item);
		}
	}

	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
				} catch (Exception e) {
				}
				GitblitManager frame = new GitblitManager();
				frame.initialize();
				frame.setVisible(true);
			}
		});
	}
}
