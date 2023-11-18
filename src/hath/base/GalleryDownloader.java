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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Pattern;
import java.io.File;
import java.net.URL;

public class GalleryDownloader implements Runnable {
	protected HentaiAtHomeClient client;
	private Thread myThread;
	private FileValidator validator;
	protected HTTPBandwidthMonitor downloadLimiter;
	private boolean downloadsAvailable = true, pendingDownload = false, markDownloaded = false;
	
	private String title, information;
	private GalleryFile[] galleryFiles;
	private List<String> failures;
	protected int gid, filecount;
	protected String minxres;
	protected File todir;

	public GalleryDownloader(HentaiAtHomeClient client) {
		this.client = client;
		validator = new FileValidator();
		downloadLimiter = Settings.isDisableDownloadBWM() ? null : new HTTPBandwidthMonitor();
		myThread = new Thread(this);
		myThread.start();
	}

	public void run() {
		while( !client.isShuttingDown() && downloadsAvailable ) {
			if(!pendingDownload) {
				pendingDownload = initializeNewGalleryMeta();
			}

			if(!pendingDownload) {
				downloadsAvailable = false;
				break;
			}

			Out.info("GalleryDownloader: Starting download of gallery: " + title);

			int galleryretry = 0;
			boolean success = false;
			
			while(!success && ++galleryretry < 10) {
				int successfulFiles = 0;
				
				for(GalleryFile gFile : galleryFiles) {
					if(client.isShuttingDown()) {
						break;
					}

					long sleepTime = 0;

					if(client.isSuspended()) {
						sleepTime = 60000;
					}
					else if(downloadDirectoryHasLowSpace()) {
						Out.warning("GalleryDownloader: Download suspended; there is less than the minimum allowed space left on the storage device.");
						sleepTime = 300000;
					}
					else {
						int downloadState = gFile.download();
						
						if(downloadState == GalleryFile.STATE_DOWNLOAD_SUCCESSFUL) {
							++successfulFiles;
							sleepTime = 1000;
						}
						else if(downloadState == GalleryFile.STATE_ALREADY_DOWNLOADED) {
							++successfulFiles;
						}
						else if(downloadState == GalleryFile.STATE_DOWNLOAD_FAILED) {
							sleepTime = 5000;
						}
					}

					if(sleepTime > 0) {
						try {
							myThread.sleep(sleepTime);
						}
						catch(java.lang.InterruptedException e) {}
					}
				}
				
				if(successfulFiles == filecount) {
					success = true;
				}
			}

			finalizeGalleryDownload(success);
		}

		// the GalleryDownloader thread is created on-demand. when it is done, it goes away until needed again.
		Out.info("GalleryDownloader: Download thread finished.");
		client.deleteDownloader();
	}

	private boolean downloadDirectoryHasLowSpace() {
		return !Settings.isSkipFreeSpaceCheck() && Settings.getDownloadDir().getFreeSpace() < Settings.getDiskMinRemainingBytes() + 1048576000;
	}

	private void finalizeGalleryDownload(boolean success) {
		pendingDownload = false;
		markDownloaded = true;

		if(success) {
			Out.info("GalleryDownloader: Finished download of gallery: " + title);

			try {
				Tools.putStringFileContents(new File(todir, "galleryinfo.txt"), information, "UTF8");
			}
			catch(java.io.IOException e) {
				Out.warning("GalleryDownloader: Could not write galleryinfo file");
				e.printStackTrace();
			}
		}
		else {
			Out.warning("GalleryDownloader: Permanently failed downloading gallery: " + title);
		}
	}

	private boolean initializeNewGalleryMeta() {
		if(markDownloaded) {
			if(failures != null) {
				// tattletale
				client.getServerHandler().reportDownloaderFailures(failures);
			}
		}
		
		URL metaurl;
		
		try {
			metaurl = new URL(Settings.CLIENT_RPC_PROTOCOL + Settings.getRPCServerHost() + "/15/dl?" + ServerHandler.getURLQueryString("fetchqueue", markDownloaded ? gid + ";" + minxres : ""));
		}
		catch(java.net.MalformedURLException e) {
			e.printStackTrace();
			return false;
		}

		// this does two things: marks the previous gallery as downloaded and removes it from the queue, and fetches metadata of the next gallery in the queue
		FileDownloader metaDownloader = new FileDownloader(metaurl, 30000, 30000);
		String galleryMeta = metaDownloader.getResponseAsString("UTF8");

		if(galleryMeta == null) {
			return false;
		}

		if(galleryMeta.equals("INVALID_REQUEST")) {
			Out.warning("GalleryDownloader: Request was rejected by the server");
			return false;
		}
		
		if(galleryMeta.equals("NO_PENDING_DOWNLOADS")) {
			return false;
		}

		Out.debug("GalleryDownloader: Started gallery metadata parsing");

		// reset
		gid = 0;
		filecount = 0;
		minxres = null;
		title = null;
		information = "";
		galleryFiles = null;
		todir = null;
		markDownloaded = false;
		failures = null;

		// parse the metadata for this gallery. this is basically a hathdl file with a new file list format
		// incidentally, did you know Java SE does not have a built-in JSON parser? how stupid is that
		int parseState = 0;

		try {
			for(String s: galleryMeta.split("\n")) {
				if(s.equals("FILELIST") && parseState == 0) {
					parseState = 1;
					continue;
				}

				if(s.equals("INFORMATION") && parseState == 1) {
					parseState = 2;
					continue;
				}

				if(parseState < 2 && s.isEmpty()) {
					continue;
				}

				if(parseState == 0) {
					String[] split = s.split(" ", 2);

					if(split[0].equals("GID")) {
						gid = Integer.parseInt(split[1]);
						Out.debug("GalleryDownloader: Parsed gid=" + gid);
					}
					else if(split[0].equals("FILECOUNT")) {
						filecount = Integer.parseInt(split[1]);
						galleryFiles = new GalleryFile[filecount];
						Out.debug("GalleryDownloader: Parsed filecount=" + filecount);
					}
					else if(split[0].equals("MINXRES")) {
						if(Pattern.matches("^org|\\d+$", split[1])) {
							minxres = split[1];
							Out.debug("GalleryDownloader: Parsed minxres=" + minxres);
						}
						else {
							throw new Exception("Encountered invalid minxres");
						}
					}
					else if(split[0].equals("TITLE")) {
						title = split[1].replaceAll("(\\*|\\\"|\\\\|<|>|:\\|\\?)", "").replaceAll("\\s+", " ").replaceAll("(^\\s+|\\s+$)", "");
						Out.debug("GalleryDownloader: Parsed title=" + title);
						
						// MINXRES must be passed before TITLE for this to work. the only purpose is to make distinct titles
						String xresTitle = minxres.equals("org") ? "" : "-" + minxres + "x";

						if(title.length() > 100) {
							todir = new File(Settings.getDownloadDir(), title.substring(0, 97) + "... [" + gid + xresTitle + "]");
						} else {
							todir = new File(Settings.getDownloadDir(), title + " [" + gid + xresTitle + "]");
						}

						// just in case, check for directory traversal
						if( !todir.getParentFile().equals(Settings.getDownloadDir()) ) {
							Out.warning("GalleryDownloader: Unexpected download location.");
							todir = null;
							break;
						}

						try {
							Tools.checkAndCreateDir(todir);
						}
						catch(Exception e) {}
						
						if(!todir.exists()) {
							Out.warning("GalleryDownloader: Could not create gallery download directory \"" + todir.getName() + "\". Your filesystem may not support Unicode. Attempting fallback.");
							todir = new File(Settings.getDownloadDir(), gid + xresTitle);
							Tools.checkAndCreateDir(todir);
						}

						Out.debug("GalleryDownloader: Created directory " + todir);
					}
				}
				else if(parseState == 1) {
					// entries are on the form: page fileindex xres sha1hash filetype filename
					String[] split = s.split(" ", 6);
					int page = Integer.parseInt(split[0]);
					int fileindex = Integer.parseInt(split[1]);
					String xres = split[2];
					
					// sha1hash can be "unknown" if the file has not been generated yet
					String sha1hash = split[3].equals("unknown") ? null : split[3];
					
					// the server guarantees that all filenames in the meta file are unique, and that none of them are reserved device filenames
					String filetype = split[4];
					String filename = split[5];

					GalleryFile gf = new GalleryFile(page, fileindex, xres, sha1hash, filetype, filename);

					if(gf != null) {
						Out.debug("GalleryDownloader: Parsed file " + gf);
						galleryFiles[page - 1] = gf;
					}
				}
				else {
					information = information.concat(s).concat(Settings.NEWLINE);
				}
			}
		}
		catch(Exception e) {
			Out.warning("GalleryDownloader: Failed to parse metadata for new gallery");
			e.printStackTrace();
			return false;
		}

		return gid > 0 && filecount > 0 && minxres != null && title != null && todir != null && galleryFiles != null;
	}
	
	protected void logFailure(String fail) {
		if(failures == null) {
			failures = Collections.checkedList(new ArrayList<String>(), String.class);
		}
		
		if(!failures.contains(fail)) {
			failures.add(fail);
		}
	}

	private class GalleryFile {
		public static final int STATE_DOWNLOAD_FAILED = 0;
		public static final int STATE_DOWNLOAD_SUCCESSFUL = 1;
		public static final int STATE_ALREADY_DOWNLOADED = 2;
		private File tofile;
		private String filetype, filename, xres, expectedSHA1Hash;
		private int page, fileindex;
		private int fileretry = 0;
		private boolean fileComplete = false;

		public GalleryFile(int page, int fileindex, String xres, String expectedSHA1Hash, String filetype, String filename) {
			this.page = page;
			this.fileindex = fileindex;
			this.xres = xres;
			this.expectedSHA1Hash = expectedSHA1Hash;
			this.filetype = filetype;
			this.filename = filename;
			tofile = new File(todir, filename + "." + filetype);
		}

		public int download() {
			if(fileComplete) {
				return STATE_ALREADY_DOWNLOADED;
			}

			if(tofile.isFile()) {
				boolean verified = false;

				if(tofile.length() > 0) {
					try {
						if(expectedSHA1Hash == null) {
							// if the file was generated on-demand for this download, we cannot verify the hash
							verified = true;
						}
						else if(validator.validateFile(tofile.toPath(), expectedSHA1Hash)) {
							verified = true;
							Out.debug("GalleryDownloader: Verified SHA-1 hash for " + this + ": " + expectedSHA1Hash);
						}
					}
					catch(java.io.IOException e) {
						Out.warning("GalleryDownloader: Encountered I/O error while validating " + tofile);
						e.printStackTrace();
					}
				}

				if(verified) {
					fileComplete = true;
					return STATE_ALREADY_DOWNLOADED;
				}
				else {
					tofile.delete();
				}
			}

			// if this turns out to be a file that can be handled by this client, the returned link will be to localhost, which will trigger a static range fetch using the standard mechanism
			// we don't have enough information at this point to initiate a ProxyFileDownload directly, so while the extra roundtrip might seem wasteful, it is necessary (and usually fairly rare)
			URL source = client.getServerHandler().getDownloaderFetchURL(gid, page, fileindex, xres, ++fileretry > 1);
			
			if(source != null) {
				FileDownloader dler = new FileDownloader(source, 10000, 300000, tofile.toPath());
				dler.setDownloadLimiter(downloadLimiter);
				fileComplete = dler.downloadFile();

				try {
					if(fileComplete && expectedSHA1Hash != null) {
						if(!validator.validateFile(tofile.toPath(), expectedSHA1Hash)) {
							fileComplete = false;
							tofile.delete();
							Out.debug("GalleryDownloader: Corrupted download for " + this + ", forcing retry");
						}
						else {
							Out.debug("GalleryDownloader: Verified SHA-1 hash for " + this + ": " + expectedSHA1Hash);
						}
					}
				}
				catch(java.io.IOException e) {
					Out.warning("GalleryDownloader: Encountered I/O error while validating " + tofile);
					e.printStackTrace();
					fileComplete = false;
					tofile.delete();
				}
				
				Out.debug("GalleryDownloader: Download of " + this + " " + (fileComplete ? "successful" : "FAILED") + " (attempt=" + fileretry + ")");
				
				if(fileComplete) {
					Stats.fileRcvd();
					Out.info("GalleryDownloader: Finished downloading gid=" + gid + " page=" + page + ": " + filename + "." + filetype);
				}
				else {
					logFailure(source.getHost() + "-" + fileindex + "-" + xres);
				}
			}
			
			return fileComplete ? STATE_DOWNLOAD_SUCCESSFUL : STATE_DOWNLOAD_FAILED;
		}
		
		public String toString() {
			return "gid=" + gid + " page=" + page + " fileindex=" + fileindex + " xres=" + xres + " filetype=" + filetype + " filename=" + filename;
		}
	}
}
