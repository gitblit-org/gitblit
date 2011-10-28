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
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.Base64;
import org.eclipse.jgit.util.FS;

import com.gitblit.Constants;
import com.gitblit.GitBlitException.ForbiddenException;
import com.gitblit.utils.StringUtils;

/**
 * Gitblit Manager issues JSON RPC requests to a Gitblit server.
 * 
 * @author James Moger
 * 
 */
public class GitblitManager extends JFrame implements RegistrationsDialog.RegistrationListener {

	private static final long serialVersionUID = 1L;
	private final SimpleDateFormat dateFormat;
	private JTabbedPane serverTabs;
	private File configFile = new File(System.getProperty("user.home"), ".gitblit/config");

	private Map<String, GitblitRegistration> registrations = new LinkedHashMap<String, GitblitRegistration>();
	private JMenu recentMenu;
	private int maxRecentCount = 5;

	private GitblitManager() {
		super();
		dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
		dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
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

			@Override
			public void windowOpened(WindowEvent event) {
				manageRegistrations();
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

	private JMenuBar setupMenu() {
		JMenuBar menuBar = new JMenuBar();
		JMenu serversMenu = new JMenu(Translation.get("gb.servers"));
		menuBar.add(serversMenu);
		recentMenu = new JMenu(Translation.get("gb.recent"));
		serversMenu.add(recentMenu);

		JMenuItem manage = new JMenuItem(Translation.get("gb.manage") + "...");
		manage.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, KeyEvent.CTRL_DOWN_MASK, false));
		manage.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				manageRegistrations();
			}
		});
		serversMenu.add(manage);

		return menuBar;
	}

	private JPanel getCenterPanel() {
		serverTabs = new JTabbedPane(JTabbedPane.TOP);
		JMenuBar menubar = setupMenu();
		JPanel panel = new JPanel(new BorderLayout());
		panel.add(menubar, BorderLayout.NORTH);
		panel.add(serverTabs, BorderLayout.CENTER);
		return panel;
	}

	private void manageRegistrations() {
		RegistrationsDialog dialog = new RegistrationsDialog(new ArrayList<GitblitRegistration>(
				registrations.values()), this);
		dialog.setLocationRelativeTo(GitblitManager.this);
		dialog.setVisible(true);
	}

	@Override
	public void login(GitblitRegistration reg) {
		if (!reg.savePassword && (reg.password == null || reg.password.length == 0)) {
			// prompt for password
			EditRegistrationDialog dialog = new EditRegistrationDialog(this, reg, true);
			dialog.setLocationRelativeTo(GitblitManager.this);
			dialog.setVisible(true);
			reg = dialog.getRegistration();
			if (reg == null) {
				// user canceled
				return;
			}
		}
		
		// login
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		final GitblitRegistration registration = reg;
		final GitblitPanel panel = new GitblitPanel(registration);
		SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {

			@Override
			protected Boolean doInBackground() throws IOException {
				panel.login();
				return true;
			}

			@Override
			protected void done() {
				try {
					boolean success = get();
					serverTabs.addTab(registration.name, panel);
					int idx = serverTabs.getTabCount() - 1;
					serverTabs.setSelectedIndex(idx);
					serverTabs.setTabComponentAt(idx, new ClosableTabComponent(registration.name,
							null, serverTabs, panel));
					registration.lastLogin = new Date();
					saveRegistration(registration.name, registration);
					registrations.put(registration.name, registration);
					rebuildRecentMenu();
					if (!registration.savePassword) {
						// clear password
						registration.password = null;
					}
				} catch (Throwable t) {
					Throwable cause = t.getCause();
					if (cause instanceof ConnectException) {
						JOptionPane.showMessageDialog(GitblitManager.this, cause.getMessage(),
								Translation.get("gb.error"), JOptionPane.ERROR_MESSAGE);
					} else if (cause instanceof ForbiddenException) {
						JOptionPane
								.showMessageDialog(
										GitblitManager.this,
										"This Gitblit server does not allow RPC Management or Administration",
										Translation.get("gb.error"), JOptionPane.ERROR_MESSAGE);
					} else {
						Utils.showException(GitblitManager.this, t);
					}
				} finally {
					setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
				}
			}
		};
		worker.execute();
	}

	private void rebuildRecentMenu() {
		recentMenu.removeAll();
		ImageIcon icon = new ImageIcon(getClass().getResource("/gitblt-favicon.png"));
		List<GitblitRegistration> list = new ArrayList<GitblitRegistration>(registrations.values());
		Collections.sort(list, new Comparator<GitblitRegistration>() {
			@Override
			public int compare(GitblitRegistration o1, GitblitRegistration o2) {
				return o2.lastLogin.compareTo(o1.lastLogin);
			}
		});
		if (list.size() > maxRecentCount) {
			list = list.subList(0, maxRecentCount);
		}
		for (int i = 0; i < list.size(); i++) {
			final GitblitRegistration reg = list.get(i);
			JMenuItem item = new JMenuItem(reg.name, icon);
			item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_1 + i, KeyEvent.CTRL_DOWN_MASK,
					false));
			item.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					login(reg);
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
				Date lastLogin = new Date(0);
				String date = config.getString("servers", server, "lastLogin");
				if (!StringUtils.isEmpty(date)) {
					lastLogin = dateFormat.parse(date);
				}
				String url = config.getString("servers", server, "url");
				String account = config.getString("servers", server, "account");
				char[] password;
				String pw = config.getString("servers", server, "password");
				if (StringUtils.isEmpty(pw)) {
					password = new char[0];
				} else {
					password = new String(Base64.decode(pw)).toCharArray();
				}
				GitblitRegistration reg = new GitblitRegistration(server, url, account, password);
				reg.lastLogin = lastLogin;
				registrations.put(reg.name, reg);
			}
		} catch (Throwable t) {
			Utils.showException(GitblitManager.this, t);
		}
	}

	@Override
	public boolean saveRegistration(String name, GitblitRegistration reg) {
		try {
			StoredConfig config = getConfig();
			if (!StringUtils.isEmpty(name) && !name.equals(reg.name)) {
				// delete old registration
				registrations.remove(name);
				config.unsetSection("servers", name);
			}

			// update registration
			config.setString("servers", reg.name, "url", reg.url);
			config.setString("servers", reg.name, "account", reg.account);
			if (reg.savePassword) {
				config.setString("servers", reg.name, "password",
						Base64.encodeBytes(new String(reg.password).getBytes("UTF-8")));
			} else {
				config.setString("servers", reg.name, "password", "");
			}
			if (reg.lastLogin != null) {
				config.setString("servers", reg.name, "lastLogin", dateFormat.format(reg.lastLogin));
			}
			config.save();
			return true;
		} catch (Throwable t) {
			Utils.showException(GitblitManager.this, t);
		}
		return false;
	}

	@Override
	public boolean deleteRegistrations(List<GitblitRegistration> list) {
		boolean success = false;
		try {
			StoredConfig config = getConfig();
			for (GitblitRegistration reg : list) {
				config.unsetSection("servers", reg.name);
				registrations.remove(reg.name);
			}
			config.save();
			success = true;
		} catch (Throwable t) {
			Utils.showException(GitblitManager.this, t);
		}
		return success;
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
