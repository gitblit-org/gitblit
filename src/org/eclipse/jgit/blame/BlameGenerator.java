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

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import org.eclipse.jgit.blame.Candidate.BlobCandidate;
import org.eclipse.jgit.blame.Candidate.ReverseCandidate;
import org.eclipse.jgit.blame.ReverseWalk.ReverseCommit;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.HistogramDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

/**
 * Generate author information for lines based on introduction to the file.
 * <p>
 * Applications that want a simple one-shot computation of blame for a file
 * should use {@link #computeBlameResult()} to prepare the entire result in one
 * method call. This may block for significant time as the history of the
 * repository must be traversed until information is gathered for every line.
 * <p>
 * Applications that want more incremental update behavior may use either the
 * raw {@link #next()} streaming approach supported by this class, or construct
 * a {@link BlameResult} using {@link BlameResult#create(BlameGenerator)} and
 * incrementally construct the result with {@link BlameResult#computeNext()}.
 * <p>
 * This class is not thread-safe.
 * <p>
 * An instance of BlameGenerator can only be used once. To blame multiple files
 * the application must create a new BlameGenerator.
 * <p>
 * During blame processing there are two files involved:
 * <ul>
 * <li>result - The file whose lines are being examined. This is the revision
 * the user is trying to view blame/annotation information alongside of.</li>
 * <li>source - The file that was blamed with supplying one or more lines of
 * data into result. The source may be a different file path (due to copy or
 * rename). Source line numbers may differ from result line numbers due to lines
 * being added/removed in intermediate revisions.</li>
 * </ul>
 * <p>
 * The blame algorithm is implemented by initially assigning responsibility for
 * all lines of the result to the starting commit. A difference against the
 * commit's ancestor is computed, and responsibility is passed to the ancestor
 * commit for any lines that are common. The starting commit is blamed only for
 * the lines that do not appear in the ancestor, if any. The loop repeats using
 * the ancestor, until there are no more lines to acquire information on, or the
 * file's creation point is discovered in history.
 */
public class BlameGenerator {
	private final Repository repository;

	private final PathFilter resultPath;

	private final MutableObjectId idBuf;

	/** Revision pool used to acquire commits from. */
	private RevWalk revPool;

	/** Indicates the commit has already been processed. */
	private RevFlag SEEN;

	private ObjectReader reader;

	private TreeWalk treeWalk;

	private DiffAlgorithm diffAlgorithm = new HistogramDiff();

	private RawTextComparator textComparator = RawTextComparator.DEFAULT;

	private RenameDetector renameDetector;

	/** Potential candidates, sorted by commit time descending. */
	private Candidate queue;

	/** Number of lines that still need to be discovered. */
	private int remaining;

	/** Blame is currently assigned to this source. */
	private Candidate currentSource;

	/**
	 * Create a blame generator for the repository and path
	 *
	 * @param repository
	 *            repository to access revision data from.
	 * @param path
	 *            initial path of the file to start scanning.
	 */
	public BlameGenerator(Repository repository, String path) {
		this.repository = repository;
		this.resultPath = PathFilter.create(path);

		idBuf = new MutableObjectId();
		setFollowFileRenames(true);
		initRevPool(false);

		remaining = -1;
	}

	private void initRevPool(boolean reverse) {
		if (queue != null)
			throw new IllegalStateException();

		if (revPool != null)
			revPool.release();

		if (reverse)
			revPool = new ReverseWalk(getRepository());
		else
			revPool = new RevWalk(getRepository());

		revPool.setRetainBody(true);
		SEEN = revPool.newFlag("SEEN");
		reader = revPool.getObjectReader();
		treeWalk = new TreeWalk(reader);
	}

	/** @return repository being scanned for revision history. */
	public Repository getRepository() {
		return repository;
	}

	/** @return path file path being processed. */
	public String getResultPath() {
		return resultPath.getPath();
	}

	/**
	 * Difference algorithm to use when comparing revisions.
	 *
	 * @param algorithm
	 * @return {@code this}
	 */
	public BlameGenerator setDiffAlgorithm(DiffAlgorithm algorithm) {
		diffAlgorithm = algorithm;
		return this;
	}

	/**
	 * Text comparator to use when comparing revisions.
	 *
	 * @param comparator
	 * @return {@code this}
	 */
	public BlameGenerator setTextComparator(RawTextComparator comparator) {
		textComparator = comparator;
		return this;
	}

	/**
	 * Enable (or disable) following file renames, on by default.
	 * <p>
	 * If true renames are followed using the standard FollowFilter behavior
	 * used by RevWalk (which matches {@code git log --follow} in the C
	 * implementation). This is not the same as copy/move detection as
	 * implemented by the C implementation's of {@code git blame -M -C}.
	 *
	 * @param follow
	 *            enable following.
	 * @return {@code this}
	 */
	public BlameGenerator setFollowFileRenames(boolean follow) {
		if (follow)
			renameDetector = new RenameDetector(getRepository());
		else
			renameDetector = null;
		return this;
	}

	/**
	 * Obtain the RenameDetector if {@code setFollowFileRenames(true)}.
	 *
	 * @return the rename detector, allowing the application to configure its
	 *         settings for rename score and breaking behavior.
	 */
	public RenameDetector getRenameDetector() {
		return renameDetector;
	}

	/**
	 * Push a candidate blob onto the generator's traversal stack.
	 * <p>
	 * Candidates should be pushed in history order from oldest-to-newest.
	 * Applications should push the starting commit first, then the index
	 * revision (if the index is interesting), and finally the working tree
	 * copy (if the working tree is interesting).
	 *
	 * @param description
	 *            description of the blob revision, such as "Working Tree".
	 * @param contents
	 *            contents of the file.
	 * @return {@code this}
	 * @throws IOException
	 *             the repository cannot be read.
	 */
	public BlameGenerator push(String description, byte[] contents)
			throws IOException {
		return push(description, new RawText(contents));
	}

	/**
	 * Push a candidate blob onto the generator's traversal stack.
	 * <p>
	 * Candidates should be pushed in history order from oldest-to-newest.
	 * Applications should push the starting commit first, then the index
	 * revision (if the index is interesting), and finally the working tree copy
	 * (if the working tree is interesting).
	 *
	 * @param description
	 *            description of the blob revision, such as "Working Tree".
	 * @param contents
	 *            contents of the file.
	 * @return {@code this}
	 * @throws IOException
	 *             the repository cannot be read.
	 */
	public BlameGenerator push(String description, RawText contents)
			throws IOException {
		if (description == null)
			// XXX description = JGitText.get().blameNotCommittedYet;
			description = "blame not committed yet";
		BlobCandidate c = new BlobCandidate(description, resultPath);
		c.sourceText = contents;
		c.regionList = new Region(0, 0, contents.size());
		remaining = contents.size();
		push(c);
		return this;
	}

	/**
	 * Push a candidate object onto the generator's traversal stack.
	 * <p>
	 * Candidates should be pushed in history order from oldest-to-newest.
	 * Applications should push the starting commit first, then the index
	 * revision (if the index is interesting), and finally the working tree copy
	 * (if the working tree is interesting).
	 *
	 * @param description
	 *            description of the blob revision, such as "Working Tree".
	 * @param id
	 *            may be a commit or a blob.
	 * @return {@code this}
	 * @throws IOException
	 *             the repository cannot be read.
	 */
	public BlameGenerator push(String description, AnyObjectId id)
			throws IOException {
		ObjectLoader ldr = reader.open(id);
		if (ldr.getType() == OBJ_BLOB) {
			if (description == null)
				// XXX description = JGitText.get().blameNotCommittedYet;
				description = "blame not committed yet";
			BlobCandidate c = new BlobCandidate(description, resultPath);
			c.sourceBlob = id.toObjectId();
			c.sourceText = new RawText(ldr.getCachedBytes(Integer.MAX_VALUE));
			c.regionList = new Region(0, 0, c.sourceText.size());
			remaining = c.sourceText.size();
			push(c);
			return this;
		}

		RevCommit commit = revPool.parseCommit(id);
		if (!find(commit, resultPath))
			return this;

		Candidate c = new Candidate(commit, resultPath);
		c.sourceBlob = idBuf.toObjectId();
		c.loadText(reader);
		c.regionList = new Region(0, 0, c.sourceText.size());
		remaining = c.sourceText.size();
		push(c);
		return this;
	}

	/**
	 * Configure the generator to compute reverse blame (history of deletes).
	 * <p>
	 * This method is expensive as it immediately runs a RevWalk over the
	 * history spanning the expression {@code start..end} (end being more recent
	 * than start) and then performs the equivalent operation as
	 * {@link #push(String, AnyObjectId)} to begin blame traversal from the
	 * commit named by {@code start} walking forwards through history until
	 * {@code end} blaming line deletions.
	 * <p>
	 * A reverse blame may produce multiple sources for the same result line,
	 * each of these is a descendant commit that removed the line, typically
	 * this occurs when the same deletion appears in multiple side branches such
	 * as due to a cherry-pick. Applications relying on reverse should use
	 * {@link BlameResult} as it filters these duplicate sources and only
	 * remembers the first (oldest) deletion.
	 *
	 * @param start
	 *            oldest commit to traverse from. The result file will be loaded
	 *            from this commit's tree.
	 * @param end
	 *            most recent commit to stop traversal at. Usually an active
	 *            branch tip, tag, or HEAD.
	 * @return {@code this}
	 * @throws IOException
	 *             the repository cannot be read.
	 */
	public BlameGenerator reverse(AnyObjectId start, AnyObjectId end)
			throws IOException {
		return reverse(start, Collections.singleton(end.toObjectId()));
	}

	/**
	 * Configure the generator to compute reverse blame (history of deletes).
	 * <p>
	 * This method is expensive as it immediately runs a RevWalk over the
	 * history spanning the expression {@code start..end} (end being more recent
	 * than start) and then performs the equivalent operation as
	 * {@link #push(String, AnyObjectId)} to begin blame traversal from the
	 * commit named by {@code start} walking forwards through history until
	 * {@code end} blaming line deletions.
	 * <p>
	 * A reverse blame may produce multiple sources for the same result line,
	 * each of these is a descendant commit that removed the line, typically
	 * this occurs when the same deletion appears in multiple side branches such
	 * as due to a cherry-pick. Applications relying on reverse should use
	 * {@link BlameResult} as it filters these duplicate sources and only
	 * remembers the first (oldest) deletion.
	 *
	 * @param start
	 *            oldest commit to traverse from. The result file will be loaded
	 *            from this commit's tree.
	 * @param end
	 *            most recent commits to stop traversal at. Usually an active
	 *            branch tip, tag, or HEAD.
	 * @return {@code this}
	 * @throws IOException
	 *             the repository cannot be read.
	 */
	public BlameGenerator reverse(AnyObjectId start,
			Collection<? extends ObjectId> end) throws IOException {
		initRevPool(true);

		ReverseCommit result = (ReverseCommit) revPool.parseCommit(start);
		if (!find(result, resultPath))
			return this;

		revPool.markUninteresting(result);
		for (ObjectId id : end)
			revPool.markStart(revPool.parseCommit(id));

		while (revPool.next() != null) {
			// just pump the queue
		}

		ReverseCandidate c = new ReverseCandidate(result, resultPath);
		c.sourceBlob = idBuf.toObjectId();
		c.loadText(reader);
		c.regionList = new Region(0, 0, c.sourceText.size());
		remaining = c.sourceText.size();
		push(c);
		return this;
	}

	/**
	 * Execute the generator in a blocking fashion until all data is ready.
	 *
	 * @return the complete result. Null if no file exists for the given path.
	 * @throws IOException
	 *             the repository cannot be read.
	 */
	public BlameResult computeBlameResult() throws IOException {
		try {
			BlameResult r = BlameResult.create(this);
			if (r != null)
				r.computeAll();
			return r;
		} finally {
			release();
		}
	}

	/**
	 * Step the blame algorithm one iteration.
	 *
	 * @return true if the generator has found a region's source. The getSource*
	 *         and {@link #getResultStart()}, {@link #getResultEnd()} methods
	 *         can be used to inspect the region found. False if there are no
	 *         more regions to describe.
	 * @throws IOException
	 *             repository cannot be read.
	 */
	public boolean next() throws IOException {
		// If there is a source still pending, produce the next region.
		if (currentSource != null) {
			Region r = currentSource.regionList;
			Region n = r.next;
			remaining -= r.length;
			if (n != null) {
				currentSource.regionList = n;
				return true;
			}

			if (currentSource.queueNext != null)
				return result(currentSource.queueNext);

			currentSource = null;
		}

		// If there are no lines remaining, the entire result is done,
		// even if there are revisions still available for the path.
		if (remaining == 0)
			return done();

		for (;;) {
			Candidate n = pop();
			if (n == null)
				return done();

			int pCnt = n.getParentCount();
			if (pCnt == 1) {
				if (processOne(n))
					return true;

			} else if (1 < pCnt) {
				if (processMerge(n))
					return true;

			} else if (n instanceof ReverseCandidate) {
				// Do not generate a tip of a reverse. The region
				// survives and should not appear to be deleted.

			} else /* if (pCnt == 0) */{
				// Root commit, with at least one surviving region.
				// Assign the remaining blame here.
				return result(n);
			}
		}
	}

	private boolean done() {
		release();
		return false;
	}

	private boolean result(Candidate n) throws IOException {
		if (n.sourceCommit != null)
			revPool.parseBody(n.sourceCommit);
		currentSource = n;
		return true;
	}

	private boolean reverseResult(Candidate parent, Candidate source)
			throws IOException {
		// On a reverse blame present the application the parent
		// (as this is what did the removals), however the region
		// list to enumerate is the source's surviving list.
		Candidate res = parent.copy(parent.sourceCommit);
		res.regionList = source.regionList;
		return result(res);
	}

	private Candidate pop() {
		Candidate n = queue;
		if (n != null) {
			queue = n.queueNext;
			n.queueNext = null;
		}
		return n;
	}

	private void push(BlobCandidate toInsert) {
		Candidate c = queue;
		if (c != null) {
			c.regionList = null;
			toInsert.parent = c;
		}
		queue = toInsert;
	}

	private void push(Candidate toInsert) {
		// Mark sources to ensure they get discarded (above) if
		// another path to the same commit.
		toInsert.add(SEEN);

		// Insert into the queue using descending commit time, so
		// the most recent commit will pop next.
		int time = toInsert.getTime();
		Candidate n = queue;
		if (n == null || time >= n.getTime()) {
			toInsert.queueNext = n;
			queue = toInsert;
			return;
		}

		for (Candidate p = n;; p = n) {
			n = p.queueNext;
			if (n == null || time >= n.getTime()) {
				toInsert.queueNext = n;
				p.queueNext = toInsert;
				return;
			}
		}
	}

	private boolean processOne(Candidate n) throws IOException {
		RevCommit parent = n.getParent(0);
		if (parent == null)
			return split(n.getNextCandidate(0), n);
		if (parent.has(SEEN))
			return false;
		revPool.parseHeaders(parent);

		if (find(parent, n.sourcePath)) {
			if (idBuf.equals(n.sourceBlob)) {
				// The common case of the file not being modified in
				// a simple string-of-pearls history. Blame parent.
				n.sourceCommit = parent;
				push(n);
				return false;
			}

			Candidate next = n.create(parent, n.sourcePath);
			next.sourceBlob = idBuf.toObjectId();
			next.loadText(reader);
			return split(next, n);
		}

		if (n.sourceCommit == null)
			return result(n);

		DiffEntry r = findRename(parent, n.sourceCommit, n.sourcePath);
		if (r == null)
			return result(n);

		if (0 == r.getOldId().prefixCompare(n.sourceBlob)) {
			// A 100% rename without any content change can also
			// skip directly to the parent.
			n.sourceCommit = parent;
			n.sourcePath = PathFilter.create(r.getOldPath());
			push(n);
			return false;
		}

		Candidate next = n.create(parent, PathFilter.create(r.getOldPath()));
		next.sourceBlob = r.getOldId().toObjectId();
		next.renameScore = r.getScore();
		next.loadText(reader);
		return split(next, n);
	}

	private boolean split(Candidate parent, Candidate source)
			throws IOException {
		EditList editList = diffAlgorithm.diff(textComparator,
				parent.sourceText, source.sourceText);
		if (editList.isEmpty()) {
			// Ignoring whitespace (or some other special comparator) can
			// cause non-identical blobs to have an empty edit list. In
			// a case like this push the parent alone.
			parent.regionList = source.regionList;
			push(parent);
			return false;
		}

		parent.takeBlame(editList, source);
		if (parent.regionList != null)
			push(parent);
		if (source.regionList != null) {
			if (source instanceof ReverseCandidate)
				return reverseResult(parent, source);
			return result(source);
		}
		return false;
	}

	private boolean processMerge(Candidate n) throws IOException {
		int pCnt = n.getParentCount();

		for (int pIdx = 0; pIdx < pCnt; pIdx++) {
			RevCommit parent = n.getParent(pIdx);
			if (parent.has(SEEN))
				continue;
			revPool.parseHeaders(parent);
		}

		// If any single parent exactly matches the merge, follow only
		// that one parent through history.
		ObjectId[] ids = null;
		for (int pIdx = 0; pIdx < pCnt; pIdx++) {
			RevCommit parent = n.getParent(pIdx);
			if (parent.has(SEEN))
				continue;
			if (!find(parent, n.sourcePath))
				continue;
			if (!(n instanceof ReverseCandidate) && idBuf.equals(n.sourceBlob)) {
				n.sourceCommit = parent;
				push(n);
				return false;
			}
			if (ids == null)
				ids = new ObjectId[pCnt];
			ids[pIdx] = idBuf.toObjectId();
		}

		// If rename detection is enabled, search for any relevant names.
		DiffEntry[] renames = null;
		if (renameDetector != null) {
			renames = new DiffEntry[pCnt];
			for (int pIdx = 0; pIdx < pCnt; pIdx++) {
				RevCommit parent = n.getParent(pIdx);
				if (parent.has(SEEN))
					continue;
				if (ids != null && ids[pIdx] != null)
					continue;

				DiffEntry r = findRename(parent, n.sourceCommit, n.sourcePath);
				if (r == null)
					continue;

				if (n instanceof ReverseCandidate) {
					if (ids == null)
						ids = new ObjectId[pCnt];
					ids[pCnt] = r.getOldId().toObjectId();
				} else if (0 == r.getOldId().prefixCompare(n.sourceBlob)) {
					// A 100% rename without any content change can also
					// skip directly to the parent. Note this bypasses an
					// earlier parent that had the path (above) but did not
					// have an exact content match. For performance reasons
					// we choose to follow the one parent over trying to do
					// possibly both parents.
					n.sourceCommit = parent;
					n.sourcePath = PathFilter.create(r.getOldPath());
					push(n);
					return false;
				}

				renames[pIdx] = r;
			}
		}

		// Construct the candidate for each parent.
		Candidate[] parents = new Candidate[pCnt];
		for (int pIdx = 0; pIdx < pCnt; pIdx++) {
			RevCommit parent = n.getParent(pIdx);
			if (parent.has(SEEN))
				continue;

			Candidate p;
			if (renames != null && renames[pIdx] != null) {
				p = n.create(parent,
						PathFilter.create(renames[pIdx].getOldPath()));
				p.renameScore = renames[pIdx].getScore();
				p.sourceBlob = renames[pIdx].getOldId().toObjectId();
			} else if (ids != null && ids[pIdx] != null) {
				p = n.create(parent, n.sourcePath);
				p.sourceBlob = ids[pIdx];
			} else {
				continue;
			}

			EditList editList;
			if (n instanceof ReverseCandidate
					&& p.sourceBlob.equals(n.sourceBlob)) {
				// This special case happens on ReverseCandidate forks.
				p.sourceText = n.sourceText;
				editList = new EditList(0);
			} else {
				p.loadText(reader);
				editList = diffAlgorithm.diff(textComparator,
						p.sourceText, n.sourceText);
			}

			if (editList.isEmpty()) {
				// Ignoring whitespace (or some other special comparator) can
				// cause non-identical blobs to have an empty edit list. In
				// a case like this push the parent alone.
				if (n instanceof ReverseCandidate) {
					parents[pIdx] = p;
					continue;
				}

				p.regionList = n.regionList;
				push(p);
				return false;
			}

			p.takeBlame(editList, n);

			// Only remember this parent candidate if there is at least
			// one region that was blamed on the parent.
			if (p.regionList != null) {
				// Reverse blame requires inverting the regions. This puts
				// the regions the parent deleted from us into the parent,
				// and retains the common regions to look at other parents
				// for deletions.
				if (n instanceof ReverseCandidate) {
					Region r = p.regionList;
					p.regionList = n.regionList;
					n.regionList = r;
				}

				parents[pIdx] = p;
			}
		}

		if (n instanceof ReverseCandidate) {
			// On a reverse blame report all deletions found in the children,
			// and pass on to them a copy of our region list.
			Candidate resultHead = null;
			Candidate resultTail = null;

			for (int pIdx = 0; pIdx < pCnt; pIdx++) {
				Candidate p = parents[pIdx];
				if (p == null)
					continue;

				if (p.regionList != null) {
					Candidate r = p.copy(p.sourceCommit);
					if (resultTail != null) {
						resultTail.queueNext = r;
						resultTail = r;
					} else {
						resultHead = r;
						resultTail = r;
					}
				}

				if (n.regionList != null) {
					p.regionList = n.regionList.deepCopy();
					push(p);
				}
			}

			if (resultHead != null)
				return result(resultHead);
			return false;
		}

		// Push any parents that are still candidates.
		for (int pIdx = 0; pIdx < pCnt; pIdx++) {
			if (parents[pIdx] != null)
				push(parents[pIdx]);
		}

		if (n.regionList != null)
			return result(n);
		return false;
	}

	/**
	 * Get the revision blamed for the current region.
	 * <p>
	 * The source commit may be null if the line was blamed to an uncommitted
	 * revision, such as the working tree copy, or during a reverse blame if the
	 * line survives to the end revision (e.g. the branch tip).
	 *
	 * @return current revision being blamed.
	 */
	public RevCommit getSourceCommit() {
		return currentSource.sourceCommit;
	}

	/** @return current author being blamed. */
	public PersonIdent getSourceAuthor() {
		return currentSource.getAuthor();
	}

	/** @return current committer being blamed. */
	public PersonIdent getSourceCommitter() {
		RevCommit c = getSourceCommit();
		return c != null ? c.getCommitterIdent() : null;
	}

	/** @return path of the file being blamed. */
	public String getSourcePath() {
		return currentSource.sourcePath.getPath();
	}

	/** @return rename score if a rename occurred in {@link #getSourceCommit}. */
	public int getRenameScore() {
		return currentSource.renameScore;
	}

	/**
	 * @return first line of the source data that has been blamed for the
	 *         current region. This is line number of where the region was added
	 *         during {@link #getSourceCommit()} in file
	 *         {@link #getSourcePath()}.
	 */
	public int getSourceStart() {
		return currentSource.regionList.sourceStart;
	}

	/**
	 * @return one past the range of the source data that has been blamed for
	 *         the current region. This is line number of where the region was
	 *         added during {@link #getSourceCommit()} in file
	 *         {@link #getSourcePath()}.
	 */
	public int getSourceEnd() {
		Region r = currentSource.regionList;
		return r.sourceStart + r.length;
	}

	/**
	 * @return first line of the result that {@link #getSourceCommit()} has been
	 *         blamed for providing. Line numbers use 0 based indexing.
	 */
	public int getResultStart() {
		return currentSource.regionList.resultStart;
	}

	/**
	 * @return one past the range of the result that {@link #getSourceCommit()}
	 *         has been blamed for providing. Line numbers use 0 based indexing.
	 *         Because a source cannot be blamed for an empty region of the
	 *         result, {@link #getResultEnd()} is always at least one larger
	 *         than {@link #getResultStart()}.
	 */
	public int getResultEnd() {
		Region r = currentSource.regionList;
		return r.resultStart + r.length;
	}

	/**
	 * @return number of lines in the current region being blamed to
	 *         {@link #getSourceCommit()}. This is always the value of the
	 *         expression {@code getResultEnd() - getResultStart()}, but also
	 *         {@code getSourceEnd() - getSourceStart()}.
	 */
	public int getRegionLength() {
		return currentSource.regionList.length;
	}

	/**
	 * @return complete contents of the source file blamed for the current
	 *         output region. This is the contents of {@link #getSourcePath()}
	 *         within {@link #getSourceCommit()}. The source contents is
	 *         temporarily available as an artifact of the blame algorithm. Most
	 *         applications will want the result contents for display to users.
	 */
	public RawText getSourceContents() {
		return currentSource.sourceText;
	}

	/**
	 * @return complete file contents of the result file blame is annotating.
	 *         This value is accessible only after being configured and only
	 *         immediately before the first call to {@link #next()}. Returns
	 *         null if the path does not exist.
	 * @throws IOException
	 *             repository cannot be read.
	 * @throws IllegalStateException
	 *             {@link #next()} has already been invoked.
	 */
	public RawText getResultContents() throws IOException {
		return queue != null ? queue.sourceText : null;
	}

	/** Release the current blame session. */
	public void release() {
		revPool.release();
		queue = null;
		currentSource = null;
	}

	private boolean find(RevCommit commit, PathFilter path) throws IOException {
		treeWalk.setFilter(path);
		treeWalk.reset(commit.getTree());
		while (treeWalk.next()) {
			if (path.isDone(treeWalk)) {
				if (treeWalk.getFileMode(0).getObjectType() != OBJ_BLOB)
					return false;
				treeWalk.getObjectId(idBuf, 0);
				return true;
			}

			if (treeWalk.isSubtree())
				treeWalk.enterSubtree();
		}
		return false;
	}

	private DiffEntry findRename(RevCommit parent, RevCommit commit,
			PathFilter path) throws IOException {
		if (renameDetector == null)
			return null;

		treeWalk.setFilter(TreeFilter.ANY_DIFF);
		treeWalk.reset(parent.getTree(), commit.getTree());
		renameDetector.addAll(DiffEntry.scan(treeWalk));
		for (DiffEntry ent : renameDetector.compute()) {
			if (isRename(ent) && ent.getNewPath().equals(path.getPath()))
				return ent;
		}
		return null;
	}

	private static boolean isRename(DiffEntry ent) {
		return ent.getChangeType() == ChangeType.RENAME
				|| ent.getChangeType() == ChangeType.COPY;
	}
}
