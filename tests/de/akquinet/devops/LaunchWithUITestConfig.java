package de.akquinet.devops;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import junit.framework.Assert;

import org.junit.Test;

import com.gitblit.Constants;
import com.gitblit.GitBlitServer;
import com.gitblit.tests.GitBlitSuite;

/**
 * This test checks if it is possible to run two server instances in the same
 * JVM sequentially
 * 
 * @author saheba
 * 
 */
public class LaunchWithUITestConfig {

	@Test
	public void testSequentialLaunchOfSeveralInstances()
			throws InterruptedException {
		// different ports than in testParallelLaunchOfSeveralInstances to
		// ensure that both test cases do not affect each others test results
		int httpPort = 9191, httpsPort = 9292, shutdownPort = 9393;
		String gitblitPropertiesPath = "test-ui-gitblit.properties", usersPropertiesPath = "test-ui-users.conf";

		GitblitRunnable gitblitRunnable = new GitblitRunnable(httpPort,
				httpsPort, shutdownPort, gitblitPropertiesPath,
				usersPropertiesPath);
		Thread serverThread = new Thread(gitblitRunnable);
		serverThread.start();
		Thread.sleep(2000);
		Assert.assertFalse(gitblitRunnable.isStartFailed());
		LaunchWithUITestConfig.shutdownGitBlitServer(shutdownPort);

		Thread.sleep(5000);

		GitblitRunnable gitblitRunnable2 = new GitblitRunnable(httpPort,
				httpsPort, shutdownPort, gitblitPropertiesPath,
				usersPropertiesPath);
		Thread serverThread2 = new Thread(gitblitRunnable2);
		serverThread2.start();
		Thread.sleep(2000);
		Assert.assertFalse(gitblitRunnable2.isStartFailed());
		LaunchWithUITestConfig.shutdownGitBlitServer(shutdownPort);
	}

	@Test
	public void testParallelLaunchOfSeveralInstances()
			throws InterruptedException {
		// different ports than in testSequentialLaunchOfSeveralInstances to
		// ensure that both test cases do not affect each others test results
		int httpPort = 9797, httpsPort = 9898, shutdownPort = 9999;
		int httpPort2 = 9494, httpsPort2 = 9595, shutdownPort2 = 9696;
		String gitblitPropertiesPath = "test-ui-gitblit.properties", usersPropertiesPath = "test-ui-users.conf";

		GitblitRunnable gitblitRunnable = new GitblitRunnable(httpPort,
				httpsPort, shutdownPort, gitblitPropertiesPath,
				usersPropertiesPath);
		Thread serverThread = new Thread(gitblitRunnable);
		serverThread.start();
		Thread.sleep(2000);
		Assert.assertFalse(gitblitRunnable.isStartFailed());

		GitblitRunnable gitblitRunnable2 = new GitblitRunnable(httpPort2,
				httpsPort2, shutdownPort2, gitblitPropertiesPath,
				usersPropertiesPath);
		Thread serverThread2 = new Thread(gitblitRunnable2);
		serverThread2.start();
		Thread.sleep(2000);
		Assert.assertFalse(gitblitRunnable2.isStartFailed());

		LaunchWithUITestConfig.shutdownGitBlitServer(shutdownPort);
		LaunchWithUITestConfig.shutdownGitBlitServer(shutdownPort2);
	}

	/**
	 * main runs the tests without assert checks. You have to check the console
	 * output manually.
	 * 
	 * @param args
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws InterruptedException {
		new LaunchWithUITestConfig().testSequentialLaunchOfSeveralInstances();
		new LaunchWithUITestConfig().testParallelLaunchOfSeveralInstances();
	}

	private static void shutdownGitBlitServer(int shutdownPort) {
		try {
			Socket s = new Socket(InetAddress.getByName("127.0.0.1"),
					shutdownPort);
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
}
