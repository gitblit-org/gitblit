/*
 * Copyright 2012 gitblit.com.
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import com.gitblit.utils.ArrayUtils;

public class ArrayUtilsTest {

	@Test
	public void testArrays() {
		Object [] nullArray = null;
		assertTrue(ArrayUtils.isEmpty(nullArray));

		Object [] emptyArray = new Object[0];
		assertTrue(ArrayUtils.isEmpty(emptyArray));
		
		assertFalse(ArrayUtils.isEmpty(new String [] { "" }));
	}
	
	@Test
	public void testLists() {
		List<?> nullList = null;
		assertTrue(ArrayUtils.isEmpty(nullList));

		List<?> emptyList = new ArrayList<Object>();
		assertTrue(ArrayUtils.isEmpty(emptyList));
		
		List<?> list = Arrays.asList("");
		assertFalse(ArrayUtils.isEmpty(list));
	}
	
	@Test
	public void testSets() {
		Set<?> nullSet = null;
		assertTrue(ArrayUtils.isEmpty(nullSet));

		Set<?> emptySet = new HashSet<Object>();
		assertTrue(ArrayUtils.isEmpty(emptySet));
		
		Set<?> set = new HashSet<Object>(Arrays.asList(""));
		assertFalse(ArrayUtils.isEmpty(set));
	}
}