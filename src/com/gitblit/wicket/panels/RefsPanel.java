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
import java.util.List;
import java.util.Map;

import org.apache.wicket.Component;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.CommitPage;
import com.gitblit.wicket.pages.LogPage;
import com.gitblit.wicket.pages.TagPage;

public class RefsPanel extends Panel {

	private static final long serialVersionUID = 1L;

	public RefsPanel(String id, final String repositoryName, RevCommit c,
			Map<ObjectId, List<String>> refs) {
		super(id);
		List<String> refNames = refs.get(c.getId());
		if (refNames == null) {
			refNames = new ArrayList<String>();
		}
		Collections.sort(refNames);
		// refNames.remove(Constants.HEAD);

		ListDataProvider<String> refsDp = new ListDataProvider<String>(refNames);
		DataView<String> refsView = new DataView<String>("ref", refsDp) {
			private static final long serialVersionUID = 1L;

			public void populateItem(final Item<String> item) {
				String entry = item.getModelObject();
				Component c = null;
				if (entry.startsWith(Constants.R_HEADS)) {
					// local head
					c = new LinkPanel("refName", null, entry.substring(Constants.R_HEADS.length()),
							LogPage.class, WicketUtils.newObjectParameter(repositoryName, entry));
					WicketUtils.setCssClass(c, "headRef");
				} else if (entry.startsWith(Constants.R_REMOTES)) {
					// remote head
					c = new LinkPanel("refName", null,
							entry.substring(Constants.R_REMOTES.length()), LogPage.class,
							WicketUtils.newObjectParameter(repositoryName, entry));
					WicketUtils.setCssClass(c, "remoteRef");
				} else if (entry.startsWith(Constants.R_TAGS)) {
					// tag
					c = new LinkPanel("refName", null, entry.substring(Constants.R_TAGS.length()),
							TagPage.class, WicketUtils.newObjectParameter(repositoryName, entry));
					WicketUtils.setCssClass(c, "tagRef");
				} else {
					// other
					c = new LinkPanel("refName", null, entry, CommitPage.class,
							WicketUtils.newObjectParameter(repositoryName, entry));
					WicketUtils.setCssClass(c, "otherRef");
				}
				WicketUtils.setHtmlTooltip(c, entry);
				item.add(c);
			}
		};
		add(refsView);
	}
}