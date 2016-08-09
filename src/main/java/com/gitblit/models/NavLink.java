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
package com.gitblit.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.markup.html.WebPage;

import com.gitblit.models.Menu.MenuItem;

/**
 * Represents a navigation link for the navigation panel.
 *
 * @author James Moger
 *
 */
public abstract class NavLink implements Serializable {
	private static final long serialVersionUID = 1L;

	public final String translationKey;
	public final boolean hiddenPhone;

	public NavLink(String translationKey, boolean hiddenPhone) {
		this.translationKey = translationKey;
		this.hiddenPhone = hiddenPhone;
	}


	/**
	 * Represents a Wicket page link.
	 *
	 * @author James Moger
	 *
	 */
	public static class PageNavLink extends NavLink implements Serializable {
		private static final long serialVersionUID = 1L;

		public final Class<? extends WebPage> pageClass;
		public final PageParameters params;

		public PageNavLink(String translationKey, Class<? extends WebPage> pageClass) {
			this(translationKey, pageClass, null);
		}

		public PageNavLink(String translationKey, Class<? extends WebPage> pageClass,
				PageParameters params) {
			this(translationKey, pageClass, params, false);
		}

		public PageNavLink(String translationKey, Class<? extends WebPage> pageClass,
				PageParameters params, boolean hiddenPhone) {
			super(translationKey, hiddenPhone);
			this.pageClass = pageClass;
			this.params = params;
		}
	}

	/**
	 * Represents an explicitly href link.
	 *
	 * @author James Moger
	 *
	 */
	public static class ExternalNavLink extends NavLink implements Serializable {

		private static final long serialVersionUID = 1L;

		public final String url;

		public ExternalNavLink(String keyOrText, String url) {
			super(keyOrText, false);
			this.url = url;
		}

		public ExternalNavLink(String keyOrText, String url, boolean hiddenPhone) {
			super(keyOrText,  hiddenPhone);
			this.url = url;
		}
	}

	/**
	 * Represents a DropDownMenu for the current page.
	 *
	 * @author James Moger
	 *
	 */
	public static class DropDownPageMenuNavLink extends PageNavLink implements Serializable {

		private static final long serialVersionUID = 1L;

		public final List<MenuItem> menuItems;

		public DropDownPageMenuNavLink(String keyOrText, Class<? extends WebPage> pageClass) {
			this(keyOrText, pageClass, false);
		}

		public DropDownPageMenuNavLink(String keyOrText, Class<? extends WebPage> pageClass, boolean hiddenPhone) {
			super(keyOrText, pageClass, null, hiddenPhone);
			menuItems = new ArrayList<MenuItem>();
		}
	}

	/**
	 * Represents a DropDownMenu.
	 *
	 * @author James Moger
	 *
	 */
	public static class DropDownMenuNavLink extends NavLink implements Serializable {

		private static final long serialVersionUID = 1L;

		public final List<MenuItem> menuItems;

		public DropDownMenuNavLink(String keyOrText) {
			this(keyOrText, false);
		}

		public DropDownMenuNavLink(String keyOrText, boolean hiddenPhone) {
			super(keyOrText, hiddenPhone);
			menuItems = new ArrayList<MenuItem>();
		}
	}
}