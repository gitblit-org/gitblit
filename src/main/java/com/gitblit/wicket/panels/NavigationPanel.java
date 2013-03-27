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

import com.gitblit.wicket.PageRegistration;
import com.gitblit.wicket.PageRegistration.DropDownMenuRegistration;
import com.gitblit.wicket.PageRegistration.OtherPageLink;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.BasePage;

public class NavigationPanel extends Panel {

	private static final long serialVersionUID = 1L;

	public NavigationPanel(String id, final Class<? extends BasePage> pageClass,
			List<PageRegistration> registeredPages) {
		super(id);

		ListDataProvider<PageRegistration> refsDp = new ListDataProvider<PageRegistration>(
				registeredPages);
		DataView<PageRegistration> refsView = new DataView<PageRegistration>("navLink", refsDp) {
			private static final long serialVersionUID = 1L;

			public void populateItem(final Item<PageRegistration> item) {
				PageRegistration entry = item.getModelObject();
				if (entry instanceof OtherPageLink) {
					// other link
					OtherPageLink link = (OtherPageLink) entry;
					Component c = new LinkPanel("link", null, getString(entry.translationKey), link.url);
					c.setRenderBodyOnly(true);
					item.add(c);
				} else if (entry instanceof DropDownMenuRegistration) {
					// drop down menu
					DropDownMenuRegistration reg = (DropDownMenuRegistration) entry;
					Component c = new DropDownMenu("link", getString(entry.translationKey), reg);
					c.setRenderBodyOnly(true);
					item.add(c);
					WicketUtils.setCssClass(item, "dropdown");
				} else {
					// standard page link
					Component c = new LinkPanel("link", null, getString(entry.translationKey),
							entry.pageClass, entry.params);
					c.setRenderBodyOnly(true);
					if (entry.pageClass.equals(pageClass)) {
						WicketUtils.setCssClass(item, "active");
					}
					item.add(c);
				}
			}
		};
		add(refsView);
	}
}