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
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * class representing the repo list view, which you see e.g. right after you
 * logged in.
 * 
 * @author saheba
 * 
 */
public class RepoListView extends GitblitDashboardView {

	public RepoListView(WebDriver driver, String baseUrl) {
		super(driver, baseUrl);
	}
	
	public boolean isEmptyRepo(String fullyQualifiedRepoName) {
		String pathToLink = "//a[@href = \"?" + WICKET_HREF_PAGE_PATH
				+ ".EmptyRepositoryPage&r=" + fullyQualifiedRepoName + "\"]";
		List<WebElement> found = getDriver().findElements(By.xpath(pathToLink));
		return found != null && found.size() > 0;
	}

	private String getEditRepoPath(String fullyQualifiedRepoName) {
		return "//a[@href =\"?" + WICKET_HREF_PAGE_PATH
				+ ".EditRepositoryPage&r=" + fullyQualifiedRepoName + "\"]";
	}

	private String getDeleteRepoOnclickIdentifier(
			String fullyQualifiedRepoPathAndName) {
		return "var conf = confirm('Delete repository \""
				+ fullyQualifiedRepoPathAndName
				+ "\"?'); if (!conf) return false; ";
	}

	public boolean navigateToNewRepo(long waitSecToLoad) {
		String pathToLink = "//a[@href =\"?" + WICKET_HREF_PAGE_PATH
				+ ".EditRepositoryPage\"]";
		List<WebElement> found = getDriver().findElements(By.xpath(pathToLink));
		if (found == null || found.size() == 0 || found.size() > 1) {
			return false;
		}
		found.get(0).click();
		WebDriverWait webDriverWait = new WebDriverWait(getDriver(),
				waitSecToLoad);
		webDriverWait.until(new Exp.EditRepoViewLoaded());
		return true;
	}

	private boolean checkOrDoEditRepo(String fullyQualifiedRepoName,
			boolean doEdit) {
		List<WebElement> found = getDriver().findElements(
				By.xpath(getEditRepoPath(fullyQualifiedRepoName)));
		if (found == null || found.size() == 0 || found.size() > 1) {
			return false;
		}
		if (doEdit) {
			found.get(0).click();
		}
		return true;
	}

	public boolean navigateToEditRepo(String fullyQualifiedRepoName,
			int waitSecToLoad) {
		boolean result = checkOrDoEditRepo(fullyQualifiedRepoName, true);
		WebDriverWait webDriverWait = new WebDriverWait(getDriver(),
				waitSecToLoad);
		webDriverWait.until(new Exp.EditRepoViewLoaded());
		return result;
	}

	public boolean isEditableRepo(String fullyQualifiedRepoName) {
		return checkOrDoEditRepo(fullyQualifiedRepoName, false);
	}

	private boolean checkOrDoDeleteRepo(String fullyQualifiedRepoPathAndName,
			boolean doDelete) {
		List<WebElement> found = getDriver().findElements(
				By.partialLinkText("delete"));
		String onclickIdentifier = getDeleteRepoOnclickIdentifier(fullyQualifiedRepoPathAndName);
		WebElement result = null;
		for (WebElement webElement : found) {
			if (webElement.getAttribute("onclick") != null
					&& webElement.getAttribute("onclick").equals(
							onclickIdentifier)) {
				result = webElement;
				break;
			}
		}
		System.out.println("result ? " + result);
		if (result == null) {
			return false;
		}
		if (doDelete) {
			System.out.println(".............. DO DELETE .... ");
			result.click();
		}
		return true;
	}

	public boolean isDeletableRepo(String fullyQualifiedRepoPathAndName) {
		return checkOrDoDeleteRepo(fullyQualifiedRepoPathAndName, false);
	}

	public boolean navigateToDeleteRepo(String fullyQualifiedRepoPathAndName) {
		return checkOrDoDeleteRepo(fullyQualifiedRepoPathAndName, true);

	}
}
