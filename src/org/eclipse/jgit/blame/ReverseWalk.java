/*
 * Copyright (C) 2011, Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.blame;

import java.io.IOException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

final class ReverseWalk extends RevWalk {
	ReverseWalk(Repository repo) {
		super(repo);
	}

	@Override
	public ReverseCommit next() throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		ReverseCommit c = (ReverseCommit) super.next();
		if (c == null)
			return null;
		for (int pIdx = 0; pIdx < c.getParentCount(); pIdx++)
			((ReverseCommit) c.getParent(pIdx)).addChild(c);
		return c;
	}

	@Override
	protected RevCommit createCommit(AnyObjectId id) {
		return new ReverseCommit(id);
	}

	static final class ReverseCommit extends RevCommit {
		private static final ReverseCommit[] NO_CHILDREN = {};

		private ReverseCommit[] children = NO_CHILDREN;

		ReverseCommit(AnyObjectId id) {
			super(id);
		}

		void addChild(ReverseCommit c) {
			// Always put the most recent child onto the front of the list.
			// This works correctly because our ReverseWalk parent (above)
			// runs in COMMIT_TIME_DESC order. Older commits will be popped
			// later and should go in front of the children list so they are
			// visited first by BlameGenerator when considering candidates.

			int cnt = children.length;
			if (cnt == 0)
				children = new ReverseCommit[] { c };
			else if (cnt == 1)
				children = new ReverseCommit[] { c, children[0] };
			else {
				ReverseCommit[] n = new ReverseCommit[1 + cnt];
				n[0] = c;
				System.arraycopy(children, 0, n, 1, cnt);
				children = n;
			}
		}

		int getChildCount() {
			return children.length;
		}

		ReverseCommit getChild(final int nth) {
			return children[nth];
		}
	}
}
