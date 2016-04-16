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

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.models.FilestoreModel;
import com.gitblit.models.FilestoreModel.Status;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.JsonUtils.GmtDateTypeAdapter;
import com.google.gson.ExclusionStrategy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * FilestoreManager handles files uploaded via:
 * 	+ git-lfs
 *  + ticket attachment (TBD)
 *
 * Files are stored using their SHA256 hash (as per git-lfs)
 * If the same file is uploaded through different repositories no additional space is used
 * Access is controlled through the current repository permissions.
 *
 * TODO: Identify what and how the actual BLOBs should work with federation
 *
 * @author Paul Martin
 *
 */
@Singleton
public class FilestoreManager implements IFilestoreManager {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final IRuntimeManager runtimeManager;
	
	private final IStoredSettings settings;

	public static final int UNDEFINED_SIZE = -1;

	private static final String METAFILE = "filestore.json";

	private static final String METAFILE_TMP = "filestore.json.tmp";

	protected static final Type METAFILE_TYPE = new TypeToken<Collection<FilestoreModel>>() {}.getType();

	private Map<String, FilestoreModel > fileCache = new ConcurrentHashMap<String, FilestoreModel>();


	@Inject
	public FilestoreManager(IRuntimeManager runtimeManager) {
		this.runtimeManager = runtimeManager;
		this.settings = runtimeManager.getSettings();
	}

	@Override
	public IManager start() {

		// Try to load any existing metadata
		File dir = getStorageFolder();
		dir.mkdirs();
		File metadata = new File(dir, METAFILE);

		if (metadata.exists()) {
			Collection<FilestoreModel> items = null;

			Gson gson = gson();
			try (FileReader file = new FileReader(metadata)) {
				items = gson.fromJson(file, METAFILE_TYPE);
				file.close();

			} catch (IOException e) {
				e.printStackTrace();
			}

			for(Iterator<FilestoreModel> itr = items.iterator(); itr.hasNext(); ) {
			    FilestoreModel model = itr.next();
			    fileCache.put(model.oid, model);
			}

			logger.info("Loaded {} items from filestore metadata file", fileCache.size());
		}
		else
		{
			logger.info("No filestore metadata file found");
		}

		return this;
	}

	@Override
	public IManager stop() {
		return this;
	}


	@Override
	public boolean isValidOid(String oid) {
		//NOTE: Assuming SHA256 support only as per git-lfs
		return Pattern.matches("[a-fA-F0-9]{64}", oid);
	}

	@Override
	public FilestoreModel.Status addObject(String oid, long size, UserModel user, RepositoryModel repo) {

		//Handle access control
		if (!user.canPush(repo)) {
			if (user == UserModel.ANONYMOUS) {
				return Status.AuthenticationRequired;
			} else {
				return Status.Error_Unauthorized;
			}
		}

		//Handle object details
		if (!isValidOid(oid)) { return Status.Error_Invalid_Oid; }

		if (fileCache.containsKey(oid)) {
			FilestoreModel item = fileCache.get(oid);

			if (!item.isInErrorState() && (size != UNDEFINED_SIZE) && (item.getSize() != size)) {
				return Status.Error_Size_Mismatch;
			}

			item.addRepository(repo.name);

			if (item.isInErrorState()) {
				item.reset(user, size);
			}
		} else {

			if (size  < 0) {return Status.Error_Invalid_Size; }
			if ((getMaxUploadSize() != UNDEFINED_SIZE) && (size > getMaxUploadSize())) { return Status.Error_Exceeds_Size_Limit; }

			FilestoreModel model = new FilestoreModel(oid, size, user, repo.name);
			fileCache.put(oid, model);
			saveFilestoreModel(model);
		}

		return fileCache.get(oid).getStatus();
	}

	@Override
	public FilestoreModel.Status uploadBlob(String oid, long size, UserModel user, RepositoryModel repo, InputStream streamIn) {

		//Access control and object logic
		Status state = addObject(oid, size, user, repo);

		if (state != Status.Upload_Pending) {
			return state;
		}

		FilestoreModel model = fileCache.get(oid);

		if (!model.actionUpload(user)) {
			return Status.Upload_In_Progress;
		} else {
			long actualSize = 0;
			File file = getStoragePath(oid);

			try {
				file.getParentFile().mkdirs();
				file.createNewFile();

				try (FileOutputStream streamOut = new FileOutputStream(file)) {

					actualSize = IOUtils.copyLarge(streamIn, streamOut);

					streamOut.flush();
					streamOut.close();

					if (model.getSize() != actualSize) {
						model.setStatus(Status.Error_Size_Mismatch, user);

						logger.warn(MessageFormat.format("Failed to upload blob {0} due to size mismatch, expected {1} got {2}",
								oid, model.getSize(), actualSize));
					} else {
						String actualOid = "";

						try (FileInputStream fileForHash = new FileInputStream(file)) {
							actualOid = DigestUtils.sha256Hex(fileForHash);
							fileForHash.close();
						}

						if (oid.equalsIgnoreCase(actualOid)) {
							model.setStatus(Status.Available, user);
						} else {
							model.setStatus(Status.Error_Hash_Mismatch, user);

							logger.warn(MessageFormat.format("Failed to upload blob {0} due to hash mismatch, got {1}", oid, actualOid));
						}
					}
				}
			} catch (Exception e) {

				model.setStatus(Status.Error_Unknown, user);
				logger.warn(MessageFormat.format("Failed to upload blob {0}", oid), e);
			} finally {
				saveFilestoreModel(model);
			}

			if (model.isInErrorState()) {
				file.delete();
				model.removeRepository(repo.name);
			}
		}

		return model.getStatus();
	}

	private FilestoreModel.Status canGetObject(String oid, UserModel user, RepositoryModel repo) {

		//Access Control
		if (!user.canView(repo)) {
			if (user == UserModel.ANONYMOUS) {
				return Status.AuthenticationRequired;
			} else {
				return Status.Error_Unauthorized;
			}
		}

		//Object Logic
		if (!isValidOid(oid)) {
			return Status.Error_Invalid_Oid;
		}

		if (!fileCache.containsKey(oid)) {
			return Status.Unavailable;
		}

		FilestoreModel item = fileCache.get(oid);

		if (item.getStatus() == Status.Available) {
			return Status.Available;
		}

		return Status.Unavailable;
	}

	@Override
	public FilestoreModel getObject(String oid, UserModel user, RepositoryModel repo) {

		if (canGetObject(oid, user, repo) == Status.Available) {
			return fileCache.get(oid);
		}

		return null;
	}

	@Override
	public FilestoreModel.Status downloadBlob(String oid, UserModel user, RepositoryModel repo, OutputStream streamOut) {

		//Access control and object logic
		Status status = canGetObject(oid, user, repo);

		if (status != Status.Available) {
			return status;
		}

		FilestoreModel item = fileCache.get(oid);

		if (streamOut != null) {
			try (FileInputStream streamIn = new FileInputStream(getStoragePath(oid))) {

				IOUtils.copyLarge(streamIn, streamOut);

				streamOut.flush();
				streamIn.close();
			} catch (EOFException e) {
				logger.error(MessageFormat.format("Client aborted connection for {0}", oid), e);
				return Status.Error_Unexpected_Stream_End;
			} catch (Exception e) {
				logger.error(MessageFormat.format("Failed to download blob {0}", oid), e);
				return Status.Error_Unknown;
			}
		}

		return item.getStatus();
	}

	@Override
	public List<FilestoreModel> getAllObjects(List<RepositoryModel> viewableRepositories) {
		
		List<String> viewableRepositoryNames = new ArrayList<String>(viewableRepositories.size());
		
		for (RepositoryModel repository : viewableRepositories) {
			viewableRepositoryNames.add(repository.name);
		}
		
		if (viewableRepositoryNames.size() == 0) {
			return null;
		}
		
		final Collection<FilestoreModel> allFiles = fileCache.values();
		List<FilestoreModel> userViewableFiles = new ArrayList<FilestoreModel>(allFiles.size());
		
		for (FilestoreModel file : allFiles) {
			if (file.isInRepositoryList(viewableRepositoryNames)) {
				userViewableFiles.add(file);
			}
		}
		
		return userViewableFiles;				
	}

	@Override
	public File getStorageFolder() {
		return runtimeManager.getFileOrFolder(Keys.filestore.storageFolder, "${baseFolder}/lfs");
	}

	@Override
	public File getStoragePath(String oid) {
		 return new File(getStorageFolder(), oid.substring(0, 2).concat("/").concat(oid.substring(2)));
	}

	@Override
	public long getMaxUploadSize() {
		return settings.getLong(Keys.filestore.maxUploadSize, -1);
	}

	@Override
	public long getFilestoreUsedByteCount() {
		Iterator<FilestoreModel> iterator = fileCache.values().iterator();
		long total = 0;

		while (iterator.hasNext()) {

			FilestoreModel item = iterator.next();
			if (item.getStatus() == Status.Available) {
				total += item.getSize();
			}
		}

		return total;
	}

	@Override
	public long getFilestoreAvailableByteCount() {

		try {
			return Files.getFileStore(getStorageFolder().toPath()).getUsableSpace();
		} catch (IOException e) {
			logger.error(MessageFormat.format("Failed to retrive available space in Filestore {0}", e));
		}

		return UNDEFINED_SIZE;
	};

	private synchronized void saveFilestoreModel(FilestoreModel model) {

		File metaFile = new File(getStorageFolder(), METAFILE);
		File metaFileTmp = new File(getStorageFolder(), METAFILE_TMP);
		boolean isNewFile = false;

		try {
			if (!metaFile.exists()) {
				metaFile.getParentFile().mkdirs();
				metaFile.createNewFile();
				isNewFile = true;
			}
			FileUtils.copyFile(metaFile, metaFileTmp);

		} catch (IOException e) {
			logger.error("Writing filestore model to file {0}, {1}", METAFILE, e);
		}

		try (RandomAccessFile fs = new RandomAccessFile(metaFileTmp, "rw")) {

			if (isNewFile) {
				fs.writeBytes("[");
			} else {
				fs.seek(fs.length() - 1);
				fs.writeBytes(",");
			}

			fs.writeBytes(gson().toJson(model));
			fs.writeBytes("]");

			fs.close();

		} catch (IOException e) {
			logger.error("Writing filestore model to file {0}, {1}", METAFILE_TMP, e);
		}

		try {
			if (metaFileTmp.exists()) {
				FileUtils.copyFile(metaFileTmp, metaFile);

				metaFileTmp.delete();
			} else {
				logger.error("Writing filestore model to file {0}", METAFILE);
			}
		}
		catch (IOException e) {
			logger.error("Writing filestore model to file {0}, {1}", METAFILE, e);
		}
	}

	/*
	 * Intended for testing purposes only
	 */
	@Override
	public void clearFilestoreCache() {
		fileCache.clear();
	}

	private static Gson gson(ExclusionStrategy... strategies) {
		GsonBuilder builder = new GsonBuilder();
		builder.registerTypeAdapter(Date.class, new GmtDateTypeAdapter());
		if (!ArrayUtils.isEmpty(strategies)) {
			builder.setExclusionStrategies(strategies);
		}
		return builder.create();
	}

}
