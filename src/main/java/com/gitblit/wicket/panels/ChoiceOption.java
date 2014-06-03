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

import java.util.List;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.model.IModel;
import org.parboiled.common.StringUtils;

/**
 * A re-usable choice option panel.
 *
 * title
 *     description
 *     [choices]
 *
 * @author James Moger
 *
 */
public class ChoiceOption<T> extends BasePanel {

	private static final long serialVersionUID = 1L;

	public ChoiceOption(String wicketId, String title, String description, IModel<T> model, List<T> choices) {
		super(wicketId);
		add(new Label("name", title));
		add(new Label("description", description).setVisible(!StringUtils.isEmpty(description)));
		add(new DropDownChoice<>("choice", model, choices).setEnabled(choices.size() > 0));
	}

	public ChoiceOption(String wicketId, String title, String description, DropDownChoice<?> choice) {
		super(wicketId);
		add(new Label("name", title));
		add(new Label("description", description).setVisible(!StringUtils.isEmpty(description)));
		add(choice.setMarkupId("choice").setEnabled(choice.getChoices().size() > 0));
	}
}
