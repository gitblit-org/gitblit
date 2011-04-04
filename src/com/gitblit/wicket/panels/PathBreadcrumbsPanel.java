package com.gitblit.wicket.panels;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;

import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.pages.TreePage;


public class PathBreadcrumbsPanel extends Panel {

	private static final long serialVersionUID = 1L;

	private final String ROOT = "--ROOT--";

	public PathBreadcrumbsPanel(String id, final String repositoryName, String pathName, final String commitId) {
		super(id);
		List<BreadCrumb> crumbs = new ArrayList<BreadCrumb>();
		crumbs.add(new BreadCrumb("[" + repositoryName + "]", ROOT, false));

		String[] paths = pathName.split("/");
		StringBuilder sb = new StringBuilder();
		
		for (int i = 0; i < paths.length; i++) {
			String path = paths[i];
			sb.append(path);
			crumbs.add(new BreadCrumb(path, sb.toString(), (i == (paths.length - 1))));
			sb.append("/");
		}

		ListDataProvider<BreadCrumb> crumbsDp = new ListDataProvider<BreadCrumb>(crumbs);
		DataView<BreadCrumb> pathsView = new DataView<BreadCrumb>("path", crumbsDp) {
			private static final long serialVersionUID = 1L;

			public void populateItem(final Item<BreadCrumb> item) {
				final BreadCrumb entry = item.getModelObject();
				String path = entry.getPath();
				String parameters = "p=" + repositoryName + ",h=" + commitId;
				if (path != null) {
					parameters += ",f=" + path;
				}

				item.add(new LinkPanel("pathLink", null, entry.name, TreePage.class, new PageParameters(parameters)));
				item.add(new Label("pathSeparator", entry.isLeaf ? "":"/"));
			}
		};
		add(pathsView);
	}

	private class BreadCrumb implements Serializable {
		
		private static final long serialVersionUID = 1L;
		
		final String name;
		final String path;
		final boolean isLeaf;
		
		BreadCrumb(String name, String path, boolean isLeaf) {
			this.name = name;
			this.path = path;
			this.isLeaf = isLeaf;
		}

		String getPath() {
			if (path.equals(ROOT)) {
				return null;
			}
			return path;
		}
	}
}