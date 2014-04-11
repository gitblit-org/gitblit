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
package com.gitblit.transport.ssh.git;

import java.io.IOException;
import java.util.Properties;

import org.eclipse.jgit.api.GarbageCollectCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.transport.ssh.commands.CommandMetaData;
import com.gitblit.transport.ssh.commands.UsageExample;

@CommandMetaData(name = "gc", description = "Cleanup unnecessary files and optimize the local repository", admin = true)
@UsageExample(syntax = "${cmd} test/myrepository.git", description = "Garbage collect \"test/myrepository.git\"")
public class GarbageCollectionCommand extends BaseGitCommand {

	private static final Logger log = LoggerFactory.getLogger(GarbageCollectionCommand.class);

	@Override
	protected void runImpl() throws IOException, Failure {
		try {
			GarbageCollectCommand gc = Git.wrap(repo).gc();
			logGcInfo("before:", gc.getStatistics());
			gc.setProgressMonitor(NullProgressMonitor.INSTANCE);
			Properties statistics = gc.call();
			logGcInfo("after: ", statistics);
		} catch (Exception e) {
			throw new Failure(1, "fatal: Cannot run gc: ", e);
		}
	}

	private static void logGcInfo(String msg,
			Properties statistics) {
		StringBuilder b = new StringBuilder();
		b.append(msg);
		if (statistics != null) {
			b.append(" ");
			String s = statistics.toString();
			if (s.startsWith("{") && s.endsWith("}")) {
				s = s.substring(1, s.length() - 1);
			}
			b.append(s);
		}
		log.info(b.toString());
	}
}
