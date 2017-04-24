/*
 * Copyright 2017 gitblit.com.
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

package com.gitblit.service;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.gitblit.utils.LuceneIndexStore;

/**
 * @author Florian Zschocke
 *
 */
public class LuceneRepoIndexStoreTest
{

	private static final int LUCENE_VERSION = LuceneIndexStore.LUCENE_CODEC_VERSION;
	private static final String LUCENE_DIR = "lucene";


	@Rule
	public TemporaryFolder baseFolder = new TemporaryFolder();


	private String getIndexDir(int version)
	{
		return version + "_" + LUCENE_VERSION;
	}


	private String getLuceneIndexDir(int version)
	{
		return LUCENE_DIR + "/" + version + "_" + LUCENE_VERSION;
	}


	@Test
	public void testGetConfigFile() throws IOException
	{
		int version = 1;
		File repositoryFolder = baseFolder.getRoot();
		LuceneRepoIndexStore li = new LuceneRepoIndexStore(repositoryFolder, version);
		File confFile= li.getConfigFile();
		File luceneDir = new File(repositoryFolder, getLuceneIndexDir(version) + "/gb_lucene.conf");
		assertEquals(luceneDir, confFile);
	}


	@Test
	public void testCreate()
	{
		int version = 0;
		File repositoryFolder = baseFolder.getRoot();
		File luceneDir = new File(repositoryFolder, getLuceneIndexDir(version));
		assertFalse("Precondition failure: directory exists already", new File(repositoryFolder, LUCENE_DIR).exists());
		assertFalse("Precondition failure: directory exists already", luceneDir.exists());

		LuceneIndexStore li = new LuceneRepoIndexStore(repositoryFolder, version);
		li.create();

		assertTrue(luceneDir.exists());
		assertTrue(luceneDir.isDirectory());
	}

	@Test
	public void testCreateIndexDir()
	{
		int version = 7777;
		File repositoryFolder = baseFolder.getRoot();
		try {
			baseFolder.newFolder(LUCENE_DIR);
		} catch (IOException e) {
			fail("Failed in setup of folder: " + e);
		}
		File luceneDir = new File(repositoryFolder, getLuceneIndexDir(version));

		assertTrue("Precondition failure: directory does not exist", new File(repositoryFolder, LUCENE_DIR).exists());
		assertFalse("Precondition failure: directory exists already", luceneDir.exists());

		LuceneIndexStore li = new LuceneRepoIndexStore(repositoryFolder, version);
		li.create();

		assertTrue(luceneDir.exists());
		assertTrue(luceneDir.isDirectory());

		// Make sure nothing else was created.
		assertEquals(0, luceneDir.list().length);
		assertEquals(1, luceneDir.getParentFile().list().length);
	}

	@Test
	public void testCreateIfNecessary()
	{
		int version = 7777888;
		File repositoryFolder = baseFolder.getRoot();
		File luceneDir = null;
		try {
			luceneDir = baseFolder.newFolder(LUCENE_DIR, getIndexDir(version));
		} catch (IOException e) {
			fail("Failed in setup of folder: " + e);
		}
		assertTrue("Precondition failure: directory does not exist", new File(repositoryFolder, LUCENE_DIR).exists());
		assertTrue("Precondition failure: directory does not exist", luceneDir.exists());

		LuceneIndexStore li = new LuceneRepoIndexStore(repositoryFolder, version);
		li.create();

		assertTrue(luceneDir.exists());
		assertTrue(luceneDir.isDirectory());

		// Make sure nothing else was created.
		assertEquals(0, luceneDir.list().length);
		assertEquals(1, luceneDir.getParentFile().list().length);
	}


	@Test
	public void testDelete()
	{
		int version = 111222333;

		File repositoryFolder = baseFolder.getRoot();
		File luceneDir = null;
		try {
			luceneDir = baseFolder.newFolder(LUCENE_DIR, getIndexDir(version));
		} catch (IOException e) {
			fail("Failed in setup of folder: " + e);
		}
		assertTrue("Precondition failure: directory does not exist", luceneDir.exists());

		LuceneIndexStore li = new LuceneRepoIndexStore(repositoryFolder, version);
		assertTrue(li.delete());

		assertFalse(luceneDir.exists());
		assertTrue(new File(repositoryFolder, LUCENE_DIR).exists());
	}


	@Test
	public void testDeleteNotExist()
	{
		int version = 0;

		File repositoryFolder = baseFolder.getRoot();
		try {
			baseFolder.newFolder(LUCENE_DIR);
		} catch (IOException e) {
			fail("Failed in setup of folder: " + e);
		}
		File luceneDir = new File(repositoryFolder, getLuceneIndexDir(version));
		assertTrue("Precondition failure: directory does not exist", new File(repositoryFolder, LUCENE_DIR).exists());
		assertFalse("Precondition failure: directory does exist", luceneDir.exists());

		LuceneIndexStore li = new LuceneRepoIndexStore(repositoryFolder, version);
		assertTrue(li.delete());

		assertFalse(luceneDir.exists());
		assertTrue(new File(repositoryFolder, LUCENE_DIR).exists());
	}


	@Test
	public void testDeleteWithFiles()
	{
		int version = 5;

		File repositoryFolder = baseFolder.getRoot();
		File luceneFolder = new File(baseFolder.getRoot(), LUCENE_DIR);
		File luceneDir = null;

		File otherDir = new File(luceneFolder, version + "_10");
		File dbFile = null;
		try {
			luceneDir = baseFolder.newFolder(LUCENE_DIR, getIndexDir(version));
			File file = new File(luceneDir, "_file1");
			file.createNewFile();
			file = new File(luceneDir, "_file2.db");
			file.createNewFile();
			file = new File(luceneDir, "conf.conf");
			file.createNewFile();

			otherDir.mkdirs();
			dbFile = new File(otherDir, "_file2.db");
			dbFile.createNewFile();
			file = new File(otherDir, "conf.conf");
			file.createNewFile();
		}
		catch (IOException e) {
			fail("Failed in setup of folder: " + e);
		}
		assertTrue("Precondition failure: index directory does not exist", luceneDir.exists());
		assertTrue("Precondition failure: other index directory does not exist", otherDir.exists());

		LuceneIndexStore li = new LuceneRepoIndexStore(repositoryFolder, version);
		li.delete();

		assertFalse(luceneDir.exists());
		assertTrue(luceneFolder.exists());
		assertTrue(otherDir.exists());
		assertTrue(dbFile.exists());
	}



	@Test
	public void testGetPath() throws IOException
	{
		int version = 7;
		File repositoryFolder = baseFolder.getRoot();
		LuceneIndexStore li = new LuceneRepoIndexStore(repositoryFolder, version);
		Path dir = li.getPath();
		File luceneDir = new File(repositoryFolder, getLuceneIndexDir(version));
		assertEquals(luceneDir.toPath(), dir);
	}


	@Test
	public void testHasIndex() throws IOException
	{
		int version = 0;
		File luceneFolder = new File(baseFolder.getRoot(), "lucene");

		LuceneIndexStore li = new LuceneRepoIndexStore(luceneFolder, version);
		assertFalse(li.hasIndex());

		baseFolder.newFolder("lucene");
		li = new LuceneIndexStore(luceneFolder, version);
		assertFalse(li.hasIndex());

		File luceneDir = baseFolder.newFolder("lucene", getIndexDir(version));
		li = new LuceneIndexStore(luceneFolder, version);
		assertFalse(li.hasIndex());

		new File(luceneDir, "write.lock").createNewFile();
		li = new LuceneIndexStore(luceneFolder, version);
		assertFalse(li.hasIndex());

		new File(luceneDir, "segments_1").createNewFile();
		li = new LuceneIndexStore(luceneFolder, version);
		System.out.println("Check " + luceneDir);
		assertTrue(li.hasIndex());

	}


}
