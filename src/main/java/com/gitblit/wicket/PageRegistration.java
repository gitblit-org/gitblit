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
import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.WebPage;

import com.gitblit.models.Menu.MenuItem;

/**
 * Represents a page link registration for the topbar.
 *
 * @author James Moger
 *
 */
public class PageRegistration implements Serializable {
	private static final long serialVersionUID = 1L;

	public final String translationKey;
	public final Class<? extends WebPage> pageClass;
	public final PageParameters params;
	public final boolean hiddenPhone;

	public PageRegistration(String translationKey, Class<? extends WebPage> pageClass) {
		this(translationKey, pageClass, null);
	}

	public PageRegistration(String translationKey, Class<? extends WebPage> pageClass,
			PageParameters params) {
		this(translationKey, pageClass, params, false);
	}

	public PageRegistration(String translationKey, Class<? extends WebPage> pageClass,
			PageParameters params, boolean hiddenPhone) {
		this.translationKey = translationKey;
		this.pageClass = pageClass;
		this.params = params;
		this.hiddenPhone = hiddenPhone;
	}

	/**
	 * Represents a page link to a non-Wicket page. Might be external.
	 *
	 * @author James Moger
	 *
	 */
	public static class OtherPageLink extends PageRegistration {

		private static final long serialVersionUID = 1L;

		public final String url;

		public OtherPageLink(String translationKey, String url) {
			super(translationKey, null);
			this.url = url;
		}

		public OtherPageLink(String translationKey, String url, boolean hiddenPhone) {
			super(translationKey, null, null, hiddenPhone);
			this.url = url;
		}
	}

	/**
	 * Represents a DropDownMenu for the topbar
	 *
	 * @author James Moger
	 *
	 */
	public static class DropDownMenuRegistration extends PageRegistration {

		private static final long serialVersionUID = 1L;

		public final List<MenuItem> menuItems;

		public DropDownMenuRegistration(String translationKey, Class<? extends WebPage> pageClass) {
			super(translationKey, pageClass);
			menuItems = new ArrayList<MenuItem>();
		}
	}

}