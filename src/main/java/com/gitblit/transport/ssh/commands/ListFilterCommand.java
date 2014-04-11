/*
 * Copyright 2014 gitblit.com.
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
package com.gitblit.transport.ssh.commands;

import java.util.ArrayList;
import java.util.List;

import org.kohsuke.args4j.Argument;

import com.gitblit.utils.StringUtils;

/**
 * List command that accepts a filter parameter.
 *
 * @author James Moger
 *
 * @param <T>
 */
public abstract class ListFilterCommand<T> extends ListCommand<T> {

	@Argument(index = 0, metaVar = "FILTER", usage = "filter expression")
	private String filter;

	protected abstract boolean matches(String filter, T t);

	@Override
	public void run() throws UnloggedFailure {
		validateOutputFormat();

		List<T> list = getItems();
		List<T> filtered;
		if (StringUtils.isEmpty(filter)) {
			// no filter
			filtered = list;
		} else {
			// filter the list
			filtered = new ArrayList<T>();
			for (T t : list) {
				if (matches(filter, t)) {
					filtered.add(t);
				}
			}
		}

		if (tabbed) {
			asTabbed(filtered);
		} else if (json) {
			asJSON(filtered);
		} else {
			asTable(filtered);
		}
	}
}