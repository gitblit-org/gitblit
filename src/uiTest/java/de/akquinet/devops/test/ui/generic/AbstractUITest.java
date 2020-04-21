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
package de.akquinet.devops.test.ui.generic;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;

import com.gitblit.GitBlitServer;

import de.akquinet.devops.GitblitRunnable;

/**
 * This abstract class implements the setUpClass and tearDownClass for
 * selenium-based UITests. They require a running gitblit server instance and a
 * webdriver instance, which are managed by the setUpClass and tearDownClass
 * method. Write a separate test class derived from this abstract class for each
 * scenario consisting of one or more test cases, which can share the same
 * server instance.
 * 
 * @author saheba
 * 
 */
public abstract class AbstractUITest {

	private static Thread serverThread;
	private static WebDriver driver;

	protected static final int HTTP_PORT = 8080, HTTPS_PORT = 0, //8443,
			SHUTDOWN_PORT = 8081;
	private static final String GITBLIT_PROPERTIES_PATH = "src/test/config/test-ui-gitblit.properties",
			USERS_PROPERTIES_PATH = "src/test/config/test-ui-users.conf";

	/**
	 * starts a gitblit server instance in a separate thread before test cases
	 * of concrete, non-abstract child-classes are executed
	 */
	@BeforeClass
	public static void setUpClass() {
		Runnable gitblitRunnable = new GitblitRunnable(HTTP_PORT, HTTPS_PORT,
				SHUTDOWN_PORT, GITBLIT_PROPERTIES_PATH, USERS_PROPERTIES_PATH);

		serverThread = new Thread(gitblitRunnable);
		serverThread.start();
		FirefoxProfile firefoxProfile = new FirefoxProfile();
		firefoxProfile.setPreference("startup.homepage_welcome_url",
				"https://www.google.de");

		firefoxProfile.setPreference("browser.download.folderList", 2);
		firefoxProfile.setPreference(
				"browser.download.manager.showWhenStarting", false);
		String downloadDir = System.getProperty("java.io.tmpdir");
		firefoxProfile.setPreference("browser.download.dir", downloadDir);
		firefoxProfile.setPreference("browser.helperApps.neverAsk.saveToDisk",
				"text/csv,text/plain,application/zip,application/pdf");
		firefoxProfile.setPreference("browser.helperApps.alwaysAsk.force",
				false);
		System.out.println("Saving all attachments to: " + downloadDir);

		//driver = new FirefoxDriver(firefoxProfile); //Selenium 2
		driver = new FirefoxDriver(new FirefoxOptions().setProfile(firefoxProfile)); //Selenium 3, and JDK8+
	}

	/**
	 * stops the gitblit server instance running in a separate thread after test
	 * cases of concrete, non-abstract child-classes have been executed
	 */
	@AfterClass
	public static void tearDownClass() throws InterruptedException {
		driver.quit();
		// Stop Gitblit
		GitBlitServer.main("--stop", "--shutdownPort", "" + SHUTDOWN_PORT);

		// Wait a few seconds for it to be running completely including thread
		// destruction
		Thread.sleep(1000);
	}

	public static WebDriver getDriver() {
		return AbstractUITest.driver;
	}
}
