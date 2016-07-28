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

import java.util.List;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.request.cycle.RequestCycle;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;

import com.gitblit.models.RefModel;
import com.gitblit.servlet.RawServlet;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.pages.BlobPage;
import com.gitblit.wicket.pages.CommitPage;
import com.gitblit.wicket.pages.LogPage;
import com.gitblit.wicket.pages.TagPage;
import com.gitblit.wicket.pages.TagsPage;
import com.gitblit.wicket.pages.TreePage;

public class TagsPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	private final boolean hasTags;

	public TagsPanel(String wicketId, final String repositoryName, Repository r, final int maxCount) {
		super(wicketId);

		// header
		List<RefModel> tags = JGitUtils.getTags(r, false, maxCount);
		if (maxCount > 0) {
			// summary page
			// show tags page link
			add(new LinkPanel("header", "title", new StringResourceModel("gb.tags", this, null),
					TagsPage.class, WicketUtils.newRepositoryParameter(repositoryName)));
		} else {
			// tags page
			add(new Label("header", new StringResourceModel("gb.tags", this, null)));
		}

		ListDataProvider<RefModel> tagsDp = new ListDataProvider<RefModel>(tags);
		DataView<RefModel> tagView = new DataView<RefModel>("tag", tagsDp) {
			private static final long serialVersionUID = 1L;
			int counter;

			@Override
			public void populateItem(final Item<RefModel> item) {
				RefModel entry = item.getModelObject();

				item.add(WicketUtils.createDateLabel("tagDate", entry.getDate(), getTimeZone(), getTimeUtils()));

				Class<? extends WebPage> linkClass;
				switch (entry.getReferencedObjectType()) {
				case Constants.OBJ_BLOB:
					linkClass = BlobPage.class;
					break;
				case Constants.OBJ_TREE:
					linkClass = TreePage.class;
					break;
				case Constants.OBJ_COMMIT:
				default:
					linkClass = CommitPage.class;
					break;
				}
				item.add(new LinkPanel("tagName", "list name", entry.displayName, linkClass,
						WicketUtils.newObjectParameter(repositoryName, entry
								.getReferencedObjectId().getName())));

				// workaround for RevTag returning a lengthy shortlog. :(
				String message = StringUtils.trimString(entry.getShortMessage(),
						com.gitblit.Constants.LEN_SHORTLOG);

				if (linkClass.equals(BlobPage.class)) {
					// Blob Tag Object
					item.add(WicketUtils.newImage("tagIcon", "file_16x16.png"));
					LinkPanel messageLink = new LinkPanel("tagDescription", "list", message, TagPage.class,
							WicketUtils.newObjectParameter(repositoryName, entry.getObjectId()
									.getName()));
					if (!entry.getShortMessage().equals(message)) {
						messageLink.setTooltip(entry.getShortMessage());
					}
					item.add(messageLink);

					Fragment fragment = new Fragment("tagLinks", "blobLinks", this);
					fragment.add(new BookmarkablePageLink<Void>("tag", TagPage.class, WicketUtils
							.newObjectParameter(repositoryName, entry.getObjectId().getName()))
							.setEnabled(entry.isAnnotatedTag()));

					fragment.add(new BookmarkablePageLink<Void>("blob", linkClass, WicketUtils
							.newObjectParameter(repositoryName, entry.getReferencedObjectId()
									.getName())));

					String contextUrl = RequestCycle.get().getRequest().getRelativePathPrefixToContextRoot();
					String rawUrl = RawServlet.asLink(contextUrl, repositoryName, entry.displayName,
							entry.getReferencedObjectId().getName());
					fragment.add(new ExternalLink("raw", rawUrl));
					item.add(fragment);
				} else {
					// TODO Tree Tag Object
					// Standard Tag Object
					if (entry.isAnnotatedTag()) {
						item.add(WicketUtils.newImage("tagIcon", "tag_16x16.png"));
						LinkPanel messageLink = new LinkPanel("tagDescription", "list", message, TagPage.class,
								WicketUtils.newObjectParameter(repositoryName, entry.getObjectId()
										.getName()));
						if (!message.equals(entry.getShortMessage())) {
							messageLink.setTooltip(entry.getShortMessage());
						}
						item.add(messageLink);

						Fragment fragment = new Fragment("tagLinks", "annotatedLinks", this);
						fragment.add(new BookmarkablePageLink<Void>("tag", TagPage.class,
								WicketUtils.newObjectParameter(repositoryName, entry.getObjectId()
										.getName())).setEnabled(entry.isAnnotatedTag()));

						fragment.add(new BookmarkablePageLink<Void>("commit", linkClass,
								WicketUtils.newObjectParameter(repositoryName, entry
										.getReferencedObjectId().getName())));

						fragment.add(new BookmarkablePageLink<Void>("log", LogPage.class,
								WicketUtils.newObjectParameter(repositoryName, entry.getName())));
						item.add(fragment);
					} else {
						item.add(WicketUtils.newBlankImage("tagIcon"));
						item.add(new LinkPanel("tagDescription", "list", message, CommitPage.class,
								WicketUtils.newObjectParameter(repositoryName, entry.getObjectId()
										.getName())));
						Fragment fragment = new Fragment("tagLinks", "lightweightLinks", this);
						fragment.add(new BookmarkablePageLink<Void>("commit", CommitPage.class,
								WicketUtils.newObjectParameter(repositoryName, entry
										.getReferencedObjectId().getName())));
						fragment.add(new BookmarkablePageLink<Void>("log", LogPage.class,
								WicketUtils.newObjectParameter(repositoryName, entry.getName())));
						item.add(fragment);
					}
				}

				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(tagView);
		if (tags.size() < maxCount || maxCount <= 0) {
			add(new Label("allTags", "").setVisible(false));
		} else {
			add(new LinkPanel("allTags", "link", new StringResourceModel("gb.allTags", this, null),
					TagsPage.class, WicketUtils.newRepositoryParameter(repositoryName)));
		}

		hasTags = tags.size() > 0;
	}

	public TagsPanel hideIfEmpty() {
		setVisible(hasTags);
		return this;
	}
}
