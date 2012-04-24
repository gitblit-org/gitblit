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
import java.io.File;
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
import java.util.List;
import java.util.Scanner;

import org.eclipse.jetty.ajp.Ajp13SocketConnector;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.session.HashSessionManager;
import org.eclipse.jetty.server.ssl.SslConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSocketConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jgit.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.gitblit.utils.StringUtils;
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
		Params params = new Params();
		JCommander jc = new JCommander(params);
		try {
			jc.parse(args);
			if (params.help) {
				usage(jc, null);
			}
		} catch (ParameterException t) {
			usage(jc, t);
		}

		if (params.stop) {
			stop(params);
		} else {
			start(params);
		}
	}

	/**
	 * Display the command line usage of Gitblit GO.
	 * 
	 * @param jc
	 * @param t
	 */
	private static void usage(JCommander jc, ParameterException t) {
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
	public static void stop(Params params) {
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
	private static void start(Params params) {
		FileSettings settings = Params.FILESETTINGS;
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
			Connector httpConnector = createConnector(params.useNIO, params.port);
			String bindInterface = settings.getString(Keys.server.httpBindInterface, null);
			if (!StringUtils.isEmpty(bindInterface)) {
				logger.warn(MessageFormat.format("Binding connector on port {0,number,0} to {1}",
						params.port, bindInterface));
				httpConnector.setHost(bindInterface);
			}
			if (params.port < 1024 && !isWindows()) {
				logger.warn("Gitblit needs to run with ROOT permissions for ports < 1024!");
			}
			connectors.add(httpConnector);
		}

		// conditionally configure the https connector
		if (params.securePort > 0) {
			File keystore = new File("keystore");
			if (!keystore.exists()) {
				logger.info("Generating self-signed SSL certificate for localhost");
				MakeCertificate.generateSelfSignedCertificate("localhost", keystore,
						params.storePassword);
			}
			if (keystore.exists()) {
				Connector secureConnector = createSSLConnector(keystore, params.storePassword,
						params.useNIO, params.securePort);
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
		File tempDir = new File(params.temp);
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
		sessionManager.setSecureCookies(params.port <= 0 && params.securePort > 0);
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
		
		// Start up an in-memory LDAP server, if configured
		try {
			if (StringUtils.isEmpty(params.ldapLdifFile) == false) {
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

		// Setup the GitBlit context
		GitBlit gitblit = GitBlit.self();
		gitblit.configureContext(settings, true);
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

	/**
	 * Creates an http connector.
	 * 
	 * @param useNIO
	 * @param port
	 * @return an http connector
	 */
	private static Connector createConnector(boolean useNIO, int port) {
		Connector connector;
		if (useNIO) {
			logger.info("Setting up NIO SelectChannelConnector on port " + port);
			SelectChannelConnector nioconn = new SelectChannelConnector();
			nioconn.setSoLingerTime(-1);
			nioconn.setThreadPool(new QueuedThreadPool(20));
			connector = nioconn;
		} else {
			logger.info("Setting up SocketConnector on port " + port);
			SocketConnector sockconn = new SocketConnector();
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
	 * @param keystore
	 * @param password
	 * @param useNIO
	 * @param port
	 * @return an https connector
	 */
	private static Connector createSSLConnector(File keystore, String password, boolean useNIO,
			int port) {
		SslConnector connector;
		if (useNIO) {
			logger.info("Setting up NIO SslSelectChannelConnector on port " + port);
			SslSelectChannelConnector ssl = new SslSelectChannelConnector();
			ssl.setSoLingerTime(-1);
			ssl.setThreadPool(new QueuedThreadPool(20));
			connector = ssl;
		} else {
			logger.info("Setting up NIO SslSocketConnector on port " + port);
			SslSocketConnector ssl = new SslSocketConnector();
			connector = ssl;
		}
		// disable renegotiation unless this is a patched JVM
		boolean allowRenegotiation = false;
		String v = System.getProperty("java.version");
		if (v.startsWith("1.7")) {
			allowRenegotiation = true;
		} else if (v.startsWith("1.6")) {
			// 1.6.0_22 was first release with RFC-5746 implemented fix.
			if (v.indexOf('_') > -1) {
				String b = v.substring(v.indexOf('_') + 1);
				if (Integer.parseInt(b) >= 22) {
					allowRenegotiation = true;
				}
			}
		}
		if (allowRenegotiation) {
			logger.info("   allowing SSL renegotiation on Java " + v);
			connector.setAllowRenegotiate(allowRenegotiation);
		}
		connector.setKeystore(keystore.getAbsolutePath());
		connector.setPassword(password);
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
	private static Connector createAJPConnector(int port) {
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
	private static boolean isWindows() {
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
	private static class Params {

		private static final FileSettings FILESETTINGS = new FileSettings(Constants.PROPERTIES_FILE);

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
				"repos");

		/*
		 * Authentication Parameters
		 */
		@Parameter(names = { "--userService" }, description = "Authentication and Authorization Service (filename or fully qualified classname)")
		public String userService = FILESETTINGS.getString(Keys.realm.userService,
				"users.properties");

		/*
		 * JETTY Parameters
		 */
		@Parameter(names = { "--useNio" }, description = "Use NIO Connector else use Socket Connector.")
		public Boolean useNIO = FILESETTINGS.getBoolean(Keys.server.useNio, true);

		@Parameter(names = "--httpPort", description = "HTTP port for to serve. (port <= 0 will disable this connector)")
		public Integer port = FILESETTINGS.getInteger(Keys.server.httpPort, 80);

		@Parameter(names = "--httpsPort", description = "HTTPS port to serve.  (port <= 0 will disable this connector)")
		public Integer securePort = FILESETTINGS.getInteger(Keys.server.httpsPort, 443);

		@Parameter(names = "--ajpPort", description = "AJP port to serve.  (port <= 0 will disable this connector)")
		public Integer ajpPort = FILESETTINGS.getInteger(Keys.server.ajpPort, 0);

		@Parameter(names = "--storePassword", description = "Password for SSL (https) keystore.")
		public String storePassword = FILESETTINGS.getString(Keys.server.storePassword, "");

		@Parameter(names = "--shutdownPort", description = "Port for Shutdown Monitor to listen on. (port <= 0 will disable this monitor)")
		public Integer shutdownPort = FILESETTINGS.getInteger(Keys.server.shutdownPort, 8081);

		/*
		 * Setting overrides
		 */
		@Parameter(names = { "--settings" }, description = "Path to alternative settings")
		public String settingsfile;
		
		@Parameter(names = { "--ldapLdifFile" }, description = "Path to LDIF file.  This will cause an in-memory LDAP server to be started according to gitblit settings")
		public String ldapLdifFile;

	}
}