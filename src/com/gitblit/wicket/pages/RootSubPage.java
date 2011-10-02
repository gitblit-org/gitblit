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
package com.gitblit.wicket.pages;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;

import com.gitblit.utils.StringUtils;

/**
 * RootSubPage is a non-topbar navigable RootPage. It also has a page header.
 * 
 * @author James Moger
 * 
 */
public abstract class RootSubPage extends RootPage {

	public RootSubPage() {
		super();
	}

	public RootSubPage(PageParameters params) {
		super(params);
	}

	@Override
	protected void setupPage(String pageName, String subName) {
		add(new Label("pageName", pageName));
		if (!StringUtils.isEmpty(subName)) {
			subName = "/ " + subName;
		}
		add(new Label("pageSubName", subName));
		super.setupPage("", pageName);
	}
}
