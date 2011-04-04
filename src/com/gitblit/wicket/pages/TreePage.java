package com.gitblit.wicket.pages;

import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.utils.ByteFormat;
import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.models.PathModel;
import com.gitblit.wicket.panels.PathBreadcrumbsPanel;
import com.gitblit.wicket.panels.TreeLinksPanel;


public class TreePage extends RepositoryPage {

	public TreePage(PageParameters params) {
		super(params, "tree");

		final String basePath = params.getString("f", null);

		Repository r = getRepository();
		RevCommit commit = JGitUtils.getCommit(r, commitId);
		List<PathModel> paths = JGitUtils.getFilesInPath(r, basePath, commit);

		// tree page links
		add(new Label("historyLink", "history"));
		add(new Label("headLink", "HEAD"));

		add(new LinkPanel("shortlog", "title", commit.getShortMessage(), CommitPage.class, newCommitParameter()));

		// breadcrumbs
		if (basePath == null || basePath.trim().length() == 0) {
			add(new Label("breadcrumbs", "").setVisible(false));
		} else {
			add(new PathBreadcrumbsPanel("breadcrumbs", repositoryName, basePath, commitId));
			paths.add(0, PathModel.getParentPath(basePath, commitId));
		}

		final ByteFormat byteFormat = new ByteFormat();
		
		// changed paths list		
		ListDataProvider<PathModel> pathsDp = new ListDataProvider<PathModel>(paths);
		DataView<PathModel> pathsView = new DataView<PathModel>("changedPath", pathsDp) {
			private static final long serialVersionUID = 1L;
			int counter = 0;

			public void populateItem(final Item<PathModel> item) {
				PathModel entry = item.getModelObject();
				item.add(new Label("pathPermissions", JGitUtils.getPermissionsFromMode(entry.mode)));
				if (entry.isParentPath) {
					// parent .. path
					item.add(new Label("pathSize", "").setVisible(false));
					item.add(new LinkPanel("pathName", null, entry.name, TreePage.class, newPathParameter(entry.path)));
					item.add(new Label("treeLinks", "").setVisible(false));
				} else {
					if (entry.isTree()) {
						// folder/tree link
						item.add(new Label("pathSize", "-"));
						item.add(new LinkPanel("pathName", null, entry.name, TreePage.class, newPathParameter(entry.path)));
					} else {
						// blob link
						item.add(new Label("pathSize", byteFormat.format(entry.size)));
						item.add(new LinkPanel("pathName", "list", entry.name, BlobPage.class, newPathParameter(entry.path)));
					}
					item.add(new TreeLinksPanel("treeLinks", repositoryName, entry));
				}
				String clazz = counter % 2 == 0 ? "dark" : "light";
				WicketUtils.setCssClass(item, clazz);
				counter++;
			}
		};
		add(pathsView);

		// close repository
		r.close();

		// footer
		addFooter();
	}
}
