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
import java.util.List;

public class Gallery {
	public static final int STATE_PROCESSED_ERRORS = -1;
	public static final int STATE_PENDING = 0;
	public static final int STATE_PROCESSED = 1;

	private HentaiAtHomeClient client;
	private File hhdlFile, todir;
	private String title, information;
	private GalleryFile[] galleryFiles;
	private int state;

	public Gallery(HentaiAtHomeClient client, File hhdlFile, File todir, String title, String information, GalleryFile[] galleryFiles) {
		this.client = client;
		this.hhdlFile = hhdlFile;
		this.todir = todir;
		this.title = title;
		this.information = information;
		this.galleryFiles = galleryFiles;
		this.state = STATE_PENDING;
		
		Out.debug("Downloader: Download session for gallery " + title + " is now pending");
	}
	
	public int getState() {
		return state;
	}
	
	// takes and modifies list of the files that needs a token - these are borked together and requested by the main thread
	public void galleryPass(List<GalleryFile> requestTokens) {
		boolean allFilesProcessed = true;
		boolean errorsEncountered = false;
		
		for(GalleryFile gf : galleryFiles) {
			if(client.isShuttingDown()) {
				break;
			}
			
			if(gf == null) {
				errorsEncountered = true;
				continue;
			}
		
			int gfstate = gf.getState();
		
			if(gfstate == STATE_PENDING) {
				allFilesProcessed = false;
				
				if(gf.attemptDownload() == GalleryFile.FILE_INVALID_TOKEN) {
					if(! requestTokens.contains(gf) && requestTokens.size() < 20) {
						requestTokens.add(gf);
					}
				}
			} else if(gfstate == STATE_PROCESSED_ERRORS) {
				errorsEncountered = true;
			}
		}
		
		if(allFilesProcessed) {
			if(errorsEncountered) {
				Out.info("Downloader: Finished downloading gallery " + title + ", but some files could not be retrieved");
				state = STATE_PROCESSED_ERRORS;
			} else {
				Out.info("Downloader: Finished downloading gallery " + title);
				state = STATE_PROCESSED;
			}
			
			try {
				FileTools.putStringFileContentsUTF8(new File(todir, "galleryinfo.txt"), information);
			} catch(java.io.IOException e) {
				Out.warning("Downloader: Could not write galleryinfo file");
				e.printStackTrace();
			}
			
			hhdlFile.delete();
		}
	}
}

