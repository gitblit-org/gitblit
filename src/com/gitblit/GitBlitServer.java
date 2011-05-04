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
import org.eclipse.jetty.server.ssl.SslConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSocketConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.FilterMapping;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jgit.http.server.GitServlet;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.GitBlitWebApp;

public class GitBlitServer {

	private final static Logger logger = Log.getLogger(GitBlitServer.class.getSimpleName());
	private final static String border_star = "***********************************************************";

	private final static FileSettings fileSettings = new FileSettings();

	public static void main(String[] args) {
		Params params = new Params();
		JCommander jc = new JCommander(params);
		try {
			jc.parse(args);
			if (params.help)
				usage(jc, null);
		} catch (ParameterException t) {
			usage(jc, t);
		}

		if (params.stop)
			stop(params);
		else
			start(params);
	}

	private static void usage(JCommander jc, ParameterException t) {
		System.out.println(border_star);
		System.out.println(Constants.getRunningVersion());
		System.out.println(border_star);
		System.out.println();
		if (t != null) {
			System.out.println(t.getMessage());
			System.out.println();
		}
		if (jc != null) {
			jc.usage();
			System.out.println("\nExample:\n  java -server -Xmx1024M -jar gitblit.jar --repos c:\\git --port 80 --securePort 443");
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
			out.write(("\r\n").getBytes());
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
		String pattern = fileSettings.getString(Keys.server.log4jPattern, "%-5p %d{MM-dd HH:mm:ss.SSS}  %-20.20c{1}  %m%n");

		// allow os override of logging pattern
		String os = System.getProperty("os.name").toLowerCase();
		if (os.indexOf("windows") > -1) {
			String winPattern = fileSettings.getString(Keys.server.log4jPattern_windows, pattern);
			if (!StringUtils.isEmpty(winPattern)) {
				pattern = winPattern;
			}
		} else if (os.indexOf("linux") > -1) {
			String linuxPattern = fileSettings.getString(Keys.server.log4jPattern_linux, pattern);
			if (!StringUtils.isEmpty(linuxPattern)) {
				pattern = linuxPattern;
			}
		}

		PatternLayout layout = new PatternLayout(pattern);
		org.apache.log4j.Logger rootLogger = org.apache.log4j.Logger.getRootLogger();
		rootLogger.addAppender(new ConsoleAppender(layout));

		logger.info(border_star);
		logger.info(Constants.getRunningVersion());
		logger.info(border_star);

		String osname = System.getProperty("os.name");
		String osversion = System.getProperty("os.version");
		logger.info("Running on " + osname + " (" + osversion + ")");

		// Determine port connectors
		List<Connector> connectors = new ArrayList<Connector>();
		if (params.port > 0) {
			Connector httpConnector = createConnector(params.useNIO, params.port);
			String bindInterface = fileSettings.getString(Keys.server.httpBindInterface, null);
			if (!StringUtils.isEmpty(bindInterface)) {
				logger.warn(MessageFormat.format("Binding port {0} to {1}", params.port, bindInterface));
				httpConnector.setHost(bindInterface);
			}
			connectors.add(httpConnector);
		}

		if (params.securePort > 0) {
			if (new File("keystore").exists()) {
				Connector secureConnector = createSSLConnector(params.useNIO, params.securePort, params.storePassword);
				String bindInterface = fileSettings.getString(Keys.server.httpsBindInterface, null);
				if (!StringUtils.isEmpty(bindInterface)) {
					logger.warn(MessageFormat.format("Binding port {0} to {1}", params.port, bindInterface));
					secureConnector.setHost(bindInterface);
				}
				connectors.add(secureConnector);
			} else {
				logger.warn("Failed to find Keystore?  Did you run \"makekeystore\"?");
				logger.warn("SSL connector DISABLED.");
			}
		}

		// tempDir = Directory where...
		// * WebApp is expanded
		//
		File tempDir = new File(params.temp);
		if (tempDir.exists())
			deleteRecursively(tempDir);
		tempDir.mkdirs();

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

		// Wicket Filter
		String wicketPathSpec = "/*";
		FilterHolder wicketFilter = new FilterHolder(WicketFilter.class);
		wicketFilter.setInitParameter(ContextParamWebApplicationFactory.APP_CLASS_PARAM, GitBlitWebApp.class.getName());
		wicketFilter.setInitParameter(WicketFilter.FILTER_MAPPING_PARAM, wicketPathSpec);
		wicketFilter.setInitParameter(WicketFilter.IGNORE_PATHS_PARAM, "git/");
		rootContext.addFilter(wicketFilter, wicketPathSpec, FilterMapping.DEFAULT);

		// Git Servlet
		ServletHolder gitServlet = null;
		String gitServletPathSpec = "/git/*";
		if (fileSettings.getBoolean(Keys.git.allowPushPull, true)) {
			gitServlet = rootContext.addServlet(GitServlet.class, gitServletPathSpec);
			gitServlet.setInitParameter("base-path", params.repositoriesFolder);
			gitServlet.setInitParameter("export-all", params.exportAll ? "1" : "0");
		}

		// Login Service
		LoginService loginService = null;
		String realmUsers = params.realmFile;
		if (realmUsers != null && new File(realmUsers).exists()) {
			logger.info("Setting up login service from " + realmUsers);
			JettyLoginService jettyLoginService = new JettyLoginService(realmUsers);
			GitBlit.self().setLoginService(jettyLoginService);
			loginService = jettyLoginService;
		}

		// Determine what handler to use
		Handler handler;
		if (gitServlet != null) {
			if (loginService != null && params.authenticatePushPull) {
				// Authenticate Pull/Push
				String[] roles = new String[] { Constants.PULL_ROLE, Constants.PUSH_ROLE };
				logger.info("Authentication required for git servlet pull/push access");

				Constraint constraint = new Constraint();
				constraint.setName("auth");
				constraint.setAuthenticate(true);
				constraint.setRoles(roles);

				ConstraintMapping mapping = new ConstraintMapping();
				mapping.setPathSpec(gitServletPathSpec);
				mapping.setConstraint(constraint);

				ConstraintSecurityHandler security = new ConstraintSecurityHandler();
				security.addConstraintMapping(mapping);
				for (String role : roles) {
					security.addRole(role);
				}
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
			logger.info("Git servlet pull/push disabled");
			handler = rootContext;
		}

		// Set the server's contexts
		server.setHandler(handler);

		// Setup the GitBlit context
		GitBlit gitblit = GitBlit.self();
		gitblit.configureContext(fileSettings);
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

	private static Connector createSSLConnector(boolean useNIO, int port, String password) {
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
		connector.setKeystore("keystore");
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
	private static void deleteRecursively(File folder) {
		for (File file : folder.listFiles()) {
			if (file.isDirectory())
				deleteRecursively(file);
			else
				file.delete();
		}
		folder.delete();
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
				logger.warn(e);
			}
			socket = skt;
		}

		@Override
		public void run() {
			logger.info("Shutdown Monitor listening on port " + socket.getLocalPort());
			Socket accept;
			try {
				accept = socket.accept();
				BufferedReader reader = new BufferedReader(new InputStreamReader(accept.getInputStream()));
				reader.readLine();
				logger.info(border_star);
				logger.info("Stopping " + Constants.NAME);
				logger.info(border_star);
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

		@Parameter(names = { "--temp" }, description = "Server temp folder")
		public String temp = fileSettings.getString(Keys.server.tempFolder, "temp");

		/*
		 * GIT Servlet Parameters
		 */
		@Parameter(names = { "--repos" }, description = "Git Repositories Folder")
		public String repositoriesFolder = fileSettings.getString(Keys.git.repositoriesFolder, "repos");

		@Parameter(names = { "--exportAll" }, description = "Export All Found Repositories")
		public Boolean exportAll = fileSettings.getBoolean(Keys.git.exportAll, true);

		/*
		 * Authentication Parameters
		 */
		@Parameter(names = { "--authenticatePushPull" }, description = "Authenticate Git Push/Pull access")
		public Boolean authenticatePushPull = fileSettings.getBoolean(Keys.git.authenticate, true);

		@Parameter(names = { "--realm" }, description = "Users Realm Hash File")
		public String realmFile = fileSettings.getString(Keys.server.realmFile, "users.properties");

		/*
		 * JETTY Parameters
		 */
		@Parameter(names = { "--nio" }, description = "Use NIO Connector else use Socket Connector.")
		public Boolean useNIO = fileSettings.getBoolean(Keys.server.useNio, true);

		@Parameter(names = "--port", description = "HTTP port for to serve. (port <= 0 will disable this connector)")
		public Integer port = fileSettings.getInteger(Keys.server.httpPort, 80);

		@Parameter(names = "--securePort", description = "HTTPS port to serve.  (port <= 0 will disable this connector)")
		public Integer securePort = fileSettings.getInteger(Keys.server.httpsPort, 443);

		@Parameter(names = "--storePassword", description = "Password for SSL (https) keystore.")
		public String storePassword = fileSettings.getString(Keys.server.storePassword, "");

		@Parameter(names = "--shutdownPort", description = "Port for Shutdown Monitor to listen on. (port <= 0 will disable this monitor)")
		public Integer shutdownPort = fileSettings.getInteger(Keys.server.shutdownPort, 8081);

	}
}