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
package de.akquinet.devops.test.ui.cases;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import de.akquinet.devops.test.ui.generic.AbstractUITest;
import de.akquinet.devops.test.ui.view.RepoListWithViewSelectorView;

/**
 * tests the "show selected projects only" feature.
 * 
 * @author amurphy
 * 
 */
public class UI_SelectedProjectsSupportTest extends AbstractUITest {

	String baseUrl = "https://localhost:8443";
	RepoListWithViewSelectorView view;

	@Before
	public void before() {
		System.out.println("IN BEFORE");
		this.view = new RepoListWithViewSelectorView(AbstractUITest.getDriver(), baseUrl);
		AbstractUITest.getDriver().navigate().to(baseUrl);
	}

	@Test
	public void test_DefaultRepoView() {
		
		// check that the toggle is not present
		Assert.assertFalse(view.isSelectionToggleVisible());
		
		// log in
		view.login("repocreator", "repocreator");

		// check that the toggle is present
		Assert.assertTrue(view.isSelectionToggleVisible());
		
		// check that the "view all" option is active
		Assert.assertTrue(view.isShowAllReposViewSelected());
		
		// check that there are no selected projects
		Assert.assertFalse(view.hasSelectedProjects());
		
		// check that clicking on the "selected only" button shows no repos
		view.selectShowSelectedProjectsView();
		view.sleep(1000);
		Assert.assertFalse(view.hasVisibleProjects());
		
		// check that clicking on the "view all" button shows repos again
		view.selectShowAllReposView();
		view.sleep(1000);
		Assert.assertTrue(view.hasVisibleProjects());
		
		// check that selecting projects works
		Assert.assertEquals(6, view.getNumVisibleProjects());
		Assert.assertEquals(view.getNumUnselectedVisibleProjects(), view.getNumVisibleProjects());
		view.selectNthVisibleUnselectedProject(1);
		view.sleep(1000);
		Assert.assertTrue(view.hasSelectedProjects());
		Assert.assertEquals(1, view.getNumSelectedVisibleProjects());
		Assert.assertEquals(5, view.getNumUnselectedVisibleProjects());
		view.selectNthVisibleUnselectedProject(3);
		view.sleep(1000);
		Assert.assertEquals(2, view.getNumSelectedVisibleProjects());
		
		// check that unselecting projects works
		view.unselectNthVisibleSelectedProject(1);
		view.sleep(1000);
		Assert.assertEquals(1, view.getNumSelectedVisibleProjects());
		
		// check that clicking on the "selected only" button again shows only the one selected project
		view.selectShowSelectedProjectsView();
		view.sleep(1000);
		Assert.assertTrue(view.hasVisibleProjects());
		Assert.assertEquals(view.getNumSelectedVisibleProjects(), view.getNumVisibleProjects());
		
		// check that unstarring the repo shows no repos
		view.unselectNthVisibleSelectedProject(view.getNumSelectedVisibleProjects() - 1);
		Assert.assertFalse(view.hasVisibleProjects());
		
		// log out
		view.logout();
		
		// log in
		view.login("repocreator", "repocreator");
		
		// check that the selected view is displayed
		Assert.assertTrue(view.isShowSelectedProjectsViewSelected());
		
		// check that switching back to the full view shows all repos again
		view.selectShowAllReposView();
		view.sleep(1000);
		Assert.assertTrue(view.hasVisibleProjects());
		
	}

}
