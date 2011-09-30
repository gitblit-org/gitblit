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

public abstract class StandardPage extends BasePage {
	
	public StandardPage() {
		// create constructor
		super();
		setStatelessHint(true);
	}

	public StandardPage(PageParameters params) {
		// edit constructor
		super(params);
		setStatelessHint(true);
	}

	protected void setupPage(String pageName, String subName) {		
		add(new Label("pageName", pageName));
		add(new Label("pageSubName", "/ " + subName));
		super.setupPage("", pageName);
	}
}
