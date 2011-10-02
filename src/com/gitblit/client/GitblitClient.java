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
import java.awt.EventQueue;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.MenuShortcut;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import com.gitblit.Constants;
import com.gitblit.utils.StringUtils;

public class GitblitClient extends JFrame {

	private static final long serialVersionUID = 1L;
	private JTabbedPane serverTabs;

	private GitblitClient() {
		super();
	}

	private void initialize() {
		setupMenu();
		setContentPane(getCenterPanel());

		setTitle("Gitblit Client v" + Constants.VERSION + " (" + Constants.VERSION_DATE + ")");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(800, 600);
		setLocationRelativeTo(null);
	}

	private void setupMenu() {
		MenuBar menuBar = new MenuBar();
		setMenuBar(menuBar);
		Menu serversMenu = new Menu("Servers");
		menuBar.add(serversMenu);
		MenuItem login = new MenuItem("Login...", new MenuShortcut(KeyEvent.VK_L, false));
		login.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				String url = JOptionPane.showInputDialog(GitblitClient.this,
						"Please enter Gitblit server URL", "https://localhost:8443");
				if (StringUtils.isEmpty(url)) {
					return;
				}
				login(url, "admin", "admin".toCharArray());
			}
		});
		serversMenu.add(login);
	}

	private JPanel getCenterPanel() {
		serverTabs = new JTabbedPane(JTabbedPane.TOP);
		JPanel panel = new JPanel(new BorderLayout());
		panel.add(serverTabs, BorderLayout.CENTER);
		return panel;
	}

	private void login(String url, String account, char[] password) {
		try {
			GitblitPanel panel = new GitblitPanel(url, account, password);
			panel.login();
			serverTabs.addTab(url.substring(url.indexOf("//") + 2), panel);
			serverTabs.setSelectedIndex(serverTabs.getTabCount() - 1);
		} catch (IOException e) {
			JOptionPane.showMessageDialog(GitblitClient.this, e.getMessage(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				GitblitClient frame = new GitblitClient();
				frame.initialize();
				frame.setVisible(true);
			}
		});
	}
}
