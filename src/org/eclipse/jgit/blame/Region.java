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

/**
 * Region of the result that still needs to be computed.
 * <p>
 * Regions are held in a singly-linked-list by {@link Candidate} using the
 * {@link Candidate#regionList} field. The list is kept in sorted order by
 * {@link #resultStart}.
 */
class Region {
	/** Next entry in the region linked list. */
	Region next;

	/** First position of this region in the result file blame is computing. */
	int resultStart;

	/** First position in the {@link Candidate} that owns this Region. */
	int sourceStart;

	/** Length of the region, always >= 1. */
	int length;

	Region(int rs, int ss, int len) {
		resultStart = rs;
		sourceStart = ss;
		length = len;
	}

	/**
	 * Copy the entire result region, but at a new source position.
	 *
	 * @param newSource
	 *            the new source position.
	 * @return the same result region, but offset for a new source.
	 */
	Region copy(int newSource) {
		return new Region(resultStart, newSource, length);
	}

	/**
	 * Split the region, assigning a new source position to the first half.
	 *
	 * @param newSource
	 *            the new source position.
	 * @param newLen
	 *            length of the new region.
	 * @return the first half of the region, at the new source.
	 */
	Region splitFirst(int newSource, int newLen) {
		return new Region(resultStart, newSource, newLen);
	}

	/**
	 * Edit this region to remove the first {@code d} elements.
	 *
	 * @param d
	 *            number of elements to remove from the start of this region.
	 */
	void slideAndShrink(int d) {
		resultStart += d;
		sourceStart += d;
		length -= d;
	}

	Region deepCopy() {
		Region head = new Region(resultStart, sourceStart, length);
		Region tail = head;
		for (Region n = next; n != null; n = n.next) {
			Region q = new Region(n.resultStart, n.sourceStart, n.length);
			tail.next = q;
			tail = q;
		}
		return head;
	}

	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder();
		Region r = this;
		do {
			if (r != this)
				buf.append(',');
			buf.append(r.resultStart);
			buf.append('-');
			buf.append(r.resultStart + r.length);
			r = r.next;
		} while (r != null);
		return buf.toString();
	}
}
