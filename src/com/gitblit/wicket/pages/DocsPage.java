package com.gitblit.wicket.pages;

import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.utils.ByteFormat;
import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.LinkPanel;
import com.gitblit.wicket.RepositoryPage;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.models.PathModel;

public class DocsPage extends RepositoryPage {

	public DocsPage(PageParameters params) {
		super(params);

		Repository r = getRepository();
		List<String> extensions = GitBlit.self().settings().getStrings(Keys.web.markdownExtensions);
		List<PathModel> paths = JGitUtils.getDocuments(r, extensions);

		final ByteFormat byteFormat = new ByteFormat();

		add(new Label("header", getString("gb.docs")));
		
		// documents list
		ListDataProvider<PathModel> pathsDp = new ListDataProvider<PathModel>(paths);
		DataView<PathModel> pathsView = new DataView<PathModel>("document", pathsDp) {
			private static final long serialVersionUID = 1L;
			int counter = 0;

			public void populateItem(final Item<PathModel> item) {
				PathModel entry = item.getModelObject();
				item.add(WicketUtils.newImage("docIcon", "file_world_16x16.png"));
				item.add(new Label("docSize", byteFormat.format(entry.size)));
				item.add(new LinkPanel("docName", "list", entry.name, BlobPage.class, newPathParameter(entry.path)));

				// links
				item.add(new BookmarkablePageLink<Void>("view", BlobPage.class, WicketUtils.newPathParameter(repositoryName, entry.commitId, entry.path)));
				item.add(new BookmarkablePageLink<Void>("raw", RawPage.class, WicketUtils.newPathParameter(repositoryName, entry.commitId, entry.path)));
				item.add(new BookmarkablePageLink<Void>("blame", BlobPage.class).setEnabled(false));
				item.add(new BookmarkablePageLink<Void>("history", HistoryPage.class, WicketUtils.newPathParameter(repositoryName, entry.commitId, entry.path)));				
				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(pathsView);
	}

	@Override
	protected String getPageName() {
		return getString("gb.docs");
	}
}
