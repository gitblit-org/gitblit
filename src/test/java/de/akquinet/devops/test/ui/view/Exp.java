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

/**
 * container class for selenium conditions
 * 
 * @author saheba
 *
 */
public class Exp {
	public static class EditRepoViewLoaded implements ExpectedCondition<Boolean> {
		public Boolean apply(WebDriver d) {
			List<WebElement> findElements = d.findElements(By.partialLinkText("general"));
			return findElements.size() == 1;
		}
	}
	public static class RepoListViewLoaded implements ExpectedCondition<Boolean> {
		public Boolean apply(WebDriver d) {
			String xpath = "//img[@src=\"git-black-16x16.png\"]";
			List<WebElement> findElements = d.findElements(By.xpath(xpath ));
			return findElements.size() == 1;
		}
	}
}
