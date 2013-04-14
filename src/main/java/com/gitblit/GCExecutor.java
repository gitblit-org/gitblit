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
package com.gitblit;

import java.lang.reflect.Field;
import java.text.MessageFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.internal.storage.file.GC.RepoStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.models.RepositoryModel;
import com.gitblit.utils.FileUtils;

/**
 * The GC executor handles periodic garbage collection in repositories.
 * 
 * @author James Moger
 * 
 */
public class GCExecutor implements Runnable {

	public static enum GCStatus {
		READY, COLLECTING;
		
		public boolean exceeds(GCStatus s) {
			return ordinal() > s.ordinal();
		}
	}
	private final Logger logger = LoggerFactory.getLogger(GCExecutor.class);

	private final IStoredSettings settings;
	
	private AtomicBoolean running = new AtomicBoolean(false);
	
	private AtomicBoolean forceClose = new AtomicBoolean(false);
	
	private final Map<String, GCStatus> gcCache = new ConcurrentHashMap<String, GCStatus>();

	public GCExecutor(IStoredSettings settings) {
		this.settings = settings;
	}

	/**
	 * Indicates if the GC executor is ready to process repositories.
	 * 
	 * @return true if the GC executor is ready to process repositories
	 */
	public boolean isReady() {
		return settings.getBoolean(Keys.git.enableGarbageCollection, false);
	}
	
	public boolean isRunning() {
		return running.get();
	}
	
	public boolean lock(String repositoryName) {
		return setGCStatus(repositoryName, GCStatus.COLLECTING);
	}

	/**
	 * Tries to set a GCStatus for the specified repository.
	 * 
	 * @param repositoryName
	 * @return true if the status has been set
	 */
	private boolean setGCStatus(String repositoryName, GCStatus status) {
		String key = repositoryName.toLowerCase();
		if (gcCache.containsKey(key)) {
			if (gcCache.get(key).exceeds(GCStatus.READY)) {
				// already collecting or blocked
				return false;
			}
		}
		gcCache.put(key, status);
		return true;
	}

	/**
	 * Returns true if Gitblit is actively collecting garbage in this repository.
	 * 
	 * @param repositoryName
	 * @return true if actively collecting garbage
	 */
	public boolean isCollectingGarbage(String repositoryName) {
		String key = repositoryName.toLowerCase();
		return gcCache.containsKey(key) && GCStatus.COLLECTING.equals(gcCache.get(key));
	}

	/**
	 * Resets the GC status to ready.
	 * 
	 * @param repositoryName
	 */
	public void releaseLock(String repositoryName) {
		gcCache.put(repositoryName.toLowerCase(), GCStatus.READY);
	}
	
	public void close() {
		forceClose.set(true);
	}

	@Override
	public void run() {
		if (!isReady()) {
			return;
		}
		
		running.set(true);		
		Date now = new Date();

		for (String repositoryName : GitBlit.self().getRepositoryList()) {
			if (forceClose.get()) {
				break;
			}
			if (isCollectingGarbage(repositoryName)) {
				logger.warn(MessageFormat.format("Already collecting garbage from {0}?!?", repositoryName));
				continue;
			}
			boolean garbageCollected = false;
			RepositoryModel model = null;
			FileRepository repository = null;
			try {
				model = GitBlit.self().getRepositoryModel(repositoryName);
				repository = (FileRepository) GitBlit.self().getRepository(repositoryName);
				if (repository == null) {
					logger.warn(MessageFormat.format("GCExecutor is missing repository {0}?!?", repositoryName));
					continue;
				}
				
				if (!isRepositoryIdle(repository)) {
					logger.debug(MessageFormat.format("GCExecutor is skipping {0} because it is not idle", repositoryName));
					continue;
				}

				// By setting the GCStatus to COLLECTING we are
				// disabling *all* access to this repository from Gitblit.
				// Think of this as a clutch in a manual transmission vehicle.
				if (!setGCStatus(repositoryName, GCStatus.COLLECTING)) {
					logger.warn(MessageFormat.format("Can not acquire GC lock for {0}, skipping", repositoryName));
					continue;
				}
				
				logger.debug(MessageFormat.format("GCExecutor locked idle repository {0}", repositoryName));
				
				GC gc = new GC(repository);
				RepoStatistics stats = gc.getStatistics();
				
				// determine if this is a scheduled GC
				Calendar cal = Calendar.getInstance();
				cal.setTime(model.lastGC);
				cal.set(Calendar.HOUR_OF_DAY, 0);
				cal.set(Calendar.MINUTE, 0);
				cal.set(Calendar.SECOND, 0);
				cal.set(Calendar.MILLISECOND, 0);
				cal.add(Calendar.DATE, model.gcPeriod);
				Date gcDate = cal.getTime();
				boolean shouldCollectGarbage = now.after(gcDate);

				// determine if filesize triggered GC
				long gcThreshold = FileUtils.convertSizeToLong(model.gcThreshold, 500*1024L);
				boolean hasEnoughGarbage = stats.sizeOfLooseObjects >= gcThreshold;

				// if we satisfy one of the requirements, GC
				boolean hasGarbage = stats.sizeOfLooseObjects > 0;
				if (hasGarbage && (hasEnoughGarbage || shouldCollectGarbage)) {
					long looseKB = stats.sizeOfLooseObjects/1024L;
					logger.info(MessageFormat.format("Collecting {1} KB of loose objects from {0}", repositoryName, looseKB));
					
					// do the deed
					gc.gc();
					
					garbageCollected = true;
				}
			} catch (Exception e) {
				logger.error("Error collecting garbage in " + repositoryName, e);
			} finally {
				// cleanup
				if (repository != null) {
					if (garbageCollected) {
						// update the last GC date
						model.lastGC = new Date();
						GitBlit.self().updateConfiguration(repository, model);
					}
				
					repository.close();
				}
				
				// reset the GC lock 
				releaseLock(repositoryName);
				logger.debug(MessageFormat.format("GCExecutor released GC lock for {0}", repositoryName));
			}
		}
		
		running.set(false);
	}
	
	private boolean isRepositoryIdle(FileRepository repository) {
		try {
			// Read the use count.
			// An idle use count is 2:
			// +1 for being in the cache
			// +1 for the repository parameter in this method
			Field useCnt = Repository.class.getDeclaredField("useCnt");
			useCnt.setAccessible(true);
			int useCount = ((AtomicInteger) useCnt.get(repository)).get();
			return useCount == 2;
		} catch (Exception e) {
			logger.warn(MessageFormat
					.format("Failed to reflectively determine use count for repository {0}",
							repository.getDirectory().getPath()), e);
		}
		return false;
	}
}
