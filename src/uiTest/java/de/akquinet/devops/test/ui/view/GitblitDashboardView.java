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
package de.akquinet.devops.test.ui.view;

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * class representing the view components and possible user interactions, you
 * can see and do on most screens when you are logged in.
 * 
 * @author saheba
 * 
 */
public class GitblitDashboardView extends GitblitPageView {

	public static final String TITLE_STARTS_WITH = "localhost";

	public GitblitDashboardView(WebDriver driver, String baseUrl) {
		super(driver, baseUrl);
	}

	public boolean isLoginPartVisible() {
		List<WebElement> found = getDriver().findElements(
				By.partialLinkText("logout"));
		return found == null || found.size() == 0;
	}

	public void logout() {
		// String pathLogout = "//a[@href =\"?" + WICKET_HREF_PAGE_PATH
		// + ".LogoutPage\"]";
		// List<WebElement> logout =
		// getDriver().findElements(By.xpath(pathLogout));
		// logout.get(0).click();
		// replaced by url call because click hangs sometimes if the clicked
		// object is not a button or selenium ff driver does not notice the
		// change for any other reason
		getDriver().navigate().to(
				getBaseUrl() + "?" + WICKET_HREF_PAGE_PATH + ".LogoutPage");
	}

	public static final String LOGIN_AREA_SELECTOR = "//span[@class = \"form-search\" ]";
	public static final String WICKET_PAGES_PACKAGE_NAME = "com.gitblit.wicket.pages";
	public static final String WICKET_HREF_PAGE_PATH = "wicket:bookmarkablePage=:"
			+ WICKET_PAGES_PACKAGE_NAME;

	synchronized public void waitToLoadFor(int sec) {
		WebDriverWait webDriverWait = new WebDriverWait(getDriver(), sec);
		webDriverWait.until(new ExpectedCondition<Boolean>() {
			public Boolean apply(WebDriver d) {
				return d.getTitle().toLowerCase()
						.startsWith(GitblitDashboardView.TITLE_STARTS_WITH);
			}
		});
	}

	public void login(String id, String pw) {
		String pathID = LOGIN_AREA_SELECTOR + "/input[@name = \"username\" ]";
		String pathPW = LOGIN_AREA_SELECTOR + "/input[@name = \"password\" ]";
		String pathSubmit = LOGIN_AREA_SELECTOR
				+ "/button[@type = \"submit\" ]";
		// System.out.println("DRIVER:"+getDriver());
		// List<WebElement> findElement =
		// getDriver().findElements(By.xpath("//span[@class = \"form-search\" ]"));
		//
		// System.out.println("ELEM: "+findElement);
		// System.out.println("SIZE: "+findElement.size());
		// System.out.println("XPath: "+pathID);
		WebElement idField = getDriver().findElement(By.xpath(pathID));
		// System.out.println("IDFIELD:"+idField);
		idField.sendKeys(id);
		WebElement pwField = getDriver().findElement(By.xpath(pathPW));
		// System.out.println(pwField);
		pwField.sendKeys(pw);
		WebElement submit = getDriver().findElement(By.xpath(pathSubmit));
		submit.click();
	}

	public void acceptAlertDialog() {
		getDriver().switchTo().alert().accept();
	}
}
