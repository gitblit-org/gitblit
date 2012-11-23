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
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableRowSorter;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

import com.gitblit.ConfigUserService;
import com.gitblit.Constants;
import com.gitblit.FileSettings;
import com.gitblit.IStoredSettings;
import com.gitblit.IUserService;
import com.gitblit.Keys;
import com.gitblit.MailExecutor;
import com.gitblit.client.HeaderPanel;
import com.gitblit.client.Translation;
import com.gitblit.models.UserModel;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.X509Utils;
import com.gitblit.utils.X509Utils.RevocationReason;
import com.gitblit.utils.X509Utils.X509Metadata;

/**
 * Simple GUI tool for administering Gitblit client certificates.
 * 
 * @author James Moger
 *
 */
public class GitblitAuthority extends JFrame {

	private static final long serialVersionUID = 1L;
	
	private final UserCertificateTableModel tableModel;

	private UserCertificatePanel userCertificatePanel;
	
	private File folder;
	
	private IStoredSettings gitblitSettings;
	
	private IUserService userService;
	
	private String caKeystorePassword = null;

	private JTable table;
	
	private int defaultDuration;
	
	private TableRowSorter<UserCertificateTableModel> defaultSorter;

	public static void main(String... args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
				} catch (Exception e) {
				}
				GitblitAuthority authority = new GitblitAuthority();
				authority.initialize();
				authority.setLocationRelativeTo(null);
				authority.setVisible(true);
			}
		});
	}

	public GitblitAuthority() {
		super();
		tableModel = new UserCertificateTableModel();
		defaultSorter = new TableRowSorter<UserCertificateTableModel>(tableModel);
	}
	
	public void initialize() {
		setIconImage(new ImageIcon(getClass().getResource("/gitblt-favicon.png")).getImage());
		setTitle("Gitblit Certificate Authority v" + Constants.VERSION + " (" + Constants.VERSION_DATE + ")");
		setContentPane(getUI());
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent event) {
				saveSizeAndPosition();
			}

			@Override
			public void windowOpened(WindowEvent event) {
			}
		});		

		setSizeAndPosition();
		
		File folder = new File(System.getProperty("user.dir"));
		load(folder);
	}
	
	private void setSizeAndPosition() {
		String sz = null;
		String pos = null;
		try {
			StoredConfig config = getConfig();
			sz = config.getString("ui", null, "size");
			pos = config.getString("ui", null, "position");
			defaultDuration = config.getInt("new",  "duration", 365);
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
			Dimension sz = GitblitAuthority.this.getSize();
			config.setString("ui", null, "size",
					MessageFormat.format("{0,number,0}x{1,number,0}", sz.width, sz.height));
			Point pos = GitblitAuthority.this.getLocationOnScreen();
			config.setString("ui", null, "position",
					MessageFormat.format("{0,number,0},{1,number,0}", pos.x, pos.y));
			config.save();
		} catch (Throwable t) {
			Utils.showException(GitblitAuthority.this, t);
		}
	}
	
	private StoredConfig getConfig() throws IOException, ConfigInvalidException {
		File configFile  = new File(System.getProperty("user.dir"), X509Utils.CA_CONFIG);
		FileBasedConfig config = new FileBasedConfig(configFile, FS.detect());
		config.load();
		return config;
	}
	
	private IUserService loadUsers(File folder) {
		File file = new File(folder, "gitblit.properties");
		if (!file.exists()) {
			return null;
		}
		gitblitSettings = new FileSettings(file.getAbsolutePath());
		caKeystorePassword = gitblitSettings.getString(Keys.server.storePassword, null);
		String us = gitblitSettings.getString(Keys.realm.userService, "users.conf");
		String ext = us.substring(us.lastIndexOf(".") + 1).toLowerCase();
		IUserService service = null;
		if (!ext.equals("conf") && !ext.equals("properties")) {
			if (us.equals("com.gitblit.LdapUserService")) {
				us = gitblitSettings.getString(Keys.realm.ldap.backingUserService, "users.conf");		
			} else if (us.equals("com.gitblit.LdapUserService")) {
				us = gitblitSettings.getString(Keys.realm.redmine.backingUserService, "users.conf");
			}
		}

		if (us.endsWith(".conf")) {
			service = new ConfigUserService(new File(us));
		} else {
			throw new RuntimeException("Unsupported user service: " + us);
		}
		
		service = new ConfigUserService(new File(us));
		return service;
	}
	
	private void load(File folder) {
		this.folder = folder;
		this.userService = loadUsers(folder);
		if (userService != null) {
			// build empty certificate model for all users
			Map<String, UserCertificateModel> map = new HashMap<String, UserCertificateModel>();
			for (String user : userService.getAllUsernames()) {
				UserModel model = userService.getUserModel(user);
				UserCertificateModel ucm = new UserCertificateModel(model);				
				map.put(user, ucm);
			}
			File certificatesConfigFile = new File(folder, X509Utils.CA_CONFIG);
			FileBasedConfig config = new FileBasedConfig(certificatesConfigFile, FS.detect());
			if (certificatesConfigFile.exists()) {
				try {
					config.load();
					// replace user certificate model with actual data
					List<UserCertificateModel> list = UserCertificateConfig.KEY.parse(config).list;					
					for (UserCertificateModel ucm : list) {						
						ucm.user = userService.getUserModel(ucm.user.username);
						map.put(ucm.user.username, ucm);
					}
				} catch (IOException e) {
					e.printStackTrace();
				} catch (ConfigInvalidException e) {
					e.printStackTrace();
				}
			}
			
			tableModel.list = new ArrayList<UserCertificateModel>(map.values());
			Collections.sort(tableModel.list);
			tableModel.fireTableDataChanged();
		}
	}
	
	private List<X509Certificate> findCerts(File folder, String username) {
		List<X509Certificate> list = new ArrayList<X509Certificate>();
		File userFolder = new File(folder, X509Utils.CERTS + File.separator + username);
		if (!userFolder.exists()) {
			return list;
		}
		File [] certs = userFolder.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".cer") || name.toLowerCase().endsWith(".crt");
			}
		});
		try {
			CertificateFactory factory = CertificateFactory.getInstance("X.509");
			for (File cert : certs) {				
				BufferedInputStream is = new BufferedInputStream(new FileInputStream(cert));
				X509Certificate x509 = (X509Certificate) factory.generateCertificate(is);
				is.close();
				list.add(x509);
			}
		} catch (Exception e) {
			Utils.showException(GitblitAuthority.this, e);
		}
		return list;
	}
	
	private Container getUI() {		
		userCertificatePanel = new UserCertificatePanel(this) {
			
			private static final long serialVersionUID = 1L;
			@Override
			public Insets getInsets() {
				return Utils.INSETS;
			}

			@Override
			public Date getDefaultExpiration() {
				Calendar c = Calendar.getInstance();
				c.add(Calendar.DATE, defaultDuration);
				c.set(Calendar.HOUR_OF_DAY, 0);
				c.set(Calendar.MINUTE, 0);
				c.set(Calendar.SECOND, 0);
				c.set(Calendar.MILLISECOND, 0);
				return c.getTime();
			}
			
			@Override
			public void saveUser(String username, UserCertificateModel ucm) {
				userService.updateUserModel(username, ucm.user);
			}
			
			@Override
			public void newCertificate(UserCertificateModel ucm, X509Metadata metadata, boolean sendEmail) {
				Date notAfter = metadata.notAfter;
				metadata.serverHostname = gitblitSettings.getString(Keys.web.siteName, "localhost");
				UserModel user = ucm.user;				
				
				// set default values from config file
				File certificatesConfigFile = new File(folder, X509Utils.CA_CONFIG);
				FileBasedConfig config = new FileBasedConfig(certificatesConfigFile, FS.detect());
				if (certificatesConfigFile.exists()) {
					try {
						config.load();
					} catch (Exception e) {
						Utils.showException(GitblitAuthority.this, e);
					}
					NewCertificateConfig certificateConfig = NewCertificateConfig.KEY.parse(config);
					certificateConfig.update(metadata);
				}
				
				// restore expiration date
				metadata.notAfter = notAfter;
				
				// set user's specified OID values
				if (!StringUtils.isEmpty(user.organizationalUnit)) {
					metadata.oids.put("OU", user.organizationalUnit);
				}
				if (!StringUtils.isEmpty(user.organization)) {
					metadata.oids.put("O", user.organization);
				}
				if (!StringUtils.isEmpty(user.locality)) {
					metadata.oids.put("L", user.locality);
				}
				if (!StringUtils.isEmpty(user.stateProvince)) {
					metadata.oids.put("ST", user.stateProvince);
				}
				if (!StringUtils.isEmpty(user.countryCode)) {
					metadata.oids.put("C", user.countryCode);
				}

				File caKeystoreFile = new File(folder, X509Utils.CA_KEY_STORE);
				File zip = X509Utils.newClientBundle(metadata, caKeystoreFile, caKeystorePassword);
				
				// save latest expiration date
				if (ucm.expires == null || metadata.notAfter.after(ucm.expires)) {
					ucm.expires = metadata.notAfter;
				}
				ucm.update(config);
				try {
					config.save();
				} catch (Exception e) {
					Utils.showException(GitblitAuthority.this, e);
				}
				
				// refresh user
				ucm.certs = null;
				int modelIndex = table.convertRowIndexToModel(table.getSelectedRow());
				tableModel.fireTableDataChanged();
				table.getSelectionModel().setSelectionInterval(modelIndex, modelIndex);
				
				if (sendEmail) {
					// send email
					try {
						MailExecutor mail = new MailExecutor(gitblitSettings);
						if (mail.isReady()) {
							Message message = mail.createMessage(user.emailAddress);
							message.setSubject("Your Gitblit client certificate for " + metadata.serverHostname);

							// body of email
							String body = X509Utils.processTemplate(new File(caKeystoreFile.getParentFile(), "mail.tmpl"), metadata);
							if (StringUtils.isEmpty(body)) {
								body = MessageFormat.format("Hi {0}\n\nHere is your client certificate bundle.\nInside the zip file are installation instructions.", user.getDisplayName());
							}
							Multipart mp = new MimeMultipart();
							MimeBodyPart messagePart = new MimeBodyPart();
							messagePart.setText(body);
							mp.addBodyPart(messagePart);

							// attach zip
							MimeBodyPart filePart = new MimeBodyPart();
							FileDataSource fds = new FileDataSource(zip);
							filePart.setDataHandler(new DataHandler(fds));
							filePart.setFileName(fds.getName());
							mp.addBodyPart(filePart);

							message.setContent(mp);

							mail.sendNow(message);
						} else {
							JOptionPane.showMessageDialog(GitblitAuthority.this, "Sorry, the mail server settings are not configured properly.\nCan not send email.", Translation.get("gb.error"), JOptionPane.ERROR_MESSAGE);
						}
					} catch (Exception e) {
						Utils.showException(GitblitAuthority.this, e);
					}
				}
			}
			
			@Override
			public void revoke(UserCertificateModel ucm, X509Certificate cert, RevocationReason reason) {
				File caRevocationList = new File(folder, X509Utils.CA_REVOCATION_LIST);
				File caKeystoreFile = new File(folder, X509Utils.CA_KEY_STORE);
				if (X509Utils.revoke(cert, reason, caRevocationList, caKeystoreFile, caKeystorePassword)) {
					File certificatesConfigFile = new File(folder, X509Utils.CA_CONFIG);
					FileBasedConfig config = new FileBasedConfig(certificatesConfigFile, FS.detect());
					if (certificatesConfigFile.exists()) {
						try {
							config.load();
						} catch (Exception e) {
							Utils.showException(GitblitAuthority.this, e);
						}
					}
					// add serial to revoked list
					ucm.revoke(cert.getSerialNumber(), reason);
					ucm.update(config);
					try {
						config.save();
					} catch (Exception e) {
						Utils.showException(GitblitAuthority.this, e);
					}
					
					// refresh user
					ucm.certs = null;
					int modelIndex = table.convertRowIndexToModel(table.getSelectedRow());
					tableModel.fireTableDataChanged();
					table.getSelectionModel().setSelectionInterval(modelIndex, modelIndex);
				}
			}
		};
		
		table = Utils.newTable(tableModel, Utils.DATE_FORMAT);
		table.setRowSorter(defaultSorter);
		table.setDefaultRenderer(CertificateStatus.class, new CertificateStatusRenderer());
		table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

			@Override
			public void valueChanged(ListSelectionEvent e) {
				if (e.getValueIsAdjusting()) {
					return;
				}
				int row = table.getSelectedRow();
				if (row < 0) {
					return;
				}
				int modelIndex = table.convertRowIndexToModel(row);
				UserCertificateModel ucm = tableModel.get(modelIndex);
				if (ucm.certs == null) {
					ucm.certs = findCerts(folder, ucm.user.username);
				}
				userCertificatePanel.setUserCertificateModel(ucm);
			}
		});
		
		JPanel usersPanel = new JPanel(new BorderLayout()) {
			
			private static final long serialVersionUID = 1L;

			@Override
			public Insets getInsets() {
				return Utils.INSETS;
			}
		};
		usersPanel.add(new HeaderPanel(Translation.get("gb.users"), "users_16x16.png"), BorderLayout.NORTH);
		usersPanel.add(new JScrollPane(table), BorderLayout.CENTER);
		usersPanel.setMinimumSize(new Dimension(400, 10));
		
		final JTextField filterTextfield = new JTextField(20);
		filterTextfield.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				filterUsers(filterTextfield.getText());
			}
		});
		filterTextfield.addKeyListener(new KeyAdapter() {
			public void keyReleased(KeyEvent e) {
				filterUsers(filterTextfield.getText());
			}
		});

		JPanel userControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
		userControls.add(new JLabel(Translation.get("gb.filter")));
		userControls.add(filterTextfield);
		
		JPanel leftPanel = new JPanel(new BorderLayout());
		leftPanel.add(userControls, BorderLayout.NORTH);
		leftPanel.add(usersPanel, BorderLayout.CENTER);
		
		userCertificatePanel.setMinimumSize(new Dimension(375, 10));
		
		JPanel root = new JPanel(new BorderLayout()) {
			private static final long serialVersionUID = 1L;
			public Insets getInsets() {
				return Utils.INSETS;
			}
		};
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, userCertificatePanel);
		splitPane.setDividerLocation(1d);
		root.add(splitPane);
		return root;
	}
	
	private void filterUsers(final String fragment) {
		if (StringUtils.isEmpty(fragment)) {
			table.setRowSorter(defaultSorter);
			return;
		}
		RowFilter<UserCertificateTableModel, Object> containsFilter = new RowFilter<UserCertificateTableModel, Object>() {
			public boolean include(Entry<? extends UserCertificateTableModel, ? extends Object> entry) {
				for (int i = entry.getValueCount() - 1; i >= 0; i--) {
					if (entry.getStringValue(i).toLowerCase().contains(fragment.toLowerCase())) {
						return true;
					}
				}
				return false;
			}
		};
		TableRowSorter<UserCertificateTableModel> sorter = new TableRowSorter<UserCertificateTableModel>(
				tableModel);
		sorter.setRowFilter(containsFilter);
		table.setRowSorter(sorter);
	}
}
