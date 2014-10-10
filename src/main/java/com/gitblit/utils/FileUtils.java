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
import java.nio.file.Path;
import java.nio.file.Paths;

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
	 * Returns the byte [] content of the specified file.
	 *
	 * @param file
	 * @return the byte content of the file
	 */
	public static byte [] readContent(File file) {
		byte [] buffer = new byte[(int) file.length()];
		BufferedInputStream is = null;
		try {
			is = new BufferedInputStream(new FileInputStream(file));
			is.read(buffer,  0,  buffer.length);
		} catch (Throwable t) {
			System.err.println("Failed to read byte content of " + file.getAbsolutePath());
			t.printStackTrace();
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException ioe) {
					System.err.println("Failed to close file " + file.getAbsolutePath());
					ioe.printStackTrace();
				}
			}
		}
		return buffer;
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
		InputStreamReader is = null;
		try {
			is = new InputStreamReader(new FileInputStream(file), Charset.forName("UTF-8"));
			BufferedReader reader = new BufferedReader(is);
			String line = null;
			while ((line = reader.readLine()) != null) {
				sb.append(line);
				if (lineEnding != null) {
					sb.append(lineEnding);
				}
			}
		} catch (Throwable t) {
			System.err.println("Failed to read content of " + file.getAbsolutePath());
			t.printStackTrace();
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException ioe) {
					System.err.println("Failed to close file " + file.getAbsolutePath());
					ioe.printStackTrace();
				}
			}
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
		OutputStreamWriter os = null;
		try {
			os = new OutputStreamWriter(new FileOutputStream(file), Charset.forName("UTF-8"));
			BufferedWriter writer = new BufferedWriter(os);
			writer.append(content);
			writer.flush();
		} catch (Throwable t) {
			System.err.println("Failed to write content of " + file.getAbsolutePath());
			t.printStackTrace();
		} finally {
			if (os != null) {
				try {
					os.close();
				} catch (IOException ioe) {
					System.err.println("Failed to close file " + file.getAbsolutePath());
					ioe.printStackTrace();
				}
			}
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
		if (directory.isDirectory()) {
			long length = 0;
			for (File file : directory.listFiles()) {
				length += folderSize(file);
			}
			return length;
		} else if (directory.isFile()) {
			return directory.length();
		}
		return 0;
	}

	/**
	 * Delete a file or recursively delete a folder.
	 *
	 * @param fileOrFolder
	 * @return true, if successful
	 */
	public static boolean delete(File fileOrFolder) {
		boolean success = false;
		if (fileOrFolder.isDirectory()) {
			File [] files = fileOrFolder.listFiles();
			if (files != null) {
				for (File file : files) {
					if (file.isDirectory()) {
						success |= delete(file);
					} else {
						success |= file.delete();
					}
				}
			}
		}
		success |= fileOrFolder.delete();
		return success;
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
						if (bufin != null) bufin.close();
					} catch (Throwable t) {
					}
					try {
						if (fos != null) fos.close();
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
		Path exactBase = Paths.get(getExactFile(basePath).toURI());
		Path exactPath = Paths.get(getExactFile(path).toURI());
		if (exactPath.startsWith(exactBase)) {
			return exactBase.relativize(exactPath).toString();
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

	public static File resolveParameter(String parameter, File aFolder, String path) {
		if (aFolder == null) {
			// strip any parameter reference
			path = path.replace(parameter, "").trim();
			if (path.length() > 0 && path.charAt(0) == '/') {
				// strip leading /
				path = path.substring(1);
			}
		} else if (path.contains(parameter)) {
			// replace parameter with path
			path = path.replace(parameter, aFolder.getAbsolutePath());
		}
		return new File(path);
	}
}
