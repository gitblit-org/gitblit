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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.wicket.PageParameters;
import org.apache.wicket.extensions.markup.html.repeater.util.SortableDataProvider;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import com.gitblit.Constants;
import com.gitblit.Keys;
import com.gitblit.models.FilestoreModel;
import com.gitblit.models.FilestoreModel.Status;
import com.gitblit.models.UserModel;
import com.gitblit.wicket.CacheControl;
import com.gitblit.wicket.FilestoreUI;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;
import com.gitblit.wicket.CacheControl.LastModified;

/**
 * Page to display the current status of the filestore.
 * Certain errors also displayed to aid in fault finding
 *
 * @author Paul Martin
 */
@CacheControl(LastModified.ACTIVITY)
public class FilestorePage extends RootPage {

	public FilestorePage(PageParameters params) {
		super(params);
		setupPage("", "");

		int itemsPerPage = app().settings().getInteger(Keys.web.itemsPerPage, 20);
		if (itemsPerPage <= 1) {
			itemsPerPage = 20;
		}
		
		final int pageNumber = WicketUtils.getPage(params);
		final String filter = WicketUtils.getSearchString(params);
		
		int prevPage = Math.max(0, pageNumber - 1);
		int nextPage = pageNumber + 1;
		boolean hasMore = false;
		
		final UserModel user = (GitBlitWebSession.get().getUser() == null) ? UserModel.ANONYMOUS : GitBlitWebSession.get().getUser();
		final long nBytesUsed = app().filestore().getFilestoreUsedByteCount();
		final long nBytesAvailable = app().filestore().getFilestoreAvailableByteCount();
		List<FilestoreModel> files = app().filestore().getAllObjects(user);

		if (files == null) {
			files = new ArrayList<FilestoreModel>();
		}

		long nOk = 0;
		long nPending = 0;
		long nInprogress = 0;
		long nError = 0;
		long nDeleted = 0;
		
		for (FilestoreModel file : files) {
			switch (file.getStatus()) {
			case Available: { nOk++;} break;
			case Upload_Pending: { nPending++; } break;
			case Upload_In_Progress: { nInprogress++; } break;
			case Deleted: { nDeleted++; } break;
			default: { nError++; } break;
			}
		}
		
		
		BookmarkablePageLink<Void> itemOk = new BookmarkablePageLink<Void>("filterByOk", FilestorePage.class,
				WicketUtils.newFilestorePageParameter(prevPage, SortBy.ok.name()));
		
		BookmarkablePageLink<Void> itemPending = new BookmarkablePageLink<Void>("filterByPending", FilestorePage.class,
				WicketUtils.newFilestorePageParameter(prevPage, SortBy.pending.name()));
		
		BookmarkablePageLink<Void> itemInprogress = new BookmarkablePageLink<Void>("filterByInprogress", FilestorePage.class,
				WicketUtils.newFilestorePageParameter(prevPage, SortBy.inprogress.name()));
		
		BookmarkablePageLink<Void> itemError = new BookmarkablePageLink<Void>("filterByError", FilestorePage.class,
				WicketUtils.newFilestorePageParameter(prevPage, SortBy.error.name()));

		BookmarkablePageLink<Void> itemDeleted = new BookmarkablePageLink<Void>("filterByDeleted", FilestorePage.class,
				WicketUtils.newFilestorePageParameter(prevPage, SortBy.deleted.name()));
		
		
		List<FilestoreModel> filteredResults = new ArrayList<FilestoreModel>(files.size());
		
		if (filter == null) {
			filteredResults = files;
		} else if (filter.equals(SortBy.ok.name())) {
			WicketUtils.setCssClass(itemOk, "filter-on");
			
			for (FilestoreModel item : files) {
				if (item.getStatus() == Status.Available) {
					filteredResults.add(item);
				}
			}
		} else if (filter.equals(SortBy.pending.name())) {
			WicketUtils.setCssClass(itemPending, "filter-on");
			
			for (FilestoreModel item : files) {
				if (item.getStatus() == Status.Upload_Pending) {
					filteredResults.add(item);
				}
			}
		} else if (filter.equals(SortBy.inprogress.name())) {
			WicketUtils.setCssClass(itemInprogress, "filter-on");
			
			for (FilestoreModel item : files) {
				if (item.getStatus() == Status.Upload_In_Progress) {
					filteredResults.add(item);
				}
			}
		} else if (filter.equals(SortBy.error.name())) {
			WicketUtils.setCssClass(itemError, "filter-on");
			
			for (FilestoreModel item : files) {
				if (item.isInErrorState()) {
					filteredResults.add(item);
				}
			}
		} else if (filter.equals(SortBy.deleted.name())) {
			WicketUtils.setCssClass(itemDeleted, "filter-on");
			
			for (FilestoreModel item : files) {
				if (item.getStatus() == Status.Deleted) {
					filteredResults.add(item);
				}
			}
		}
		
		DataView<FilestoreModel> filesView = new DataView<FilestoreModel>("fileRow", 
				new SortableFilestoreProvider(filteredResults) , itemsPerPage) {
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


		if (filteredResults.size() < itemsPerPage) {
			filesView.setCurrentPage(0);
			hasMore = false;
		} else {
			filesView.setCurrentPage(pageNumber - 1);
			hasMore = true;
		}

		
		add(filesView);
		
		
		add(new BookmarkablePageLink<Void>("firstPageBottom", FilestorePage.class).setEnabled(pageNumber > 1));
		add(new BookmarkablePageLink<Void>("prevPageBottom", FilestorePage.class,
				WicketUtils.newFilestorePageParameter(prevPage, filter)).setEnabled(pageNumber > 1));
		add(new BookmarkablePageLink<Void>("nextPageBottom", FilestorePage.class,
				WicketUtils.newFilestorePageParameter(nextPage, filter)).setEnabled(hasMore));
		

		itemOk.add(FilestoreUI.getStatusIcon("statusOkIcon", FilestoreModel.Status.Available));
		itemPending.add(FilestoreUI.getStatusIcon("statusPendingIcon", FilestoreModel.Status.Upload_Pending));
		itemInprogress.add(FilestoreUI.getStatusIcon("statusInprogressIcon", FilestoreModel.Status.Upload_In_Progress));
		itemError.add(FilestoreUI.getStatusIcon("statusErrorIcon", FilestoreModel.Status.Error_Unknown));
		itemDeleted.add(FilestoreUI.getStatusIcon("statusDeletedIcon", FilestoreModel.Status.Deleted));
		
		itemOk.add(new Label("statusOkCount", String.valueOf(nOk)));
		itemPending.add(new Label("statusPendingCount", String.valueOf(nPending)));
		itemInprogress.add(new Label("statusInprogressCount", String.valueOf(nInprogress)));
		itemError.add(new Label("statusErrorCount", String.valueOf(nError)));
		itemDeleted.add(new Label("statusDeletedCount", String.valueOf(nDeleted)));
		
		add(itemOk);
		add(itemPending);
		add(itemInprogress);
		add(itemError);
		add(itemDeleted);
		
		add(new Label("spaceAvailable", String.format("%s / %s",
				FileUtils.byteCountToDisplaySize(nBytesUsed),
				FileUtils.byteCountToDisplaySize(nBytesAvailable))));
		
		BookmarkablePageLink<Void> helpLink = new BookmarkablePageLink<Void>("filestoreHelp", FilestoreUsage.class);
		helpLink.add(new Label("helpMessage", getString("gb.filestoreHelp")));
		add(helpLink);

	}
		
	protected enum SortBy {
		ok, pending, inprogress, error, deleted;
	}
	
	private static class SortableFilestoreProvider extends SortableDataProvider<FilestoreModel> {

		private static final long serialVersionUID = 1L;

		private List<FilestoreModel> list;

		protected SortableFilestoreProvider(List<FilestoreModel> list) {
			this.list = list;
		}

		@Override
		public int size() {
			if (list == null) {
				return 0;
			}
			return list.size();
		}

		@Override
		public IModel<FilestoreModel> model(FilestoreModel header) {
			return new Model<FilestoreModel>(header);
		}

		@Override
		public Iterator<FilestoreModel> iterator(int first, int count) {
			Collections.sort(list, new Comparator<FilestoreModel>() {
				@Override
				public int compare(FilestoreModel o1, FilestoreModel o2) {
					return o2.getChangedOn().compareTo(o1.getChangedOn());
				}
			});
			return list.subList(first, first + count).iterator();
		}
	}
	
}