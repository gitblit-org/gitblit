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

import java.io.File;

import com.gitblit.utils.LuceneIndexStore;

/**
 * @author Florian Zschocke
 *
 * @since 1.9.0
 */
class LuceneRepoIndexStore extends LuceneIndexStore
{

	private static final String LUCENE_DIR = "lucene";
	private static final String CONF_FILE = "gb_lucene.conf";


	/**
	 * @param repositoryFolder
	 * 			The directory of the repository for this index
	 * @param indexVersion
	 * 			Version of the index definition
	 */
	public LuceneRepoIndexStore(File repositoryFolder, int indexVersion) {
		super(new File(repositoryFolder, LUCENE_DIR), indexVersion);
	}


	/**
	 * Get the index config File.
	 *
	 * @return	The index config File
	 */
	public File getConfigFile() {
		return new File(this.indexFolder, CONF_FILE);
	}

}
