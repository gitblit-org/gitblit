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

import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;

public class BulletListPanel extends Panel {

	private static final long serialVersionUID = 1L;

	public BulletListPanel(String id, String header, List<String> list) {
		super(id);
		if (list == null) {
			list = new ArrayList<String>();
		}
		add(new Label("header", header).setVisible(list.size() > 0));
		ListDataProvider<String> listDp = new ListDataProvider<String>(list);
		DataView<String> listView = new DataView<String>("list", listDp) {
			private static final long serialVersionUID = 1L;

			public void populateItem(final Item<String> item) {
				String entry = item.getModelObject();
				item.add(new Label("listItem", entry));
			}
		};
		add(listView.setVisible(list.size() > 0));
	}
}