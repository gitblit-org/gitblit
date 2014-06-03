/*
 * Copyright 2014 gitblit.com.
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
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.IModel;

import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.WicketUtils;

/**
 * A re-usable textfield option panel.
 *
 * title
 *     description
 *     [textfield]
 *
 * @author James Moger
 *
 */
public class TextOption extends BasePanel {

	private static final long serialVersionUID = 1L;

	public TextOption(String wicketId, String title, String description, IModel<String> model) {
		this(wicketId, title, description, null, model);
	}

	public TextOption(String wicketId, String title, String description, String css, IModel<String> model) {
		super(wicketId);
		add(new Label("name", title));
		add(new Label("description", description).setVisible(!StringUtils.isEmpty(description)));
		TextField<String> tf = new TextField<String>("text", model);
		if (!StringUtils.isEmpty(css)) {
			WicketUtils.setCssClass(tf, css);
		}
		add(tf);
	}
}
