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
package com.gitblit.wicket.panels;

import java.util.ResourceBundle;
import java.util.TimeZone;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.Keys;
import com.gitblit.utils.TimeUtils;
import com.gitblit.wicket.GitBlitWebApp;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;

public abstract class BasePanel extends Panel {

	private static final long serialVersionUID = 1L;

	private transient TimeUtils timeUtils;

	private transient Logger logger;

	public BasePanel(String wicketId) {
		super(wicketId);
	}

	protected GitBlitWebApp app() {
		return GitBlitWebApp.get();
	}

	protected Logger logger() {
		if (logger == null) {
			logger = LoggerFactory.getLogger(getClass());
		}
		return logger;
	}

	protected String getContextUrl() {
		return getRequest().getRelativePathPrefixToContextRoot();
	}

	protected TimeZone getTimeZone() {
		return app().settings().getBoolean(Keys.web.useClientTimezone, false) ? GitBlitWebSession.get()
				.getTimezone() : app().getTimezone();
	}

	protected TimeUtils getTimeUtils() {
		if (timeUtils == null) {
			ResourceBundle bundle;
			try {
				bundle = ResourceBundle.getBundle("com.gitblit.wicket.GitBlitWebApp", GitBlitWebSession.get().getLocale());
			} catch (Throwable t) {
				bundle = ResourceBundle.getBundle("com.gitblit.wicket.GitBlitWebApp");
			}
			timeUtils = new TimeUtils(bundle, getTimeZone());
		}
		return timeUtils;
	}

	protected void setPersonSearchTooltip(Component component, String value, Constants.SearchType searchType) {
		if (searchType.equals(Constants.SearchType.AUTHOR)) {
			WicketUtils.setHtmlTooltip(component, getString("gb.searchForAuthor") + " " + value);
		} else if (searchType.equals(Constants.SearchType.COMMITTER)) {
			WicketUtils.setHtmlTooltip(component, getString("gb.searchForCommitter") + " " + value);
		}
	}

	public static class JavascriptEventConfirmation extends AttributeModifier {

		private static final long serialVersionUID = 1L;

		public JavascriptEventConfirmation(String event, String msg) {
			super(event, new Model<String>(msg));
		}

		@Override
		protected String newValue(final String currentValue, final String replacementValue) {
			String prefix = "var conf = confirm('" + replacementValue + "'); "
					+ "if (!conf) return false; ";
			String result = prefix;
			if (currentValue != null) {
				result = prefix + currentValue;
			}
			return result;
		}
	}

	public static class JavascriptTextPrompt extends AttributeModifier {

		private static final long serialVersionUID = 1L;

		private String initialValue = "";

		public JavascriptTextPrompt(String event, String msg, String value) {
			super(event,  new Model<String>(msg));
			initialValue = value;
		}

		@Override
		protected String newValue(final String currentValue, final String message) {
			String result = "var userText = prompt('" + message + "','"
					+ (initialValue == null ? "" : initialValue) + "'); " + "return userText; ";
			return result;
		}
	}
}
