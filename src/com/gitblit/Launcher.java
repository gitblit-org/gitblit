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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Launch helper class that adds all jars found in the local "lib" folder and
 * then calls the application main. Using this technique we do not have to
 * specify a classpath and we can dynamically add jars to the distribution.
 * 
 */
public class Launcher {

	public final static boolean debug = false;

	public static void main(String[] args) {
		if (debug)
			System.out.println("jcp=" + System.getProperty("java.class.path"));

		ProtectionDomain protectionDomain = Launcher.class.getProtectionDomain();
		final String launchJar = protectionDomain.getCodeSource().getLocation().toExternalForm();
		if (debug)
			System.out.println("launcher=" + launchJar);

		Build.runtime();

		// Load the JARs in the lib and ext folder
		String[] folders = new String[] { "lib", "ext" };
		List<File> jars = new ArrayList<File>();
		for (String folder : folders) {
			if (folder == null)
				continue;
			File libFolder = new File(folder);
			if (!libFolder.exists())
				continue;
			try {
				libFolder = libFolder.getCanonicalFile();
			} catch (IOException iox) {
			}
			jars.addAll(findJars(libFolder));
		}

		if (jars.size() == 0) {
			for (String folder : folders) {
				File libFolder = new File(folder);
				System.err.println("Failed to find any JARs in " + libFolder.getPath());
			}
			System.exit(-1);
		} else {
			for (File jar : jars) {
				try {
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
				if (debug) {
					for (File jar : jars)
						System.out.println("found " + jar);
				}
			}
		}
		return jars;
	}

	/**
	 * Parameters of the method to add an URL to the System classes.
	 */
	private static final Class<?>[] parameters = new Class[] { URL.class };

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
		if (debug)
			System.out.println("load=" + u.toExternalForm());
		URLClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
		Class<?> sysclass = URLClassLoader.class;
		try {
			Method method = sysclass.getDeclaredMethod("addURL", parameters);
			method.setAccessible(true);
			method.invoke(sysloader, new Object[] { u });
		} catch (Throwable t) {
			throw new IOException("Error, could not add " + f.getPath() + " to system classloader", t);
		}
	}
}
