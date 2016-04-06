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
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.request.target.resource.ResourceStreamRequestTarget;
import org.apache.wicket.util.resource.AbstractResourceStreamWriter;
import org.apache.wicket.util.resource.IResourceStream;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.models.PathModel;
import com.gitblit.models.SubmoduleModel;
import com.gitblit.models.UserModel;
import com.gitblit.servlet.RawServlet;
import com.gitblit.utils.ByteFormat;
import com.gitblit.utils.JGitUtils;
import com.gitblit.wicket.CacheControl;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.CacheControl.LastModified;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.panels.CommitHeaderPanel;
import com.gitblit.wicket.panels.CompressedDownloadsPanel;
import com.gitblit.wicket.panels.LinkPanel;
import com.gitblit.wicket.panels.PathBreadcrumbsPanel;

@CacheControl(LastModified.BOOT)
public class TreePage extends RepositoryPage {

	public TreePage(PageParameters params) {
		super(params);

		final String path = WicketUtils.getPath(params);

		Repository r = getRepository();
		RevCommit commit = getCommit();
		List<PathModel> paths = JGitUtils.getFilesInPath2(r, path, commit);

		// tree page links
		add(new BookmarkablePageLink<Void>("historyLink", HistoryPage.class,
				WicketUtils.newPathParameter(repositoryName, objectId, path)));
		add(new CompressedDownloadsPanel("compressedLinks", getRequest()
				.getRelativePathPrefixToContextRoot(), repositoryName, objectId, path));

		add(new CommitHeaderPanel("commitHeader", repositoryName, commit));

		// breadcrumbs
		add(new PathBreadcrumbsPanel("breadcrumbs", repositoryName, path, objectId));
		if (path != null && path.trim().length() > 0) {
			// add .. parent path entry
			String parentPath = null;
			if (path.lastIndexOf('/') > -1) {
				parentPath = path.substring(0, path.lastIndexOf('/'));
			}
			PathModel model = new PathModel("..", parentPath, null, 0, FileMode.TREE.getBits(), null, objectId);
			model.isParentPath = true;
			paths.add(0, model);
		}

		final String id = getBestCommitId(commit);
		
		final ByteFormat byteFormat = new ByteFormat();
		final String baseUrl = WicketUtils.getGitblitURL(getRequest());

		// changed paths list
		ListDataProvider<PathModel> pathsDp = new ListDataProvider<PathModel>(paths);
		DataView<PathModel> pathsView = new DataView<PathModel>("changedPath", pathsDp) {
			private static final long serialVersionUID = 1L;
			int counter;

			@Override
			public void populateItem(final Item<PathModel> item) {
				final PathModel entry = item.getModelObject();
				
				item.add(new Label("pathPermissions", JGitUtils.getPermissionsFromMode(entry.mode)));
				item.add(WicketUtils.setHtmlTooltip(new Label("filestore", ""), getString("gb.filestore"))
									.setVisible(entry.isFilestoreItem()));

				if (entry.isParentPath) {
					// parent .. path
					item.add(WicketUtils.newBlankImage("pathIcon"));
					item.add(new Label("pathSize", ""));
					item.add(new LinkPanel("pathName", null, entry.name, TreePage.class,
							WicketUtils.newPathParameter(repositoryName, id, entry.path)));
					item.add(new Label("pathLinks", ""));
				} else {
					if (entry.isTree()) {
						// folder/tree link
						item.add(WicketUtils.newImage("pathIcon", "folder_16x16.png"));
						item.add(new Label("pathSize", ""));
						item.add(new LinkPanel("pathName", "list", entry.name, TreePage.class,
								WicketUtils.newPathParameter(repositoryName, id,
										entry.path)));

						// links
						Fragment links = new Fragment("pathLinks", "treeLinks", this);
						links.add(new BookmarkablePageLink<Void>("tree", TreePage.class,
								WicketUtils.newPathParameter(repositoryName, id,
										entry.path)));
						links.add(new BookmarkablePageLink<Void>("history", HistoryPage.class,
								WicketUtils.newPathParameter(repositoryName, id,
										entry.path)));
						links.add(new CompressedDownloadsPanel("compressedLinks", baseUrl,
								repositoryName, objectId, entry.path));

						item.add(links);
					} else if (entry.isSubmodule()) {
						// submodule
						String submoduleId = entry.objectId;
						String submodulePath;
						boolean hasSubmodule = false;
						SubmoduleModel submodule = getSubmodule(entry.path);
						submodulePath = submodule.gitblitPath;
						hasSubmodule = submodule.hasSubmodule;

						item.add(WicketUtils.newImage("pathIcon", "git-orange-16x16.png"));
						item.add(new Label("pathSize", ""));
						item.add(new LinkPanel("pathName", "list", entry.name + " @ " +
								getShortObjectId(submoduleId), TreePage.class,
								WicketUtils.newPathParameter(submodulePath, submoduleId, "")).setEnabled(hasSubmodule));

						Fragment links = new Fragment("pathLinks", "submoduleLinks", this);
						links.add(new BookmarkablePageLink<Void>("view", SummaryPage.class,
								WicketUtils.newRepositoryParameter(submodulePath)).setEnabled(hasSubmodule));
						links.add(new BookmarkablePageLink<Void>("tree", TreePage.class,
								WicketUtils.newPathParameter(submodulePath, submoduleId,
										"")).setEnabled(hasSubmodule));
						links.add(new BookmarkablePageLink<Void>("history", HistoryPage.class,
								WicketUtils.newPathParameter(repositoryName, id,
										entry.path)));
						links.add(new CompressedDownloadsPanel("compressedLinks", baseUrl,
								submodulePath, submoduleId, "").setEnabled(hasSubmodule));
						item.add(links);
					} else {
						// blob link
						String displayPath = entry.name;
						String path = entry.path;
						if (entry.isSymlink()) {
							path = JGitUtils.getStringContent(getRepository(), getCommit().getTree(), path);
							displayPath = entry.name + " -> " + path;
						}
						item.add(WicketUtils.getFileImage("pathIcon", entry.name));
						item.add(new Label("pathSize", byteFormat.format(entry.size)));
						
						// links
						Fragment links = new Fragment("pathLinks", "blobLinks", this);
						
						if (entry.isFilestoreItem()) {
							item.add(new LinkPanel("pathName", "list", displayPath, new Link<Object>("link", null) {
								 
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
							
							links.add(new Link<Object>("view", null) {
								 
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
							
							links.add(new Link<Object>("raw", null) {
								 
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
							item.add(new LinkPanel("pathName", "list", displayPath, BlobPage.class,
									WicketUtils.newPathParameter(repositoryName, id,
											path)));
							
							links.add(new BookmarkablePageLink<Void>("view", BlobPage.class,
									WicketUtils.newPathParameter(repositoryName, id,
											path)));
							String rawUrl = RawServlet.asLink(getContextUrl(), repositoryName, id, path);
							links.add(new ExternalLink("raw", rawUrl));
						}
						
						links.add(new BookmarkablePageLink<Void>("blame", BlamePage.class,
								WicketUtils.newPathParameter(repositoryName, id,
										path)));
						links.add(new BookmarkablePageLink<Void>("history", HistoryPage.class,
								WicketUtils.newPathParameter(repositoryName, id,
										path)));
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

	@Override
	protected boolean isCommitPage() {
		return true;
	}

}
