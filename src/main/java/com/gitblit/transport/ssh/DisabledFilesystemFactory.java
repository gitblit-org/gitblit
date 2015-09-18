/*
 * Copyright 2014 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.gitblit.transport.ssh;

import java.io.IOException;
import java.nio.file.FileSystem;

import org.apache.sshd.common.file.FileSystemFactory;
import org.apache.sshd.common.session.Session;

public class DisabledFilesystemFactory implements FileSystemFactory {

	 /**
     * Create user specific file system.
     *
     * @param session The session created for the user
     * @return The current {@link FileSystem} for the provided session
     * @throws java.io.IOException when the filesystem can not be created
     */
    @Override
	public FileSystem createFileSystem(Session session) throws IOException {
    	return null;
    }
}
