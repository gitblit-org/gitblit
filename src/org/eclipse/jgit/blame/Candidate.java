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

import org.eclipse.jgit.blame.ReverseWalk.ReverseCommit;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.treewalk.filter.PathFilter;

/**
 * A source that may have supplied some (or all) of the result file.
 * <p>
 * Candidates are kept in a queue by BlameGenerator, allowing the generator to
 * perform a parallel search down the parents of any merges that are discovered
 * during the history traversal. Each candidate retains a {@link #regionList}
 * describing sections of the result file the candidate has taken responsibility
 * for either directly or indirectly through its history. Actual blame from this
 * region list will be assigned to the candidate when its ancestor commit(s) are
 * themselves converted into Candidate objects and the ancestor's candidate uses
 * {@link #takeBlame(EditList, Candidate)} to accept responsibility for sections
 * of the result.
 */
class Candidate {
	/** Next candidate in the candidate queue. */
	Candidate queueNext;

	/** Commit being considered (or blamed, depending on state). */
	RevCommit sourceCommit;

	/** Path of the candidate file in {@link #sourceCommit}. */
	PathFilter sourcePath;

	/** Unique name of the candidate blob in {@link #sourceCommit}. */
	ObjectId sourceBlob;

	/** Complete contents of the file in {@link #sourceCommit}. */
	RawText sourceText;

	/**
	 * Chain of regions this candidate may be blamed for.
	 * <p>
	 * This list is always kept sorted by resultStart order, making it simple to
	 * merge-join with the sorted EditList during blame assignment.
	 */
	Region regionList;

	/**
	 * Score assigned to the rename to this candidate.
	 * <p>
	 * Consider the history "A<-B<-C". If the result file S in C was renamed to
	 * R in B, the rename score for this rename will be held in this field by
	 * the candidate object for B. By storing the score with B, the application
	 * can see what the rename score was as it makes the transition from C/S to
	 * B/R. This may seem backwards since it was C that performed the rename,
	 * but the application doesn't learn about path R until B.
	 */
	int renameScore;

	Candidate(RevCommit commit, PathFilter path) {
		sourceCommit = commit;
		sourcePath = path;
	}

	int getParentCount() {
		return sourceCommit.getParentCount();
	}

	RevCommit getParent(int idx) {
		return sourceCommit.getParent(idx);
	}

	Candidate getNextCandidate(@SuppressWarnings("unused") int idx) {
		return null;
	}

	void add(RevFlag flag) {
		sourceCommit.add(flag);
	}

	int getTime() {
		return sourceCommit.getCommitTime();
	}

	PersonIdent getAuthor() {
		return sourceCommit.getAuthorIdent();
	}

	Candidate create(RevCommit commit, PathFilter path) {
		return new Candidate(commit, path);
	}

	Candidate copy(RevCommit commit) {
		Candidate r = create(commit, sourcePath);
		r.sourceBlob = sourceBlob;
		r.sourceText = sourceText;
		r.regionList = regionList;
		r.renameScore = renameScore;
		return r;
	}

	void loadText(ObjectReader reader) throws IOException {
		ObjectLoader ldr = reader.open(sourceBlob, Constants.OBJ_BLOB);
		sourceText = new RawText(ldr.getCachedBytes(Integer.MAX_VALUE));
	}

	void takeBlame(EditList editList, Candidate child) {
		blame(editList, this, child);
	}

	private static void blame(EditList editList, Candidate a, Candidate b) {
		Region r = b.clearRegionList();
		Region aTail = null;
		Region bTail = null;

		for (int eIdx = 0; eIdx < editList.size();) {
			// If there are no more regions left, neither side has any
			// more responsibility for the result. Remaining edits can
			// be safely ignored.
			if (r == null)
				return;

			Edit e = editList.get(eIdx);

			// Edit ends before the next candidate region. Skip the edit.
			if (e.getEndB() <= r.sourceStart) {
				eIdx++;
				continue;
			}

			// Next candidate region starts before the edit. Assign some
			// of the blame onto A, but possibly split and also on B.
			if (r.sourceStart < e.getBeginB()) {
				int d = e.getBeginB() - r.sourceStart;
				if (r.length <= d) {
					// Pass the blame for this region onto A.
					Region next = r.next;
					r.sourceStart = e.getBeginA() - d;
					aTail = add(aTail, a, r);
					r = next;
					continue;
				}

				// Split the region and assign some to A, some to B.
				aTail = add(aTail, a, r.splitFirst(e.getBeginA() - d, d));
				r.slideAndShrink(d);
			}

			// At this point e.getBeginB() <= r.sourceStart.

			// An empty edit on the B side isn't relevant to this split,
			// as it does not overlap any candidate region.
			if (e.getLengthB() == 0) {
				eIdx++;
				continue;
			}

			// If the region ends before the edit, blame on B.
			int rEnd = r.sourceStart + r.length;
			if (rEnd <= e.getEndB()) {
				Region next = r.next;
				bTail = add(bTail, b, r);
				r = next;
				if (rEnd == e.getEndB())
					eIdx++;
				continue;
			}

			// This region extends beyond the edit. Blame the first
			// half of the region on B, and process the rest after.
			int len = e.getEndB() - r.sourceStart;
			bTail = add(bTail, b, r.splitFirst(r.sourceStart, len));
			r.slideAndShrink(len);
			eIdx++;
		}

		if (r == null)
			return;

		// For any remaining region, pass the blame onto A after shifting
		// the source start to account for the difference between the two.
		Edit e = editList.get(editList.size() - 1);
		int endB = e.getEndB();
		int d = endB - e.getEndA();
		if (aTail == null)
			a.regionList = r;
		else
			aTail.next = r;
		do {
			if (endB <= r.sourceStart)
				r.sourceStart -= d;
			r = r.next;
		} while (r != null);
	}

	private static Region add(Region aTail, Candidate a, Region n) {
		// If there is no region on the list, use only this one.
		if (aTail == null) {
			a.regionList = n;
			n.next = null;
			return n;
		}

		// If the prior region ends exactly where the new region begins
		// in both the result and the source, combine these together into
		// one contiguous region. This occurs when intermediate commits
		// have inserted and deleted lines in the middle of a region. Try
		// to report this region as a single region to the application,
		// rather than in fragments.
		if (aTail.resultStart + aTail.length == n.resultStart
				&& aTail.sourceStart + aTail.length == n.sourceStart) {
			aTail.length += n.length;
			return aTail;
		}

		// Append the region onto the end of the list.
		aTail.next = n;
		n.next = null;
		return n;
	}

	private Region clearRegionList() {
		Region r = regionList;
		regionList = null;
		return r;
	}

	@Override
	public String toString() {
		StringBuilder r = new StringBuilder();
		r.append("Candidate[");
		r.append(sourcePath.getPath());
		if (sourceCommit != null)
			r.append(" @ ").append(sourceCommit.abbreviate(6).name());
		if (regionList != null)
			r.append(" regions:").append(regionList);
		r.append("]");
		return r.toString();
	}

	/**
	 * Special candidate type used for reverse blame.
	 * <p>
	 * Reverse blame inverts the commit history graph to follow from a commit to
	 * its descendant children, rather than the normal history direction of
	 * child to parent. These types require a {@link ReverseCommit} which keeps
	 * children pointers, allowing reverse navigation of history.
	 */
	static final class ReverseCandidate extends Candidate {
		ReverseCandidate(ReverseCommit commit, PathFilter path) {
			super(commit, path);
		}

		@Override
		int getParentCount() {
			return ((ReverseCommit) sourceCommit).getChildCount();
		}

		@Override
		RevCommit getParent(int idx) {
			return ((ReverseCommit) sourceCommit).getChild(idx);
		}

		@Override
		int getTime() {
			// Invert the timestamp so newer dates sort older.
			return -sourceCommit.getCommitTime();
		}

		@Override
		Candidate create(RevCommit commit, PathFilter path) {
			return new ReverseCandidate((ReverseCommit) commit, path);
		}

		@Override
		public String toString() {
			return "Reverse" + super.toString();
		}
	}

	/**
	 * Candidate loaded from a file source, and not a commit.
	 * <p>
	 * The {@link Candidate#sourceCommit} field is always null on this type of
	 * candidate. Instead history traversal follows the single {@link #parent}
	 * field to discover the next Candidate. Often this is a normal Candidate
	 * type that has a valid sourceCommit.
	 */
	static final class BlobCandidate extends Candidate {
		/**
		 * Next candidate to pass blame onto.
		 * <p>
		 * When computing the differences that this candidate introduced to the
		 * file content, the parent's sourceText is used as the base.
		 */
		Candidate parent;

		/** Author name to refer to this blob with. */
		String description;

		BlobCandidate(String name, PathFilter path) {
			super(null, path);
			description = name;
		}

		@Override
		int getParentCount() {
			return parent != null ? 1 : 0;
		}

		@Override
		RevCommit getParent(int idx) {
			return null;
		}

		@Override
		Candidate getNextCandidate(int idx) {
			return parent;
		}

		@Override
		void add(RevFlag flag) {
			// Do nothing, sourceCommit is null.
		}

		@Override
		int getTime() {
			return Integer.MAX_VALUE;
		}

		@Override
		PersonIdent getAuthor() {
			return new PersonIdent(description, null);
		}
	}
}
