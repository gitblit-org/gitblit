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

/**
 * Launch helper class that adds all jars found in the local "ext" folder
 * and then calls the application main. Using this technique we do not
 * have to specify a classpath and we can dynamically add jars to the
 * distribution.
 *
 */
public class FederationClientLauncher {

	public static void main(String[] args) {
		// Load the JARs in the ext folder, with no splash screen
		int numberOfJars = LibraryLoader.loadLibraries("ext", false, null);
		if (numberOfJars == 0) {
			System.err.println("Failed to find any JARs in 'ext' folder!");
			System.exit(-1);
		}

		// Start the Federation Client
		FederationClient.main(args);
	}

}
