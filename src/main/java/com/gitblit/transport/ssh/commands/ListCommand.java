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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.kohsuke.args4j.Option;

/**
 * Parent class of a list command.
 * 
 * @author James Moger
 *
 * @param <T>
 */
public abstract class ListCommand<T> extends SshCommand {

	@Option(name = "--verbose", aliases = { "-v" }, usage = "verbose")
	protected boolean verbose;

	@Option(name = "--tabbed", aliases = { "-t" }, usage = "as tabbed output")
	protected boolean tabbed;

	private DateFormat df;

	protected abstract List<T> getItems() throws UnloggedFailure;
	
	@Override
	public void run() throws UnloggedFailure {
		List<T> list = getItems();
		if (tabbed) {
			asTabbed(list);
		} else {
			asTable(list);
		}
	}

	protected abstract void asTable(List<T> list);
	
	protected abstract void asTabbed(List<T> list);
	
	protected void outTabbed(Object... values) {
		StringBuilder pattern = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			pattern.append("%s\t");
		}
		pattern.setLength(pattern.length() - 1);
		stdout.println(String.format(pattern.toString(), values));
	}
	
	protected String formatDate(Date date) {
		if (df == null) {
			df = new SimpleDateFormat("yyyy-MM-dd");
		}
		return df.format(date);
	}
}