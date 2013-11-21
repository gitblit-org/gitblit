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
package com.gitblit;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.ProtectionDomain;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

import org.eclipse.jetty.ajp.Ajp13SocketConnector;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.session.HashSessionManager;
import org.eclipse.jetty.server.ssl.SslConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSocketConnector;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.gitblit.authority.GitblitAuthority;
import com.gitblit.authority.NewCertificateConfig;
import com.gitblit.servlet.GitblitContext;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.TimeUtils;
import com.gitblit.utils.X509Utils;
import com.gitblit.utils.X509Utils.X509Log;
import com.gitblit.utils.X509Utils.X509Metadata;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldif.LDIFReader;

/**
 * GitBlitServer is the embedded Jetty server for Gitblit GO. This class starts
 * and stops an instance of Jetty that is configured from a combination of the
 * gitblit.properties file and command line parameters. JCommander is used to
 * simplify command line parameter processing. This class also automatically
 * generates a self-signed certificate for localhost, if the keystore does not
 * already exist.
 *
 * @author James Moger
 *
 */
public class GitBlitServer {

	private static Logger logger;

	public static void main(String... args) {
		GitBlitServer server = new GitBlitServer();

		// filter out the baseFolder parameter
		List<String> filtered = new ArrayList<String>();
		String folder = "data";
		for (int i = 0; i< args.length; i++) {
			String arg = args[i];
			if (arg.equals("--baseFolder")) {
				if (i + 1 == args.length) {
					System.out.println("Invalid --baseFolder parameter!");
					System.exit(-1);
				} else if (!".".equals(args[i + 1])) {
					folder = args[i + 1];
				}
				i = i + 1;
			} else {
				filtered.add(arg);
			}
		}

		Params.baseFolder = folder;
		Params params = new Params();
		JCommander jc = new JCommander(params);
		try {
			jc.parse(filtered.toArray(new String[filtered.size()]));
			if (params.help) {
				server.usage(jc, null);
			}
		} catch (ParameterException t) {
			server.usage(jc, t);
		}

		if (params.stop) {
			server.stop(params);
		} else {
			server.start(params);
		}
	}

	/**
	 * Display the command line usage of Gitblit GO.
	 *
	 * @param jc
	 * @param t
	 */
	protected final void usage(JCommander jc, ParameterException t) {
		System.out.println(Constants.BORDER);
		System.out.println(Constants.getGitBlitVersion());
		System.out.println(Constants.BORDER);
		System.out.println();
		if (t != null) {
			System.out.println(t.getMessage());
			System.out.println();
		}
		if (jc != null) {
			jc.usage();
			System.out
					.println("\nExample:\n  java -server -Xmx1024M -jar gitblit.jar --repositoriesFolder c:\\git --httpPort 80 --httpsPort 443");
		}
		System.exit(0);
	}

	/**
	 * Stop Gitblt GO.
	 */
	public void stop(Params params) {
		try {
			Socket s = new Socket(InetAddress.getByName("127.0.0.1"), params.shutdownPort);
			OutputStream out = s.getOutputStream();
			System.out.println("Sending Shutdown Request to " + Constants.NAME);
			out.write("\r\n".getBytes());
			out.flush();
			s.close();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Start Gitblit GO.
	 */
	protected final void start(Params params) {
		final File baseFolder = new File(Params.baseFolder).getAbsoluteFile();
		FileSettings settings = params.FILESETTINGS;
		if (!StringUtils.isEmpty(params.settingsfile)) {
			if (new File(params.settingsfile).exists()) {
				settings = new FileSettings(params.settingsfile);
			}
		}
		logger = LoggerFactory.getLogger(GitBlitServer.class);
		logger.info(Constants.BORDER);
		logger.info("            _____  _  _    _      _  _  _");
		logger.info("           |  __ \\(_)| |  | |    | |(_)| |");
		logger.info("           | |  \\/ _ | |_ | |__  | | _ | |_");
		logger.info("           | | __ | || __|| '_ \\ | || || __|");
		logger.info("           | |_\\ \\| || |_ | |_) || || || |_");
		logger.info("            \\____/|_| \\__||_.__/ |_||_| \\__|");
		int spacing = (Constants.BORDER.length() - Constants.getGitBlitVersion().length()) / 2;
		StringBuilder sb = new StringBuilder();
		while (spacing > 0) {
			spacing--;
			sb.append(' ');
		}
		logger.info(sb.toString() + Constants.getGitBlitVersion());
		logger.info("");
		logger.info(Constants.BORDER);

		System.setProperty("java.awt.headless", "true");

		String osname = System.getProperty("os.name");
		String osversion = System.getProperty("os.version");
		logger.info("Running on " + osname + " (" + osversion + ")");

		List<Connector> connectors = new ArrayList<Connector>();

		// conditionally configure the http connector
		if (params.port > 0) {
			Connector httpConnector = createConnector(params.useNIO, params.port, settings.getInteger(Keys.server.threadPoolSize, 50));
			String bindInterface = settings.getString(Keys.server.httpBindInterface, null);
			if (!StringUtils.isEmpty(bindInterface)) {
				logger.warn(MessageFormat.format("Binding connector on port {0,number,0} to {1}",
						params.port, bindInterface));
				httpConnector.setHost(bindInterface);
			}
			if (params.port < 1024 && !isWindows()) {
				logger.warn("Gitblit needs to run with ROOT permissions for ports < 1024!");
			}
			if (params.port > 0 && params.securePort > 0 && settings.getBoolean(Keys.server.redirectToHttpsPort, true)) {
				// redirect HTTP requests to HTTPS
				if (httpConnector instanceof SelectChannelConnector) {
					((SelectChannelConnector) httpConnector).setConfidentialPort(params.securePort);
				} else {
					((SocketConnector) httpConnector).setConfidentialPort(params.securePort);
				}
			}
			connectors.add(httpConnector);
		}

		// conditionally configure the https connector
		if (params.securePort > 0) {
			File certificatesConf = new File(baseFolder, X509Utils.CA_CONFIG);
			File serverKeyStore = new File(baseFolder, X509Utils.SERVER_KEY_STORE);
			File serverTrustStore = new File(baseFolder, X509Utils.SERVER_TRUST_STORE);
			File caRevocationList = new File(baseFolder, X509Utils.CA_REVOCATION_LIST);

			// generate CA & web certificates, create certificate stores
			X509Metadata metadata = new X509Metadata("localhost", params.storePassword);
			// set default certificate values from config file
			if (certificatesConf.exists()) {
				FileBasedConfig config = new FileBasedConfig(certificatesConf, FS.detect());
				try {
					config.load();
				} catch (Exception e) {
					logger.error("Error parsing " + certificatesConf, e);
				}
				NewCertificateConfig certificateConfig = NewCertificateConfig.KEY.parse(config);
				certificateConfig.update(metadata);
			}

			metadata.notAfter = new Date(System.currentTimeMillis() + 10*TimeUtils.ONEYEAR);
			X509Utils.prepareX509Infrastructure(metadata, baseFolder, new X509Log() {
				@Override
				public void log(String message) {
					BufferedWriter writer = null;
					try {
						writer = new BufferedWriter(new FileWriter(new File(baseFolder, X509Utils.CERTS + File.separator + "log.txt"), true));
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
			});

			if (serverKeyStore.exists()) {
				Connector secureConnector = createSSLConnector(params.alias, serverKeyStore, serverTrustStore, params.storePassword,
						caRevocationList, params.useNIO, params.securePort, settings.getInteger(Keys.server.threadPoolSize, 50), params.requireClientCertificates);
				String bindInterface = settings.getString(Keys.server.httpsBindInterface, null);
				if (!StringUtils.isEmpty(bindInterface)) {
					logger.warn(MessageFormat.format(
							"Binding ssl connector on port {0,number,0} to {1}", params.securePort,
							bindInterface));
					secureConnector.setHost(bindInterface);
				}
				if (params.securePort < 1024 && !isWindows()) {
					logger.warn("Gitblit needs to run with ROOT permissions for ports < 1024!");
				}
				connectors.add(secureConnector);
			} else {
				logger.warn("Failed to find or load Keystore?");
				logger.warn("SSL connector DISABLED.");
			}
		}

		// conditionally configure the ajp connector
		if (params.ajpPort > 0) {
			Connector ajpConnector = createAJPConnector(params.ajpPort);
			String bindInterface = settings.getString(Keys.server.ajpBindInterface, null);
			if (!StringUtils.isEmpty(bindInterface)) {
				logger.warn(MessageFormat.format("Binding connector on port {0,number,0} to {1}",
						params.ajpPort, bindInterface));
				ajpConnector.setHost(bindInterface);
			}
			if (params.ajpPort < 1024 && !isWindows()) {
				logger.warn("Gitblit needs to run with ROOT permissions for ports < 1024!");
			}
			connectors.add(ajpConnector);
		}

		// tempDir is where the embedded Gitblit web application is expanded and
		// where Jetty creates any necessary temporary files
		File tempDir = com.gitblit.utils.FileUtils.resolveParameter(Constants.baseFolder$, baseFolder, params.temp);
		if (tempDir.exists()) {
			try {
				FileUtils.delete(tempDir, FileUtils.RECURSIVE | FileUtils.RETRY);
			} catch (IOException x) {
				logger.warn("Failed to delete temp dir " + tempDir.getAbsolutePath(), x);
			}
		}
		if (!tempDir.mkdirs()) {
			logger.warn("Failed to create temp dir " + tempDir.getAbsolutePath());
		}

		Server server = new Server();
		server.setStopAtShutdown(true);
		server.setConnectors(connectors.toArray(new Connector[connectors.size()]));

		// Get the execution path of this class
		// We use this to set the WAR path.
		ProtectionDomain protectionDomain = GitBlitServer.class.getProtectionDomain();
		URL location = protectionDomain.getCodeSource().getLocation();

		// Root WebApp Context
		WebAppContext rootContext = new WebAppContext();
		rootContext.setContextPath(settings.getString(Keys.server.contextPath, "/"));
		rootContext.setServer(server);
		rootContext.setWar(location.toExternalForm());
		rootContext.setTempDirectory(tempDir);

		// Set cookies HttpOnly so they are not accessible to JavaScript engines
		HashSessionManager sessionManager = new HashSessionManager();
		sessionManager.setHttpOnly(true);
		// Use secure cookies if only serving https
		sessionManager.setSecureRequestOnly(params.port <= 0 && params.securePort > 0);
		rootContext.getSessionHandler().setSessionManager(sessionManager);

		// Ensure there is a defined User Service
		String realmUsers = params.userService;
		if (StringUtils.isEmpty(realmUsers)) {
			logger.error(MessageFormat.format("PLEASE SPECIFY {0}!!", Keys.realm.userService));
			return;
		}

		// Override settings from the command-line
		settings.overrideSetting(Keys.realm.userService, params.userService);
		settings.overrideSetting(Keys.git.repositoriesFolder, params.repositoriesFolder);
		settings.overrideSetting(Keys.git.daemonPort, params.gitPort);

		// Start up an in-memory LDAP server, if configured
		try {
			if (!StringUtils.isEmpty(params.ldapLdifFile)) {
				File ldifFile = new File(params.ldapLdifFile);
				if (ldifFile != null && ldifFile.exists()) {
					URI ldapUrl = new URI(settings.getRequiredString(Keys.realm.ldap.server));
					String firstLine = new Scanner(ldifFile).nextLine();
					String rootDN = firstLine.substring(4);
					String bindUserName = settings.getString(Keys.realm.ldap.username, "");
					String bindPassword = settings.getString(Keys.realm.ldap.password, "");

					// Get the port
					int port = ldapUrl.getPort();
					if (port == -1)
						port = 389;

					InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(rootDN);
					config.addAdditionalBindCredentials(bindUserName, bindPassword);
					config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("default", port));
					config.setSchema(null);

					InMemoryDirectoryServer ds = new InMemoryDirectoryServer(config);
					ds.importFromLDIF(true, new LDIFReader(ldifFile));
					ds.startListening();

					logger.info("LDAP Server started at ldap://localhost:" + port);
				}
			}
		} catch (Exception e) {
			// Completely optional, just show a warning
			logger.warn("Unable to start LDAP server", e);
		}

		// Set the server's contexts
		server.setHandler(rootContext);

		// redirect HTTP requests to HTTPS
		if (params.port > 0 && params.securePort > 0 && settings.getBoolean(Keys.server.redirectToHttpsPort, true)) {
			logger.info(String.format("Configuring automatic http(%1$s) -> https(%2$s) redirects", params.port, params.securePort));
			// Create the internal mechanisms to handle secure connections and redirects
			Constraint constraint = new Constraint();
			constraint.setDataConstraint(Constraint.DC_CONFIDENTIAL);

			ConstraintMapping cm = new ConstraintMapping();
			cm.setConstraint(constraint);
			cm.setPathSpec("/*");

			ConstraintSecurityHandler sh = new ConstraintSecurityHandler();
			sh.setConstraintMappings(new ConstraintMapping[] { cm });

			// Configure this context to use the Security Handler defined before
			rootContext.setHandler(sh);
		}

		// Setup the Gitblit context
		GitblitContext gitblit = newGitblit(settings, baseFolder);
		rootContext.addEventListener(gitblit);

		try {
			// start the shutdown monitor
			if (params.shutdownPort > 0) {
				Thread shutdownMonitor = new ShutdownMonitorThread(server, params);
				shutdownMonitor.start();
			}

			// start Jetty
			server.start();
			server.join();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(100);
		}
	}

	protected GitblitContext newGitblit(IStoredSettings settings, File baseFolder) {
		return new GitblitContext(settings, baseFolder);
	}

	/**
	 * Creates an http connector.
	 *
	 * @param useNIO
	 * @param port
	 * @param threadPoolSize
	 * @return an http connector
	 */
	private Connector createConnector(boolean useNIO, int port, int threadPoolSize) {
		Connector connector;
		if (useNIO) {
			logger.info("Setting up NIO SelectChannelConnector on port " + port);
			SelectChannelConnector nioconn = new SelectChannelConnector();
			nioconn.setSoLingerTime(-1);
			if (threadPoolSize > 0) {
				nioconn.setThreadPool(new QueuedThreadPool(threadPoolSize));
			}
			connector = nioconn;
		} else {
			logger.info("Setting up SocketConnector on port " + port);
			SocketConnector sockconn = new SocketConnector();
			if (threadPoolSize > 0) {
				sockconn.setThreadPool(new QueuedThreadPool(threadPoolSize));
			}
			connector = sockconn;
		}

		connector.setPort(port);
		connector.setMaxIdleTime(30000);
		return connector;
	}

	/**
	 * Creates an https connector.
	 *
	 * SSL renegotiation will be enabled if the JVM is 1.6.0_22 or later.
	 * oracle.com/technetwork/java/javase/documentation/tlsreadme2-176330.html
	 *
	 * @param certAlias
	 * @param keyStore
	 * @param clientTrustStore
	 * @param storePassword
	 * @param caRevocationList
	 * @param useNIO
	 * @param port
	 * @param threadPoolSize
	 * @param requireClientCertificates
	 * @return an https connector
	 */
	private Connector createSSLConnector(String certAlias, File keyStore, File clientTrustStore,
			String storePassword, File caRevocationList, boolean useNIO,  int port, int threadPoolSize,
			boolean requireClientCertificates) {
		GitblitSslContextFactory factory = new GitblitSslContextFactory(certAlias,
				keyStore, clientTrustStore, storePassword, caRevocationList);
		SslConnector connector;
		if (useNIO) {
			logger.info("Setting up NIO SslSelectChannelConnector on port " + port);
			SslSelectChannelConnector ssl = new SslSelectChannelConnector(factory);
			ssl.setSoLingerTime(-1);
			if (requireClientCertificates) {
				factory.setNeedClientAuth(true);
			} else {
				factory.setWantClientAuth(true);
			}
			if (threadPoolSize > 0) {
				ssl.setThreadPool(new QueuedThreadPool(threadPoolSize));
			}
			connector = ssl;
		} else {
			logger.info("Setting up NIO SslSocketConnector on port " + port);
			SslSocketConnector ssl = new SslSocketConnector(factory);
			if (threadPoolSize > 0) {
				ssl.setThreadPool(new QueuedThreadPool(threadPoolSize));
			}
			connector = ssl;
		}
		connector.setPort(port);
		connector.setMaxIdleTime(30000);

		return connector;
	}

	/**
	 * Creates an ajp connector.
	 *
	 * @param port
	 * @return an ajp connector
	 */
	private Connector createAJPConnector(int port) {
		logger.info("Setting up AJP Connector on port " + port);
		Ajp13SocketConnector ajp = new Ajp13SocketConnector();
		ajp.setPort(port);
		if (port < 1024 && !isWindows()) {
			logger.warn("Gitblit needs to run with ROOT permissions for ports < 1024!");
		}
		return ajp;
	}

	/**
	 * Tests to see if the operating system is Windows.
	 *
	 * @return true if this is a windows machine
	 */
	private boolean isWindows() {
		return System.getProperty("os.name").toLowerCase().indexOf("windows") > -1;
	}

	/**
	 * The ShutdownMonitorThread opens a socket on a specified port and waits
	 * for an incoming connection. When that connection is accepted a shutdown
	 * message is issued to the running Jetty server.
	 *
	 * @author James Moger
	 *
	 */
	private static class ShutdownMonitorThread extends Thread {

		private final ServerSocket socket;

		private final Server server;

		private final Logger logger = LoggerFactory.getLogger(ShutdownMonitorThread.class);

		public ShutdownMonitorThread(Server server, Params params) {
			this.server = server;
			setDaemon(true);
			setName(Constants.NAME + " Shutdown Monitor");
			ServerSocket skt = null;
			try {
				skt = new ServerSocket(params.shutdownPort, 1, InetAddress.getByName("127.0.0.1"));
			} catch (Exception e) {
				logger.warn("Could not open shutdown monitor on port " + params.shutdownPort, e);
			}
			socket = skt;
		}

		@Override
		public void run() {
			logger.info("Shutdown Monitor listening on port " + socket.getLocalPort());
			Socket accept;
			try {
				accept = socket.accept();
				BufferedReader reader = new BufferedReader(new InputStreamReader(
						accept.getInputStream()));
				reader.readLine();
				logger.info(Constants.BORDER);
				logger.info("Stopping " + Constants.NAME);
				logger.info(Constants.BORDER);
				server.stop();
				server.setStopAtShutdown(false);
				accept.close();
				socket.close();
			} catch (Exception e) {
				logger.warn("Failed to shutdown Jetty", e);
			}
		}
	}

	/**
	 * JCommander Parameters class for GitBlitServer.
	 */
	@Parameters(separators = " ")
	public static class Params {

		public static String baseFolder;

		private final FileSettings FILESETTINGS = new FileSettings(new File(baseFolder, Constants.PROPERTIES_FILE).getAbsolutePath());

		/*
		 * Server parameters
		 */
		@Parameter(names = { "-h", "--help" }, description = "Show this help")
		public Boolean help = false;

		@Parameter(names = { "--stop" }, description = "Stop Server")
		public Boolean stop = false;

		@Parameter(names = { "--tempFolder" }, description = "Folder for server to extract built-in webapp")
		public String temp = FILESETTINGS.getString(Keys.server.tempFolder, "temp");

		/*
		 * GIT Servlet Parameters
		 */
		@Parameter(names = { "--repositoriesFolder" }, description = "Git Repositories Folder")
		public String repositoriesFolder = FILESETTINGS.getString(Keys.git.repositoriesFolder,
				"git");

		/*
		 * Authentication Parameters
		 */
		@Parameter(names = { "--userService" }, description = "Authentication and Authorization Service (filename or fully qualified classname)")
		public String userService = FILESETTINGS.getString(Keys.realm.userService,
				"users.conf");

		/*
		 * JETTY Parameters
		 */
		@Parameter(names = { "--useNio" }, description = "Use NIO Connector else use Socket Connector.")
		public Boolean useNIO = FILESETTINGS.getBoolean(Keys.server.useNio, true);

		@Parameter(names = "--httpPort", description = "HTTP port for to serve. (port <= 0 will disable this connector)")
		public Integer port = FILESETTINGS.getInteger(Keys.server.httpPort, 0);

		@Parameter(names = "--httpsPort", description = "HTTPS port to serve.  (port <= 0 will disable this connector)")
		public Integer securePort = FILESETTINGS.getInteger(Keys.server.httpsPort, 8443);

		@Parameter(names = "--ajpPort", description = "AJP port to serve.  (port <= 0 will disable this connector)")
		public Integer ajpPort = FILESETTINGS.getInteger(Keys.server.ajpPort, 0);

		@Parameter(names = "--gitPort", description = "Git Daemon port to serve.  (port <= 0 will disable this connector)")
		public Integer gitPort = FILESETTINGS.getInteger(Keys.git.daemonPort, 9418);

		@Parameter(names = "--alias", description = "Alias of SSL certificate in keystore for serving https.")
		public String alias = FILESETTINGS.getString(Keys.server.certificateAlias, "");

		@Parameter(names = "--storePassword", description = "Password for SSL (https) keystore.")
		public String storePassword = FILESETTINGS.getString(Keys.server.storePassword, "");

		@Parameter(names = "--shutdownPort", description = "Port for Shutdown Monitor to listen on. (port <= 0 will disable this monitor)")
		public Integer shutdownPort = FILESETTINGS.getInteger(Keys.server.shutdownPort, 8081);

		@Parameter(names = "--requireClientCertificates", description = "Require client X509 certificates for https connections.")
		public Boolean requireClientCertificates = FILESETTINGS.getBoolean(Keys.server.requireClientCertificates, false);

		/*
		 * Setting overrides
		 */
		@Parameter(names = { "--settings" }, description = "Path to alternative settings")
		public String settingsfile;

		@Parameter(names = { "--ldapLdifFile" }, description = "Path to LDIF file.  This will cause an in-memory LDAP server to be started according to gitblit settings")
		public String ldapLdifFile;

	}
}