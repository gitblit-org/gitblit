/*
 * Copyright 2011 gitblit.com.
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
package com.gitblit.tests;

import static org.junit.Assert.*;

import java.util.Date;

import org.junit.Test;

import com.gitblit.utils.ObjectCache;

public class ObjectCacheTest {

	@Test
	public void testCache() throws Exception {
		ObjectCache<String> cache = new ObjectCache<String>();
		cache.updateObject("test", "alpha");
		Date date = cache.getDate("test");
		assertTrue("cache date is not working!", cache.hasCurrent("test", date));
		// The cache is time-based (msecs) so we insert this artificial sleep to
		// ensure that time (msecs) advances. The ObjectCache class is suitable
		// for Gitblit's needs but may not be suitable for other needs.
		Thread.sleep(10);
		cache.updateObject("test", "beta");
		assertFalse("update cache date is not working!", cache.hasCurrent("test", date));
		assertEquals("unexpected cache object", cache.getObject("test"), "beta");
		assertEquals("beta", cache.remove("test"));
		assertEquals(null, cache.getObject("test"));
		assertEquals(null, cache.remove("test"));
	}
}
