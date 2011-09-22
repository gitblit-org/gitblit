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
import java.io.FileFilter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.gitblit.build.Build;

/**
 * Launch helper class that adds all jars found in the local "lib" & "ext"
 * folders and then calls the application main. Using this technique we do not
 * have to specify a classpath and we can dynamically add jars to the
 * distribution.
 * 
 * This class also downloads all runtime dependencies, if they are not found.
 * 
 * @author James Moger
 * 
 */
public class Launcher {

	public static final boolean DEBUG = false;

	/**
	 * Parameters of the method to add an URL to the System classes.
	 */
	private static final Class<?>[] PARAMETERS = new Class[] { URL.class };

	public static void main(String[] args) {
		if (DEBUG) {
			System.out.println("jcp=" + System.getProperty("java.class.path"));
			ProtectionDomain protectionDomain = Launcher.class.getProtectionDomain();
			System.out.println("launcher="
					+ protectionDomain.getCodeSource().getLocation().toExternalForm());
		}

		// download all runtime dependencies
		Build.runtime();

		// Load the JARs in the lib and ext folder
		String[] folders = new String[] { "lib", "ext" };
		List<File> jars = new ArrayList<File>();
		for (String folder : folders) {
			if (folder == null) {
				continue;
			}
			File libFolder = new File(folder);
			if (!libFolder.exists()) {
				continue;
			}
			List<File> found = findJars(libFolder.getAbsoluteFile());
			jars.addAll(found);
		}
		// sort the jars by name and then reverse the order so the newer version
		// of the library gets loaded in the event that this is an upgrade
		Collections.sort(jars);
		Collections.reverse(jars);

		if (jars.size() == 0) {
			for (String folder : folders) {
				File libFolder = new File(folder);
				// this is a test of adding a comment
				// more really interesting things
				System.err.println("Failed to find any really cool JARs in " + libFolder.getPath());
			}
			System.exit(-1);
		} else {
			for (File jar : jars) {
				try {
					jar.canRead();
					addJarFile(jar);
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
		}

		// Start Server
		GitBlitServer.main(args);
	}

	public static List<File> findJars(File folder) {
		List<File> jars = new ArrayList<File>();
		if (folder.exists()) {
			File[] libs = folder.listFiles(new FileFilter() {
				@Override
				public boolean accept(File file) {
					return !file.isDirectory() && file.getName().toLowerCase().endsWith(".jar");
				}
			});
			if (libs != null && libs.length > 0) {
				jars.addAll(Arrays.asList(libs));
				if (DEBUG) {
					for (File jar : jars) {
						System.out.println("found " + jar);
					}
				}
			}
		}

		return jars;
	}

	/**
	 * Adds a file to the classpath
	 * 
	 * @param f
	 *            the file to be added
	 * @throws IOException
	 */
	public static void addJarFile(File f) throws IOException {
		if (f.getName().indexOf("-sources") > -1 || f.getName().indexOf("-javadoc") > -1) {
			// don't add source or javadoc jars to runtime classpath
			return;
		}
		URL u = f.toURI().toURL();
		if (DEBUG) {
			System.out.println("load=" + u.toExternalForm());
		}
		URLClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
		Class<?> sysclass = URLClassLoader.class;
		try {
			Method method = sysclass.getDeclaredMethod("addURL", PARAMETERS);
			method.setAccessible(true);
			method.invoke(sysloader, new Object[] { u });
		} catch (Throwable t) {
			throw new IOException(MessageFormat.format(
					"Error, could not add {0} to system classloader", f.getPath()), t);
		}
	}
}
