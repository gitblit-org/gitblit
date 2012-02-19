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

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

import com.gitblit.models.SearchResult;
import com.gitblit.utils.LuceneUtils;

/**
 * Tests Lucene indexing and querying.
 * 
 * @author James Moger
 * 
 */
public class LuceneUtilsTest {

	@Test
	public void testFullIndex() throws Exception {
		// reindex helloworld
		Repository repository = GitBlitSuite.getHelloworldRepository();
		LuceneUtils.index(repository);
		repository.close();

		// reindex theoretical physics
		repository = GitBlitSuite.getTheoreticalPhysicsRepository();
		LuceneUtils.index(repository);
		repository.close();

		// reindex bluez-gnome
		repository = GitBlitSuite.getBluezGnomeRepository();
		LuceneUtils.index(repository);
		repository.close();

		LuceneUtils.close();
	}

	@Test
	public void testQuery() throws Exception {
		// 2 occurrences on the master branch
		Repository repository = GitBlitSuite.getHelloworldRepository();
		List<SearchResult> results = LuceneUtils.search("ada", 10, repository);
		assertEquals(2, results.size());

		// author test
		results = LuceneUtils.search("author: tinogomes", 10, repository);
		assertEquals(2, results.size());

		repository.close();
		// blob test
		results = LuceneUtils.search("type: blob AND \"import std.stdio\"", 10, repository);
		assertEquals(1, results.size());
		assertEquals("d.D", results.get(0).id);
		
		// 1 occurrence on the gh-pages branch
		repository = GitBlitSuite.getTheoreticalPhysicsRepository();
		results = LuceneUtils.search("\"add the .nojekyll file\"", 10, repository);
		assertEquals(1, results.size());
		assertEquals("Ondrej Certik", results.get(0).author);
		assertEquals("2648c0c98f2101180715b4d432fc58d0e21a51d7", results.get(0).id);
		
		// tag test
		results = LuceneUtils.search("\"qft split\"", 10, repository);
		assertEquals(1, results.size());
		assertEquals("Ondrej Certik", results.get(0).author);
		assertEquals("57c4f26f157ece24b02f4f10f5f68db1d2ce7ff5", results.get(0).id);
		assertEquals("[1st-edition]", results.get(0).labels.toString());

		results = LuceneUtils.search("type:blob AND \"src/intro.rst\"", 10, repository);
		assertEquals(4, results.size());
		
		// hash id tests
		results = LuceneUtils.search("id:57c4f26f157ece24b02f4f10f5f68db1d2ce7ff5", 10, repository);
		assertEquals(1, results.size());

		results = LuceneUtils.search("id:57c4f26f157*", 10, repository);
		assertEquals(1, results.size());

		repository.close();
		
		// annotated tag test
		repository = GitBlitSuite.getBluezGnomeRepository();
		results = LuceneUtils.search("\"release 1.8\"", 10, repository);
		assertEquals(1, results.size());
		assertEquals("[1.8]", results.get(0).labels.toString());

		repository.close();
		
		LuceneUtils.close();
	}
	
	@Test
	public void testMultiSearch() throws Exception {
		List<SearchResult> results = LuceneUtils.search("test", 10,
				GitBlitSuite.getHelloworldRepository(), 
				GitBlitSuite.getBluezGnomeRepository());
		LuceneUtils.close();
		assertEquals(10, results.size());
	}
}