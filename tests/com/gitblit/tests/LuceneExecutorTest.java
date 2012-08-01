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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

import com.gitblit.LuceneExecutor;
import com.gitblit.models.RefModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.SearchResult;
import com.gitblit.tests.mock.MemorySettings;
import com.gitblit.utils.FileUtils;
import com.gitblit.utils.JGitUtils;

/**
 * Tests Lucene indexing and querying.
 * 
 * @author James Moger
 * 
 */
public class LuceneExecutorTest {

	private LuceneExecutor newLuceneExecutor() {
		Map<String, Object> map = new HashMap<String, Object>();
		MemorySettings settings = new MemorySettings(map);		
		return new LuceneExecutor(settings, GitBlitSuite.REPOSITORIES);
	}
	
	private RepositoryModel newRepositoryModel(Repository repository) {		
		RepositoryModel model = new RepositoryModel();
		model.name = FileUtils.getRelativePath(GitBlitSuite.REPOSITORIES, repository.getDirectory());
		model.hasCommits = JGitUtils.hasCommits(repository);
		
		// index all local branches
		model.indexedBranches = new ArrayList<String>();
		for (RefModel ref : JGitUtils.getLocalBranches(repository, true, -1)) {
			model.indexedBranches.add(ref.getName());
		}
		return model;
	}
	
	@Test
	public void testIndex() throws Exception {
		LuceneExecutor lucene = newLuceneExecutor();
		
		// reindex helloworld
		Repository repository = GitBlitSuite.getHelloworldRepository();
		RepositoryModel model = newRepositoryModel(repository);
		lucene.reindex(model, repository);
		repository.close();
		
		SearchResult result = lucene.search("type:blob AND path:bit.bit", 1, 1, model.name).get(0);		
		assertEquals("Mike Donaghy", result.author);
		result = lucene.search("type:blob AND path:clipper.prg", 1, 1, model.name).get(0);		
		assertEquals("tinogomes", result.author);		

		// reindex theoretical physics
		repository = GitBlitSuite.getTheoreticalPhysicsRepository();
		model = newRepositoryModel(repository);
		lucene.reindex(model, repository);
		repository.close();
		
		// reindex JGit
		repository = GitBlitSuite.getJGitRepository();
		model = newRepositoryModel(repository);
		lucene.reindex(model, repository);
		repository.close();
		
		lucene.close();
	}

	@Test
	public void testQuery() throws Exception {
		LuceneExecutor lucene = new LuceneExecutor(null, GitBlitSuite.REPOSITORIES);
		
		// 2 occurrences on the master branch
		Repository repository = GitBlitSuite.getHelloworldRepository();				
		RepositoryModel model = newRepositoryModel(repository);
		repository.close();
		
		List<SearchResult> results = lucene.search("ada", 1, 10, model.name);
		assertEquals(2, results.size());
		for (SearchResult res : results) {
			assertEquals("refs/heads/master", res.branch);
		}

		// author test
		results = lucene.search("author: tinogomes AND type:commit", 1, 10, model.name);
		assertEquals(2, results.size());
		
		// blob test
		results = lucene.search("type: blob AND \"import std.stdio\"", 1, 10, model.name);
		assertEquals(1, results.size());
		assertEquals("d.D", results.get(0).path);
		
		// 1 occurrence on the gh-pages branch
		repository = GitBlitSuite.getTheoreticalPhysicsRepository();
		model = newRepositoryModel(repository);
		repository.close();
		
		results = lucene.search("\"add the .nojekyll file\"", 1, 10, model.name);
		assertEquals(1, results.size());
		assertEquals("Ondrej Certik", results.get(0).author);
		assertEquals("2648c0c98f2101180715b4d432fc58d0e21a51d7", results.get(0).commitId);
		assertEquals("refs/heads/gh-pages", results.get(0).branch);
		
		results = lucene.search("type:blob AND \"src/intro.rst\"", 1, 10, model.name);
		assertEquals(4, results.size());
		
		// hash id tests
		results = lucene.search("commit:57c4f26f157ece24b02f4f10f5f68db1d2ce7ff5", 1, 10, model.name);
		assertEquals(1, results.size());

		results = lucene.search("commit:57c4f26f157*", 1, 10, model.name);
		assertEquals(1, results.size());		
		
		// annotated tag test
		repository = GitBlitSuite.getJGitRepository();
		model = newRepositoryModel(repository);
		repository.close();
		
		results = lucene.search("I663208919f297836a9c16bf458e4a43ffaca4c12", 1, 10, model.name);
		assertEquals(1, results.size());
		assertEquals("[v1.3.0.201202151440-r]", results.get(0).tags.toString());		
		
		lucene.close();
	}
	
	@Test
	public void testMultiSearch() throws Exception {
		LuceneExecutor lucene = newLuceneExecutor();
		List<String> list = new ArrayList<String>();
		Repository repository = GitBlitSuite.getHelloworldRepository();
		list.add(newRepositoryModel(repository).name);
		repository.close();

		repository = GitBlitSuite.getJGitRepository();
		list.add(newRepositoryModel(repository).name);
		repository.close();

		List<SearchResult> results = lucene.search("test", 1, 10, list);
		lucene.close();
		assertEquals(10, results.size());
	}
	
	@Test
	public void testDeleteBlobFromIndex() throws Exception {
		// start with a fresh reindex of entire repository
		LuceneExecutor lucene = newLuceneExecutor();
		Repository repository = GitBlitSuite.getHelloworldRepository();
		RepositoryModel model = newRepositoryModel(repository);
		lucene.reindex(model, repository);
		
		// now delete a blob
		assertTrue(lucene.deleteBlob(model.name, "refs/heads/master", "java.java"));
		assertFalse(lucene.deleteBlob(model.name, "refs/heads/master", "java.java"));
	}
}