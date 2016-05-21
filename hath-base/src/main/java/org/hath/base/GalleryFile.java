/*

Copyright 2008-2012 E-Hentai.org
http://forums.e-hentai.org/
ehentai@gmail.com

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

package org.hath.base;

import java.io.File;

public class GalleryFile {
	private HentaiAtHomeClient client;

	public static final int FILE_SUCCESS = 1;
	public static final int FILE_TEMPFAIL = -1;
	public static final int FILE_PERMFAIL = -2;
	public static final int FILE_INVALID_TOKEN = -3;
	
	public static final int STATE_PROCESSED_ERRORS = -1;
	public static final int STATE_PENDING = 0;
	public static final int STATE_PROCESSED = 1;
	
	private File todir, tofile;
	private HVFile hvfile;
	private String fileid, filename, token;
	private int gid, page;
	private long tokentime;	// tokens expire after about an hour, so we'll need to keep track of this in case a file repeatedly fails downloading
	private int state;
	private int retrycount;
	private long lastretry;
	
	private GalleryFile(HentaiAtHomeClient client, File todir, String fileid, int gid, int page, String givenFilename) {
		this.hvfile = HVFile.getHVFileFromFileid(fileid);

		this.client = client;
		this.todir = todir;
		this.fileid = fileid;
		this.gid = gid;
		this.page = page;
		
		int fileExtIndex = givenFilename.lastIndexOf(".");
		filename = givenFilename.substring(0, Math.min(80, fileExtIndex > -1 ? fileExtIndex : givenFilename.length())) + "." + hvfile.getType();
		tofile = new File(todir, filename);

		this.state = STATE_PENDING;
		this.retrycount = 0;
		this.lastretry = 0;
		
		Out.debug("Downloader: Pending download for " + fileid + " => " + filename);
	}
	
	public static GalleryFile getGalleryFile(HentaiAtHomeClient client, File todir, String fileid, int gid, int page, String filename) {
		if((client != null ) && (todir != null) && (fileid != null) && (gid > 0) && (page > 0) && (filename != null)) {
			if(HVFile.isValidHVFileid(fileid) && (filename.length() > 0)) {
				return new GalleryFile(client, todir, fileid, gid, page, filename);
			}
		}
		
		Out.warning("Invalid GalleryFile " + fileid + " (" + filename + ")");		
		return null;
	}
	
	public void setNewToken(String token) {
		this.token = token;
		this.tokentime = System.currentTimeMillis();
	}
	
	public int getState() {
		return state;
	}
	
	public String getFileid() {
		return fileid;
	}
	
	public int attemptDownload() {
		// wait at least six minutes between each download attempt, to allow the host routing cache to clear. increase retry delay by 6 minutes per fail.
		if(System.currentTimeMillis() < lastretry + 360000 * retrycount) {
			return FILE_TEMPFAIL;
		}
			
		// just in case, check for directory traversal
		if(! tofile.getParentFile().equals(todir)) {
			state = STATE_PROCESSED_ERRORS;
			return FILE_PERMFAIL;
		}
		
		// was the file already downloaded?
		if(tofile.isFile()) {
			Out.debug("Downloader: " + tofile + " already exists - marking as completed");
			state = STATE_PROCESSED;
			return FILE_SUCCESS;
		}
		
		boolean validToken = tokentime > System.currentTimeMillis() - 3600000;		
		File fromfile = hvfile.getLocalFileRef();
		
		// try to download if it doesn't exist and we have a token
		if(validToken && !fromfile.isFile()) {
			Out.debug("Downloader: " + tofile + " - initializing GalleryFileDownloader");
			GalleryFileDownloader gfd = new GalleryFileDownloader(client, fileid, token, gid, page, filename, retrycount > 3);
			gfd.initialize();
			
			int sleepTime = 1000;
			int runTime = 0;
			
			// we check every second, and give it max five minutes before moving on. if gfd finishes it after we give up, it will be caught on the next pass-through.
			do {
				try {
					Thread.sleep(sleepTime);
					runTime += sleepTime;
				} catch(java.lang.InterruptedException e) {}
			} while((gfd.getDownloadState() == GalleryFileDownloader.DOWNLOAD_PENDING) && (runTime < 300000));
		}
		
		if(fromfile.isFile()) {
			// copy the file to the output directory, and we're done
			FileTools.copy(fromfile, tofile);
			Out.debug("Downloader: " + fromfile + " copied to " + tofile);
			state = STATE_PROCESSED;
			return FILE_SUCCESS;
		} else if(!validToken) {
			// we need a token for this file before we can download it
			return FILE_INVALID_TOKEN;
		} else {
			// download was attempted but failed - flag necessary conditions for retry
			
			lastretry = System.currentTimeMillis();
		
			if(++retrycount > 100) {
				try {
					(new File(todir, filename + ".fail")).createNewFile();
					Out.debug("Downloader: Permanently failing download of " + fileid);
				} catch(java.io.IOException e) {
					Out.warning("Downloader: Failed to create empty .fail file");
					e.printStackTrace();
				}
				
				state = STATE_PROCESSED_ERRORS;
				return FILE_PERMFAIL;
			} else {
				return FILE_TEMPFAIL;
			}
		}
	}
}
