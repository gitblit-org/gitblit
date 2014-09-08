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
package com.gitblit.models;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Encapsulates an email notification.
 *
 * @author James Moger
 *
 */
public class Mailing {

	public enum Type {
		plain, html
	}

	public final Type type;
	public final Set<String> toAddresses;
	public final Set<String> ccAddresses;
	public final List<File> attachments;

	public String from;
	public String subject;
	public String content;
	public String id;

	public static Mailing newHtml() {
		return new Mailing(Type.html);
	}

	public static Mailing newPlain() {
		return new Mailing(Type.plain);
	}

	private Mailing(Type type) {
		this.type = type;
		this.toAddresses = new TreeSet<String>();
		this.ccAddresses = new TreeSet<String>();
		this.attachments = new ArrayList<File>();
	}

	public boolean hasRecipients() {
		return (toAddresses.size() + ccAddresses.size()) > 0;
	}

	public void setRecipients(String... addrs) {
		setRecipients(Arrays.asList(addrs));
	}

	public void setRecipients(Collection<String> addrs) {
		toAddresses.clear();
		for (String addr : addrs) {
			toAddresses.add(addr.toLowerCase());
		}
		cleanup();
	}

	public boolean hasCCs() {
		return ccAddresses.size() > 0;
	}

	public void setCCs(String... addrs) {
		setCCs(Arrays.asList(addrs));
	}

	public void setCCs(Collection<String> addrs) {
		ccAddresses.clear();
		for (String addr : addrs) {
			ccAddresses.add(addr.toLowerCase());
		}
		cleanup();
	}

	private void cleanup() {
		ccAddresses.removeAll(toAddresses);
	}

	public boolean hasAttachments() {
		return attachments.size() > 0;
	}

	public void addAttachment(File file) {
		attachments.add(file);
	}

	@Override
	public String toString() {
		return subject + "\n\n" + content;
	}
}