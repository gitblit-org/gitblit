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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;

import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.TreePage;

public class PathBreadcrumbsPanel extends Panel {

	private static final long serialVersionUID = 1L;

	private static final String ROOT = "--ROOT--";

	public PathBreadcrumbsPanel(String id, final String repositoryName, String pathName,
			final String objectId) {
		super(id);
		List<BreadCrumb> crumbs = new ArrayList<BreadCrumb>();
		crumbs.add(new BreadCrumb("[" + repositoryName + "]", ROOT, false));

		if (pathName != null && pathName.length() > 0) {
			String[] paths = pathName.split("/");
			StringBuilder sb = new StringBuilder();

			for (int i = 0; i < paths.length; i++) {
				String path = paths[i];
				sb.append(path);
				crumbs.add(new BreadCrumb(path, sb.toString(), i == (paths.length - 1)));
				sb.append('/');
			}
		}

		ListDataProvider<BreadCrumb> crumbsDp = new ListDataProvider<BreadCrumb>(crumbs);
		DataView<BreadCrumb> pathsView = new DataView<BreadCrumb>("path", crumbsDp) {
			private static final long serialVersionUID = 1L;

			public void populateItem(final Item<BreadCrumb> item) {
				final BreadCrumb entry = item.getModelObject();
				String path = entry.path;
				if (path.equals(ROOT)) {
					path = null;
				}
				if (entry.isLeaf) {
					item.add(new Label("pathLink", entry.name));
					item.add(new Label("pathSeparator", "").setVisible(false));
				} else {
					item.add(new LinkPanel("pathLink", null, entry.name, TreePage.class,
							WicketUtils.newPathParameter(repositoryName, objectId, path)));
					item.add(new Label("pathSeparator", "/"));
				}
			}
		};
		add(pathsView);
	}

	private static class BreadCrumb implements Serializable {

		private static final long serialVersionUID = 1L;

		final String name;
		final String path;
		final boolean isLeaf;

		BreadCrumb(String name, String path, boolean isLeaf) {
			this.name = name;
			this.path = path;
			this.isLeaf = isLeaf;
		}
	}
}