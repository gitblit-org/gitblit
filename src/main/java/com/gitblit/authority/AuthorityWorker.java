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
package com.gitblit.authority;

import java.awt.Component;
import java.awt.Cursor;
import java.io.IOException;

import javax.swing.SwingWorker;

public abstract class AuthorityWorker extends SwingWorker<Boolean, Void> {

	private final Component parent;

	public AuthorityWorker(Component parent) {
		this.parent = parent;
		parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
	}

	@Override
	protected Boolean doInBackground() throws IOException {
		return doRequest();
	}

	@Override
	protected void done() {
		parent.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
		try {
			Boolean success = get();
			if (success) {
				onSuccess();
			} else {
				onFailure();
			}
		} catch (Throwable t) {
			Utils.showException(parent, t);
		}
	}

	protected abstract Boolean doRequest() throws IOException;

	protected abstract void onSuccess();

	protected void onFailure() {
	}
}
