/*
 * Copyright 2013 gitblit.com.
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

import com.sun.jna.Library;
import com.sun.jna.Native;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collection of static methods to access native OS library functionality.
 *
 * @author Florian Zschocke
 */
public class JnaUtils {
	public static final int S_IFMT =   0170000;
	public static final int S_IFIFO =  0010000;
	public static final int S_IFCHR =  0020000;
	public static final int S_IFDIR =  0040000;
	public static final int S_IFBLK =  0060000;
	public static final int S_IFREG =  0100000;
	public static final int S_IFLNK =  0120000;
	public static final int S_IFSOCK = 0140000;

	public static final int S_ISUID =  0004000;
	public static final int S_ISGID =  0002000;
	public static final int S_ISVTX =  0001000;

	public static final int S_IRWXU =  0000700;
	public static final int S_IRUSR =  0000400;
	public static final int S_IWUSR =  0000200;
	public static final int S_IXUSR =  0000100;
	public static final int S_IRWXG =  0000070;
	public static final int S_IRGRP =  0000040;
	public static final int S_IWGRP =  0000020;
	public static final int S_IXGRP =  0000010;
	public static final int S_IRWXO =  0000007;
	public static final int S_IROTH =  0000004;
	public static final int S_IWOTH =  0000002;
	public static final int S_IXOTH =  0000001;


	private static final Logger LOGGER = LoggerFactory.getLogger(JGitUtils.class);

	private static UnixCLibrary unixlibc = null;


	public static boolean isWindows()
	{
		return System.getProperty("os.name").toLowerCase().startsWith("windows");
	}


	private interface UnixCLibrary extends Library {
		public int chmod(String path, int mode);
	}


	public static int setFilemode(File path, int mode)
	{
		return setFilemode(path.getAbsolutePath(), mode);
	}

	public static int setFilemode(String path, int mode)
	{
		if (isWindows()) {
			throw new UnsupportedOperationException("The method JnaUtils.getFilemode is not supported under Windows.");
		}

		return getUnixCLibrary().chmod(path, mode);
	}



	public static int getFilemode(File path)
	{
		return getFilemode(path.getAbsolutePath());
	}

	public static int getFilemode(String path)
	{
		if (isWindows()) {
			throw new UnsupportedOperationException("The method JnaUtils.getFilemode is not supported under Windows.");
		}


		int mode = 0;

		// Use a Runtime, because implementing stat() via JNA is just too much trouble.
		String lsLine = runProcessLs(path);
		if (lsLine == null) {
			LOGGER.debug("Could not get file information for path " + path);
			return -1;
		}

		Pattern p = Pattern.compile("^(([-bcdlsp])([-r][-w][-xSs])([-r][-w][-xSs])([-r][-w][-xTt])) ");
		Matcher m = p.matcher(lsLine);
		if ( !m.lookingAt() ) {
			LOGGER.debug("Could not parse valid file mode information for path " + path);
			return -1;
		}

		// Parse mode string to mode bits
		String group = m.group(2);
		switch (group.charAt(0)) {
		case 'p' :
			mode |= 0010000; break;
		case 'c':
			mode |= 0020000; break;
		case 'd':
			mode |= 0040000; break;
		case 'b':
			mode |= 0060000; break;
		case '-':
			mode |= 0100000; break;
		case 'l':
			mode |= 0120000; break;
		case 's':
			mode |= 0140000; break;
		}

		for ( int i = 0; i < 3; i++) {
			group = m.group(3 + i);
			switch (group.charAt(0)) {
			case 'r':
				mode |= (0400 >> i*3); break;
			case '-':
				break;
			}

			switch (group.charAt(1)) {
			case 'w':
				mode |= (0200 >> i*3); break;
			case '-':
				break;
			}

			switch (group.charAt(2)) {
			case 'x':
				mode |= (0100 >> i*3); break;
			case 'S':
				mode |= (04000 >> i); break;
			case 's':
				mode |= (0100 >> i*3);
				mode |= (04000 >> i); break;
			case 'T':
				mode |= 01000; break;
			case 't':
				mode |= (0100 >> i*3);
				mode |= 01000; break;
			case '-':
				break;
			}
		}

		return mode;
	}


	private static String runProcessLs(String path)
	{
		String cmd = "ls -ldO " + path;
		Runtime rt = Runtime.getRuntime();
		Process pr = null;
		InputStreamReader ir = null;
		BufferedReader br = null;
		String output = null;

		try {
			pr = rt.exec(cmd);
			ir = new InputStreamReader(pr.getInputStream());
			br = new BufferedReader(ir);

			output = br.readLine();

			while (br.readLine() != null) ; // Swallow remaining output
		}
		catch (IOException e) {
			LOGGER.debug("Exception while running unix command '" + cmd + "': " + e);
		}
		finally {
			if (pr != null) try { pr.waitFor();	} catch (Exception ignored) {}

			if (br != null) try { br.close(); } catch (Exception ignored) {}
			if (ir != null) try { ir.close(); } catch (Exception ignored) {}

			if (pr != null) try { pr.getOutputStream().close();	} catch (Exception ignored) {}
			if (pr != null) try { pr.getInputStream().close();	} catch (Exception ignored) {}
			if (pr != null) try { pr.getErrorStream().close();	} catch (Exception ignored) {}
		}

		return output;
	}


	private static UnixCLibrary getUnixCLibrary()
	{
		if (unixlibc == null) {
			unixlibc = (UnixCLibrary) Native.loadLibrary("c", UnixCLibrary.class);
			if (unixlibc == null) throw new RuntimeException("Could not initialize native C library.");
		}
		return unixlibc;
	}

}
