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
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URI;
import java.security.PrivateKey;
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
import java.util.ResourceBundle;

import javax.mail.Message;
import javax.swing.ImageIcon;
import javax.swing.InputVerifier;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableRowSorter;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.slf4j.LoggerFactory;

import com.gitblit.ConfigUserService;
import com.gitblit.Constants;
import com.gitblit.FileSettings;
import com.gitblit.IStoredSettings;
import com.gitblit.IUserService;
import com.gitblit.Keys;
import com.gitblit.client.HeaderPanel;
import com.gitblit.client.Translation;
import com.gitblit.models.Mailing;
import com.gitblit.models.UserModel;
import com.gitblit.service.MailService;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.FileUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;
import com.gitblit.utils.X509Utils;
import com.gitblit.utils.X509Utils.RevocationReason;
import com.gitblit.utils.X509Utils.X509Log;
import com.gitblit.utils.X509Utils.X509Metadata;
import com.gitblit.wicket.GitBlitWebSession;

/**
 * Simple GUI tool for administering Gitblit client certificates.
 *
 * @author James Moger
 *
 */
public class GitblitAuthority extends JFrame implements X509Log {

	private static final long serialVersionUID = 1L;

	private final UserCertificateTableModel tableModel;

	private UserCertificatePanel userCertificatePanel;

	private File folder;

	private IStoredSettings gitblitSettings;

	private IUserService userService;

	private String caKeystorePassword;

	private JTable table;

	private int defaultDuration;

	private TableRowSorter<UserCertificateTableModel> defaultSorter;

	private MailService mail;

	private JButton certificateDefaultsButton;

	private JButton newSSLCertificate;

	public static void main(String... args) {
		// filter out the baseFolder parameter
		String folder = "data";
		for (int i = 0; i< args.length; i++) {
			String arg = args[i];
			if (arg.equals("--baseFolder")) {
				if (i + 1 == args.length) {
					System.out.println("Invalid --baseFolder parameter!");
					System.exit(-1);
				} else if (!".".equals(args[i + 1])) {
					folder = args[i+1];
				}
				break;
			}
		}
		final String baseFolder = folder;
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
				} catch (Exception e) {
				}
				GitblitAuthority authority = new GitblitAuthority();
				authority.initialize(baseFolder);
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

	public void initialize(String baseFolder) {
		setIconImage(new ImageIcon(getClass().getResource("/gitblt-favicon.png")).getImage());
		setTitle("Gitblit Certificate Authority v" + Constants.getVersion() + " (" + Constants.getBuildDate() + ")");
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

		File folder = new File(baseFolder).getAbsoluteFile();
		load(folder);

		setSizeAndPosition();
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
			setSize(900, 600);
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
		File configFile  = new File(folder, X509Utils.CA_CONFIG);
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
		mail = new MailService(gitblitSettings);
		String us = gitblitSettings.getString(Keys.realm.userService, "${baseFolder}/users.conf");
		String ext = us.substring(us.lastIndexOf(".") + 1).toLowerCase();
		IUserService service = null;
		if (!ext.equals("conf") && !ext.equals("properties") && ext.contains("userservice")) {
			String realm = ext.substring(0, ext.indexOf("userservice"));
			us = gitblitSettings.getString(MessageFormat.format("realm.{0}.backingUserService", realm), "${baseFolder}/users.conf");
		}

		if (us.endsWith(".conf")) {
			service = new ConfigUserService(FileUtils.resolveParameter(Constants.baseFolder$, folder, us));
		} else {
			throw new RuntimeException("Unsupported user service: " + us);
		}

		service = new ConfigUserService(FileUtils.resolveParameter(Constants.baseFolder$, folder, us));
		return service;
	}

	private void load(File folder) {
		this.folder = folder;
		this.userService = loadUsers(folder);
		System.out.println(Constants.baseFolder$ + " set to " + folder);
		if (userService == null) {
			JOptionPane.showMessageDialog(this, MessageFormat.format("Sorry, {0} doesn't look like a Gitblit GO installation.", folder));
		} else {
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
			Utils.packColumns(table, Utils.MARGIN);

			File caKeystore = new File(folder, X509Utils.CA_KEY_STORE);
			if (!caKeystore.exists()) {

				if (!X509Utils.unlimitedStrength) {
					// prompt to confirm user understands JCE Standard Strength encryption
					int res = JOptionPane.showConfirmDialog(GitblitAuthority.this, Translation.get("gb.jceWarning"),
							Translation.get("gb.warning"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
					if (res != JOptionPane.YES_OPTION) {
						if (Desktop.isDesktopSupported()) {
							if (Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
								try {
									Desktop.getDesktop().browse(URI.create("http://www.oracle.com/technetwork/java/javase/downloads/index.html"));
								} catch (IOException e) {
								}
							}
						}
						System.exit(1);
					}
				}

				// show certificate defaults dialog
				certificateDefaultsButton.doClick();

				// create "localhost" ssl certificate
				prepareX509Infrastructure();
			}
		}
	}

	private boolean prepareX509Infrastructure() {
		if (caKeystorePassword == null) {
			JPasswordField pass = new JPasswordField(10);
			pass.setText(caKeystorePassword);
			pass.addAncestorListener(new RequestFocusListener());
			JPanel panel = new JPanel(new BorderLayout());
			panel.add(new JLabel(Translation.get("gb.enterKeystorePassword")), BorderLayout.NORTH);
			panel.add(pass, BorderLayout.CENTER);
			int result = JOptionPane.showConfirmDialog(GitblitAuthority.this, panel, Translation.get("gb.password"), JOptionPane.OK_CANCEL_OPTION);
			if (result == JOptionPane.OK_OPTION) {
				caKeystorePassword = new String(pass.getPassword());
			} else {
				return false;
			}
		}

		X509Metadata metadata = new X509Metadata("localhost", caKeystorePassword);
		setMetadataDefaults(metadata);
		metadata.notAfter = new Date(System.currentTimeMillis() + 10*TimeUtils.ONEYEAR);
		X509Utils.prepareX509Infrastructure(metadata, folder, this);
		return true;
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
			public boolean isAllowEmail() {
				return mail.isReady();
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
			public boolean saveUser(String username, UserCertificateModel ucm) {
				return userService.updateUserModel(username, ucm.user);
			}

			@Override
			public boolean newCertificate(UserCertificateModel ucm, X509Metadata metadata, boolean sendEmail) {
				if (!prepareX509Infrastructure()) {
					return false;
				}

				Date notAfter = metadata.notAfter;
				setMetadataDefaults(metadata);
				metadata.notAfter = notAfter;

				// set user's specified OID values
				UserModel user = ucm.user;
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
				File zip = X509Utils.newClientBundle(user,metadata, caKeystoreFile, caKeystorePassword, GitblitAuthority.this);

				// save latest expiration date
				if (ucm.expires == null || metadata.notAfter.before(ucm.expires)) {
					ucm.expires = metadata.notAfter;
				}

				updateAuthorityConfig(ucm);

				// refresh user
				ucm.certs = null;
				int selectedIndex = table.getSelectedRow();
				tableModel.fireTableDataChanged();
				table.getSelectionModel().setSelectionInterval(selectedIndex, selectedIndex);

				if (sendEmail) {
					sendEmail(user, metadata, zip);
				}
				return true;
			}

			@Override
			public boolean revoke(UserCertificateModel ucm, X509Certificate cert, RevocationReason reason) {
				if (!prepareX509Infrastructure()) {
					return false;
				}

				File caRevocationList = new File(folder, X509Utils.CA_REVOCATION_LIST);
				File caKeystoreFile = new File(folder, X509Utils.CA_KEY_STORE);
				if (X509Utils.revoke(cert, reason, caRevocationList, caKeystoreFile, caKeystorePassword, GitblitAuthority.this)) {
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

					return true;
				}

				return false;
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

		certificateDefaultsButton = new JButton(new ImageIcon(getClass().getResource("/settings_16x16.png")));
		certificateDefaultsButton.setFocusable(false);
		certificateDefaultsButton.setToolTipText(Translation.get("gb.newCertificateDefaults"));
		certificateDefaultsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				X509Metadata metadata = new X509Metadata("whocares", "whocares");
				File certificatesConfigFile = new File(folder, X509Utils.CA_CONFIG);
				FileBasedConfig config = new FileBasedConfig(certificatesConfigFile, FS.detect());
				NewCertificateConfig certificateConfig = null;
				if (certificatesConfigFile.exists()) {
					try {
						config.load();
					} catch (Exception x) {
						Utils.showException(GitblitAuthority.this, x);
					}
					certificateConfig = NewCertificateConfig.KEY.parse(config);
					certificateConfig.update(metadata);
				}
				InputVerifier verifier = new InputVerifier() {
					@Override
					public boolean verify(JComponent comp) {
						boolean returnValue;
						JTextField textField = (JTextField) comp;
						try {
							Integer.parseInt(textField.getText());
							returnValue = true;
						} catch (NumberFormatException e) {
							returnValue = false;
						}
						return returnValue;
					}
				};

				JTextField siteNameTF = new JTextField(20);
				siteNameTF.setText(gitblitSettings.getString(Keys.web.siteName, "Gitblit"));
				JPanel siteNamePanel = Utils.newFieldPanel(Translation.get("gb.siteName"),
						siteNameTF, Translation.get("gb.siteNameDescription"));

				JTextField validityTF = new JTextField(4);
				validityTF.setInputVerifier(verifier);
				validityTF.setVerifyInputWhenFocusTarget(true);
				validityTF.setText("" + certificateConfig.duration);
				JPanel validityPanel = Utils.newFieldPanel(Translation.get("gb.validity"),
						validityTF, Translation.get("gb.duration.days").replace("{0}",  "").trim());

				JPanel p1 = new JPanel(new GridLayout(0, 1, 5, 2));
				p1.add(siteNamePanel);
				p1.add(validityPanel);

				DefaultOidsPanel oids = new DefaultOidsPanel(metadata);

				JPanel panel = new JPanel(new BorderLayout());
				panel.add(p1, BorderLayout.NORTH);
				panel.add(oids, BorderLayout.CENTER);

				int result = JOptionPane.showConfirmDialog(GitblitAuthority.this,
						panel, Translation.get("gb.newCertificateDefaults"), JOptionPane.OK_CANCEL_OPTION,
						JOptionPane.QUESTION_MESSAGE, new ImageIcon(getClass().getResource("/settings_32x32.png")));
				if (result == JOptionPane.OK_OPTION) {
					try {
						oids.update(metadata);
						certificateConfig.duration = Integer.parseInt(validityTF.getText());
						certificateConfig.store(config, metadata);
						config.save();

						Map<String, String> updates = new HashMap<String, String>();
						updates.put(Keys.web.siteName, siteNameTF.getText());
						gitblitSettings.saveSettings(updates);
					} catch (Exception e1) {
						Utils.showException(GitblitAuthority.this, e1);
					}
				}
			}
		});

		newSSLCertificate = new JButton(new ImageIcon(getClass().getResource("/rosette_16x16.png")));
		newSSLCertificate.setFocusable(false);
		newSSLCertificate.setToolTipText(Translation.get("gb.newSSLCertificate"));
		newSSLCertificate.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				Date defaultExpiration = new Date(System.currentTimeMillis() + 10*TimeUtils.ONEYEAR);
				NewSSLCertificateDialog dialog = new NewSSLCertificateDialog(GitblitAuthority.this, defaultExpiration);
				dialog.setModal(true);
				dialog.setVisible(true);
				if (dialog.isCanceled()) {
					return;
				}
				final Date expires = dialog.getExpiration();
				final String hostname = dialog.getHostname();
				final boolean serveCertificate = dialog.isServeCertificate();

				AuthorityWorker worker = new AuthorityWorker(GitblitAuthority.this) {

					@Override
					protected Boolean doRequest() throws IOException {
						if (!prepareX509Infrastructure()) {
							return false;
						}

						// read CA private key and certificate
						File caKeystoreFile = new File(folder, X509Utils.CA_KEY_STORE);
						PrivateKey caPrivateKey = X509Utils.getPrivateKey(X509Utils.CA_ALIAS, caKeystoreFile, caKeystorePassword);
						X509Certificate caCert = X509Utils.getCertificate(X509Utils.CA_ALIAS, caKeystoreFile, caKeystorePassword);

						// generate new SSL certificate
						X509Metadata metadata = new X509Metadata(hostname, caKeystorePassword);
						setMetadataDefaults(metadata);
						metadata.notAfter = expires;
						File serverKeystoreFile = new File(folder, X509Utils.SERVER_KEY_STORE);
						X509Certificate cert = X509Utils.newSSLCertificate(metadata, caPrivateKey, caCert, serverKeystoreFile, GitblitAuthority.this);
						boolean hasCert = cert != null;
						if (hasCert && serveCertificate) {
							// update Gitblit https connector alias
							Map<String, String> updates = new HashMap<String, String>();
							updates.put(Keys.server.certificateAlias, metadata.commonName);
							gitblitSettings.saveSettings(updates);
						}
						return hasCert;
					}

					@Override
					protected void onSuccess() {
						if (serveCertificate) {
							JOptionPane.showMessageDialog(GitblitAuthority.this,
									MessageFormat.format(Translation.get("gb.sslCertificateGeneratedRestart"), hostname),
									Translation.get("gb.newSSLCertificate"), JOptionPane.INFORMATION_MESSAGE);
						} else {
							JOptionPane.showMessageDialog(GitblitAuthority.this,
								MessageFormat.format(Translation.get("gb.sslCertificateGenerated"), hostname),
								Translation.get("gb.newSSLCertificate"), JOptionPane.INFORMATION_MESSAGE);
						}
					}
				};

				worker.execute();
			}
		});

		JButton emailBundle = new JButton(new ImageIcon(getClass().getResource("/mail_16x16.png")));
		emailBundle.setFocusable(false);
		emailBundle.setToolTipText(Translation.get("gb.emailCertificateBundle"));
		emailBundle.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				int row = table.getSelectedRow();
				if (row < 0) {
					return;
				}
				int modelIndex = table.convertRowIndexToModel(row);
				final UserCertificateModel ucm = tableModel.get(modelIndex);
				if (ArrayUtils.isEmpty(ucm.certs)) {
					JOptionPane.showMessageDialog(GitblitAuthority.this, MessageFormat.format(Translation.get("gb.pleaseGenerateClientCertificate"), ucm.user.getDisplayName()));
				}
				final File zip = new File(folder, X509Utils.CERTS + File.separator + ucm.user.username + File.separator + ucm.user.username + ".zip");
				if (!zip.exists()) {
					return;
				}

				AuthorityWorker worker = new AuthorityWorker(GitblitAuthority.this) {
					@Override
					protected Boolean doRequest() throws IOException {
						X509Metadata metadata = new X509Metadata(ucm.user.username, "whocares");
						metadata.serverHostname = gitblitSettings.getString(Keys.web.siteName, Constants.NAME);
						if (StringUtils.isEmpty(metadata.serverHostname)) {
							metadata.serverHostname = Constants.NAME;
						}
						metadata.userDisplayname = ucm.user.getDisplayName();
						return sendEmail(ucm.user, metadata, zip);
					}

					@Override
					protected void onSuccess() {
						JOptionPane.showMessageDialog(GitblitAuthority.this, MessageFormat.format(Translation.get("gb.clientCertificateBundleSent"),
								ucm.user.getDisplayName()));
					}

				};
				worker.execute();
			}
		});

		JButton logButton = new JButton(new ImageIcon(getClass().getResource("/script_16x16.png")));
		logButton.setFocusable(false);
		logButton.setToolTipText(Translation.get("gb.log"));
		logButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				File log = new File(folder, X509Utils.CERTS + File.separator + "log.txt");
				if (log.exists()) {
					String content = FileUtils.readContent(log,  "\n");
					JTextArea textarea = new JTextArea(content);
					JScrollPane scrollPane = new JScrollPane(textarea);
					scrollPane.setPreferredSize(new Dimension(700, 400));
					JOptionPane.showMessageDialog(GitblitAuthority.this, scrollPane, log.getAbsolutePath(), JOptionPane.INFORMATION_MESSAGE);
				}
			}
		});

		final JTextField filterTextfield = new JTextField(15);
		filterTextfield.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				filterUsers(filterTextfield.getText());
			}
		});
		filterTextfield.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				filterUsers(filterTextfield.getText());
			}
		});

		JToolBar buttonControls = new JToolBar(JToolBar.HORIZONTAL);
		buttonControls.setFloatable(false);
		buttonControls.add(certificateDefaultsButton);
		buttonControls.add(newSSLCertificate);
		buttonControls.add(emailBundle);
		buttonControls.add(logButton);

		JPanel userControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, Utils.MARGIN, Utils.MARGIN));
		userControls.add(new JLabel(Translation.get("gb.filter")));
		userControls.add(filterTextfield);

		JPanel topPanel = new JPanel(new BorderLayout(0, 0));
		topPanel.add(buttonControls, BorderLayout.WEST);
		topPanel.add(userControls, BorderLayout.EAST);

		JPanel leftPanel = new JPanel(new BorderLayout());
		leftPanel.add(topPanel, BorderLayout.NORTH);
		leftPanel.add(usersPanel, BorderLayout.CENTER);

		userCertificatePanel.setMinimumSize(new Dimension(375, 10));

		JLabel statusLabel = new JLabel();
		statusLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		if (X509Utils.unlimitedStrength) {
			statusLabel.setText("JCE Unlimited Strength Jurisdiction Policy");
		} else {
			statusLabel.setText("JCE Standard Encryption Policy");
		}

		JPanel root = new JPanel(new BorderLayout()) {
			private static final long serialVersionUID = 1L;
			@Override
			public Insets getInsets() {
				return Utils.INSETS;
			}
		};
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, userCertificatePanel);
		splitPane.setDividerLocation(1d);
		root.add(splitPane, BorderLayout.CENTER);
		root.add(statusLabel, BorderLayout.SOUTH);
		return root;
	}

	private void filterUsers(final String fragment) {
		table.clearSelection();
		userCertificatePanel.setUserCertificateModel(null);
		if (StringUtils.isEmpty(fragment)) {
			table.setRowSorter(defaultSorter);
			return;
		}
		RowFilter<UserCertificateTableModel, Object> containsFilter = new RowFilter<UserCertificateTableModel, Object>() {
			@Override
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

	@Override
	public void log(String message) {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(new File(folder, X509Utils.CERTS + File.separator + "log.txt"), true));
			writer.write(MessageFormat.format("{0,date,yyyy-MM-dd HH:mm}: {1}", new Date(), message));
			writer.newLine();
			writer.flush();
		} catch (Exception e) {
			LoggerFactory.getLogger(GitblitAuthority.class).error("Failed to append log entry!", e);
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException e) {
				}
			}
		}
	}

	private boolean sendEmail(UserModel user, X509Metadata metadata, File zip) {
		// send email
		try {
			if (mail.isReady()) {
				Mailing mailing = Mailing.newPlain();
				mailing.subject = MessageFormat.format(ResourceBundle.getBundle("com.gitblit.wicket.GitBlitWebApp",user.getPreferences().getLocale()).getString("gb.emailClientCertificateSubject"), metadata.serverHostname);
				mailing.setRecipients(user.emailAddress);
				File fileMailTmp = null;
				String body = null;
				if( (fileMailTmp = new File(folder, X509Utils.CERTS + File.separator +  "mail.tmpl"+"_"+user.getPreferences().getLocale())).exists())
				  body = X509Utils.processTemplate(fileMailTmp, metadata);
				else{
				  fileMailTmp = new File(folder, X509Utils.CERTS + File.separator +  "mail.tmpl");
				  body = X509Utils.processTemplate(fileMailTmp, metadata);
				}  
				if (StringUtils.isEmpty(body)) {
					body = MessageFormat.format("Hi {0}\n\nHere is your client certificate bundle.\nInside the zip file are installation instructions.", user.getDisplayName());
				}
				mailing.content = body;
				mailing.addAttachment(zip);

				Message message = mail.createMessage(mailing);

				mail.sendNow(message);
				return true;
			} else {
				JOptionPane.showMessageDialog(GitblitAuthority.this, "Sorry, the mail server settings are not configured properly.\nCan not send email.", Translation.get("gb.error"), JOptionPane.ERROR_MESSAGE);
			}
		} catch (Exception e) {
			Utils.showException(GitblitAuthority.this, e);
		}
		return false;
	}

	private void setMetadataDefaults(X509Metadata metadata) {
		metadata.serverHostname = gitblitSettings.getString(Keys.web.siteName, Constants.NAME);
		if (StringUtils.isEmpty(metadata.serverHostname)) {
			metadata.serverHostname = Constants.NAME;
		}

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
	}

	private void updateAuthorityConfig(UserCertificateModel ucm) {
		File certificatesConfigFile = new File(folder, X509Utils.CA_CONFIG);
		FileBasedConfig config = new FileBasedConfig(certificatesConfigFile, FS.detect());
		if (certificatesConfigFile.exists()) {
			try {
				config.load();
			} catch (Exception e) {
				Utils.showException(GitblitAuthority.this, e);
			}
		}
		ucm.update(config);
		try {
			config.save();
		} catch (Exception e) {
			Utils.showException(GitblitAuthority.this, e);
		}
	}
}
