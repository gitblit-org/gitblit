/*
 * Copyright 2015 gitblit.com.
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
package com.gitblit.manager;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import com.gitblit.models.FilestoreModel;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;


public interface IFilestoreManager extends IManager {

	boolean isValidOid(String oid);
	
	FilestoreModel.Status addObject(String oid, long size, UserModel user, RepositoryModel repo);
	
	FilestoreModel getObject(String oid, UserModel user, RepositoryModel repo);
	
	FilestoreModel.Status uploadBlob(String oid, long size, UserModel user, RepositoryModel repo, InputStream streamIn );
	
	FilestoreModel.Status downloadBlob(String oid, UserModel user, RepositoryModel repo, OutputStream streamOut );
	
	List<FilestoreModel> getAllObjects();
	
	File getStorageFolder();
	
	File getStoragePath(String oid);
	
	long getMaxUploadSize();
	
	void clearFilestoreCache();
	
	long getFilestoreUsedByteCount();
	
	long getFilestoreAvailableByteCount();

}
