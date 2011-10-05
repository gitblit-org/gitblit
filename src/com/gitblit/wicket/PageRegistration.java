/*
 * Copyright 2011 gitblit.com.
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
package com.gitblit.wicket;

import java.io.Serializable;

import org.apache.wicket.PageParameters;

import com.gitblit.wicket.pages.BasePage;

/**
 * Represents a page link registration for the topbar.
 * 
 * @author James Moger
 * 
 */
public class PageRegistration implements Serializable {
	private static final long serialVersionUID = 1L;

	public final String translationKey;
	public final Class<? extends BasePage> pageClass;
	public final PageParameters params;

	public PageRegistration(String translationKey, Class<? extends BasePage> pageClass) {
		this(translationKey, pageClass, null);
	}

	public PageRegistration(String translationKey, Class<? extends BasePage> pageClass,
			PageParameters params) {
		this.translationKey = translationKey;
		this.pageClass = pageClass;
		this.params = params;
	}
}