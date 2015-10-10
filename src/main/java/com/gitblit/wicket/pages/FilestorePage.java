/*
 * Copyright 2015 gitblit.com.
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

import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.wicket.Component;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;

import com.gitblit.Constants;
import com.gitblit.Keys;
import com.gitblit.models.FilestoreModel;
import com.gitblit.models.UserModel;
import com.gitblit.wicket.FilestoreUI;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;

/**
 * Page to display the current status of the filestore.
 * Certain errors also displayed to aid in fault finding  
 *
 * @author Paul Martin
 *
 *
 */
public class FilestorePage extends RootPage {

	public FilestorePage() {
		super();
		setupPage("", "");
		// check to see if we should display a login message
		boolean authenticateView = app().settings().getBoolean(Keys.web.authenticateViewPages, true);
		if (authenticateView && !GitBlitWebSession.get().isLoggedIn()) {
			String messageSource = app().settings().getString(Keys.web.loginMessage, "gitblit");
			return;
		}
		
		final List<FilestoreModel> files = app().filestore().getAllObjects();
		final long nBytesUsed = app().filestore().getFilestoreUsedByteCount();
		final long nBytesAvailable = app().filestore().getFilestoreAvailableByteCount();
		
		// Load the markdown welcome message
		String messageSource = app().settings().getString(Keys.web.repositoriesMessage, "gitblit");
		String message = MessageFormat.format(getString("gb.filestoreStats"), files.size(), 
				FileUtils.byteCountToDisplaySize(nBytesUsed), FileUtils.byteCountToDisplaySize(nBytesAvailable) ); 

		Component repositoriesMessage = new Label("repositoriesMessage", message)
				.setEscapeModelStrings(false).setVisible(message.length() > 0);
		
		add(repositoriesMessage);
		
		BookmarkablePageLink<Void> helpLink = new BookmarkablePageLink<Void>("filestoreHelp", FilestoreUsage.class);
		helpLink.add(new Label("helpMessage", getString("gb.filestoreHelp")));
		add(helpLink);
		

		DataView<FilestoreModel> filesView = new DataView<FilestoreModel>("fileRow",
				new ListDataProvider<FilestoreModel>(files)) {
			private static final long serialVersionUID = 1L;
			private int counter;

			@Override
			protected void onBeforeRender() {
				super.onBeforeRender();
				counter = 0;
			}

			@Override
			public void populateItem(final Item<FilestoreModel> item) {
				final FilestoreModel entry = item.getModelObject();
				
				DateFormat dateFormater = new SimpleDateFormat(Constants.ISO8601);
				
				UserModel user = app().users().getUserModel(entry.getChangedBy());
				user = user == null ? UserModel.ANONYMOUS : user;
				
				Label icon = FilestoreUI.getStatusIcon("status", entry);
				item.add(icon);
				item.add(new Label("on", dateFormater.format(entry.getChangedOn())));
				item.add(new Label("by", user.getDisplayName()));
				
				item.add(new Label("oid", entry.oid));
				item.add(new Label("size", FileUtils.byteCountToDisplaySize(entry.getSize())));				
				
				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}

		};
		
		add(filesView);
	}
}
