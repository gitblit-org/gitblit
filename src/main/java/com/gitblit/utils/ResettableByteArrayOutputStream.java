// Copyright (C) 2014 Tom <tw201207@gmail.com>
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
package com.gitblit.utils;

import java.io.ByteArrayOutputStream;

/**
 * A {@link ByteArrayOutputStream} that can be reset to a specified position.
 *
 * @author Tom <tw201207@gmail.com>
 */
public class ResettableByteArrayOutputStream extends ByteArrayOutputStream {

	/**
	 * Reset the stream to the given position. If {@code mark} is <= 0, see {@link #reset()}.
	 * A no-op if the stream contains less than {@code mark} bytes. Otherwise, resets the
	 * current writing position to {@code mark}. Previously allocated buffer space will be
	 * reused in subsequent writes.
	 *
	 * @param mark
	 *            to set the current writing position to.
	 */
	public synchronized void resetTo(int mark) {
		if (mark <= 0) {
			reset();
		} else if (mark < count) {
			count = mark;
		}
	}

}
