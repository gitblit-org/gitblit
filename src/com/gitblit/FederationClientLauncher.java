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
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.gitblit.build.Build;

/**
 * Downloads dependencies and launches command-line Federation client.
 * 
 * @author James Moger
 * 
 */
public class FederationClientLauncher {

	public static void main(String[] args) {
		// download federation client runtime dependencies
		Build.federationClient();

		File libFolder = new File("ext");
		List<File> jars = Launcher.findJars(libFolder.getAbsoluteFile());
		
		// sort the jars by name and then reverse the order so the newer version
		// of the library gets loaded in the event that this is an upgrade
		Collections.sort(jars);
		Collections.reverse(jars);
		for (File jar : jars) {
			try {
				Launcher.addJarFile(jar);
			} catch (IOException e) {

			}
		}
		
		FederationClient.main(args);
	}
}
