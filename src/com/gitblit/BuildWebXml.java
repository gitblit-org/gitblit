/*
 * Copyright 2011 gitblit.com.
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class BuildWebXml {
	private static final String PARAMS = "<!-- PARAMS -->";

	private static final String[] STRIP_TOKENS = { "<!-- STRIP", "STRIP -->" };

	private static final String COMMENT_PATTERN = "\n\t<!-- {0} -->";

	private static final String PARAM_PATTERN = "\n\t<context-param>\n\t\t<param-name>{0}</param-name>\n\t\t<param-value>{1}</param-value>\n\t</context-param>\n";

	public static void main(String[] args) throws Exception {
		// Read the current Gitblit properties
		BufferedReader propertiesReader = new BufferedReader(new FileReader(new File(
				"distrib/gitblit.properties")));

		Vector<Setting> settings = new Vector<Setting>();
		List<String> comments = new ArrayList<String>();
		String line = null;
		while ((line = propertiesReader.readLine()) != null) {
			if (line.length() == 0) {
				comments.clear();
			} else {
				if (line.charAt(0) == '#') {
					if (line.length() > 1) {
						comments.add(line.substring(1).trim());
					}
				} else {
					String[] kvp = line.split("=", 2);
					String key = kvp[0].trim();
					if (!skipKey(key)) {
						Setting s = new Setting(key, kvp[1].trim(), comments);
						settings.add(s);
					}
					comments.clear();
				}
			}
		}
		propertiesReader.close();

		StringBuilder parameters = new StringBuilder();

		for (Setting setting : settings) {
			for (String comment : setting.comments) {
				parameters.append(MessageFormat.format(COMMENT_PATTERN, comment));
			}
			parameters.append(MessageFormat.format(PARAM_PATTERN, setting.name, setting.value));
		}

		// Read the prototype web.xml file
		File webxml = new File("src/WEB-INF/web.xml");
		char[] buffer = new char[(int) webxml.length()];
		FileReader webxmlReader = new FileReader(webxml);
		webxmlReader.read(buffer);
		webxmlReader.close();
		String webXmlContent = new String(buffer);

		// Insert the Gitblit properties into the prototype web.xml
		for (String stripToken : STRIP_TOKENS) {
			webXmlContent = webXmlContent.replace(stripToken, "");
		}
		int idx = webXmlContent.indexOf(PARAMS);
		StringBuilder sb = new StringBuilder();
		sb.append(webXmlContent.substring(0, idx));
		sb.append(parameters.toString());
		sb.append(webXmlContent.substring(idx + PARAMS.length()));

		// Save the merged web.xml to the war build folder
		FileOutputStream os = new FileOutputStream(new File("war/WEB-INF/web.xml"), false);
		os.write(sb.toString().getBytes());
		os.close();
	}

	private static boolean skipKey(String key) {
		return key.startsWith(Keys.server._ROOT);
	}

	private static class Setting {
		final String name;
		final String value;
		final List<String> comments;

		Setting(String name, String value, List<String> comments) {
			this.name = name;
			this.value = value;
			this.comments = new ArrayList<String>(comments);
		}
	}
}
