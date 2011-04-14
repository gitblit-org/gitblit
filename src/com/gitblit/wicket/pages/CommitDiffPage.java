package com.gitblit.wicket.pages;

import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.models.PathModel;

public class CommitDiffPage extends RepositoryPage {

	public CommitDiffPage(PageParameters params) {
		super(params);

		Repository r = getRepository();
		RevCommit commit = JGitUtils.getCommit(r, objectId);
		String diff = JGitUtils.getCommitDiff(r, commit, true);

		List<String> parents = new ArrayList<String>();
		if (commit.getParentCount() > 0) {
			for (RevCommit parent : commit.getParents()) {
				parents.add(parent.name());
			}
		}

		// commit page links
		if (parents.size() == 0) {
			add(new Label("parentLink", "none"));
		} else {
			add(new LinkPanel("parentLink", null, parents.get(0).substring(0, 8), CommitDiffPage.class, newCommitParameter(parents.get(0))));
		}
		add(new BookmarkablePageLink<Void>("patchLink", PatchPage.class, WicketUtils.newObjectParameter(repositoryName, objectId)));
		add(new BookmarkablePageLink<Void>("commitLink", CommitPage.class, WicketUtils.newObjectParameter(repositoryName, objectId)));

		add(new LinkPanel("shortlog", "title", commit.getShortMessage(), CommitPage.class, newCommitParameter()));

		// changed paths list
		List<PathModel> paths = JGitUtils.getFilesInCommit(r, commit);
		ListDataProvider<PathModel> pathsDp = new ListDataProvider<PathModel>(paths);
		DataView<PathModel> pathsView = new DataView<PathModel>("changedPath", pathsDp) {
			private static final long serialVersionUID = 1L;
			int counter = 0;

			public void populateItem(final Item<PathModel> item) {
				final PathModel entry = item.getModelObject();
				if (entry.isTree()) {
					item.add(new LinkPanel("pathName", null, entry.path, TreePage.class, newPathParameter(entry.path)));
				} else {
					item.add(new LinkPanel("pathName", "list", entry.path, BlobPage.class, newPathParameter(entry.path)));
				}

				item.add(new BookmarkablePageLink<Void>("patch", PatchPage.class, newPathParameter(entry.path)));
				item.add(new BookmarkablePageLink<Void>("view", BlobPage.class, newPathParameter(entry.path)));
				item.add(new BookmarkablePageLink<Void>("blame", BlobPage.class).setEnabled(false));
				item.add(new BookmarkablePageLink<Void>("history", BlobPage.class).setEnabled(false));

				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(pathsView);
		add(new Label("diffText", diff).setEscapeModelStrings(false));
	}

	@Override
	protected String getPageName() {
		return getString("gb.commitdiff");
	}
}
