/*

Copyright 2008-2013 E-Hentai.org
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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import org.hath.base.util.FileTools;

public class FileDownloader implements Runnable {
	private int timeout = 30000, maxDLTime = Integer.MAX_VALUE, retries = 3;
	private long timeDownloadStart = 0, timeFirstByte = 0, timeDownloadFinish = 0;
	private byte[] bytearray = null;
	private URL source;
	private Thread myThread;
	private Object downloadLock = new Object();
	private boolean started = false;
	
	public FileDownloader(URL source) {
		this.source = source;
	}

	public FileDownloader(URL source, int timeout) {
		this.source = source;
		this.timeout = timeout;
	}
	
	public FileDownloader(URL source, int timeout, int maxDLTime) {
		this.source = source;
		this.timeout = timeout;
		this.maxDLTime = maxDLTime;
	}

	public boolean saveFile(File destination) {
		if(destination.exists()) {
			destination.delete();
		}

		FileOutputStream fos = null;

		try {
			FileTools.checkAndCreateDir(destination.getParentFile());

			if(downloadFile()) {
				fos = new FileOutputStream(destination);
				fos.write(bytearray, 0, bytearray.length);
				fos.close();

				return true;
			}
		} catch(Exception e) {
			try { fos.close(); } catch(Exception e2) {}	// nuke file handle if open

			if(e instanceof java.io.IOException && e.getMessage().equals("There is not enough space on the disk")) {
				Out.warning("Error: No space on disk");
			}
			else {
				Out.warning(e + " while saving file " + source + " to " + destination.getAbsolutePath());
				e.printStackTrace();
			}
		}

		return false;
	}

	public String getTextContent() {
		if(downloadFile()) {
			return new String(bytearray, 0, bytearray.length);
		}
		else {
			return null;
		}
	}
	
	private boolean downloadFile() {
		// this will block while the file is downloaded
		if(myThread == null) {
			// if startAsyncDownload has not been called, we invoke run() directly and skip threading
			run();
		}
		else {
			waitAsyncDownload();
		}

		return timeDownloadFinish > 0;
	}
	
	public void startAsyncDownload() {
		// start a new thread to handle the download. this will return immediately
		if(myThread == null) {
			myThread = new Thread(this);
			myThread.start();
		}
	}
	
	public boolean waitAsyncDownload() {
		// synchronize on the download lock to wait for the download attempts to complete before returning
		synchronized(downloadLock) {}
		return timeDownloadFinish > 0;
	}
	
	public long getDownloadTimeMillis() {
		return timeFirstByte > 0 ? timeDownloadFinish - timeFirstByte : 0;
	}

	public void run() {
		synchronized(downloadLock) {
			if(started) {
				return;
			}
			
			started = true;
		
			while(retries-- > 0) {
				InputStream is = null;
				BufferedInputStream bis = null;

				try {
					Out.info("Connecting to " + source.getHost() + "...");
					
					URLConnection connection = source.openConnection();
					
					connection.setConnectTimeout(10000);
					connection.setReadTimeout(timeout);	// this doesn't always seem to work however, so we'll do it somewhat differently..
					connection.setRequestProperty("Connection", "Close");
					connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US; rv:1.8.1.12) Gecko/20080201 Firefox/2.0.0.12");
					connection.connect();

					Out.debug("Connected to " + source);
					
					int contentLength = connection.getContentLength();

					if(contentLength < 0) {
						// H@H note: since we control all systems in this case, we'll demand that clients and servers always send the Content-Length
						Out.warning("Remote host did not send Content-Length, aborting transfer.");
						return;
					}
					else if(contentLength > 10485760) {
						// H@H note: we don't want clients trying to provoke an outofmemory exception by returning a malformed oversized reply, so we'll limit the download size to the H@H max (10 MB). the server will never send anything this large as a response either.
						Out.warning("Reported contentLength " + contentLength + " on request " + source + " is out of bounds!");
						return;
					} 

					Out.debug("Received contentLength=" + contentLength);
					
					bytearray = new byte[contentLength];
					is = connection.getInputStream();
					bis = new BufferedInputStream(is);

					Out.info(source.getPath() + (source.getQuery() != null ? "?" + source.getQuery() : "") + ": Retrieving " + contentLength + " bytes...");
					timeDownloadStart = System.currentTimeMillis();

					int bytecounter = 0;	// counts the number of bytes read
					int time = 0;			// counts the approximate time (in nanofortnights) since last byte was received

					while(bytecounter < contentLength) {
						int available = bis.available();
						
						if(available > 0) {
							// read-data loop..
							
							if(timeFirstByte == 0) {
								timeFirstByte = System.currentTimeMillis();
							}
							
							time = 0;
							int readcount = bis.read(bytearray, bytecounter, available);

							if(readcount >= 0) {
								bytecounter += readcount;
							}
							else {
								// readcount == -1 => EOF
								Out.warning("\nServer sent premature EOF, aborting.. (" + bytecounter + " of " + contentLength + " bytes received)");
								throw new java.net.SocketException("Unexpected end of file from server");
							}
						}
						else {
							// wait-for-data loop...
						
							if(System.currentTimeMillis() - timeDownloadStart > maxDLTime) {
								Out.warning("\nDownload time limit has expired, aborting...");
								throw new java.net.SocketTimeoutException("Download timed out");							
							}
							else if(time > timeout) {
								Out.warning("\nTimeout detected waiting for byte " + bytecounter + ", aborting..");
								throw new java.net.SocketTimeoutException("Read timed out");
							}

							time += 5;
							Thread.sleep(5);
						}
					}
					
					Out.debug("Finished. bytecounter=" + bytecounter);

					bis.close();
					is.close();
					
					Stats.bytesRcvd(contentLength);
					
					timeDownloadFinish = System.currentTimeMillis();
					return;
				} catch(Exception e) {
					try { bis.close(); } catch(Exception e2) {}
					try { is.close(); } catch(Exception e3) {}

					String message = e.getMessage();
					Throwable cause = e.getCause();
					String causemessage = null;
					
					if(cause != null) {
						causemessage = (cause.getMessage() != null) ? cause.getMessage() : "";
					}

					if(message != null) {
						if(message.equals("Connection timed out: connect")) {
							Out.warning("Connection timed out getting " + source + ", retrying.. (" + retries + " tries left)");
							continue;
						}
						else if(message.equals("Connection refused: connect")) {
							Out.warning("Connection refused getting " + source + ", retrying.. (" + retries + " tries left)");
							continue;
						}
						else if(message.equals("Unexpected end of file from server")) {
							Out.warning("Connection prematurely reset getting " + source + ", retrying.. (" + retries + " tries left)");
							continue;
						}
						else if(e instanceof java.io.FileNotFoundException) {
							Out.warning("Server returned: 404 Not Found");
							break;
						}
						else if(message.indexOf("403 for URL") >= 0) {
							Out.warning("Server returned: 403 Forbidden");
							break;
						}
						else if(e instanceof java.net.SocketException && message.equals("Connection reset")) {
							Out.warning("Connection reset getting " + source + ", retrying.. (" + retries + " tries left)");
							continue;
						}
						else if(e instanceof java.net.UnknownHostException) {
							Out.warning("Unknown host " + source.getHost() + ", aborting..");
							break;
						}
						else if(e instanceof java.net.SocketTimeoutException) {
							Out.warning("Read timed out, retrying.. (" + retries + " tries left)");
							continue;
						}
						else {
							Out.warning("Unhandled exception: " + e.toString());
							e.printStackTrace();
							Out.warning("Retrying.. (" + retries + " tries left)");
							continue;
						}
					}
					else if(cause != null){
						if(cause instanceof java.io.FileNotFoundException) {
							Out.warning("Server returned: 404 Not Found");
							break;
						}
						else if(causemessage.indexOf("403 for URL") >= 0) {
							Out.warning("Server returned: 403 Forbidden");
							break;
						}
						else if(causemessage.equals("Unexpected end of file from server")) {
							Out.warning("Connection prematurely reset getting " + source + ", retrying.. (" + retries + " tries left)");
							continue;
						}
						else {
							Out.warning("Unhandled exception/cause: " + e.toString());
							e.printStackTrace();
							Out.warning("Retrying.. (" + retries + " tries left)");
							continue;
						}
					}
					else {
						Out.warning("Exception with no exception message nor cause:");
						e.printStackTrace();
						Out.warning("Retrying.. (" + retries + " tries left)");
						continue;
					}
				}
			}
		}

		Out.warning("Exhaused retries or aborted getting " + source);
		return;
	}
	
	public static void main(String[] args) {
		if(args.length != 2) {
			System.out.println("Need source and destination.");
		}
		else {
			try {
				URL source = new URL(args[0]);
				File dest = new File(args[1]);

				System.out.println("Downloading file " + source);

				FileDownloader dler = new FileDownloader(source);
				dler.saveFile(dest);
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
	}
}