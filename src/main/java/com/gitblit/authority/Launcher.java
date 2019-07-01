/*
 * Copyright 2012 gitblit.com.
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
package com.gitblit.authority;

import com.gitblit.LibraryLoader;

/**
 * Downloads dependencies and launches Gitblit Authority.
 *
 * @author James Moger
 *
 */
public class Launcher {

	public static void main(String[] args) {
		// Load the JARs in the ext folder, with a splash screen showing progress
		int numberOfJars = LibraryLoader.loadLibraries("ext", true, "Gitblit Authority");
		if (numberOfJars == 0) {
			System.err.println("Failed to find any JARs in 'ext' folder!");
			System.exit(-1);
		}

		GitblitAuthority.main(args);
	}

}
