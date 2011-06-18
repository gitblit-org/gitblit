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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class BuildWebXml {
	private static final String PARAMS = "<!-- PARAMS -->";
	
	private static final String [] STRIP_TOKENS = { "<!-- STRIP", "STRIP -->" };

	private static final String PARAM_PATTERN = "\n\t<context-param>\n\t\t<param-name>{0}</param-name>\n\t\t<param-value>{1}</param-value>\n\t</context-param>\n";

	public static void main(String[] args) throws Exception {
		// Read the current Gitblit properties
		// TODO extract the comments and inject them into web.xml too
		FileInputStream fis = new FileInputStream(new File("distrib/gitblit.properties"));
		Properties fileSettings = new Properties();		
		fileSettings.load(fis);
		fis.close();
		List<String> keys = new ArrayList<String>(fileSettings.stringPropertyNames());
		Collections.sort(keys);
		
		StringBuilder parameters = new StringBuilder();
		for (String key : keys) {
			if (!skipKey(key)) {
				String value = fileSettings.getProperty(key);
				parameters.append(MessageFormat.format(PARAM_PATTERN, key, value));
			}
		}

		// Read the prototype web.xml file
		File webxml = new File("src/WEB-INF/web.xml");
		char [] buffer = new char[(int) webxml.length()];
		FileReader reader = new FileReader(webxml);
		reader.read(buffer);
		reader.close();
		String webXmlContent = new String(buffer);

		// Insert the Gitblit properties into the prototype web.xml
		for (String stripToken:STRIP_TOKENS) {
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
}
