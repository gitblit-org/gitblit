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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.wicket.Component;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.models.RefModel;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.CommitPage;
import com.gitblit.wicket.pages.LogPage;
import com.gitblit.wicket.pages.RepositoryPage;
import com.gitblit.wicket.pages.TagPage;

public class RefsPanel extends Panel {

	private static final long serialVersionUID = 1L;

	public RefsPanel(String id, final String repositoryName, RevCommit c,
			Map<ObjectId, List<RefModel>> refs) {
		this(id, repositoryName, refs.get(c.getId()));
	}

	public RefsPanel(String id, final String repositoryName, List<RefModel> refs) {
		super(id);
		if (refs == null) {
			refs = new ArrayList<RefModel>();
		}
		Collections.sort(refs, new Comparator<RefModel>() {
			@Override
			public int compare(RefModel o1, RefModel o2) {
				// sort remote heads last, otherwise sort by name
				// this is so we can insert a break on the refs panel
				// [head][branch][branch][tag][tag]
				// [remote][remote][remote]
				boolean remote1 = o1.displayName.startsWith(Constants.R_REMOTES);
				boolean remote2 = o2.displayName.startsWith(Constants.R_REMOTES);
				if (remote1 && remote2) {
					// both are remote heads, sort by name
					return o1.displayName.compareTo(o2.displayName);	
				}
				if (remote1) {
					// o1 is remote, o2 comes first
					return 1;
				}
				if (remote2) {
					// remote is o2, o1 comes first
					return -1;
				}
				// standard sort
				return o1.displayName.compareTo(o2.displayName);
			}
		});
		
		// count remote and determine if we should insert a break
		int remoteCount = 0;
		for (RefModel ref : refs) {
			if (ref.displayName.startsWith(Constants.R_REMOTES)) {
				remoteCount++;
			}
		}
		final boolean shouldBreak = remoteCount < refs.size();
		
		ListDataProvider<RefModel> refsDp = new ListDataProvider<RefModel>(refs);
		DataView<RefModel> refsView = new DataView<RefModel>("ref", refsDp) {
			private static final long serialVersionUID = 1L;
			private boolean alreadyInsertedBreak = !shouldBreak;

			public void populateItem(final Item<RefModel> item) {
				RefModel entry = item.getModelObject();
				String name = entry.displayName;
				String objectid = entry.getReferencedObjectId().getName();
				boolean breakLine = false;
				Class<? extends RepositoryPage> linkClass = CommitPage.class;
				String cssClass = "";
				if (name.startsWith(Constants.R_HEADS)) {
					// local branch
					linkClass = LogPage.class;
					name = name.substring(Constants.R_HEADS.length());
					cssClass = "localBranch";
				} else if (name.equals(Constants.HEAD)) {
					// local head
					linkClass = LogPage.class;
					cssClass = "headRef";
				} else if (name.startsWith(Constants.R_REMOTES)) {
					// remote branch
					linkClass = LogPage.class;
					name = name.substring(Constants.R_REMOTES.length());
					cssClass = "remoteBranch";
					if (!alreadyInsertedBreak) {
						breakLine = true;
						alreadyInsertedBreak = true;
					}
				} else if (name.startsWith(Constants.R_TAGS)) {
					// tag
					if (entry.isAnnotatedTag()) {
						linkClass = TagPage.class;
						objectid = entry.getObjectId().getName();
					} else {
						linkClass = CommitPage.class;
						objectid = entry.getReferencedObjectId().getName();
					}
					name = name.substring(Constants.R_TAGS.length());
					cssClass = "tagRef";
				} else if (name.startsWith(Constants.R_NOTES)) {
					linkClass = CommitPage.class;
					cssClass = "otherRef";
				}

				Component c = new LinkPanel("refName", null, name, linkClass,
						WicketUtils.newObjectParameter(repositoryName, objectid));
				WicketUtils.setCssClass(c, cssClass);
				WicketUtils.setHtmlTooltip(c, name);
				item.add(c);
				Label lb = new Label("lineBreak", "<br/>");
				lb.setVisible(breakLine);
				lb.setRenderBodyOnly(true);
				item.add(lb.setEscapeModelStrings(false));
				item.setRenderBodyOnly(true);
			}
		};
		add(refsView);
	}
}