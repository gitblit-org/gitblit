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
package com.gitblit.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

/**
 * Common file utilities.
 * 
 * @author James Moger
 * 
 */
public class FileUtils {

	/**
	 * Returns the string content of the specified file.
	 * 
	 * @param file
	 * @param lineEnding
	 * @return the string content of the file
	 */
	public static String readContent(File file, String lineEnding) {
		StringBuilder sb = new StringBuilder();
		try {
			InputStreamReader is = new InputStreamReader(new FileInputStream(file),
					Charset.forName("UTF-8"));
			BufferedReader reader = new BufferedReader(is);
			String line = null;
			while ((line = reader.readLine()) != null) {
				sb.append(line);
				if (lineEnding != null) {
					sb.append(lineEnding);
				}
			}
			reader.close();
		} catch (Throwable t) {
			System.err.println("Failed to read content of " + file.getAbsolutePath());
			t.printStackTrace();
		}
		return sb.toString();
	}

	/**
	 * Recursively traverses a folder and its subfolders to calculate the total
	 * size in bytes.
	 * 
	 * @param directory
	 * @return folder size in bytes
	 */
	public static long folderSize(File directory) {
		if (directory == null || !directory.exists()) {
			return -1;
		}
		if (directory.isFile()) {
			return directory.length();
		}
		long length = 0;
		for (File file : directory.listFiles()) {
			if (file.isFile())
				length += file.length();
			else
				length += folderSize(file);
		}
		return length;
	}
}
