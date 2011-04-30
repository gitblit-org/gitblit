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

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.JGitUtils.DiffOutputType;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.models.PathModel.PathChangeModel;
import com.gitblit.wicket.panels.CommitHeaderPanel;
import com.gitblit.wicket.panels.CommitLegendPanel;

public class CommitDiffPage extends RepositoryPage {

	public CommitDiffPage(PageParameters params) {
		super(params);

		Repository r = getRepository();
		RevCommit commit = getCommit();
		DiffOutputType diffType = DiffOutputType.forName(GitBlit.self().settings().getString(Keys.web.diffStyle, DiffOutputType.GITBLIT.name()));
		String diff = JGitUtils.getCommitDiff(r, commit, diffType);

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

		add(new CommitHeaderPanel("commitHeader", repositoryName, commit));

		// changed paths list
		List<PathChangeModel> paths = JGitUtils.getFilesInCommit(r, commit);
		add(new CommitLegendPanel("commitLegend", paths));
		ListDataProvider<PathChangeModel> pathsDp = new ListDataProvider<PathChangeModel>(paths);
		DataView<PathChangeModel> pathsView = new DataView<PathChangeModel>("changedPath", pathsDp) {
			private static final long serialVersionUID = 1L;
			int counter = 0;

			public void populateItem(final Item<PathChangeModel> item) {
				final PathChangeModel entry = item.getModelObject();
				Label changeType = new Label("changeType", "");
				WicketUtils.setChangeTypeCssClass(changeType, entry.changeType);
				setChangeTypeTooltip(changeType, entry.changeType);
				item.add(changeType);

				if (entry.isTree()) {
					item.add(new LinkPanel("pathName", null, entry.path, TreePage.class, newPathParameter(entry.path)));
				} else {
					item.add(new LinkPanel("pathName", "list", entry.path, BlobPage.class, newPathParameter(entry.path)));
				}

				item.add(new BookmarkablePageLink<Void>("patch", PatchPage.class, newPathParameter(entry.path)));
				item.add(new BookmarkablePageLink<Void>("view", BlobPage.class, newPathParameter(entry.path)));
				item.add(new BookmarkablePageLink<Void>("blame", BlobPage.class).setEnabled(false));
				item.add(new BookmarkablePageLink<Void>("history", HistoryPage.class, newPathParameter(entry.path)));

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
