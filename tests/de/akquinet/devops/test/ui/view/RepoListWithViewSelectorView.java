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

import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;


/**
 * class representing the repositories page plus the new selected
 * projects/groups markup
 * 
 * @author amurphy
 * 
 */
public class RepoListWithViewSelectorView extends RepoListView {
	
	public static final String TOGGLE_ID = "repositoryViewSelectorPanel", 
			SHOW_ALL_BUTTON_NAME = "showAll", 
			SHOW_SELECTED_BUTTON_NAME = "showSelected",
			SELECTED_BUTTON_CLASS = "active",
			SELECTED_PROJECT_BUTTON_CLASS = "selectedProject",
			UNSELECTED_PROJECT_BUTTON_CLASS = "unselectedProject",
			REPOSITORIES_TABLE_CLASS = "repositories",
			PROJECT_GROUP_TR_CLASS = "group";

	public RepoListWithViewSelectorView(WebDriver driver, String baseUrl) {
		super(driver, baseUrl);
	}
	
	/**
	 * Utilities
	 */
	private WebElement getShowAllReposButton() {
		return getDriver().findElement(By.cssSelector("a[name=" + SHOW_ALL_BUTTON_NAME + "]"));
	}
	private WebElement getShowSelectedReposButton() {
		return getDriver().findElement(By.cssSelector("a[name=" + SHOW_SELECTED_BUTTON_NAME + "]"));
	}
	private List<WebElement> getVisibleElements(List<WebElement> elements) {
		List<WebElement> visibleElements = new ArrayList<WebElement>();
		for (WebElement element : elements) {
			if (element.isDisplayed()) {
				visibleElements.add(element);
			}
		}
		return visibleElements;
	}
	private List<WebElement> getSelectedVisibleProjectButtons() {
		return getVisibleElements(getDriver().findElements(
				By.cssSelector("." + SELECTED_PROJECT_BUTTON_CLASS)));
	}
	private List<WebElement> getUnselectedVisibleProjectButtons() {
		return getVisibleElements(getDriver().findElements(
				By.cssSelector("." + UNSELECTED_PROJECT_BUTTON_CLASS)));
	}
	public int getNumSelectedVisibleProjects() {
		return getSelectedVisibleProjectButtons().size();
	}
	public int getNumUnselectedVisibleProjects() {
		return getUnselectedVisibleProjectButtons().size();
	}
	public List<WebElement> getAllProjects() {
		WebElement repoTable = getDriver().findElement(By.cssSelector("table." + REPOSITORIES_TABLE_CLASS));
		return repoTable.findElement(By.tagName("tbody")).findElements(By.cssSelector("tr." + PROJECT_GROUP_TR_CLASS));
	}
	public List<WebElement> getVisibleProjects() {
		return getVisibleElements(getAllProjects());
	}
	public int getNumVisibleProjects() {
		return getVisibleProjects().size();
	}
	
	
	/**
	 * Testables
	 */
	public boolean isSelectionToggleVisible() {
		try {
			getDriver().findElement(By.id(TOGGLE_ID));
	    } catch (NoSuchElementException ex) { 
	        return false; 
	    }
		return true;
	}
	public boolean isShowAllReposViewSelected() {
		return getShowAllReposButton().getAttribute("class").contains(SELECTED_BUTTON_CLASS);
	}
	public boolean isShowSelectedProjectsViewSelected() {
		return getShowSelectedReposButton().getAttribute("class").contains(SELECTED_BUTTON_CLASS);
	}
	public boolean hasVisibleProjects() {
		List<WebElement> visibleProjects = getVisibleProjects();
		return (visibleProjects != null) && (visibleProjects.size() > 0);
	}
	public boolean hasSelectedProjects() {
		List<WebElement> selectedProjects = getSelectedVisibleProjectButtons();
		return (selectedProjects != null) && (selectedProjects.size() > 0);
	}
	
	/**
	 * Actions
	 */
	public void selectShowAllReposView() {
		getShowAllReposButton().click();
	}
	public void selectShowSelectedProjectsView() {
		getShowSelectedReposButton().click();
	}
	public void selectNthVisibleUnselectedProject(int n) {
		List<WebElement> unselectedProjectButtons = getUnselectedVisibleProjectButtons();
		if (unselectedProjectButtons.size() > n) {
			unselectedProjectButtons.get(n).click();
		} else {
		}
	}
	public void unselectNthVisibleSelectedProject(int n) {
		List<WebElement> selectedProjectButtons = getSelectedVisibleProjectButtons();
		if (selectedProjectButtons.size() > n) {
			selectedProjectButtons.get(n).click();
		}
	}
}
