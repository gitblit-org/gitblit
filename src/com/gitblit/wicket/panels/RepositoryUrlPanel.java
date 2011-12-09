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

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.image.ContextImage;

import com.gitblit.wicket.WicketUtils;

public class RepositoryUrlPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	public RepositoryUrlPanel(String wicketId, String url) {
		super(wicketId);
		add(new Label("repositoryUrl", url));
		ContextImage img = WicketUtils.newImage("copyIcon", "clipboard_13x13.png");
		WicketUtils.setHtmlTooltip(img, "Manual Copy to Clipboard");
		img.add(new JavascriptTextPrompt("onclick", "Copy to Clipboard (Ctrl+C, Enter)", url));
		add(img);
	}
}
