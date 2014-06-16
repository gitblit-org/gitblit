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
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.model.IModel;
import org.parboiled.common.StringUtils;

/**
 * A re-usable checkbox option panel.
 *
 * [x] title
 *     description
 *
 * @author James Moger
 *
 */
public class BooleanOption extends BasePanel {

	private static final long serialVersionUID = 1L;

	public BooleanOption(String wicketId, String title, String description, IModel<Boolean> model) {
		super(wicketId);
		add(new Label("name", title));
		add(new Label("description", description).setVisible(!StringUtils.isEmpty(description)));
		add(new CheckBox("checkbox", model));
	}

	public BooleanOption(String wicketId, String title, String description, CheckBox checkbox) {
		super(wicketId);
		add(new Label("name", title));
		add(new Label("description", description).setVisible(!StringUtils.isEmpty(description)));
		add(checkbox.setMarkupId("checkbox"));
	}

	public BooleanOption setIsHtmlDescription(boolean val) {
		((Label) get("description")).setEscapeModelStrings(!val);
		return this;
	}
}
