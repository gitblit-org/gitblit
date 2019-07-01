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

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.SplashScreen;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarFile;

import com.gitblit.client.Translation;

/**
 * Loads jar files to the system classpath dynamically at runtime.
 */
public class LibraryLoader {

	public static final boolean DEBUG = false;

	/**
	 * Parameters of the method to add an URL to the System classes.
	 * (This is specific to Java 7 & 8 runtimes only.)
	 */
	private static final Class<?>[] PARAMETERS = new Class[] { URL.class };

	public static int loadLibraries(String libraryPath, boolean showingSplash, String startText) {
		final SplashScreen splash = (showingSplash ? SplashScreen.getSplashScreen() : null);

		File libFolder = new File(libraryPath);
		List<File> jars = findJars(libFolder.getAbsoluteFile());

		// sort the jars by name and then reverse the order so the newer version
		// of the library gets loaded in the event that this is an upgrade
		Collections.sort(jars);
		Collections.reverse(jars);
		int numberOfLoadedJars = 0;
		for (File jar : jars) {
			try {
				if (showingSplash) {
					updateSplash(splash, Translation.get("gb.loading") + " " + jar.getName() + "...");
				}
				numberOfLoadedJars += addJarFile(jar);
			} catch (IOException e) {
				System.err.println("Failed to load " + jar);
			}
		}

		if (showingSplash) {
			updateSplash(splash, Translation.get("gb.starting") + " " + startText + "...");
		}
		
		return numberOfLoadedJars;
	}

	private static void updateSplash(final SplashScreen splash, final String string) {
		if (splash == null) {
			return;
		}
		try {
			EventQueue.invokeAndWait(new Runnable() {
				@Override
				public void run() {
					Graphics2D g = splash.createGraphics();
					if (g != null) {
						// Splash is 320x120
						FontMetrics fm = g.getFontMetrics();

						// paint startup status
						g.setColor(Color.darkGray);
						int h = fm.getHeight() + fm.getMaxDescent();
						int x = 5;
						int y = 115;
						int w = 320 - 2 * x;
						g.fillRect(x, y - h, w, h);
						g.setColor(Color.lightGray);
						g.drawRect(x, y - h, w, h);
						g.setColor(Color.WHITE);
						int xw = fm.stringWidth(string);
						g.drawString(string, x + ((w - xw) / 2), y - 5);

						// paint version
						String ver = "v" + Constants.getVersion();
						int vw = g.getFontMetrics().stringWidth(ver);
						g.drawString(ver, 320 - vw - 5, 34);
						g.dispose();
						splash.update();
					}
				}
			});
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private static List<File> findJars(File folder) {
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
	 *
	 * @return 1 if the file was added successfully,
	 *         0 otherwise.
	 *
	 * @throws IOException
	 */
	private static int addJarFile(File f) throws IOException {
		if (f.getName().indexOf("-sources") > -1 || f.getName().indexOf("-javadoc") > -1) {
			// don't add source or javadoc jars to runtime classpath
			return 0;
		}
		if (DEBUG) {
			System.out.println("load=" + f.toURI().toURL().toExternalForm());
		}
		String javaVersion = System.getProperty("java.version");
		if (javaVersion.startsWith("1.7.") || javaVersion.startsWith("1.8.")) {
			//for Java 7 & 8, use the URLClassLoader method
			URLClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
			Class<?> sysclass = URLClassLoader.class;
			try {
				Method method = sysclass.getDeclaredMethod("addURL", PARAMETERS);
				method.setAccessible(true);
				method.invoke(sysloader, new Object[] { f.toURI().toURL() });
				return 1;
			} catch (Throwable t) {
				throw new IOException(MessageFormat.format(
						"Error, could not add {0} to system classloader", f.getPath()), t);
			}
		}
		else {
			//for Java 9+, use the Agent class
			return Agent.appendJarFile(new JarFile(f));
		}
	}

}
