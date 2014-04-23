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

import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;

import com.gitblit.models.NavLink;
import com.gitblit.models.NavLink.DropDownMenuNavLink;
import com.gitblit.models.NavLink.DropDownPageMenuNavLink;
import com.gitblit.models.NavLink.ExternalNavLink;
import com.gitblit.models.NavLink.PageNavLink;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.BasePage;

public class NavigationPanel extends Panel {

	private static final long serialVersionUID = 1L;

	public NavigationPanel(String id, final Class<? extends BasePage> pageClass,
			List<NavLink> navLinks) {
		super(id);

		ListDataProvider<NavLink> refsDp = new ListDataProvider<NavLink>(navLinks);
		DataView<NavLink> linksView = new DataView<NavLink>("navLink", refsDp) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(final Item<NavLink> item) {
				NavLink navLink = item.getModelObject();
				String linkText = navLink.translationKey;
				try {
					// try to lookup translation key
					linkText = getString(navLink.translationKey);
				} catch (Exception e) {
				}

				if (navLink.hiddenPhone) {
					WicketUtils.setCssClass(item, "hidden-phone");
				}
				if (navLink instanceof ExternalNavLink) {
					// other link
					ExternalNavLink link = (ExternalNavLink) navLink;
					Component c = new LinkPanel("link", null, linkText, link.url);
					c.setRenderBodyOnly(true);
					item.add(c);
				} else if (navLink instanceof DropDownPageMenuNavLink) {
					// drop down menu
					DropDownPageMenuNavLink reg = (DropDownPageMenuNavLink) navLink;
					Component c = new DropDownMenu("link", linkText, reg);
					c.setRenderBodyOnly(true);
					item.add(c);
					WicketUtils.setCssClass(item, "dropdown");
				} else if (navLink instanceof DropDownMenuNavLink) {
					// drop down menu
					DropDownMenuNavLink reg = (DropDownMenuNavLink) navLink;
					Component c = new DropDownMenu("link", linkText, reg);
					c.setRenderBodyOnly(true);
					item.add(c);
					WicketUtils.setCssClass(item, "dropdown");
				} else if (navLink instanceof PageNavLink) {
					PageNavLink reg = (PageNavLink) navLink;
					// standard page link
					Component c = new LinkPanel("link", null, linkText,
							reg.pageClass, reg.params);
					c.setRenderBodyOnly(true);
					if (reg.pageClass.equals(pageClass)) {
						WicketUtils.setCssClass(item, "active");
					}
					item.add(c);
				}
			}
		};
		add(linksView);
	}
}