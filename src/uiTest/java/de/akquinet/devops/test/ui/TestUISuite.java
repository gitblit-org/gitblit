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
package de.akquinet.devops.test.ui;

import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import de.akquinet.devops.test.ui.cases.UI_MultiAdminSupportTest;

/**
 * the test suite including all selenium-based ui-tests.
 * 
 * @author saheba
 *
 */
@RunWith(Suite.class)
@SuiteClasses({ UI_MultiAdminSupportTest.class })
public class TestUISuite {

	private static final String WEB_DRIVER_PROPERTY = "webdriver.gecko.driver";
    //local path to the latest Gecko web driver (requires Selenium 3 and JDK8+)
	private static final String WEB_DRIVER_PATH = "D:/dev/geckodriver-v0.24.0-win64/geckodriver.exe";

	@BeforeClass
	public static void suiteSetUp() {
		if (System.getProperty(WEB_DRIVER_PROPERTY) == null) {
			//only need to set it when running interactively in an IDE such as Eclipse 
			System.setProperty(WEB_DRIVER_PROPERTY, WEB_DRIVER_PATH);
		}
		System.out.println("Web driver setting: " + WEB_DRIVER_PROPERTY + "=" + System.getProperty(WEB_DRIVER_PROPERTY));
	}

}
