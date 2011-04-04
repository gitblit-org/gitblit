package com.gitblit.wicket.pages;

import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.models.PathModel;
import com.gitblit.wicket.panels.PathLinksPanel;


public class CommitPage extends RepositoryPage {

	public CommitPage(PageParameters params) {
		super(params, "commit");

		final String commitId = params.getString("h", "");

		Repository r = getRepository();
		RevCommit c = JGitUtils.getCommit(r, commitId);
		
		List<String> parents = new ArrayList<String>();
		if (c.getParentCount() > 0) {
			for (RevCommit parent : c.getParents()) {
				parents.add(parent.name());
			}
		}
		
		// commit page links
		if (parents.size() == 0) {
			add(new Label("parentLink", "none"));
		} else {
			add(new LinkPanel("parentLink", null, parents.get(0).substring(0, 8), CommitPage.class, newCommitParameter(parents.get(0))));
		}
		add(new Label("patchLink", "patch"));
		
		add(new LinkPanel("shortlog", "title", c.getShortMessage(), ShortLogPage.class, newRepositoryParameter()));
		
		addRefs(r, c);

		add(new Label("commitAuthor", JGitUtils.getDisplayName(c.getAuthorIdent())));
		String authorDate = GitBlitWebSession.get().formatDateTimeLong(c.getAuthorIdent().getWhen());
		add(new Label("commitAuthorDate", authorDate));

		add(new Label("commitCommitter", JGitUtils.getDisplayName(c.getCommitterIdent())));
		String comitterDate = GitBlitWebSession.get().formatDateTimeLong(c.getCommitterIdent().getWhen());
		add(new Label("commitCommitterDate", comitterDate));

		add(new Label("commitId", c.getName()));

		add(new LinkPanel("commitTree", "list", c.getTree().getName(), TreePage.class, newCommitParameter()));

		// Parent Commits
		ListDataProvider<String> parentsDp = new ListDataProvider<String>(parents);
		DataView<String> parentsView = new DataView<String>("commitParents", parentsDp) {
			private static final long serialVersionUID = 1L;

			public void populateItem(final Item<String> item) {
				String entry = item.getModelObject();
				item.add(new LinkPanel("commitParent", "list", entry, CommitPage.class, newCommitParameter(entry)));
			}
		};
		add(parentsView);

		addFullText("fullMessage", c.getFullMessage(), true);

		// changed paths list
		List<PathModel> paths  = JGitUtils.getCommitChangedPaths(r, c);
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
				item.add(new PathLinksPanel("pathLinks", repositoryName, entry));
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
