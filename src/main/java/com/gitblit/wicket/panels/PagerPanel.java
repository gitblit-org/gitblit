/*
 * Copyright 2012 gitblit.com.
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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;

import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.BasePage;

public class PagerPanel extends Panel {

	private static final long serialVersionUID = 1L;

	public PagerPanel(String wicketId, final int currentPage, final int totalPages,
			final Class<? extends BasePage> pageClass, final PageParameters baseParams) {
		super(wicketId);
		List<PageObject> pages = new ArrayList<PageObject>();
		int[] deltas;
		if (currentPage == 1) {
			// [1], 2, 3, 4, 5
			deltas = new int[] { 0, 1, 2, 3, 4 };
		} else if (currentPage == 2) {
			// 1, [2], 3, 4, 5
			deltas = new int[] { -1, 0, 1, 2, 3 };
		} else {
			// 1, 2, [3], 4, 5
			deltas = new int[] { -2, -1, 0, 1, 2 };
		}

		if (totalPages > 0 && currentPage > 1) {
			pages.add(new PageObject("\u2190", currentPage - 1));
		}
		for (int delta : deltas) {
			int page = currentPage + delta;
			if (page > 0 && page <= totalPages) {
				pages.add(new PageObject("" + page, page));
			}
		}
		if (totalPages > 0 && currentPage < totalPages) {
			pages.add(new PageObject("\u2192", currentPage + 1));
		}

		ListDataProvider<PageObject> pagesProvider = new ListDataProvider<PageObject>(pages);
		final DataView<PageObject> pagesView = new DataView<PageObject>("page", pagesProvider) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(final Item<PageObject> item) {
				PageObject pageItem = item.getModelObject();
				PageParameters pageParams = new PageParameters(baseParams);
				pageParams.add("pg", pageItem.page);
				LinkPanel link = new LinkPanel("pageLink", null, pageItem.text, pageClass, pageParams);
				link.setRenderBodyOnly(true);
				item.add(link);
				if (pageItem.page == currentPage || pageItem.page < 1 || pageItem.page > totalPages) {
					WicketUtils.setCssClass(item, "disabled");
					link.setEnabled(false);
				}
			}
		};
		add(pagesView);
	}

	private class PageObject implements Serializable {

		private static final long serialVersionUID = 1L;

		String text;
		int page;

		PageObject(String text, int page) {
			this.text = text;
			this.page = page;
		}
	}
}
