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

import java.util.TimeZone;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.utils.JGitUtils.SearchType;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;

public abstract class BasePanel extends Panel {

	private static final long serialVersionUID = 1L;

	public BasePanel(String wicketId) {
		super(wicketId);
	}

	protected TimeZone getTimeZone() {
		return GitBlit.self().settings().getBoolean(Keys.web.useClientTimezone, false) ? GitBlitWebSession.get().getTimezone() : TimeZone.getDefault();
	}

	protected void setPersonSearchTooltip(Component component, String value, SearchType searchType) {
		if (searchType.equals(SearchType.AUTHOR)) {
			WicketUtils.setHtmlTooltip(component, getString("gb.searchForAuthor") + " " + value);
		} else if (searchType.equals(SearchType.COMMITTER)) {
			WicketUtils.setHtmlTooltip(component, getString("gb.searchForCommitter") + " " + value);
		}
	}

	public class JavascriptEventConfirmation extends AttributeModifier {

		private static final long serialVersionUID = 1L;

		public JavascriptEventConfirmation(String event, String msg) {
			super(event, true, new Model<String>(msg));
		}

		protected String newValue(final String currentValue, final String replacementValue) {
			String prefix = "var conf = confirm('" + replacementValue + "'); " + "if (!conf) return false; ";
			String result = prefix;
			if (currentValue != null) {
				result = prefix + currentValue;
			}
			return result;
		}
	}
}
