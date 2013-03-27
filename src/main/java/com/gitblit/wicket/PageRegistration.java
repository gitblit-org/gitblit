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

import com.gitblit.utils.StringUtils;

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

	public PageRegistration(String translationKey, Class<? extends WebPage> pageClass) {
		this(translationKey, pageClass, null);
	}

	public PageRegistration(String translationKey, Class<? extends WebPage> pageClass,
			PageParameters params) {
		this.translationKey = translationKey;
		this.pageClass = pageClass;
		this.params = params;
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
	}

	/**
	 * Represents a DropDownMenu for the topbar
	 * 
	 * @author James Moger
	 * 
	 */
	public static class DropDownMenuRegistration extends PageRegistration {

		private static final long serialVersionUID = 1L;

		public final List<DropDownMenuItem> menuItems;

		public DropDownMenuRegistration(String translationKey, Class<? extends WebPage> pageClass) {
			super(translationKey, pageClass);
			menuItems = new ArrayList<DropDownMenuItem>();
		}
	}

	/**
	 * A MenuItem for the DropDownMenu.
	 * 
	 * @author James Moger
	 * 
	 */
	public static class DropDownMenuItem implements Serializable {

		private static final long serialVersionUID = 1L;

		final PageParameters parameters;
		final String displayText;
		final String parameter;
		final String value;
		final boolean isSelected;

		/**
		 * Divider constructor.
		 */
		public DropDownMenuItem() {
			this(null, null, null, null);
		}

		/**
		 * Standard Menu Item constructor.
		 * 
		 * @param displayText
		 * @param parameter
		 * @param value
		 */
		public DropDownMenuItem(String displayText, String parameter, String value) {
			this(displayText, parameter, value, null);
		}

		/**
		 * Standard Menu Item constructor that preserves aggregate parameters.
		 * 
		 * @param displayText
		 * @param parameter
		 * @param value
		 */
		public DropDownMenuItem(String displayText, String parameter, String value,
				PageParameters params) {
			this.displayText = displayText;
			this.parameter = parameter;
			this.value = value;

			if (params == null) {
				// no parameters specified
				parameters = new PageParameters();
				setParameter(parameter, value);
				isSelected = false;
			} else {
				parameters = new PageParameters(params);
				if (parameters.containsKey(parameter)) {
					isSelected = params.getString(parameter).equals(value);
					if (isSelected) {
						// already selected, so remove this enables toggling
						parameters.remove(parameter);
					} else {
						// set the new selection value
						setParameter(parameter, value);
					}
				} else {
					// not currently selected
					isSelected = false;
					setParameter(parameter, value);
				}
			}
		}

		private void setParameter(String parameter, String value) {
			if (!StringUtils.isEmpty(parameter)) {
				if (StringUtils.isEmpty(value)) {
					this.parameters.remove(parameter);
				} else {
					this.parameters.put(parameter, value);
				}
			}
		}

		public String formatParameter() {
			if (StringUtils.isEmpty(parameter) || StringUtils.isEmpty(value)) {
				return "";
			}
			return parameter + "=" + value;
		}

		public PageParameters getPageParameters() {
			return parameters;
		}

		public boolean isDivider() {
			return displayText == null && value == null && parameter == null;
		}

		public boolean isSelected() {
			return isSelected;
		}

		@Override
		public int hashCode() {
			if (isDivider()) {
				// divider menu item
				return super.hashCode();
			}
			if (StringUtils.isEmpty(displayText)) {
				return value.hashCode() + parameter.hashCode();
			}
			return displayText.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof DropDownMenuItem) {
				return hashCode() == o.hashCode();
			}
			return false;
		}

		@Override
		public String toString() {
			if (StringUtils.isEmpty(displayText)) {
				return formatParameter();
			}
			return displayText;
		}
	}
}