/*
 * Copyright (C) 2009 The Android Open Source Project
 * Copyright 2014 gitblit.com.
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
package com.gitblit.transport.ssh.git;

import org.eclipse.jgit.transport.UploadPack;

import com.gitblit.transport.ssh.SshKey;
import com.gitblit.transport.ssh.commands.CommandMetaData;

@CommandMetaData(name = "git-upload-pack", description = "Sends packs to a client for clone and fetch", hidden = true)
public class Upload extends BaseGitCommand {
	@Override
	protected void runImpl() throws Failure {
		try {
			SshKey key = getContext().getClient().getKey();
			if (key != null && !key.canClone()) {
				throw new Failure(1, "Sorry, your SSH public key is not allowed to clone!");
			}
			UploadPack up = uploadPackFactory.create(getContext().getClient(), repo);
			up.upload(in, out, null);
		} catch (Exception e) {
			throw new Failure(1, "fatal: Cannot upload pack: ", e);
		}
	}
}