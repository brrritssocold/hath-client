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

package org.hath.base.gallery;

import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.InputStreamResponseListener;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.hath.base.CacheHandler;
import org.hath.base.FileTools;
import org.hath.base.HVFile;
import org.hath.base.HentaiAtHomeClient;
import org.hath.base.MiscTools;
import org.hath.base.Out;
import org.hath.base.Settings;
import org.hath.base.Stats;

public class GalleryFileDownloader implements Runnable {
	public static final int DOWNLOAD_PENDING = 0;
	public static final int DOWNLOAD_COMPLETE = 1;
	public static final int DOWNLOAD_FAILED_INIT = -1;
	public static final int DOWNLOAD_FAILED_CONN = -2;

	private HentaiAtHomeClient client;

	private HVFile requestedHVFile;
	private String fileid;
	private String token;
	private int gid;
	private int page;
	private String filename;	
	private boolean skipHath;

	private byte[] databuffer;
	private int writeoff;

	private int contentLength;
	private Thread myThread;
	private HttpClient httpClient;
	private Request request;
	private Response response;
	private InputStreamResponseListener isrl;
	
	private int downloadState;
	private int readTimeoutMilli = 30000;
	private int connectTimeoutMilli = 10000;

	public LinkedList<Sensing> sensingPointHits = new LinkedList<>();
	public enum Sensing {
		CONTENT_LENGTH_MISMATCH, CONTENT_LENGTH_GREATER_10MB, CONTENT_LENGTH_MATCH, INTI_FAIL, BYTES_READ
	};

	private void hitSensingPoint(Sensing point) {
		sensingPointHits.add(point);
	}

	public GalleryFileDownloader(HentaiAtHomeClient client, String fileid, String token, int gid, int page, String filename, boolean skipHath) {
		this.client = client;
		this.fileid = fileid;
		this.token = token;
		this.gid = gid;
		this.page = page;
		this.filename = filename;
		this.skipHath = skipHath;

		this.requestedHVFile = HVFile.getHVFileFromFileid(fileid);
		writeoff = 0;
		downloadState = DOWNLOAD_PENDING;
		myThread = new Thread(this);
	}
	
	public int initialize() {
		try {
			initialize(new URL("http", Settings.getRequestServer(), "/r/" + fileid + "/" + token + "/" + gid + "-"
					+ page + "/" + filename + (skipHath ? "?nl=1" : "")));
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}

		downloadState = DOWNLOAD_FAILED_INIT;
		return 500;
	}

	public int initialize(URL source, int readTimeout, int connectTimeout) {
		this.readTimeoutMilli = readTimeout;
		this.connectTimeoutMilli = connectTimeout;
		return initialize(source);
	}

	public int initialize(URL source) {
		// we'll need to run this in a private thread so we can push data to the originating client at the same time we download it (pass-through), so we'll use a specialized version of the stuff found in FileDownloader
		// this also handles negotiating file browse limits with the server
		Out.info("Gallery File Download Request initializing for " + fileid + "...");

		try {
			boolean retry = false;
			int retval = 0;
			int tempLength = 0;

			httpClient = new HttpClient();
			httpClient.setConnectTimeout(connectTimeoutMilli);
			httpClient.setIdleTimeout(readTimeoutMilli);
			httpClient.start();
			httpClient.setUserAgentField(new HttpField(HttpHeader.USER_AGENT,
					"Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US; rv:1.8.1.12) Gecko/20080201 Firefox/2.0.0.12"));

			do {
				retry = false;
			
				Out.debug("GalleryFileDownloader: Requesting file download from " + source);

				request = httpClient.newRequest(source.toURI()).header("Hath-Request",
						Settings.getClientID() + "-" + MiscTools.getSHAString(Settings.getClientKey() + fileid));

				isrl = new InputStreamResponseListener();
				request.send(isrl);
				
				response = isrl.get(readTimeoutMilli, TimeUnit.MILLISECONDS);
				if (!response.getHeaders().contains(HttpHeader.CONTENT_LENGTH)) {
					tempLength = -1;
				} else {
					tempLength = response.getHeaders().getField(HttpHeader.CONTENT_LENGTH).getIntValue();
				}

				if (tempLength < 0) {
					Out.warning("Request host did not send Content-Length, aborting transfer." + " (" + request + ")");
					Out.warning("Note: A common reason for this is running firewalls with outgoing restrictions or programs like PeerGuardian/PeerBlock. Verify that the remote host is not blocked.");
					retval = 502;
				}
				else if(tempLength > 10485760) {
					Out.warning("Content-Length is larger than 10 MB, aborting transfer." + " (" + request + ")");
					retval = 502;
					hitSensingPoint(Sensing.CONTENT_LENGTH_GREATER_10MB);
				}
				else if(tempLength != requestedHVFile.getSize()) {
					Out.warning("Reported contentLength " + contentLength + " does not match expected length of file "
							+ fileid + " (" + request + ")");
					hitSensingPoint(Sensing.CONTENT_LENGTH_MISMATCH);
					
					// this could be more solid, but it's not important. this will only be tested if there is a fail, and even if the fail somehow matches the size of the error images, the server won't actually increase the limit unless we're close to it.
					if(retval == 0 && (tempLength == 28658 || tempLength == 1009)) {
						Out.warning("We appear to have reached the image limit. Attempting to contact the server to ask for a limit increase...");
						client.getServerHandler().notifyMoreFiles();
						retry = true;
						retval = 502;
					}
				} else {
					retval = 0;
					hitSensingPoint(Sensing.CONTENT_LENGTH_MATCH);
				}
			} while(retry);
			
			if(retval > 0) {
				// file could not be retrieved from upstream server
				downloadState = DOWNLOAD_FAILED_INIT;
				return retval;
			}
			
			contentLength = tempLength;
			databuffer = new byte[contentLength];
			
			// at this point, everything is ready to receive data from the server and pass it to the client. in order to do this, we'll fork off a new thread to handle the reading, while this thread returns.
			// control will thus pass to the HTTPSession where this HRP's read functions will be called, and data will be written to the connection this proxy request originated from.
			
			myThread.start();
		
			return 200;
		} catch(Exception e) {
			e.printStackTrace();
			hitSensingPoint(Sensing.INTI_FAIL);
		}
		
		downloadState = DOWNLOAD_FAILED_INIT;
		return 500;
	}

	public void run() {
		int trycounter = 3;
		boolean complete = false;
		boolean success = false;

		do {
			try (InputStream is = isrl.getInputStream()) {
				int bytestatcounter = 0;

				// note: this may seen unnecessarily hackjob-ish, but because the built-in timeouts were unreliable at best (at the time of testing), this was a way to deal with the uncertainties of the interwebs. not exactly C10K stuff, but it works.
				while(writeoff < contentLength) {
						// read-data loop..
						
					int b = is.read();

						if(b >= 0) {
							databuffer[writeoff++] = (byte) b;
							
							if(++bytestatcounter > 1000) {
								Stats.bytesRcvd(bytestatcounter);
								bytestatcounter -= 1000;
								hitSensingPoint(Sensing.BYTES_READ);
							}
						}
						else {
							// b == -1 => EOF
							Out.warning("\nServer sent premature EOF, aborting.. (" + writeoff + " of " + contentLength + " bytes received)");
							throw new java.net.SocketException("Unexpected end of file from server");
						}
				}
				
				Stats.bytesRcvd(bytestatcounter);
				Stats.fileRcvd();
				complete = true;
			} catch(Exception e) {
				e.printStackTrace();
				writeoff = 0;
				Arrays.fill(databuffer, (byte) 0);
				Out.debug("Retrying.. (" + trycounter + " tries left)");
			}
			// TODO does this even work?
		} while(!complete && --trycounter > 0);

		
		if(writeoff != getContentLength()) {
			Out.debug("Requested file " + fileid + " is incomplete, and was not stored.");				
		} else if(! MiscTools.getSHAString(databuffer).equals(requestedHVFile.getHash())) {
			Out.debug("Requested file " + fileid + " is corrupt, and was not stored.");				
		} else {
			try {
				CacheHandler cacheHandler = client.getCacheHandler();
				File tmpfile = File.createTempFile("hathproxy_", "", CacheHandler.getTmpDir());
				FileTools.putFileContents(tmpfile, databuffer);
				
				if(cacheHandler.moveFileToCacheDir(tmpfile, requestedHVFile)) {
					cacheHandler.addFileToActiveCache(requestedHVFile);
					cacheHandler.addPendingRegisterFile(requestedHVFile);
					Out.debug("Requested file " + fileid + " was successfully stored in cache.");
					success = true;
				}
				else {
					tmpfile.delete();
					Out.debug("Requested file " + fileid + " exists or cannot be cached, and was dropped.");
				}
				
				Out.info("Gallery File Download Request complete for " + fileid);
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
		
		if(success) {
			downloadState = DOWNLOAD_COMPLETE;
		} else {
			downloadState = DOWNLOAD_FAILED_CONN;
		}
	}
	
	public String getContentType() {
		return requestedHVFile.getMimeType();
	}

	public int getContentLength() {
		return requestedHVFile.getSize();
	}

	public int getCurrentWriteoff() {
		return writeoff;
	}
	
	public int getDownloadState() {
		return downloadState;
	}
	
	public byte[] getDownloadBufferRange(int readoff, int endoff) {
		return Arrays.copyOfRange(databuffer, readoff, endoff);
	}
}
