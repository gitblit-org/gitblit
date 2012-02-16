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
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;

import com.gitblit.wicket.PageRegistration.DropDownMenuItem;
import com.gitblit.wicket.PageRegistration.DropDownMenuRegistration;
import com.gitblit.wicket.WicketUtils;

public class DropDownMenu extends Panel {

	private static final long serialVersionUID = 1L;

	public DropDownMenu(String id, String label, final DropDownMenuRegistration menu) {
		super(id);

		add(new Label("label", label).setRenderBodyOnly(true));
		ListDataProvider<DropDownMenuItem> items = new ListDataProvider<DropDownMenuItem>(
				menu.menuItems);
		DataView<DropDownMenuItem> view = new DataView<DropDownMenuItem>("menuItems", items) {
			private static final long serialVersionUID = 1L;

			public void populateItem(final Item<DropDownMenuItem> item) {
				DropDownMenuItem entry = item.getModelObject();
				if (entry.isDivider()) {
					item.add(new Label("menuItem").setRenderBodyOnly(true));
					WicketUtils.setCssClass(item, "divider");
				} else {
					String icon = null;
					if (entry.isSelected()) {
						icon = "icon-ok";
					} else {
						icon = "icon-ok-white";
					}
					item.add(new LinkPanel("menuItem", icon, null, entry.toString(), menu.pageClass,
							entry.getPageParameters(), false).setRenderBodyOnly(true));
				}
			}
		};
		add(view);
		setRenderBodyOnly(true);
	}
}