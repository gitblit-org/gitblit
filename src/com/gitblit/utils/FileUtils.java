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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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
	 * Writes the string content to the file.
	 * 
	 * @param file
	 * @param content
	 */
	public static void writeContent(File file, String content) {
		try {
			OutputStreamWriter os = new OutputStreamWriter(new FileOutputStream(file),
					Charset.forName("UTF-8"));
			BufferedWriter writer = new BufferedWriter(os);
			writer.append(content);
			writer.close();
		} catch (Throwable t) {
			System.err.println("Failed to write content of " + file.getAbsolutePath());
			t.printStackTrace();
		}
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
			if (file.isFile()) {
				length += file.length();
			} else {
				length += folderSize(file);
			}
		}
		return length;
	}

	/**
	 * Copies a file or folder (recursively) to a destination folder.
	 * 
	 * @param destinationFolder
	 * @param filesOrFolders
	 * @return
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static void copy(File destinationFolder, File... filesOrFolders)
			throws FileNotFoundException, IOException {
		destinationFolder.mkdirs();
		for (File file : filesOrFolders) {
			if (file.isDirectory()) {
				copy(new File(destinationFolder, file.getName()), file.listFiles());
			} else {
				File dFile = new File(destinationFolder, file.getName());
				BufferedInputStream bufin = null;
				FileOutputStream fos = null;
				try {
					bufin = new BufferedInputStream(new FileInputStream(file));
					fos = new FileOutputStream(dFile);
					int len = 8196;
					byte[] buff = new byte[len];
					int n = 0;
					while ((n = bufin.read(buff, 0, len)) != -1) {
						fos.write(buff, 0, n);
					}
				} finally {
					try {
						bufin.close();
					} catch (Throwable t) {
					}
					try {
						fos.close();
					} catch (Throwable t) {
					}
				}
				dFile.setLastModified(file.lastModified());
			}
		}
	}
	
	/**
	 * Determine the relative path between two files.  Takes into account
	 * canonical paths, if possible.
	 * 
	 * @param basePath
	 * @param path
	 * @return a relative path from basePath to path
	 */
	public static String getRelativePath(File basePath, File path) {
		File exactBase = getExactFile(basePath);
		File exactPath = getExactFile(path);
		return StringUtils.getRelativePath(exactBase.getPath(), exactPath.getPath());
	}
	
	/**
	 * Returns the exact path for a file. This path will be the canonical path
	 * unless an exception is thrown in which case it will be the absolute path.
	 * 
	 * @param path
	 * @return the exact file
	 */
	public static File getExactFile(File path) {
		try {
			return path.getCanonicalFile();
		} catch (IOException e) {
			return path.getAbsoluteFile();
		}
	}
}
