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

import com.gitblit.models.Menu.ExternalLinkMenuItem;
import com.gitblit.models.Menu.MenuDivider;
import com.gitblit.models.Menu.MenuItem;
import com.gitblit.models.Menu.PageLinkMenuItem;
import com.gitblit.models.Menu.ParameterMenuItem;
import com.gitblit.models.NavLink.DropDownMenuNavLink;
import com.gitblit.models.NavLink.DropDownPageMenuNavLink;
import com.gitblit.wicket.WicketUtils;

public class DropDownMenu extends Panel {

	private static final long serialVersionUID = 1L;

	public DropDownMenu(String id, String label, final DropDownPageMenuNavLink menu) {
		super(id);

		add(new Label("label", label).setRenderBodyOnly(true));
		ListDataProvider<MenuItem> items = new ListDataProvider<MenuItem>(menu.menuItems);
		DataView<MenuItem> view = new DataView<MenuItem>("menuItems", items) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(final Item<MenuItem> item) {
				MenuItem entry = item.getModelObject();
				if (entry instanceof PageLinkMenuItem) {
					// link to another Wicket page
					PageLinkMenuItem pageLink = (PageLinkMenuItem) entry;
					item.add(new LinkPanel("menuItem", null, null, pageLink.toString(), pageLink.getPageClass(),
							pageLink.getPageParameters(), false).setRenderBodyOnly(true));
				} else if (entry instanceof ExternalLinkMenuItem) {
					// link to a specified href
					ExternalLinkMenuItem extLink = (ExternalLinkMenuItem) entry;
					item.add(new LinkPanel("menuItem", null, extLink.toString(), extLink.getHref(),
							extLink.openInNewWindow()).setRenderBodyOnly(true));
				} else if (entry instanceof MenuDivider) {
					// divider
					item.add(new Label("menuItem").setRenderBodyOnly(true));
					WicketUtils.setCssClass(item, "divider");
				} else {
					ParameterMenuItem parameter = (ParameterMenuItem) entry;
					// parameter link for the current page
					String icon = null;
					if (parameter.isSelected()) {
						icon = "icon-ok";
					} else {
						icon = "icon-ok-white";
					}
					item.add(new LinkPanel("menuItem", icon, null, entry.toString(), menu.pageClass,
							parameter.getPageParameters(), false).setRenderBodyOnly(true));
				}
			}
		};
		add(view);
		setRenderBodyOnly(true);
	}

	public DropDownMenu(String id, String label, final DropDownMenuNavLink menu) {
		super(id);

		add(new Label("label", label).setRenderBodyOnly(true));
		ListDataProvider<MenuItem> items = new ListDataProvider<MenuItem>(menu.menuItems);
		DataView<MenuItem> view = new DataView<MenuItem>("menuItems", items) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(final Item<MenuItem> item) {
				MenuItem entry = item.getModelObject();
				if (entry instanceof PageLinkMenuItem) {
					// link to another Wicket page
					PageLinkMenuItem pageLink = (PageLinkMenuItem) entry;
					item.add(new LinkPanel("menuItem", null, null, pageLink.toString(), pageLink.getPageClass(),
							pageLink.getPageParameters(), false).setRenderBodyOnly(true));
				} else if (entry instanceof ExternalLinkMenuItem) {
					// link to a specified href
					ExternalLinkMenuItem extLink = (ExternalLinkMenuItem) entry;
					item.add(new LinkPanel("menuItem", null, extLink.toString(), extLink.getHref(),
							extLink.openInNewWindow()).setRenderBodyOnly(true));
				} else if (entry instanceof MenuDivider) {
					// divider
					item.add(new Label("menuItem").setRenderBodyOnly(true));
					WicketUtils.setCssClass(item, "divider");
				} else {
					throw new IllegalArgumentException(String.format("Unexpected menuitem type %s",
							entry.getClass().getSimpleName()));
				}
			}
		};
		add(view);
		setRenderBodyOnly(true);
	}
}