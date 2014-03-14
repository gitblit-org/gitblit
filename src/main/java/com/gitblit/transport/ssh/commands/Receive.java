/*
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
package com.gitblit.transport.ssh.commands;

import org.eclipse.jgit.transport.ReceivePack;

import com.gitblit.transport.ssh.CommandMetaData;

@CommandMetaData(name = "git-receive-pack", description = "Receive pack")
public class Receive extends AbstractGitCommand {
	@Override
	protected void runImpl() throws Failure {
		try {
			ReceivePack rp = receivePackFactory.create(ctx.getClient(), repo);
			rp.receive(in, out, null);
		} catch (Exception e) {
			throw new Failure(1, "fatal: Cannot receive pack: ", e);
		}
	}
}
