/*
 * Copyright 2012 gitblit.com.
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

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;

import com.gitblit.Keys;
import com.gitblit.servlet.DownloadZipServlet;
import com.gitblit.servlet.DownloadZipServlet.Format;

public class CompressedDownloadsPanel extends BasePanel {

	private static final long serialVersionUID = 1L;

	public CompressedDownloadsPanel(String id, final String baseUrl, final String repositoryName, final String objectId, final String path) {
		super(id);

		List<String> types = app().settings().getStrings(Keys.web.compressedDownloads);
		if (types.isEmpty()) {
			types.add(Format.zip.name());
			types.add(Format.gz.name());
		}

		ListDataProvider<String> refsDp = new ListDataProvider<String>(types);
		DataView<String> refsView = new DataView<String>("compressedLinks", refsDp) {
			private static final long serialVersionUID = 1L;
			int counter;

			@Override
			protected void onBeforeRender() {
				super.onBeforeRender();
				counter = 0;
			}

			@Override
			public void populateItem(final Item<String> item) {
				String compressionType = item.getModelObject();
				Format format = Format.fromName(compressionType);

				String href = DownloadZipServlet.asLink(baseUrl, repositoryName,
						objectId, path, format);
				LinkPanel c = new LinkPanel("compressedLink", null, format.name(), href);
				c.setNoFollow();
				item.add(c);
				Label lb = new Label("linkSep", "|");
				lb.setVisible(counter > 0);
				lb.setRenderBodyOnly(true);
				item.add(lb.setEscapeModelStrings(false));
				item.setRenderBodyOnly(true);
				counter++;
			}
		};
		add(refsView);

		setVisible(app().settings().getBoolean(Keys.web.allowZipDownloads, true));
	}
}