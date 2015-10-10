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
package com.gitblit.wicket;

import org.apache.wicket.markup.html.basic.Label;

import com.gitblit.models.FilestoreModel;
import com.gitblit.models.FilestoreModel.Status;

/**
 * Common filestore ui methods and classes.
 *
 * @author Paul Martin
 *
 */
public class FilestoreUI {

	public static Label getStatusIcon(String wicketId, FilestoreModel item) {
		return getStatusIcon(wicketId, item.getStatus());
	}

	public static Label getStatusIcon(String wicketId, Status status) {
		Label label = new Label(wicketId);

		switch (status) {
		case Upload_Pending:
			WicketUtils.setCssClass(label, "fa fa-spinner fa-fw file-negative");
			break;
		case Upload_In_Progress:
			WicketUtils.setCssClass(label, "fa fa-spinner fa-spin fa-fw file-positive");
			break;
		case Available:
			WicketUtils.setCssClass(label, "fa fa-check fa-fw file-positive");
			break;
		case Deleted:
			WicketUtils.setCssClass(label, "fa fa-ban fa-fw file-negative");
			break;
		case Unavailable:
			WicketUtils.setCssClass(label, "fa fa-times fa-fw file-negative");
			break;
		default:
			WicketUtils.setCssClass(label, "fa fa-exclamation-triangle fa-fw file-negative");
		}
		WicketUtils.setHtmlTooltip(label, status.toString());

		return label;
	}
	
}
