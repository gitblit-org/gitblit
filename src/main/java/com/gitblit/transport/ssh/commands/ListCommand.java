package com.gitblit.transport.ssh.commands;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import com.gitblit.utils.StringUtils;

public abstract class ListCommand<T> extends SshCommand {

	@Option(name = "--verbose", aliases = { "-v" }, usage = "verbose")
	protected boolean verbose;

	@Option(name = "--tabbed", aliases = { "-t" }, usage = "as tabbed output")
	private boolean tabbed;

	@Argument(index = 0, metaVar = "REGEX", usage = "regex filter expression")
	protected String regexFilter;
	
	private DateFormat df;

	protected abstract List<T> getItems();
	
	protected abstract boolean matches(T t);
	
	@Override
	public void run() {
		List<T> list = getItems();
		List<T> filtered;
		if (StringUtils.isEmpty(regexFilter)) {
			// no regex filter 
			filtered = list;
		} else {
			// regex filter the list
			filtered = new ArrayList<T>();
			for (T t : list) {
				if (matches(t)) {
					filtered.add(t);
				}
			}
		}

		if (tabbed) {
			asTabbed(filtered);
		} else {
			asTable(filtered);
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