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
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.Base64;
import org.eclipse.jgit.util.FS;

import com.gitblit.Constants;
import com.gitblit.utils.StringUtils;

/**
 * Gitblit Manager issues JSON RPC requests to a Gitblit server.
 * 
 * @author James Moger
 * 
 */
public class GitblitManager extends JFrame {

	private static final long serialVersionUID = 1L;
	private JTabbedPane serverTabs;
	private File configFile = new File(System.getProperty("user.home"), ".gitblit/config");
	private GitblitRegistration localhost = new GitblitRegistration("default",
			"https://localhost:8443", "admin", "admin".toCharArray());

	private Map<String, GitblitRegistration> registrations = new LinkedHashMap<String, GitblitRegistration>();
	private JMenu recentMenu;

	private GitblitManager() {
		super();
	}

	private void initialize() {
		setContentPane(getCenterPanel());
		setIconImage(new ImageIcon(getClass().getResource("/gitblt-favicon.png")).getImage());
		setTitle("Gitblit Manager v" + Constants.VERSION + " (" + Constants.VERSION_DATE + ")");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent event) {
				saveSizeAndPosition();
			}
		});

		setSizeAndPosition();
		loadRegistrations();
		rebuildRecentMenu();
	}

	private void setSizeAndPosition() {
		String sz = null;
		String pos = null;
		try {
			StoredConfig config = getConfig();
			sz = config.getString("ui", null, "size");
			pos = config.getString("ui", null, "position");
		} catch (Throwable t) {
			t.printStackTrace();
		}

		// try to restore saved window size
		if (StringUtils.isEmpty(sz)) {
			setSize(850, 500);
		} else {
			String[] chunks = sz.split("x");
			int width = Integer.parseInt(chunks[0]);
			int height = Integer.parseInt(chunks[1]);
			setSize(width, height);
		}

		// try to restore saved window position
		if (StringUtils.isEmpty(pos)) {
			setLocationRelativeTo(null);
		} else {
			String[] chunks = pos.split(",");
			int x = Integer.parseInt(chunks[0]);
			int y = Integer.parseInt(chunks[1]);
			setLocation(x, y);
		}
	}

	private void saveSizeAndPosition() {
		try {
			// save window size and position
			StoredConfig config = getConfig();
			Dimension sz = GitblitManager.this.getSize();
			config.setString("ui", null, "size",
					MessageFormat.format("{0,number,0}x{1,number,0}", sz.width, sz.height));
			Point pos = GitblitManager.this.getLocationOnScreen();
			config.setString("ui", null, "position",
					MessageFormat.format("{0,number,0},{1,number,0}", pos.x, pos.y));
			config.save();
		} catch (Throwable t) {
			Utils.showException(GitblitManager.this, t);
		}
	}

	public void setVisible(boolean value) {
		if (value) {
			if (registrations.size() == 0) {
				// default prompt
				loginPrompt(localhost);
			} else if (registrations.size() == 1) {
				// single registration prompt
				GitblitRegistration reg = registrations.values().iterator().next();
				loginPrompt(reg);
			}
			super.setVisible(value);
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
		registrations.put(reg.name, reg);
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
			saveRegistration(reg);
			return true;
		} catch (IOException e) {
			JOptionPane.showMessageDialog(GitblitManager.this, e.getMessage(),
					Translation.get("gb.error"), JOptionPane.ERROR_MESSAGE);
		}
		return false;
	}

	private void rebuildRecentMenu() {
		recentMenu.removeAll();
		ImageIcon icon = new ImageIcon(getClass().getResource("/gitblt-favicon.png"));
		for (final GitblitRegistration reg : registrations.values()) {
			JMenuItem item = new JMenuItem(reg.name, icon);
			item.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					loginPrompt(reg);
				}
			});
			recentMenu.add(item);
		}
	}

	private void loadRegistrations() {
		try {
			StoredConfig config = getConfig();
			Set<String> servers = config.getSubsections("servers");
			for (String server : servers) {
				String url = config.getString("servers", server, "url");
				String account = config.getString("servers", server, "account");
				char[] password = new String(Base64.decode(config.getString("servers", server,
						"password"))).toCharArray();
				GitblitRegistration reg = new GitblitRegistration(server, url, account, password);
				registrations.put(reg.name, reg);
			}
		} catch (Throwable t) {
			Utils.showException(GitblitManager.this, t);
		}
	}

	private void saveRegistration(GitblitRegistration reg) {
		try {
			StoredConfig config = getConfig();
			config.setString("servers", reg.name, "url", reg.url);
			config.setString("servers", reg.name, "account", reg.account);
			config.setString("servers", reg.name, "password",
					Base64.encodeBytes(new String(reg.password).getBytes("UTF-8")));
			config.save();
		} catch (Throwable t) {
			Utils.showException(GitblitManager.this, t);
		}
	}

	private StoredConfig getConfig() throws IOException, ConfigInvalidException {
		FileBasedConfig config = new FileBasedConfig(configFile, FS.detect());
		config.load();
		return config;
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
