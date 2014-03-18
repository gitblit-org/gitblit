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
package com.gitblit.transport.ssh.gitblit;

import java.util.HashSet;
import java.util.Set;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import com.gitblit.models.TicketModel.Change;
import com.gitblit.models.TicketModel.Patchset;
import com.gitblit.models.TicketModel.Score;
import com.gitblit.models.UserModel;
import com.gitblit.transport.ssh.CommandMetaData;
import com.gitblit.transport.ssh.commands.SshCommand;
import com.gitblit.wicket.GitBlitWebSession;

@CommandMetaData(name = "review", description = "Verify, approve and/or submit one or more patch sets", hidden = true)
public class ReviewCommand extends SshCommand {

	private final static short REV_ID_LEN = 40;
	private final Set<Patchset> patchSets = new HashSet<Patchset>();

	@Argument(index = 0, required = true, multiValued = true, metaVar = "{COMMIT | CHANGE,PATCHSET}", usage = "list of commits or patch sets to review")
	void addPatchSetId(final String token) {
		try {
			patchSets.add(parsePatchSet(token));
		} catch (UnloggedFailure e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}
	}

	@Option(name = "--project", required = true, aliases = "-p", usage = "project containing the specified patch set(s)")
	private String project;

	@Option(name = "--message", aliases = "-m", usage = "cover message to publish on change(s)", metaVar = "MESSAGE")
	private String changeComment;

	@Option(name = "--vote", aliases = "-v", usage = "vote on this patch set", metaVar = "VOTE")
	private int vote;

	@Option(name = "--submit", aliases = "-s", usage = "submit the specified patch set(s)")
	private boolean submitChange;

	@Override
	public void run() throws UnloggedFailure {
		UserModel user = GitBlitWebSession.get().getUser();
		// TODO ensure user has permission to score +2/-2
		for (Patchset ps : patchSets) {
			// review
			Change change = new Change(user.username);
			change.review(ps, Score.fromScore(vote), false);
			// TODO(davido): add patchset comment
			if (submitChange) {
				// TODO(davido): merge (when desired and the change is mergeable)
			}
		}
	}

	private Patchset parsePatchSet(String ps) throws UnloggedFailure {
		// By commit?
		//
		if (ps.matches("^([0-9a-fA-F]{4," + REV_ID_LEN + "})$")) {
			// TODO; parse
		}

		// By older style change,patchset?
		//
		if (ps.matches("^[1-9][0-9]*,[1-9][0-9]*$")) {
			// TODO: parse
		}

		throw new UnloggedFailure(1, "fatal: Cannot parse patchset: " + ps);
	}
}
