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

import java.security.ProtectionDomain;

/**
 * Launch helper class that adds all jars found in the local "ext" folder
 * and then calls the application main. Using this technique we do not
 * have to specify a classpath and we can dynamically add jars to the
 * distribution.
 *
 * @author James Moger
 *
 */
public class Launcher {

	public static final boolean DEBUG = false;

	public static void main(String[] args) {
		if (DEBUG) {
			System.out.println("jcp=" + System.getProperty("java.class.path"));
			ProtectionDomain protectionDomain = Launcher.class.getProtectionDomain();
			System.out.println("launcher="
					+ protectionDomain.getCodeSource().getLocation().toExternalForm());
		}

		// Load the JARs in the ext folder, with no splash screen
		int numberOfJars = LibraryLoader.loadLibraries("ext", false, null);
		if (numberOfJars == 0) {
			System.err.println("Failed to find any JARs in 'ext' folder!");
			System.exit(-1);
		}

		// Start Server
		GitBlitServer.main(args);
	}

}
