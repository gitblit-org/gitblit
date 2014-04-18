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
 * class representing the tabs you can access when you edit a repo.
 *
 * @author saheba
 *
 */
public class RepoEditView extends GitblitDashboardView {

	public static final String PERMISSION_VIEW_USERS_NAME_PREFIX = "users:";
	public static final String PERMISSION_VIEW_TEAMS_NAME_PREFIX = "teams:";

	public static final String PERMISSION_VIEW_MUTABLE = "permissionToggleForm:showMutable";
	public static final String PERMISSION_VIEW_SPECIFIED = "permissionToggleForm:showSpecified";
	public static final String PERMISSION_VIEW_EFFECTIVE = "permissionToggleForm:showEffective";

	public static final int RESTRICTION_ANONYMOUS_VCP = 0;
	public static final int RESTRICTION_AUTHENTICATED_P = 1;
	public static final int RESTRICTION_AUTHENTICATED_CP = 2;
	public static final int RESTRICTION_AUTHENTICATED_VCP = 3;

	public static final int AUTHCONTROL_RWALL = 0;
	public static final int AUTHOCONTROL_FINE = 1;

	public RepoEditView(WebDriver driver) {
		super(driver, null);
	}

	public void changeName(String newName) {
		String pathName = "//input[@id = \"name\" ]";
		WebElement field = getDriver().findElement(By.xpath(pathName));
		field.clear();
		field.sendKeys(newName);
	}

	public boolean navigateToPermissionsTab() {
		String linkText = "access permissions";
		List<WebElement> found = getDriver().findElements(
				By.partialLinkText(linkText));
		if (found != null && found.size() == 1) {
			found.get(0).click();
			return true;
		}
		return false;
	}

	private void changeOwners(String action,
			String affectedSelection, String username) {
		String xpath = "//select[@name=\"" + affectedSelection
				+ "\"]/option[@value = \"" + username + "\" ]";
		WebElement option = getDriver().findElement(By.xpath(xpath));
		option.click();
		String buttonPath = "//button[@class=\"button " + action + "\"]";
		WebElement button = getDriver().findElement(By.xpath(buttonPath));
		button.click();
	}

	public void removeOwner(String username) {
		changeOwners("remove", "owners:selection",
				username);
	}

	public void addOwner(String username) {
		changeOwners("add", "owners:choices", username);
	}

	public WebElement getAccessRestrictionSelection() {
		String xpath = "//select[@name =\"accessRestriction\"]";
		List<WebElement> found = getDriver().findElements(By.xpath(xpath));
		if (found != null && found.size() == 1) {
			return found.get(0);
		}
		return null;
	}

	public boolean changeAccessRestriction(int option) {
		WebElement accessRestrictionSelection = getAccessRestrictionSelection();
		if (accessRestrictionSelection == null) {
			return false;
		}
		accessRestrictionSelection.click();
		sleep(100);
		String xpath = "//select[@name =\"accessRestriction\"]/option[@value=\""
				+ option + "\"]";
		List<WebElement> found = getDriver().findElements(By.xpath(xpath));
		if (found == null || found.size() == 0 || found.size() > 1) {
			return false;
		}
		found.get(0).click();
		return true;
	}

	public boolean changeAuthorizationControl(int option) {
		System.out.println("try to change auth control");
		String xpath = "//input[@name =\"authorizationControl\" and @value=\""
				+ option + "\"]";
		List<WebElement> found = getDriver().findElements(By.xpath(xpath));
		if (found != null && found.size() == 1) {
			found.get(0).click();
			return true;
		}
		return false;
	}

	private boolean isPermissionViewDisabled(String prefix, String view) {
		String xpath = "//[@name =\"" + prefix + view + "\"]";
		List<WebElement> found = getDriver().findElements(By.xpath(xpath));
		if (found == null || found.size() == 0 || found.size() > 1) {
			return false;
		}
		String attrValue = found.get(0).getAttribute("disabled");
		return (attrValue != null) && (attrValue.equals("disabled"));
	}

	public boolean isPermissionViewSectionDisabled(String prefix) {
		return isPermissionViewDisabled(prefix, PERMISSION_VIEW_MUTABLE)
				&& isPermissionViewDisabled(prefix, PERMISSION_VIEW_SPECIFIED)
				&& isPermissionViewDisabled(prefix, PERMISSION_VIEW_EFFECTIVE);
	}

	public boolean save() {
		String xpath = "//div[@class=\"form-actions\"]/input[@name =\""
				+ "save" + "\"]";
		List<WebElement> found = getDriver().findElements(By.xpath(xpath));
		if (found == null || found.size() == 0 || found.size() > 1) {
			return false;
		}
		found.get(0).click();
		WebDriverWait webDriverWait = new WebDriverWait(getDriver(), 1);
		webDriverWait.until(new Exp.RepoListViewLoaded());
		return true;
	}
}
