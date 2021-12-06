/*
 * Copyright 2021 gitblit.com, Ingo Lafrenz
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

package com.gitblit;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Simple class with the only purpose to save the realm file (users.conf) in
 * a fast efficient manner. The JGit Config classes used previously caused
 * a massive CPU hog if the users file got bigger than about 30000 lines.
 *
 * @author Ingo Lafrenz
 *
 */
public class StoredUserConfig {

	private final File realmFileCopy;
	private SortedMap<String, Section> sections = new TreeMap<>();

	public StoredUserConfig(File realmFileCopy) {
		this.realmFileCopy = realmFileCopy;
	}

	public void setString(final String section, final String subsection, String name, String value) {
		String key = generateKey(section, subsection);
		Section s = sections.get(key);
		if (s == null) {
			s = new Section(section, subsection);
			sections.put(key, s);
		}
		s.addEntry(name, value);
	}

	public void setBoolean(String section, String subsection, String name, boolean value) {
		setString(section, subsection, name, String.valueOf(value));
	}

	public void setStringList(String section, String subsection, String name, List<String> list) {
		for (String value : list) {
			setString(section, subsection, name, value);
		}
	}

	public void save() throws IOException {
		try (FileWriter fileWriter = new FileWriter(realmFileCopy);
				PrintWriter printWriter = new PrintWriter(fileWriter);) {
			for (Map.Entry<String,Section> entry : sections.entrySet()) {
				writeSection(printWriter, entry.getKey(), entry.getValue());
			}
		}
	}

	private static void writeSection(PrintWriter printWriter, String key, Section section) {
		printWriter.printf("[%s \"%s\"]\n", section.getName(), section.getSubSection());
		for (Entry entry : section.getEntries().values()) {
			writeEntry(printWriter, entry.getKey(), entry.getValue());
		}
	}

	private static void writeEntry(PrintWriter printWriter, String key, String value) {
		printWriter.printf("\t%s = %s\n", key, escape(value));
	}

	private static String escape(String value) {
		String fixedValue = '#' == value.charAt(0) ? "\"" + value + "\"" : value;
		fixedValue = fixedValue.replace("\\", "\\\\");
		return fixedValue;
	}

	private static String generateKey(String key, String subKey) {
		return "k:" + key + "s:" + subKey;
	}

	private static class Section {
		private final String name;
		private final String subSection;
		private final SortedMap<String, Entry> entries = new TreeMap<>();

		public Section(String name, String subSection) {
			this.name = name;
			this.subSection = subSection;
		}

		public void addEntry(final String key, final String value) {
			entries.put(generateKey(key, value), new Entry(key, value));
		}

		public String getName() {
			return name;
		}

		public String getSubSection() {
			return subSection;
		}

		public SortedMap<String, Entry> getEntries() {
			return entries;
		}

		@Override
		public int hashCode() {
			return Objects.hash(name, subSection);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Section other = (Section) obj;
			return Objects.equals(name, other.name) && Objects.equals(subSection, other.subSection);
		}

		@Override
		public String toString() {
			return String.format("Section [name=%s, subSection=%s]", name, subSection);
		}

	}

	private static class Entry {
		private final String key;
		private final String value;

		public Entry(String key, String value) {
			this.key = key;
			this.value = value;
		}

		public String getKey() {
			return key;
		}

		public String getValue() {
			return value;
		}

		@Override
		public int hashCode() {
			return Objects.hash(key, value);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Entry other = (Entry) obj;
			return Objects.equals(key, other.key) && Objects.equals(value, other.value);
		}

		@Override
		public String toString() {
			return String.format("Entry [key=%s, value=%s]", key, value);
		}

	}

}
