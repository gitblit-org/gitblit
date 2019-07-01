/*
 * Copyright 2013 akquinet tech@spree GmbH
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
package de.akquinet.devops;

import java.io.File;
import java.net.InetAddress;
import java.net.ServerSocket;

import com.gitblit.tests.GitBlitTestConfig;

/**
 * This is a runnable implementation, that is used to run a gitblit server in a
 * separate thread (e.g. alongside test cases)
 * 
 * @author saheba
 * 
 */
public class GitblitRunnable implements Runnable {

	private int httpPort, httpsPort, shutdownPort;
	private String userPropertiesPath, gitblitPropertiesPath;
	private boolean startFailed = false;

	/**
	 * constructor with reduced set of start params
	 * 
	 * @param httpPort
	 * @param httpsPort
	 * @param shutdownPort
	 * @param gitblitPropertiesPath
	 * @param userPropertiesPath
	 */
	public GitblitRunnable(int httpPort, int httpsPort, int shutdownPort,
			String gitblitPropertiesPath, String userPropertiesPath) {
		this.httpPort = httpPort;
		this.httpsPort = httpsPort;
		this.shutdownPort = shutdownPort;
		this.userPropertiesPath = userPropertiesPath;
		this.gitblitPropertiesPath = gitblitPropertiesPath;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		boolean portsFree = false;
		long lastRun = -1;
		while (!portsFree) {
			long current = System.currentTimeMillis();
			if (lastRun == -1 || lastRun + 100 < current) {
				portsFree = areAllPortsFree(new int[] { httpPort, httpsPort,
						shutdownPort }, "127.0.0.1");
			}
			lastRun = current;

		}
		try {
			GitBlitServer4UITests.main(
					"--httpPort", "" + httpPort,
					"--httpsPort", "" + httpsPort,
					"--shutdownPort", "" + shutdownPort,
					"--repositoriesFolder", GitBlitTestConfig.REPOSITORIES.getAbsolutePath(),
					"--userService", new File(userPropertiesPath).getAbsolutePath(),
					"--settings", new File(gitblitPropertiesPath).getAbsolutePath(),
					"--baseFolder", "data");
			setStartFailed(false);
		} catch (Exception iex) {
			System.out.println("Gitblit server start failed");
			setStartFailed(true);
		}
	}

	/**
	 * Method used to ensure that all ports are free, if the runnable is used
	 * JUnit test classes. Be aware that JUnit's setUpClass and tearDownClass
	 * methods, which are executed before and after a test class (consisting of
	 * several test cases), may be executed parallely if they are part of a test
	 * suite consisting of several test classes. Therefore the run method of
	 * this class calls areAllPortsFree to check port availability before
	 * starting another gitblit instance.
	 * 
	 * @param ports
	 * @param inetAddress
	 * @return
	 */
	public static boolean areAllPortsFree(int[] ports, String inetAddress) {
		System.out
				.println("\n"
						+ System.currentTimeMillis()
						+ " ----------------------------------- testing if all ports are free ...");
		String blockedPorts = "";
		for (int i = 0; i < ports.length; i++) {
			ServerSocket s;
			try {
				s = new ServerSocket(ports[i], 1,
						InetAddress.getByName(inetAddress));
				s.close();
			} catch (Exception e) {
				if (!blockedPorts.equals("")) {
					blockedPorts += ", ";
				}
			}
		}
		if (blockedPorts.equals("")) {
			System.out
					.println(" ----------------------------------- ... verified");
			return true;
		}
		System.out.println(" ----------------------------------- ... "
				+ blockedPorts + " are still blocked");
		return false;
	}

	private void setStartFailed(boolean startFailed) {
		this.startFailed = startFailed;
	}

	public boolean isStartFailed() {
		return startFailed;
	}
}
