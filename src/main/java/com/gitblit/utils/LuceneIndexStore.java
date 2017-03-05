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

import java.io.File;
import java.nio.file.Path;

/**
 * @author Florian Zschocke
 *
 * @since 1.9.0
 */
public class LuceneIndexStore
{

	public static final int LUCENE_CODEC_VERSION = 54;

	protected File indexFolder;

	/**
	 * Constructor for a base folder that contains the version specific index folders
	 * and an index version.
	 *
	 * @param luceneFolder
	 * 			Path to the base folder for the Lucene indices, i.e. the common "lucene" directory.
	 * @param indexVersion
	 * 			Version of the index definition
	 */
	public LuceneIndexStore(File luceneFolder, int indexVersion)
	{
		this.indexFolder = new File(luceneFolder, indexVersion + "_" + LUCENE_CODEC_VERSION);
	}



	/**
	 * Create the Lucene index directory for this index version and Lucene codec version
	 */
	public void create()
	{
		if (! indexFolder.exists()) {
			indexFolder.mkdirs();
		}
	}


	/**
	 * Delete the Lucene index directory for this index version and Lucene codec version
	 *
	 * @return	True if the directory could successfully be deleted.
	 */
	public boolean delete()
	{
		if (indexFolder.exists()) {
			return FileUtils.delete(indexFolder);
		}
		return true;
	}



	/**
	 * @return	The Path to the index folder
	 */
	public Path getPath()
	{
		return indexFolder.toPath();
	}



	/**
	 * Check if an index of the respective version, or compatible, already exists.
	 *
	 * @return	True if an index exists, False otherwise
	 */
	public boolean hasIndex()
	{
		return indexFolder.exists() &&
				indexFolder.isDirectory() &&
				(indexFolder.list().length > 1);
	}

}
