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
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class GalleryDownloadManager implements Runnable {
	protected HentaiAtHomeClient client;
	private Thread galleryDownloadManager;
	
	private File hhdldir, downloadeddir;
	private List<File> processedHHDLFiles; // this is never cleared while the client is running
	private List<Gallery> pendingGalleries;
	
	private long lastTokenRequest;

	public GalleryDownloadManager(HentaiAtHomeClient client) {
		this.client = client;		
		processedHHDLFiles = new ArrayList<File>();
		pendingGalleries = new ArrayList<Gallery>();
		lastTokenRequest = System.currentTimeMillis() + 3600060;	// we wait until one hour after client start before we start requesting tokens, so the server has time to qualify the client. there's no point reducing this, as it's checked server-side.

		try {
			hhdldir = FileTools.checkAndCreateDir(new File("hathdl"));
			downloadeddir = FileTools.checkAndCreateDir(new File("downloaded"));
		} catch(java.io.IOException e) {
			HentaiAtHomeClient.dieWithError(e);
		}
		
		galleryDownloadManager = new Thread(this);
		galleryDownloadManager.setName("Gallery Download Manager");
		galleryDownloadManager.start();
	}

	public void run() {
		while(! client.isShuttingDown()) {
			long sleepTime = 10000;
		
			if(client.isSuspended()) {
				lastTokenRequest = System.currentTimeMillis() + 960000; // again waiting until 15 minutes after a suspend
				sleepTime = 60000;
			} else {
				ArrayList<File> process = new ArrayList<File>();

				File[] hhdlfiles = hhdldir.listFiles();
				for(File hhdlfile : hhdlfiles) {
					if(hhdlfile.isFile()) {
						if(hhdlfile.getName().endsWith(".hathdl")) {
							process.add(hhdlfile);
						}
					}
				}
				
				try {
					for(File processFile : process) {
						if(! processedHHDLFiles.contains(processFile)) {
							Out.debug("Downloader: Started HathDL processing from " + processFile);
						
							int gid = 0;
							int files = 0;
							String title = null;
							String information = "";
							GalleryFile[] galleryFiles = null;
							File todir = null; 
							
							int parseState = 0;
							String[] toParse = FileTools.getStringFileContentsUTF8(processFile).split("\n");					
							
							for(String s : toParse) {
								if(s.equals("FILELIST") && parseState == 0) {
									parseState = 1;
									continue;
								}

								if(s.equals("INFORMATION") && parseState == 1) {
									parseState = 2;
									continue;
								}
							
								if(parseState == 0) {
									if(s.isEmpty()) {
										continue;
									}
							
									String[] split = s.split(" ", 2);
									
									if(split[0].equals("GID")) {
										gid = Integer.parseInt(split[1]);
									} else if(split[0].equals("FILES")) {
										files = Integer.parseInt(split[1]);
										galleryFiles = new GalleryFile[files];
									} else if(split[0].equals("TITLE")) {
										title = split[1].replaceAll("(\\*|\\\"|\\\\|<|>|:\\|\\?)", "").replaceAll("\\s+", " ").replaceAll("(^\\s+|\\s+$)", "");
										
										if(title.length() > 100) {
											todir = new File(downloadeddir, title.substring(0, 97) + "... [" + gid + "]");
										} else {
											todir = new File(downloadeddir, title + " [" + gid + "]");
										}
										
										// just in case, check for directory traversal
										if(! todir.getParentFile().equals(downloadeddir)) {
											Out.warning("Downloader: Security Error - HHDL target download directory isn't where it's supposed to be. Aborted HHDL.");
											gid = 0;
											break;
										}
										Out.debug("Downloader: Created directory " + todir);
										FileTools.checkAndCreateDir(todir);
									}
								} else if(parseState == 1) {
									if(s.isEmpty()) {
										continue;
									}

									String[] split = s.split(" ", 3);
									int page = Integer.parseInt(split[0]);
									GalleryFile gf = GalleryFile.getGalleryFile(client, todir, split[1], gid, page, split[2]);
									
									if(gf != null) {
										if(page >= 1 && page <= files) {
											galleryFiles[page - 1] = gf;
										} else {
											Out.warning("File " + gf.getFileid() + " is outside allowed page range.");
										}
									}
								} else {
									information = information.concat(s).concat(Settings.NEWLINE);
								}
							}
							
							if((gid > 0) && (files > 0) && (title != null) && (galleryFiles != null)) {
								pendingGalleries.add(new Gallery(client, processFile, todir, title, information, galleryFiles));
								processedHHDLFiles.add(processFile);
							}
						}
					}
				} catch(java.io.IOException e) {
					Out.warning("Downloader: Encountered I/O error while processing HHDL files.");
					e.printStackTrace();
				}

				if(! pendingGalleries.isEmpty()) {
					boolean doDownload = true;
				
					if(! Settings.isSkipFreeSpaceCheck())	{
						long diskFreeSpace = downloadeddir.getFreeSpace();

						if(diskFreeSpace < Math.max(Settings.getDiskMinRemainingBytes(), 104857600)) {
							Out.warning("Downloader: There is less than the minimum allowed space left on the storage device. The Hentai@Home Downloader is waiting for more disk space before it can continue.");
							sleepTime = 300000;
							doDownload = false;
						}
					}
					
					if(doDownload) {
						List<GalleryFile> galleryFiles = new ArrayList<GalleryFile>();
						List<Gallery> completed = new ArrayList<Gallery>();
						
						for(Gallery g : pendingGalleries) {
							if(client.isShuttingDown()) {
								break;
							}
						
							g.galleryPass(galleryFiles);
							
							if(g.getState() != Gallery.STATE_PENDING) {
								completed.add(g);
							}
						}
						
						for(Gallery g : completed) {
							pendingGalleries.remove(g);
						}
						
						if( !galleryFiles.isEmpty() && (lastTokenRequest < System.currentTimeMillis() - 60000) ) {
							// request up to 20 tokens a minute from the server
							
							List<String> requestTokens = new ArrayList<String>();
							
							for(GalleryFile gf : galleryFiles) {
								requestTokens.add(gf.getFileid());
							}
							
							lastTokenRequest = System.currentTimeMillis();
							Hashtable<String, String> tokens = client.getServerHandler().getFileTokens(requestTokens);
							
							if(tokens == null) {
								sleepTime = 180000;
							} else {
								for(GalleryFile gf : galleryFiles) {
									String token = tokens.get(gf.getFileid());
									
									if(token != null) {
										gf.setNewToken(token);
									}
								}
							}
						}
					}
				}
			}

			try {
				Thread.sleep(sleepTime);
			} catch(java.lang.InterruptedException e) {}
		}
		
		Out.info("Gallery Download Manager terminated.");
	}
}
