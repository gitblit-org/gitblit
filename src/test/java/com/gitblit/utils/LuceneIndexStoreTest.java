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

package com.gitblit.utils;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;


/**
 * @author Florian Zschocke
 *
 */
public class LuceneIndexStoreTest
{

	private final int LUCENE_VERSION = LuceneIndexStore.LUCENE_CODEC_VERSION;

	@Rule
	public TemporaryFolder baseFolder = new TemporaryFolder();

	private String getIndexDir(int version)
	{
		return version + "_" + LUCENE_VERSION;
	}



	@Test
	public void testCreate()
	{
		int version = 0;
		File luceneFolder = new File(baseFolder.getRoot(), "tickets/lucene");
		assertFalse("Precondition failure: directory exists already", luceneFolder.exists());

		LuceneIndexStore li = new LuceneIndexStore(luceneFolder, version);
		li.create();

		File luceneDir = new File(luceneFolder, getIndexDir(version));
		assertTrue(luceneDir.exists());
	}

	@Test
	public void testCreateIndexDir()
	{
		int version = 111222;
		File luceneFolder = null;
		try {
			luceneFolder = baseFolder.newFolder("tickets", "lucene");
		}
		catch (IOException e) {
			fail("Failed in setup of folder: " + e);
		}
		assertTrue("Precondition failure: directory does not exist", luceneFolder.exists());

		LuceneIndexStore li = new LuceneIndexStore(luceneFolder, version);
		li.create();

		File luceneDir = new File(luceneFolder, getIndexDir(version));
		assertTrue(luceneDir.exists());
		assertTrue(luceneDir.isDirectory());

		// Make sure nothing else was created.
		assertEquals(0, luceneDir.list().length);
		assertEquals(1, luceneDir.getParentFile().list().length);
		assertEquals(1, luceneDir.getParentFile().getParentFile().list().length);
	}

	@Test
	public void testCreateIfNecessary()
	{
		int version = 1;
		File luceneFolder = new File(baseFolder.getRoot(), "tickets/lucene");
		File luceneDir = null;
		try {
			luceneDir = baseFolder.newFolder("tickets", "lucene", getIndexDir(version));
		}
		catch (IOException e) {
			fail("Failed in setup of folder: " + e);
		}
		assertTrue("Precondition failure: directory does not exist", luceneDir.exists());

		LuceneIndexStore li = new LuceneIndexStore(luceneFolder, version);
		li.create();

		assertTrue(luceneDir.exists());
		assertTrue(luceneDir.isDirectory());

		// Make sure nothing else was created.
		assertEquals(0, luceneDir.list().length);
		assertEquals(1, luceneDir.getParentFile().list().length);
		assertEquals(1, luceneDir.getParentFile().getParentFile().list().length);
	}

	@Test
	public void testDelete()
	{
		int version = 111222333;
		File luceneFolder = new File(baseFolder.getRoot(), "repo1/lucene");
		File luceneDir = null;
		try {
			luceneDir = baseFolder.newFolder("repo1", "lucene", getIndexDir(version));
		}
		catch (IOException e) {
			fail("Failed in setup of folder: " + e);
		}
		assertTrue("Precondition failure: index directory does not exist", luceneDir.exists());

		LuceneIndexStore li = new LuceneIndexStore(luceneFolder, version);
		assertTrue(li.delete());

		assertFalse(luceneDir.exists());
		assertTrue(luceneFolder.exists());
	}

	@Test
	public void testDeleteNotExist()
	{
		int version = 0;

		File luceneFolder = null;
		try {
			luceneFolder = baseFolder.newFolder("repo1", "lucene");
		}
		catch (IOException e) {
			fail("Failed in setup of folder: " + e);
		}
		File luceneDir = new File(luceneFolder, getIndexDir(version));
		assertFalse("Precondition failure: index directory exists already", luceneDir.exists());

		LuceneIndexStore li = new LuceneIndexStore(luceneFolder, version);
		assertTrue(li.delete());

		assertFalse(luceneDir.exists());
		assertTrue(luceneFolder.exists());
	}

	@Test
	public void testDeleteWithFiles()
	{
		int version = 111222333;

		File luceneFolder = new File(baseFolder.getRoot(), "tickets/lucene");
		File luceneDir = null;

		File otherDir = new File(baseFolder.getRoot(), "tickets/lucene/" + version + "_10");
		File dbFile = null;
		try {
			luceneDir = baseFolder.newFolder("tickets", "lucene", getIndexDir(version));
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

		LuceneIndexStore li = new LuceneIndexStore(luceneFolder, version);
		li.delete();

		assertFalse(luceneDir.exists());
		assertTrue(luceneFolder.exists());
		assertTrue(otherDir.exists());
		assertTrue(dbFile.exists());
	}




	@Test
	public void testGetPath() throws IOException
	{
		int version = 2;
		File luceneFolder = baseFolder.newFolder("tickets", "lucene");
		LuceneIndexStore li = new LuceneIndexStore(luceneFolder, version);
		Path dir = li.getPath();
		File luceneDir = new File(luceneFolder, getIndexDir(version));
		assertEquals(luceneDir.toPath(), dir);
	}



	@Test
	public void testHasIndex() throws IOException
	{
		int version = 0;
		File luceneFolder = new File(baseFolder.getRoot(), "ticktock/lucene");

		LuceneIndexStore li = new LuceneIndexStore(luceneFolder, version);
		assertFalse(li.hasIndex());

		baseFolder.newFolder("ticktock");
		li = new LuceneIndexStore(luceneFolder, version);
		assertFalse(li.hasIndex());

		baseFolder.newFolder("ticktock", "lucene");
		li = new LuceneIndexStore(luceneFolder, version);
		assertFalse(li.hasIndex());

		File luceneDir = baseFolder.newFolder("ticktock", "lucene", getIndexDir(version));
		li = new LuceneIndexStore(luceneFolder, version);
		assertFalse(li.hasIndex());

		new File(luceneDir, "write.lock").createNewFile();
		li = new LuceneIndexStore(luceneFolder, version);
		assertFalse(li.hasIndex());

		new File(luceneDir, "segments_1").createNewFile();
		li = new LuceneIndexStore(luceneFolder, version);
		assertTrue(li.hasIndex());

	}

}
