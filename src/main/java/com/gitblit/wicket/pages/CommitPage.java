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
package com.gitblit.wicket.pages;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.request.target.resource.ResourceStreamRequestTarget;
import org.apache.wicket.util.resource.AbstractResourceStreamWriter;
import org.apache.wicket.util.resource.IResourceStream;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.Constants;
import com.gitblit.models.GitNote;
import com.gitblit.models.PathModel.PathChangeModel;
import com.gitblit.models.SubmoduleModel;
import com.gitblit.models.UserModel;
import com.gitblit.servlet.RawServlet;
import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.CacheControl;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.CacheControl.LastModified;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.CommitHeaderPanel;
import com.gitblit.wicket.panels.CommitLegendPanel;
import com.gitblit.wicket.panels.CompressedDownloadsPanel;
import com.gitblit.wicket.panels.DiffStatPanel;
import com.gitblit.wicket.panels.AvatarImage;
import com.gitblit.wicket.panels.LinkPanel;
import com.gitblit.wicket.panels.RefsPanel;

@CacheControl(LastModified.BOOT)
public class CommitPage extends RepositoryPage {

	public CommitPage(PageParameters params) {
		super(params);

		Repository r = getRepository();
		RevCommit c = getCommit();

		List<String> parents = new ArrayList<String>();
		if (c.getParentCount() > 0) {
			for (RevCommit parent : c.getParents()) {
				parents.add(parent.name());
			}
		}

		// commit page links
		if (parents.size() == 0) {
			add(new Label("parentLink", "none"));
			add(new Label("commitdiffLink", getString("gb.commitdiff")));
		} else {
			add(new LinkPanel("parentLink", null, getShortObjectId(parents.get(0)),
					CommitPage.class, newCommitParameter(parents.get(0))));
			add(new LinkPanel("commitdiffLink", null, new StringResourceModel("gb.commitdiff",
					this, null), CommitDiffPage.class, WicketUtils.newObjectParameter(
					repositoryName, objectId)));
		}
		add(new BookmarkablePageLink<Void>("patchLink", PatchPage.class,
				WicketUtils.newObjectParameter(repositoryName, objectId)));

		add(new CommitHeaderPanel("commitHeader", repositoryName, c));

		addRefs(r, c);

		// author
		add(createPersonPanel("commitAuthor", c.getAuthorIdent(), Constants.SearchType.AUTHOR));
		add(WicketUtils.createTimestampLabel("commitAuthorDate", c.getAuthorIdent().getWhen(),
				getTimeZone(), getTimeUtils()));

		// committer
		add(createPersonPanel("commitCommitter", c.getCommitterIdent(), Constants.SearchType.COMMITTER));
		add(WicketUtils.createTimestampLabel("commitCommitterDate",
				c.getCommitterIdent().getWhen(), getTimeZone(), getTimeUtils()));

		add(new Label("commitId", c.getName()));

		add(new LinkPanel("commitTree", "list", c.getTree().getName(), TreePage.class,
				newCommitParameter()));
		add(new BookmarkablePageLink<Void>("treeLink", TreePage.class, newCommitParameter()));
		final String baseUrl = WicketUtils.getGitblitURL(getRequest());

		add(new CompressedDownloadsPanel("compressedLinks", baseUrl, repositoryName, objectId, null));

		// Parent Commits
		ListDataProvider<String> parentsDp = new ListDataProvider<String>(parents);
		DataView<String> parentsView = new DataView<String>("commitParents", parentsDp) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(final Item<String> item) {
				String entry = item.getModelObject();
				item.add(new LinkPanel("commitParent", "list", entry, CommitPage.class,
						newCommitParameter(entry)));
				item.add(new BookmarkablePageLink<Void>("view", CommitPage.class,
						newCommitParameter(entry)));
				item.add(new BookmarkablePageLink<Void>("diff", CommitDiffPage.class,
						newCommitParameter(entry)));
			}
		};
		add(parentsView);

		addFullText("fullMessage", c.getFullMessage());

		// git notes
		List<GitNote> notes = JGitUtils.getNotesOnCommit(r, c);
		ListDataProvider<GitNote> notesDp = new ListDataProvider<GitNote>(notes);
		DataView<GitNote> notesView = new DataView<GitNote>("notes", notesDp) {
			private static final long serialVersionUID = 1L;

			@Override
			public void populateItem(final Item<GitNote> item) {
				GitNote entry = item.getModelObject();
				item.add(new RefsPanel("refName", repositoryName, Arrays.asList(entry.notesRef)));
				item.add(createPersonPanel("authorName", entry.notesRef.getAuthorIdent(),
						Constants.SearchType.AUTHOR));
				item.add(new AvatarImage("noteAuthorAvatar", entry.notesRef.getAuthorIdent()));
				item.add(WicketUtils.createTimestampLabel("authorDate", entry.notesRef
						.getAuthorIdent().getWhen(), getTimeZone(), getTimeUtils()));
				item.add(new Label("noteContent", bugtraqProcessor().processPlainCommitMessage(getRepository(), repositoryName,
						entry.content)).setEscapeModelStrings(false));
			}
		};
		add(notesView.setVisible(notes.size() > 0));

		// changed paths list
		List<PathChangeModel> paths = JGitUtils.getFilesInCommit(r, c);

		// add commit diffstat
		int insertions = 0;
		int deletions = 0;
		for (PathChangeModel pcm : paths) {
			insertions += pcm.insertions;
			deletions += pcm.deletions;
		}
		add(new DiffStatPanel("diffStat", insertions, deletions));

		add(new CommitLegendPanel("commitLegend", paths));
		ListDataProvider<PathChangeModel> pathsDp = new ListDataProvider<PathChangeModel>(paths);
		DataView<PathChangeModel> pathsView = new DataView<PathChangeModel>("changedPath", pathsDp) {
			private static final long serialVersionUID = 1L;
			int counter;

			@Override
			public void populateItem(final Item<PathChangeModel> item) {
				final PathChangeModel entry = item.getModelObject();
				
				Label changeType = new Label("changeType", "");
				WicketUtils.setChangeTypeCssClass(changeType, entry.changeType);
				setChangeTypeTooltip(changeType, entry.changeType);
				item.add(changeType);
				item.add(new DiffStatPanel("diffStat", entry.insertions, entry.deletions, true));
				item.add(WicketUtils.setHtmlTooltip(new Label("filestore", ""), getString("gb.filestore"))
									.setVisible(entry.isFilestoreItem()));

				boolean hasSubmodule = false;
				String submodulePath = null;
				if (entry.isTree()) {
					// tree
					item.add(new LinkPanel("pathName", null, entry.path, TreePage.class,
							WicketUtils
									.newPathParameter(repositoryName, entry.commitId, entry.path)));
				} else if (entry.isSubmodule()) {
					// submodule
					String submoduleId = entry.objectId;
					SubmoduleModel submodule = getSubmodule(entry.path);
					submodulePath = submodule.gitblitPath;
					hasSubmodule = submodule.hasSubmodule;

					item.add(new LinkPanel("pathName", "list", entry.path + " @ " +
							getShortObjectId(submoduleId), TreePage.class,
							WicketUtils.newPathParameter(submodulePath, submoduleId, "")).setEnabled(hasSubmodule));
				} else {
					// blob
					String displayPath = entry.path;
					String path = entry.path;
					if (entry.isSymlink()) {
						path = JGitUtils.getStringContent(getRepository(), getCommit().getTree(), path);
						displayPath = entry.path + " -> " + path;
					}
					
					if (entry.isFilestoreItem()) {
						item.add(new LinkPanel("pathName", "list", entry.path, new Link<Object>("link", null) {
							 
							private static final long serialVersionUID = 1L;

							@Override
						    public void onClick() {
						 
						    	 IResourceStream resourceStream = new AbstractResourceStreamWriter() {
						    		 								    	
									private static final long serialVersionUID = 1L;

									@Override 
						    	      public void write(OutputStream output) {
						    	   		 UserModel user =  GitBlitWebSession.get().getUser();
									     user = user == null ? UserModel.ANONYMOUS : user;
									    	
						    	        app().filestore().downloadBlob(entry.getFilestoreOid(), user, getRepositoryModel(), output);
						    	      }
						    	  };
						    	      
						    	
						    	getRequestCycle().setRequestTarget(new ResourceStreamRequestTarget(resourceStream, entry.path));
						    }}));
						
						
					} else {
						item.add(new LinkPanel("pathName", "list", displayPath, BlobPage.class,
							WicketUtils.newPathParameter(repositoryName, entry.commitId, path)));
					}
				}


				// quick links
				if (entry.isSubmodule()) {
					item.add(new ExternalLink("raw", "").setEnabled(false));

					// submodule
					item.add(new BookmarkablePageLink<Void>("diff", BlobDiffPage.class, WicketUtils
							.newPathParameter(repositoryName, entry.commitId, entry.path))
							.setEnabled(!entry.changeType.equals(ChangeType.ADD)));
					item.add(new BookmarkablePageLink<Void>("view", CommitPage.class, WicketUtils
							.newObjectParameter(submodulePath, entry.objectId)).setEnabled(hasSubmodule));
					item.add(new ExternalLink("blame", "").setEnabled(false));
					item.add(new BookmarkablePageLink<Void>("history", HistoryPage.class, WicketUtils
							.newPathParameter(repositoryName, entry.commitId, entry.path))
							.setEnabled(!entry.changeType.equals(ChangeType.ADD)));
				} else {
					// tree or blob
					item.add(new BookmarkablePageLink<Void>("diff", BlobDiffPage.class, WicketUtils
							.newPathParameter(repositoryName, entry.commitId, entry.path))
							.setEnabled(!entry.changeType.equals(ChangeType.ADD)
									&& !entry.changeType.equals(ChangeType.DELETE)));
					
					if (entry.isFilestoreItem()) {
						item.add(new Link<Object>("view", null) {
							 
							private static final long serialVersionUID = 1L;

							@Override
						    public void onClick() {
						 
						    	 IResourceStream resourceStream = new AbstractResourceStreamWriter() {
						    		 								    	
									private static final long serialVersionUID = 1L;

									@Override 
						    	      public void write(OutputStream output) {
						    	   		 UserModel user =  GitBlitWebSession.get().getUser();
									     user = user == null ? UserModel.ANONYMOUS : user;
									    	
						    	        app().filestore().downloadBlob(entry.getFilestoreOid(), user, getRepositoryModel(), output);
						    	      }
						    	  };
						    	      
						    	
						    	getRequestCycle().setRequestTarget(new ResourceStreamRequestTarget(resourceStream, entry.path));
						    }});
						
						item.add(new Link<Object>("raw", null) {
							 
							private static final long serialVersionUID = 1L;

							@Override
						    public void onClick() {
						 
						    	 IResourceStream resourceStream = new AbstractResourceStreamWriter() {
						    		 								    	
									private static final long serialVersionUID = 1L;

									@Override 
						    	      public void write(OutputStream output) {
						    	   		 UserModel user =  GitBlitWebSession.get().getUser();
									     user = user == null ? UserModel.ANONYMOUS : user;
									    	
						    	        app().filestore().downloadBlob(entry.getFilestoreOid(), user, getRepositoryModel(), output);
						    	      }
						    	  };
						    	      
						    	
						    	getRequestCycle().setRequestTarget(new ResourceStreamRequestTarget(resourceStream, entry.path));
						    }});
						    						
					} else {
						item.add(new BookmarkablePageLink<Void>("view", BlobPage.class, WicketUtils
								.newPathParameter(repositoryName, entry.commitId, entry.path))
								.setEnabled(!entry.changeType.equals(ChangeType.DELETE)));
						String rawUrl = RawServlet.asLink(getContextUrl(), repositoryName, entry.commitId, entry.path);
						item.add(new ExternalLink("raw", rawUrl)
								.setEnabled(!entry.changeType.equals(ChangeType.DELETE)));
					}
					item.add(new BookmarkablePageLink<Void>("blame", BlamePage.class, WicketUtils
							.newPathParameter(repositoryName, entry.commitId, entry.path))
							.setEnabled(!entry.changeType.equals(ChangeType.ADD)
									&& !entry.changeType.equals(ChangeType.DELETE)));
					item.add(new BookmarkablePageLink<Void>("history", HistoryPage.class, WicketUtils
							.newPathParameter(repositoryName, entry.commitId, entry.path))
							.setEnabled(!entry.changeType.equals(ChangeType.ADD)));
				}

				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(pathsView);
	}

	@Override
	protected String getPageName() {
		return getString("gb.commit");
	}

	@Override
	protected boolean isCommitPage() {
		return true;
	}

	@Override
	protected Class<? extends BasePage> getRepoNavPageClass() {
		return LogPage.class;
	}
}
