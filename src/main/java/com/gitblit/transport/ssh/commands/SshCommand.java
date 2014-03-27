// Copyright (C) 2012 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.gitblit.transport.ssh.commands;

import java.io.IOException;
import java.io.PrintWriter;

import org.apache.sshd.server.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SshCommand extends BaseCommand {

	protected Logger log = LoggerFactory.getLogger(getClass());
	protected PrintWriter stdout;
	protected PrintWriter stderr;

	@Override
	public void start(Environment env) throws IOException {
		startThread(new CommandRunnable() {
			@Override
			public void run() throws Exception {
				parseCommandLine();
				stdout = toPrintWriter(out);
				stderr = toPrintWriter(err);
				try {
					SshCommand.this.run();
				} finally {
					stdout.flush();
					stderr.flush();
				}
			}
		});
	}

	protected abstract void run() throws UnloggedFailure, Failure, Exception;
}
