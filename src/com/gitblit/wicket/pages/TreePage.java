package com.gitblit.wicket.pages;

import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.utils.ByteFormat;
import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.models.PathModel;
import com.gitblit.wicket.panels.PathBreadcrumbsPanel;


public class TreePage extends RepositoryPage {

	public TreePage(PageParameters params) {
		super(params);

		final String basePath = WicketUtils.getPath(params);

		Repository r = getRepository();
		RevCommit commit = JGitUtils.getCommit(r, objectId);
		List<PathModel> paths = JGitUtils.getFilesInPath(r, basePath, commit);

		// tree page links
		add(new Label("historyLink", getString("gb.history")));
		add(new BookmarkablePageLink<Void>("headLink", TreePage.class, WicketUtils.newPathParameter(repositoryName, Constants.HEAD, basePath)));


		add(new LinkPanel("shortlog", "title", commit.getShortMessage(), CommitPage.class, newCommitParameter()));

		// breadcrumbs
		add(new PathBreadcrumbsPanel("breadcrumbs", repositoryName, basePath, objectId));			
		if (basePath != null && basePath.trim().length() > 0) {
			paths.add(0, PathModel.getParentPath(basePath, objectId));
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
					item.add(new Label("pathSize", ""));
					item.add(new LinkPanel("pathName", null, entry.name, TreePage.class, newPathParameter(entry.path)));
					item.add(new Label("pathLinks", ""));
				} else {
					if (entry.isTree()) {
						// folder/tree link
						item.add(new Label("pathSize", ""));
						item.add(new LinkPanel("pathName", null, entry.name, TreePage.class, newPathParameter(entry.path)));
						
						// links
						Fragment links = new Fragment("pathLinks", "treeLinks", this);
						links.add(new BookmarkablePageLink<Void>("tree", TreePage.class, WicketUtils.newPathParameter(repositoryName, entry.commitId, entry.path)));
						links.add(new BookmarkablePageLink<Void>("history", TreePage.class).setEnabled(false));
						item.add(links);
					} else {
						// blob link
						item.add(new Label("pathSize", byteFormat.format(entry.size)));
						item.add(new LinkPanel("pathName", "list", entry.name, BlobPage.class, newPathParameter(entry.path)));
						
						// links
						Fragment links = new Fragment("pathLinks", "blobLinks", this);
						links.add(new BookmarkablePageLink<Void>("view", BlobPage.class, WicketUtils.newPathParameter(repositoryName, entry.commitId, entry.path)));
						links.add(new BookmarkablePageLink<Void>("raw", RawPage.class, WicketUtils.newPathParameter(repositoryName, entry.commitId, entry.path)));
						links.add(new BookmarkablePageLink<Void>("blame", BlobPage.class).setEnabled(false));
						links.add(new BookmarkablePageLink<Void>("history", BlobPage.class).setEnabled(false));
						item.add(links);
					}
				}
				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(pathsView);
	}
	
	@Override
	protected String getPageName() {
		return getString("gb.tree");
	}
}
