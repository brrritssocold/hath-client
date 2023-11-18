/*

Copyright 2008-2023 E-Hentai.org
https://forums.e-hentai.org/
tenboro@e-hentai.org

This file is part of Hentai@Home.

Hentai@Home is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Hentai@Home is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Hentai@Home.  If not, see <http://www.gnu.org/licenses/>.

*/

package hath.base;

import java.lang.Thread;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.List;
import java.util.Hashtable;

public class CacheHandler {
	private static final int MEMORY_TABLE_ELEMENTS = 1048576;
	private Hashtable<String, Long> staticRangeOldest = null;
	private HentaiAtHomeClient client = null;
	private File cachedir = null;
	private short[] lruCacheTable = null;
	private int cacheCount = 0, lruClearPointer = 0, lruSkipCheckCycle = 0, pruneAggression = 1;
	private long cacheSize = 0;
	private boolean cacheLoaded = false;

	public CacheHandler(HentaiAtHomeClient client) throws java.io.IOException {
		this.client = client;

		cachedir = Settings.getCacheDir();

		// delete orphans from the temp dir
		for(File tmpfile : Settings.getTempDir().listFiles()) {
			if(tmpfile.isFile()) {
				// some silly people might set the data and/or log dir to the same as the temp dir
				if(!tmpfile.getName().startsWith("log_") && !tmpfile.getName().startsWith("pcache_") && !tmpfile.getName().equals("client_login")) {
					Out.debug("CacheHandler: Deleted orphaned temporary file " + tmpfile);
					tmpfile.delete();
				}
			}
			else {
				Out.warning("CacheHandler: Found a non-file " + tmpfile + " in the temp directory, won't delete.");
			}
		}

		boolean fastStartup = false;

		if(!Settings.isRescanCache()) {
			Out.info("CacheHandler: Attempting to load persistent cache data...");

			if(loadPersistentData()) {
				Out.info("CacheHandler: Successfully loaded persistent cache data");
				fastStartup = true;
			}
			else {
				Out.info("CacheHandler: Persistent cache data is not available");
			}
		}

		deletePersistentData();

		if(!fastStartup) {
			Out.info("CacheHandler: Initializing the cache system...");

			// do the initial cache cleanup/reorg. this will move any qualifying files left in the first-level cache directory to the second level.
			startupCacheCleanup();
			System.gc();

			if(client.isShuttingDown()) {
				return;
			}

			// we need to zero out everything in case of a partially failed persistent load
			lruClearPointer = 0;
			cacheCount = 0;
			cacheSize = 0;

			// this is a map with the static ranges in the cache as key and the oldest lastModified file timestamp for every range as value. this is used to find old files to delete if the cache fills up.
			staticRangeOldest = new Hashtable<String,Long>((int) (Settings.getStaticRangeCount() * 1.5));

			if(!Settings.isUseLessMemory()) {
				lruCacheTable = new short[MEMORY_TABLE_ELEMENTS];
			}

			// scan the cache to calculate the total filecount and size, as well as initialize the LRU cache based on the lastModified timestamps.
			// this verifies that the files are the correct size and in an assigned static range, and optionally verifies the SHA-1 hash.
			// the staticRangeOldest hashtable of static ranges and the oldest file timestamp in that range will also be built here. 
			startupInitCache();
			System.gc();
		}

		if(!recheckFreeDiskSpace()) {
			// note: if the client ends up being starved on disk space with static ranges assigned, it will cause a major loss of trust.
			client.setFastShutdown();
			client.dieWithError("The storage device does not have enough space available to hold the given cache size.\nFree up space for H@H, or reduce the cache size from the H@H settings page:\nhttps://e-hentai.org/hentaiathome.php?cid=" + Settings.getClientID());
		}

		if(cacheCount < 1 && Settings.getStaticRangeCount() > 20) {
			// note: if the client is started with an empty cache and many static ranges assigned, it will cause a major loss of trust.
			client.setFastShutdown();
			client.dieWithError("This client has static ranges assigned to it, but the cache is empty. Check file permissions and file system integrity.\nIf the cache has been deleted or is otherwise lost, you have to manually reset your static ranges from the H@H settings page.\nhttps://e-hentai.org/hentaiathome.php?cid=" + Settings.getClientID());
		}

		long cacheLimit = Settings.getDiskLimitBytes();

		if(getCacheSizeWithOverhead() > cacheLimit) {
			Out.info("CacheHandler: We are over the cache limit, pruning until the limit is met");
			int iterations = 0;
			java.text.DecimalFormat f = new java.text.DecimalFormat("###.00");

			while(getCacheSizeWithOverhead() > cacheLimit) {
				if(iterations++ % 100 == 0) {
					Out.info("CacheHandler: Cache is currently at " + f.format(100.0 * getCacheSizeWithOverhead() / cacheLimit) + "%");
				}

				recheckFreeDiskSpace();
				System.gc();
			}

			Out.info("CacheHandler: Finished startup cache pruning");
		}

		cacheLoaded = true;
	}

	private File getPersistentLRUFile() {
		return new File(Settings.getDataDir(), "pcache_lru");
	}

	private File getPersistentInfoFile() {
		return new File(Settings.getDataDir(), "pcache_info");
	}

	private File getPersistentAgesFile() {
		return new File(Settings.getDataDir(), "pcache_ages");
	}

	private boolean loadPersistentData() {
		if(!getPersistentInfoFile().exists()) {
			Out.debug("CacheHandler: Missing pcache_info, forcing rescan");
			return false;
		}

		boolean success = false;

		try {
			File persistentInfoFile = getPersistentInfoFile();
			String[] cacheinfo = Tools.getStringFileContents(persistentInfoFile).split("\n");
			int infoChecksum = 0;
			String agesHash = null, lruHash = null;

			for(String keyval : cacheinfo) {
				String[] s = keyval.split("=");

				switch(s[0]) {
					case "cacheCount":
						cacheCount = Integer.parseInt(s[1]);
						Out.debug("CacheHandler: Loaded persistent cacheCount=" + cacheCount);
						infoChecksum |= 1;
						break;
					case "cacheSize":
						cacheSize = Long.parseLong(s[1]);
						Out.debug("CacheHandler: Loaded persistent cacheSize=" + cacheSize);
						infoChecksum |= 2;
						break;
					case "lruClearPointer":
						lruClearPointer = Integer.parseInt(s[1]);
						Out.debug("CacheHandler: Loaded persistent lruClearPointer=" + lruClearPointer);
						infoChecksum |= 4;
						break;
					case "agesHash":
						agesHash = s[1];
						Out.debug("CacheHandler: Found agesHash=" + agesHash);
						infoChecksum |= 8;
						break;
					case "lruHash":
						lruHash = s[1];
						Out.debug("CacheHandler: Found lruHash=" + lruHash);
						infoChecksum |= 16;
						break;
				}
			}

			// very rarely, if the files have been corrupted, the java deserializer might get stuck in an infinite loop when deserializing the pcache objects
			// we delete the info file early so that if this happens, it will force a cache rescan on the next startup
			if(persistentInfoFile.exists()) {
				persistentInfoFile.delete();
			}

			if(infoChecksum != 31) {
				Out.info("CacheHandler: Persistent fields were missing, forcing rescan");
			}
			else {
				Out.info("CacheHandler: All persistent fields found, loading remaining objects");

				staticRangeOldest = (Hashtable<String,Long>) readCacheObject(getPersistentAgesFile(), agesHash);
				Out.info("CacheHandler: Loaded static range ages");

				if(!Settings.isUseLessMemory()) {
					lruCacheTable = (short[]) readCacheObject(getPersistentLRUFile(), lruHash);
					Out.info("CacheHandler: Loaded LRU cache");
				}

				updateStats();
				success = true;
			}
		}
		catch(Exception e) {
			Out.debug(e.getMessage());
		}

		System.gc();

		return success;
	}

	private void savePersistentData() {
		if(!cacheLoaded) {
			return;
		}

		try {
			String agesHash = writeCacheObject(getPersistentAgesFile(), staticRangeOldest);
			String lruHash = lruCacheTable == null ? "null" : writeCacheObject(getPersistentLRUFile(), lruCacheTable);
			Tools.putStringFileContents(getPersistentInfoFile(), "cacheCount=" + cacheCount + "\ncacheSize=" + cacheSize + "\nlruClearPointer=" + lruClearPointer + "\nagesHash=" + agesHash + "\nlruHash=" + lruHash);
		}
		catch(java.io.IOException e) {
			e.printStackTrace();
		}
	}
	
	private Object readCacheObject(File file, String expectedHash) throws java.io.IOException, java.lang.ClassNotFoundException {
		if(!file.exists()) {
			Out.warning("CacheHandler: Missing " + file + ", forcing rescan");
			throw new java.io.IOException("Missing file");
		}
		
		if(!Tools.getSHA1String(file).equals(expectedHash)) {
			Out.warning("CacheHandler: Incorrect file hash while reading " + file + ", forcing rescan");
			throw new java.io.IOException("Incorrect file hash");
		}
		
		ObjectInputStream objectReader = new ObjectInputStream(new FileInputStream(file));
		Object object = objectReader.readObject();
		objectReader.close();
		return object;
	}
	
	private String writeCacheObject(File file, Object object) throws java.io.FileNotFoundException, java.io.IOException {
		Out.debug("Writing cache object " + file);
		ObjectOutputStream objectWriter = new ObjectOutputStream(new FileOutputStream(file));
		objectWriter.writeObject(object);
		objectWriter.close();
		String hash = Tools.getSHA1String(file);
		Out.debug("Wrote cache object " + file + " with size=" + file.length() + " hash=" + hash);
		return hash;
	}

	private void deletePersistentData() {
		File persistentInfoFile = getPersistentInfoFile();
		File persistentAgesFile = getPersistentAgesFile();
		File persistentLRUFile  = getPersistentLRUFile();

		if(persistentInfoFile.exists()) {
			persistentInfoFile.delete();
		}

		if(persistentAgesFile.exists()) {
			persistentAgesFile.delete();
		}

		if(persistentLRUFile.exists()) {
			persistentLRUFile.delete();
		}
	}

	public void terminateCache() {
		savePersistentData();
	}

	private void startupCacheCleanup() {
		Out.info("CacheHandler: Cache cleanup pass..");

		File[] l1dirs = Tools.listSortedFiles(cachedir);
		
		if(l1dirs == null) {
			client.dieWithError("CacheHandler: Unable to access " + cachedir + "; check permissions and I/O errors.");
		}

		// this sanity check can be tightened up when 1.2.6 is EOL and everyone have upgraded to the two-level cache tree
		//if(l1dirs.length > Settings.getStaticRangeCount()) {
		if(l1dirs.length > 5 && Settings.getStaticRangeCount() == 0) {
			Out.warning("WARNING: There are " + l1dirs.length + " directories in the cache directory, but the server has only assigned us " + Settings.getStaticRangeCount() + " static ranges.");
			Out.warning("If this is NOT expected, please close H@H with Ctrl+C or Program -> Shutdown H@H before this timeout expires.");
			Out.warning("Waiting 30 seconds before proceeding with cache cleanup...");

			try {
				Thread.currentThread().sleep(30000);
			}
			catch(Exception e) {}
		}

		if(client.isShuttingDown()) {
			return;
		}

		int checkedCounter = 0, checkedCounterPct = 0;

		for(File l1dir : l1dirs) {
			// time to take out the trash
			System.gc();
			
			if(!l1dir.isDirectory()) {
				l1dir.delete();
				continue;
			}

			File[] l2dirs = Tools.listSortedFiles(l1dir);
			
			if(l2dirs == null) {
				Out.warning("CacheHandler: Unable to access " + l1dir + "; check permissions and I/O errors.");
				continue;
			}

			if(l2dirs.length == 0) {
				l1dir.delete();
				continue;
			}

			for(File l2dir : l2dirs) {
				if(l2dir.isDirectory()) {
					continue;
				}

				// file in the level 1 directory - move it to its proper location
				HVFile hvFile = HVFile.getHVFileFromFile(l2dir);

				if(hvFile == null) {
					Out.debug("CacheHandler: The file " + l2dir + " was not recognized.");
					l2dir.delete();
				}
				else if( !Settings.isStaticRange(hvFile.getFileid()) ) {
					Out.debug("CacheHandler: The file " + l2dir + " was not in an active static range.");
					l2dir.delete();
				}
				else {
					moveFileToCacheDir(l2dir, hvFile);
					Out.debug("CacheHandler: Relocated file " + hvFile.getFileid() + " to " + hvFile.getLocalFileRef());
				}
			}

			++checkedCounter;

			if(l1dirs.length > 9) {
				if(checkedCounter * 100 / l1dirs.length >= checkedCounterPct + 10) {
					checkedCounterPct += 10;
					Out.info("CacheHandler: Cleanup pass at " + checkedCounterPct + "%");
				}
			}
		}

		Out.info("CacheHandler: Finished scanning " + checkedCounter + " cache subdirectories");
	}

	private void startupInitCache() {
		// update actions:
		// staticRangeOldest	- add oldest modified timestamp for every static range
		// addFileToActiveCache	- increments cacheCount and cacheSize
		// markRecentlyAccessed	- marks files with timestamp > 7 days in the LRU cache

		// if --verify-cache was specified, we use this shiny new FileValidator to avoid having to create a new MessageDigest and ByteBuffer for every single file in the cache
		FileValidator validator = null;
		int printFreq;

		if(Settings.isVerifyCache()) {
			Out.info("CacheHandler: Loading cache with full file verification. Depending on the size of your cache, this can take a long time.");
			validator = new FileValidator();
			printFreq = 1000;
		}
		else {
			Out.info("CacheHandler: Loading cache...");
			printFreq = 10000;
		}
		
		long recentlyAccessedCutoff = System.currentTimeMillis() - 604800000;
		int foundStaticRanges = 0;

		// cache register pass
		for(File l1dir : Tools.listSortedFiles(cachedir)) {
			if(!l1dir.isDirectory()) {
				continue;
			}
			
			File[] l2dirs = Tools.listSortedFiles(l1dir);

			if(l2dirs == null) {
				// we already warned about level 1 issues in startupCacheCleanup
				//Out.warning("CacheHandler: Unable to access " + l1dir + "; check permissions and I/O errors.");
				continue;
			}

			for(File l2dir : l2dirs) {
				// the garbage, it must be collected
				System.gc();

				if(!l2dir.isDirectory()) {
					continue;
				}

				File[] files = Tools.listSortedFiles(l2dir);
				
				if(files == null) {
					Out.warning("CacheHandler: Unable to access " + l2dir + "; check permissions and I/O errors.");
					continue;
				}

				if(files.length == 0) {
					l2dir.delete();
					continue;
				}

				long oldestLastModified = System.currentTimeMillis();

				for(File cfile : files) {
					if(!cfile.isFile()) {
						continue;
					}

					HVFile hvFile = HVFile.getHVFileFromFile(cfile, validator);

					if(hvFile == null) {
						Out.debug("CacheHandler: The file " + cfile + " was corrupt.");
						cfile.delete();
					}
					else if( !Settings.isStaticRange(hvFile.getFileid()) ) {
						Out.debug("CacheHandler: The file " + cfile + " was not in an active static range.");
						cfile.delete();
					}
					else {
						addFileToActiveCache(hvFile);
						long fileLastModified = cfile.lastModified();

						if(fileLastModified > recentlyAccessedCutoff) {
							// if lastModified is from the last week, mark this as recently accessed in the LRU cache. (this does not update the metadata)
							markRecentlyAccessed(hvFile, true);
						}

						oldestLastModified = Math.min(oldestLastModified, fileLastModified);

						if(cacheCount % printFreq == 0) {
							Out.info("CacheHandler: Loaded " + cacheCount + " files so far...");
						}
					}
				}

				String staticRange = l1dir.getName() + l2dir.getName();
				staticRangeOldest.put(staticRange, oldestLastModified);

				if(++foundStaticRanges % 100 == 0) {
					Out.info("CacheHandler: Found " + foundStaticRanges + " static ranges with files so far...");
				}
			}
		}

		Out.info("CacheHandler: Finished initializing the cache (" + cacheCount + " files, " + cacheSize + " apparent bytes, " + getCacheSizeWithOverhead() + " estimated bytes on disk)");
		Out.info("CacheHandler: Found a total of " + foundStaticRanges + " static ranges with files");
		updateStats();
	}
	
	private long getCacheSizeWithOverhead() {
		// on average, a file will have a wasted slack space (filesystem overhead) of half the blocksize of the storage device. this is assumed to be 4096 bytes but can be overriden with --filesystem-blocksize
		// we *could* calculate this exactly but this would require additional logic and a cache rescan if the filesystem changes; this is very much close enough
		// Java 9 has a way to determine the blocksize from the storage device automatically, but we do not want to bump the required version just for this
		return cacheSize + cacheCount * Settings.getFileSystemBlockSize() / 2;
	}

	public boolean recheckFreeDiskSpace() {
		if(lruSkipCheckCycle > 0) {
			// this is called every 10 seconds from the main thread, but depending on what happened in earlier runs, we skip checks when they are not necessary
			// we'll check every 10 minutes if the free cache during the last run was over 1GB, and every minute if less than 1GB but over 100MB
			--lruSkipCheckCycle;
			return true;
		}

		long wantFree = 104857600;
		long cacheLimit = Settings.getDiskLimitBytes();
		long cacheSizeWithOverhead = getCacheSizeWithOverhead();
		long bytesToFree = 0;

		if(cacheSizeWithOverhead > cacheLimit) {
			bytesToFree = wantFree + cacheSizeWithOverhead - cacheLimit;
		}
		else if(cacheLimit - cacheSizeWithOverhead < wantFree) {
			bytesToFree = wantFree - (cacheLimit - cacheSizeWithOverhead);
		}

		Out.debug("CacheHandler: Checked cache space (cacheSize=" + cacheSize + ", cacheSizeWithOverhead=" + cacheSizeWithOverhead + " cacheLimit=" + cacheLimit + ", cacheFree=" + (cacheLimit - cacheSizeWithOverhead) + ")");

		if(bytesToFree > 0 && cacheCount > 0 && Settings.getStaticRangeCount() > 0) {
			String pruneStaticRange = null;
			long nowtime = System.currentTimeMillis();
			long oldestRangeAge = nowtime;
			Enumeration<String> staticRanges = staticRangeOldest.keys();

			while(staticRanges.hasMoreElements()) {
				String checkStaticRange = staticRanges.nextElement();
				long thisRangeOldestAge = staticRangeOldest.get(checkStaticRange).longValue();

				if(thisRangeOldestAge < oldestRangeAge) {
					pruneStaticRange = checkStaticRange;
					oldestRangeAge = thisRangeOldestAge;
				}
			}

			if(pruneStaticRange == null) {
				Out.warning("CacheHandler: Failed to find aged static range to prune (oldestRangeAge=" + oldestRangeAge + ")");
				return false;
			}

			File staticRangeDir = new File(cachedir, pruneStaticRange.substring(0, 2) + "/" + pruneStaticRange.substring(2, 4) + "/");
			long lruLastModifiedPruneCutoff = oldestRangeAge;

			if(oldestRangeAge < nowtime - 15552000000L) {
				// oldest file is more than six months old, prune files newer than up to 30 days after this file
				lruLastModifiedPruneCutoff += 2592000000L;
			}
			else if(oldestRangeAge < nowtime - 7776000000L) {
				// oldest file is between three and six months old, prune files newer than up to 7 days after this file
				lruLastModifiedPruneCutoff += 604800000L;
			}
			else if(oldestRangeAge < nowtime - 2592000000L) {
				// oldest file is between one and three months old, prune files newer than up to 3 days after this file
				lruLastModifiedPruneCutoff += 259200000L;
			}
			else {
				// oldest file is less than a month old, prune files newer than up to 1 day after this file
				lruLastModifiedPruneCutoff += 86400000L;
			}

			Out.debug("CacheHandler: Trying to free " + bytesToFree + " bytes, currently scanning range " + pruneStaticRange);

			if(!staticRangeDir.isDirectory()) {
				Out.warning("CacheHandler: Expected static range directory " + staticRangeDir + " could not be accessed");
			}
			else {
				File[] files = staticRangeDir.listFiles();
				long oldestLastModified = nowtime;

				if(files != null && files.length > 0) {
					Out.debug("CacheHandler: Examining " + files.length + " files with lruLastModifiedPruneCutoff=" + lruLastModifiedPruneCutoff);

					for(File file : files) {
						long lastModified = file.lastModified();

						if(lastModified < lruLastModifiedPruneCutoff) {
							HVFile toRemove = HVFile.getHVFileFromFileid(file.getName());

							if(toRemove == null) {
								Out.warning("CacheHandler: Removed invalid file " + file);
								file.delete();
							}
							else {
								deleteFileFromCache(toRemove);
								bytesToFree -= toRemove.getSize();
								Out.debug("CacheHandler: Pruned file had lastModified=" + lastModified + " size=" + toRemove.getSize() + " bytesToFree=" + bytesToFree + " cacheCount=" + cacheCount);
							}
						}
						else {
							oldestLastModified = Math.min(oldestLastModified, lastModified);
						}
					}
				}

				// we don't have any guarantees that there were any files to prune in this directory since there is a chance the oldest file was accessed after the oldest timestamp record was updated
				// regardless, we update the record with the freshly computed last modified timestamp. this will usually knock this range back from the front of the queue
				// if we still need to prune files, we will do another pass shortly
				staticRangeOldest.put(pruneStaticRange, oldestLastModified);

				Out.debug("CacheHandler: Updated static range " + pruneStaticRange + " with new oldestLastModified=" + oldestLastModified);
			}
		}
		else {
			lruSkipCheckCycle = cacheLimit - cacheSizeWithOverhead > wantFree * 10 ? 60 : 6;
		}

		// if we are more than 10MB above where we want to be, start turning up the prune aggression, which determines how many times this cleanup function is run per cycle
		// realistically, this is almost certainly unnecessary since the 1.3.2 pruner was added, but it doesn't hurt to have it just in case
		pruneAggression = bytesToFree > 10485760 ? (int) (bytesToFree / 10485760) : 1;

		if(Settings.isSkipFreeSpaceCheck()) {
			Out.debug("CacheHandler: Disk free space check is disabled.");
			return true;
		}
		else {
			long diskFreeSpace = cachedir.getFreeSpace();

			if(diskFreeSpace < Math.max(Settings.getDiskMinRemainingBytes(), wantFree)) {
				Out.warning("CacheHandler: Did not meet space constraints: Disk free space limit reached (" + diskFreeSpace + " bytes free on device)");
				return false;
			}
			else {
				Out.debug("CacheHandler: Disk space constraints met (" + diskFreeSpace + " bytes free on device)");
				return true;
			}
		}
	}

	public int getPruneAggression() {
		return pruneAggression;
	}

	public synchronized void processBlacklist(long deltatime) {
		Out.info("CacheHandler: Retrieving list of blacklisted files...");
		String[] blacklisted = client.getServerHandler().getBlacklist(deltatime);

		if(blacklisted == null) {
			Out.warning("CacheHandler: Failed to retrieve file blacklist, will try again later.");
			return;
		}

		Out.info("CacheHandler: Looking for and deleting blacklisted files...");

		int counter = 0;

		for(String fileid : blacklisted) {
			HVFile hvFile = HVFile.getHVFileFromFileid(fileid);

			if(hvFile != null) {
				File file = hvFile.getLocalFileRef();

				if(file.exists()) {
					deleteFileFromCache(hvFile);
					Out.debug("CacheHandler: Removed blacklisted file " + fileid);
					++counter;
				}
			}
		}

		Out.info("CacheHandler: " + counter + " blacklisted files were removed.");
	}

	private void updateStats() {
		Stats.setCacheCount(cacheCount);
		Stats.setCacheSize(getCacheSizeWithOverhead());
	}

	public int getCacheCount() {
		return cacheCount;
	}

	// used to add proxied files to cache. this function assumes that tempFile has been validated
	public boolean importFile(File tempFile, HVFile hvFile) {
		if(moveFileToCacheDir(tempFile, hvFile)) {
			addFileToActiveCache(hvFile);
			markRecentlyAccessed(hvFile, true);

			// check that the static range oldest timestamp cache has an entry for this static range
			String staticRange = hvFile.getStaticRange();
			if(!staticRangeOldest.containsKey(staticRange)) {
				Out.debug("CacheHandler: Created staticRangeOldest entry for " + staticRange);
				staticRangeOldest.put(staticRange, System.currentTimeMillis());
			}

			return true;
		}

		return false;
	}

	// will just move the file into its correct location. addFileToActiveCache must be called afterwards to add the file to the cache counters.
	private boolean moveFileToCacheDir(File file, HVFile hvFile) {
		File toFile = hvFile.getLocalFileRef();

		try {
			Tools.checkAndCreateDir(toFile.getParentFile());
			Files.move(file.toPath(), toFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

			if(file.exists()) {
				// moving failed, let's try copying
				Files.copy(file.toPath(), toFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
				file.delete();
			}

			if(toFile.exists()) {
				Out.debug("CacheHandler: Imported file " + file + " as " + hvFile.getFileid());
				return true;
			}
			else {
				Out.warning("CacheHandler: Failed to move file " + file);
			}
		}
		catch(java.io.IOException e) {
			e.printStackTrace();
			Out.warning("CacheHandler: Encountered exception " + e + " when moving file " + file);
		}

		return false;
	}

	private void addFileToActiveCache(HVFile hvFile) {
		++cacheCount;
		cacheSize += hvFile.getSize();
		updateStats();
	}

	private void deleteFileFromCache(HVFile toRemove) {
		try {
			File file = toRemove.getLocalFileRef();

			if(file.exists()) {
				file.delete();
				--cacheCount;
				cacheSize -= toRemove.getSize();
				updateStats();
				Out.debug("CacheHandler: Deleted cached file " + toRemove.getFileid());
			}
		}
		catch(Exception e) {
			Out.error("CacheHandler: Failed to delete cache file");
			client.dieWithError(e);
		}
	}

	public void cycleLRUCacheTable() {
		if(lruCacheTable != null) {
			// this function is called every 10 seconds. clearing 17 of the shorts for each call means that each element will live up to a week (since 1048576 / (8640 * 7) is roughly 17).
			// if --use-less-memory is set, the LRU cache will never have been created, and this does nothing.

			int clearUntil = Math.min(MEMORY_TABLE_ELEMENTS, lruClearPointer + 17);

			//Out.debug("CacheHandler: Clearing lruCacheTable from " + lruClearPointer + " to " + clearUntil);

			while(lruClearPointer < clearUntil) {
				lruCacheTable[lruClearPointer++] = 0;
			}

			if(clearUntil >= MEMORY_TABLE_ELEMENTS) {
				lruClearPointer = 0;
			}
		}
	}

	public void markRecentlyAccessed(HVFile hvFile) {
		markRecentlyAccessed(hvFile, false);
	}

	public void markRecentlyAccessed(HVFile hvFile, boolean skipMetaUpdate) {
		boolean markFile = true;

		if(lruCacheTable != null) {
			// if --use-less-memory is not set, we use this as a first step in order to determine if the timestamp should be updated or not.
			// lruCacheTable can hold 16^5 = 1048576 shorts consisting of 16 bits each.
			// we need to compute the array index and bitmask for this particular fileid. if the bit is set, we do nothing. if not, we update the timestamp and set the bit.
			// when determening what bit to set, we skip the first four nibbles (bit 0-15) of the hash due to static range grouping
			// we use the next five nibbles (bit 16-35) to get the index of the array, and the tenth nibble (bit 36-39) to determine which bit in the short to read/set.
			// while collisions are not unlikely to occur due to the birthday paradox, they should not cause any major issues with files not having their timestamp updated.
			// any impact of this will be negligible, as it will only cause the LRU mechanism to be slightly less efficient.
			String fileid = hvFile.getFileid();

			// bit 16-35
			int arrayIndex = Integer.parseInt(fileid.substring(4, 9), 16);

			// bit 36-39
			short bitMask = (short) (1 << Short.parseShort(fileid.substring(9, 10), 16));

			if((lruCacheTable[arrayIndex] & bitMask) != 0) {
				//Out.debug("LRU bit for " + fileid + " = " + arrayIndex + ":" + fileid.charAt(9) + " was set");
				markFile = false;
			}
			else {
				//Out.debug("Written bit for " + fileid + " = " + arrayIndex + ":" + fileid.charAt(9) + " was not set - marking");
				lruCacheTable[arrayIndex] |= bitMask;
			}
		}

		if(markFile && !skipMetaUpdate) {
			File file = hvFile.getLocalFileRef();
			long nowtime = System.currentTimeMillis();

			if(file.lastModified() < nowtime - 604800000) {
				file.setLastModified(nowtime);
			}
		}
	}
}