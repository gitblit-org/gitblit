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
	
	/** 1024 (number of bytes in one kilobyte) */
	public static final int KB = 1024;

	/** 1024 {@link #KB} (number of bytes in one megabyte) */
	public static final int MB = 1024 * KB;

	/** 1024 {@link #MB} (number of bytes in one gigabyte) */
	public static final int GB = 1024 * MB;

	/**
	 * Returns an int from a string representation of a file size.
	 * e.g. 50m = 50 megabytes
	 * 
	 * @param aString
	 * @param defaultValue
	 * @return an int value or the defaultValue if aString can not be parsed
	 */
	public static int convertSizeToInt(String aString, int defaultValue) {
		return (int) convertSizeToLong(aString, defaultValue);
	}
	
	/**
	 * Returns a long from a string representation of a file size.
	 * e.g. 50m = 50 megabytes
	 * 
	 * @param aString
	 * @param defaultValue
	 * @return a long value or the defaultValue if aString can not be parsed
	 */
	public static long convertSizeToLong(String aString, long defaultValue) {
		// trim string and remove all spaces 
		aString = aString.toLowerCase().trim();
		StringBuilder sb = new StringBuilder();
		for (String a : aString.split(" ")) {
			sb.append(a);
		}
		aString = sb.toString();
		
		// identify value and unit
		int idx = 0;
		int len = aString.length();
		while (Character.isDigit(aString.charAt(idx))) {
			idx++;
			if (idx == len) {
				break;
			}
		}
		long value = 0;
		String unit = null;
		try {
			value = Long.parseLong(aString.substring(0, idx));
			unit = aString.substring(idx);
		} catch (Exception e) {
			return defaultValue;
		}
		if (unit.equals("g") || unit.equals("gb")) {
			return value * GB;
		} else if (unit.equals("m") || unit.equals("mb")) {
			return value * MB;
		} else if (unit.equals("k") || unit.equals("kb")) {
			return value * KB;
		}
		return defaultValue;
	}

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
		if (path.getAbsolutePath().startsWith(basePath.getAbsolutePath())) {
			// absolute base-path match
			return StringUtils.getRelativePath(basePath.getAbsolutePath(), path.getAbsolutePath());
		} else if (exactPath.getPath().startsWith(exactBase.getPath())) {
			// canonical base-path match
			return StringUtils.getRelativePath(exactBase.getPath(), exactPath.getPath());
		} else if (exactPath.getPath().startsWith(basePath.getAbsolutePath())) {
			// mixed path match
			return StringUtils.getRelativePath(basePath.getAbsolutePath(), exactPath.getPath());
		} else if (path.getAbsolutePath().startsWith(exactBase.getPath())) {
			// mixed path match
			return StringUtils.getRelativePath(exactBase.getPath(), path.getAbsolutePath());
		}
		// no relative relationship
		return null;
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
