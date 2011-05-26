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
import java.net.URL;
import java.net.UnknownHostException;
import java.security.ProtectionDomain;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.PatternLayout;
import org.apache.wicket.protocol.http.ContextParamWebApplicationFactory;
import org.apache.wicket.protocol.http.WicketFilter;
import org.eclipse.jetty.http.security.Constraint;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.session.HashSessionManager;
import org.eclipse.jetty.server.ssl.SslConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSocketConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.FilterMapping;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebApp;

public class GitBlitServer {

	private static final String BORDER = "***********************************************************";

	private static Logger logger;

	private static final FileSettings FILESETTINGS = new FileSettings();

	public static void main(String[] args) {
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

	private static void usage(JCommander jc, ParameterException t) {
		System.out.println(BORDER);
		System.out.println(Constants.getGitBlitVersion());
		System.out.println(BORDER);
		System.out.println();
		if (t != null) {
			System.out.println(t.getMessage());
			System.out.println();
		}
		if (jc != null) {
			jc.usage();
			System.out
					.println("\nExample:\n  java -server -Xmx1024M -jar gitblit.jar --repos c:\\git --port 80 --securePort 443");
		}
		System.exit(0);
	}

	/**
	 * Stop Server.
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
	 * Start Server.
	 */
	private static void start(Params params) {
		String pattern = FILESETTINGS.getString(Keys.server.log4jPattern,
				"%-5p %d{MM-dd HH:mm:ss.SSS}  %-20.20c{1}  %m%n");

		// allow os override of logging pattern
		String os = System.getProperty("os.name").toLowerCase();
		if (os.indexOf("windows") > -1) {
			String winPattern = FILESETTINGS.getString(Keys.server.log4jPattern_windows, pattern);
			if (!StringUtils.isEmpty(winPattern)) {
				pattern = winPattern;
			}
		} else if (os.indexOf("linux") > -1) {
			String linuxPattern = FILESETTINGS.getString(Keys.server.log4jPattern_linux, pattern);
			if (!StringUtils.isEmpty(linuxPattern)) {
				pattern = linuxPattern;
			}
		}

		PatternLayout layout = new PatternLayout(pattern);
		org.apache.log4j.Logger rootLogger = org.apache.log4j.Logger.getRootLogger();
		rootLogger.addAppender(new ConsoleAppender(layout));

		logger = LoggerFactory.getLogger(GitBlitServer.class);
		logger.info(BORDER);
		logger.info(Constants.getGitBlitVersion());
		logger.info(BORDER);

		String osname = System.getProperty("os.name");
		String osversion = System.getProperty("os.version");
		logger.info("Running on " + osname + " (" + osversion + ")");

		// Determine port connectors
		List<Connector> connectors = new ArrayList<Connector>();
		if (params.port > 0) {
			Connector httpConnector = createConnector(params.useNIO, params.port);
			String bindInterface = FILESETTINGS.getString(Keys.server.httpBindInterface, null);
			if (!StringUtils.isEmpty(bindInterface)) {
				logger.warn(MessageFormat.format("Binding connector on port {0} to {1}",
						params.port, bindInterface));
				httpConnector.setHost(bindInterface);
			}
			connectors.add(httpConnector);
		}

		if (params.securePort > 0) {
			File keystore = new File("keystore");
			if (!keystore.exists()) {
				logger.info("Generating self-signed SSL certificate");
				MakeCertificate.generateSelfSignedCertificate("localhost", keystore,
						params.storePassword);
			}
			if (keystore.exists()) {
				Connector secureConnector = createSSLConnector(keystore, params.storePassword,
						params.useNIO, params.securePort);
				String bindInterface = FILESETTINGS.getString(Keys.server.httpsBindInterface, null);
				if (!StringUtils.isEmpty(bindInterface)) {
					logger.warn(MessageFormat.format("Binding ssl connector on port {0} to {1}",
							params.securePort, bindInterface));
					secureConnector.setHost(bindInterface);
				}
				connectors.add(secureConnector);
			} else {
				logger.warn("Failed to find or load Keystore?");
				logger.warn("SSL connector DISABLED.");
			}
		}

		// tempDir = Directory where...
		// * WebApp is expanded
		//
		File tempDir = new File(params.temp);
		if (tempDir.exists()) {
			if (!deleteRecursively(tempDir)) {
				logger.warn("Failed to delete temp dir " + tempDir.getAbsolutePath());
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
		rootContext.setContextPath("/");
		rootContext.setServer(server);
		rootContext.setWar(location.toExternalForm());
		rootContext.setTempDirectory(tempDir);

		// Set cookies HttpOnly so they are not accessible to JavaScript engines
		HashSessionManager sessionManager = new HashSessionManager();
		sessionManager.setHttpOnly(true);
		// Use secure cookies if only serving https
		sessionManager.setSecureCookies(params.port <= 0 && params.securePort > 0);
		rootContext.getSessionHandler().setSessionManager(sessionManager);

		// Wicket Filter
		String wicketPathSpec = "/*";
		FilterHolder wicketFilter = new FilterHolder(WicketFilter.class);
		wicketFilter.setInitParameter(ContextParamWebApplicationFactory.APP_CLASS_PARAM,
				GitBlitWebApp.class.getName());
		wicketFilter.setInitParameter(WicketFilter.FILTER_MAPPING_PARAM, wicketPathSpec);
		wicketFilter.setInitParameter(WicketFilter.IGNORE_PATHS_PARAM, "git/");
		rootContext.addFilter(wicketFilter, wicketPathSpec, FilterMapping.DEFAULT);

		// Zip Servlet
		rootContext.addServlet(DownloadZipServlet.class, Constants.ZIP_SERVLET_PATH + "*");

		// Git Servlet
		ServletHolder gitServlet = null;
		String gitServletPathSpec = Constants.GIT_SERVLET_PATH + "*";
		if (FILESETTINGS.getBoolean(Keys.git.enableGitServlet, true)) {
			gitServlet = rootContext.addServlet(GitBlitServlet.class, gitServletPathSpec);
			gitServlet.setInitParameter("base-path", params.repositoriesFolder);
			gitServlet.setInitParameter("export-all",
					FILESETTINGS.getBoolean(Keys.git.exportAll, true) ? "1" : "0");
		}

		// Login Service
		LoginService loginService = null;
		String realmUsers = params.realmFile;
		if (!StringUtils.isEmpty(realmUsers)) {
			File realmFile = new File(realmUsers);
			if (realmFile.exists()) {
				logger.info("Setting up login service from " + realmUsers);
				JettyLoginService jettyLoginService = new JettyLoginService(realmFile);
				GitBlit.self().setLoginService(jettyLoginService);
				loginService = jettyLoginService;
			}
		}

		// Determine what handler to use
		Handler handler;
		if (gitServlet != null) {
			if (loginService != null) {
				// Authenticate Clone/Push
				logger.info("Setting up authenticated git servlet clone/push access");

				Constraint constraint = new Constraint();
				constraint.setAuthenticate(true);
				constraint.setRoles(new String[] { "*" });

				ConstraintMapping mapping = new ConstraintMapping();
				mapping.setPathSpec(gitServletPathSpec);
				mapping.setConstraint(constraint);

				ConstraintSecurityHandler security = new ConstraintSecurityHandler();
				security.addConstraintMapping(mapping);
				security.setAuthenticator(new BasicAuthenticator());
				security.setLoginService(loginService);
				security.setStrict(false);

				security.setHandler(rootContext);

				handler = security;
			} else {
				// Anonymous Pull/Push
				logger.info("Setting up anonymous git servlet pull/push access");
				handler = rootContext;
			}
		} else {
			logger.info("Git servlet clone/push disabled");
			handler = rootContext;
		}

		// Set the server's contexts
		server.setHandler(handler);

		// Setup the GitBlit context
		GitBlit gitblit = GitBlit.self();
		gitblit.configureContext(FILESETTINGS);
		rootContext.addEventListener(gitblit);

		// Start the Server
		try {
			if (params.shutdownPort > 0) {
				Thread shutdownMonitor = new ShutdownMonitorThread(server, params);
				shutdownMonitor.start();
			}
			server.start();
			server.join();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(100);
		}
	}

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
		connector.setAllowRenegotiate(false);
		connector.setKeystore(keystore.getAbsolutePath());
		connector.setPassword(password);
		connector.setPort(port);
		connector.setMaxIdleTime(30000);
		return connector;
	}

	/**
	 * Recursively delete a folder and its contents.
	 * 
	 * @param folder
	 */
	private static boolean deleteRecursively(File folder) {
		boolean deleted = true;
		for (File file : folder.listFiles()) {
			if (file.isDirectory()) {
				deleted &= deleteRecursively(file);
			} else {
				deleted &= file.delete();
			}
		}
		return deleted && folder.delete();
	}

	private static class ShutdownMonitorThread extends Thread {

		private final ServerSocket socket;

		private final Server server;

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
				logger.info(BORDER);
				logger.info("Stopping " + Constants.NAME);
				logger.info(BORDER);
				server.stop();
				server.setStopAtShutdown(false);
				accept.close();
				socket.close();
			} catch (Exception e) {
				logger.warn("Failed to shutdown Jetty", e);
			}
		}
	}

	@Parameters(separators = " ")
	private static class Params {

		/*
		 * Server parameters
		 */
		@Parameter(names = { "-h", "--help" }, description = "Show this help")
		public Boolean help = false;

		@Parameter(names = { "--stop" }, description = "Stop Server")
		public Boolean stop = false;

		@Parameter(names = { "--tempFolder" }, description = "Server temp folder")
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
		@Parameter(names = { "--realmFile" }, description = "Users Realm Hash File")
		public String realmFile = FILESETTINGS.getString(Keys.realm.realmFile, "users.properties");

		/*
		 * JETTY Parameters
		 */
		@Parameter(names = { "--useNio" }, description = "Use NIO Connector else use Socket Connector.")
		public Boolean useNIO = FILESETTINGS.getBoolean(Keys.server.useNio, true);

		@Parameter(names = "--httpPort", description = "HTTP port for to serve. (port <= 0 will disable this connector)")
		public Integer port = FILESETTINGS.getInteger(Keys.server.httpPort, 80);

		@Parameter(names = "--httpsPort", description = "HTTPS port to serve.  (port <= 0 will disable this connector)")
		public Integer securePort = FILESETTINGS.getInteger(Keys.server.httpsPort, 443);

		@Parameter(names = "--storePassword", description = "Password for SSL (https) keystore.")
		public String storePassword = FILESETTINGS.getString(Keys.server.storePassword, "");

		@Parameter(names = "--shutdownPort", description = "Port for Shutdown Monitor to listen on. (port <= 0 will disable this monitor)")
		public Integer shutdownPort = FILESETTINGS.getInteger(Keys.server.shutdownPort, 8081);

	}
}