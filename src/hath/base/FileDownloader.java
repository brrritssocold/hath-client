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

import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import javax.net.ssl.HttpsURLConnection;

public class FileDownloader implements Runnable {
	private int timeout = 30000, maxDLTime = Integer.MAX_VALUE, retries = 3;
	private long timeDownloadStart = 0, timeFirstByte = 0, timeDownloadFinish = 0;
	private ByteBuffer byteBuffer = null;
	private HTTPBandwidthMonitor downloadLimiter = null;
	private Path outputPath = null;
	private URL source;
	private Thread myThread;
	private Object downloadLock = new Object();
	private boolean started = false, discardData = false, successful = false;

	public FileDownloader(URL source, int timeout, int maxDLTime) {
		// everything will be written to a ByteBuffer
		this.source = source;
		this.timeout = timeout;
		this.maxDLTime = maxDLTime;
	}

	public FileDownloader(URL source, int timeout, int maxDLTime, boolean discardData) {
		// if discardData is true, no buffer will be allocated and the data stream will be discarded
		this.source = source;
		this.timeout = timeout;
		this.maxDLTime = maxDLTime;
		this.discardData = discardData;
	}

	public FileDownloader(URL source, int timeout, int maxDLTime, Path outputPath) {
		// in this case, the data will be written directly to a channel specified by outputPath
		this.source = source;
		this.timeout = timeout;
		this.maxDLTime = maxDLTime;
		this.outputPath = outputPath;
	}
	
	public void setDownloadLimiter(HTTPBandwidthMonitor limiter) {
		downloadLimiter = limiter;
	}

	public boolean downloadFile() {
		// this will block while the file is downloaded
		if(myThread == null) {
			// if startAsyncDownload has not been called, we invoke run() directly and skip threading
			run();
		}
		else {
			waitAsyncDownload();
		}

		return successful;
	}

	public void startAsyncDownload() {
		// start a new thread to handle the download. this will return immediately
		if(myThread == null) {
			myThread = new Thread(this);
			myThread.start();
		}
	}

	public boolean waitAsyncDownload() {
		// make sure the download thread has actually finished starting up
		try {
			int timeout = 1000;
			
			while(!started && (--timeout > 0)) {
				Thread.currentThread().sleep(1000);
			}
		}
		catch(Exception e) {}

		// synchronize on the download lock to wait for the download attempts to complete before returning
		synchronized(downloadLock) {
			Out.debug("Finished async wait for source=" + source + " with timeDownloadStart=" + timeDownloadStart + " timeFirstByte=" + timeFirstByte + " timeDownloadFinish=" + timeDownloadFinish + " successful=" + successful);
		}

		return successful;
	}

	public String getResponseAsString(String charset) {
		if(downloadFile()) {
			if(byteBuffer != null) {
				byteBuffer.flip();
				byte[] temp = new byte[byteBuffer.remaining()];
				byteBuffer.get(temp);

				try {
					return new String(temp, charset);
				} catch(UnsupportedEncodingException e) {
					HentaiAtHomeClient.dieWithError(e);
				}
			}
		}

		return null;
	}

	public long getDownloadTimeMillis() {
		return timeFirstByte > 0 ? timeDownloadFinish - timeFirstByte : 0;
	}

	public void run() {
		synchronized(downloadLock) {
			if(started) {
				return;
			}

			FileChannel outputChannel = null;
			started = true;

			while(!successful && --retries >= 0) {
				InputStream is = null;

				try {
					Out.debug("Connecting to " + source.getHost() + "...");

					// should return a HttpURLConnection for http and HttpsURLConnection for https
					URLConnection connection = source.openConnection();
					
					connection.setConnectTimeout(5000);
					connection.setReadTimeout(timeout);
					connection.setRequestProperty("Connection", "Close");
					connection.setRequestProperty("User-Agent", "Hentai@Home " + Settings.CLIENT_VERSION);
					connection.connect();
					
					/*
					if(connection instanceof HttpsURLConnection) {
						HttpsURLConnection testconn = (HttpsURLConnection) connection;
						Out.debug("type=https cipher=" + testconn.getCipherSuite() + " response=" + testconn.getResponseCode());
					}
					*/

					int contentLength = connection.getContentLength();

					if(contentLength < 0) {
						// since we control all systems in this case, we'll demand that clients and servers always send the Content-Length
						Out.warning("Request host did not send Content-Length, aborting transfer." + " (" + connection + ")");
						Out.warning("Note: A common reason for this is running firewalls with outgoing restrictions or programs like PeerGuardian/PeerBlock. Verify that the remote host is not blocked.");
						throw new java.net.SocketException("Invalid or missing Content-Length");
					}
					else if(contentLength > 10485760 && !discardData && outputPath == null) {
						// if we're writing to a ByteBuffer, hard limit responses to 10MB
						Out.warning("Reported contentLength " + contentLength + " exceeds max allowed size for memory buffer download");
						throw new java.net.SocketException("Reply exceeds expected length");
					}
					else if(contentLength > Settings.getMaxAllowedFileSize()) {
						Out.warning("Reported contentLength " + contentLength + " exceeds currently max allowed filesize " + Settings.getMaxAllowedFileSize());
						throw new java.net.SocketException("Reply exceeds expected length");
					}

					is = connection.getInputStream();

					if(!discardData) {
						if(outputPath == null) {
							if(byteBuffer != null) {
								if(byteBuffer.capacity() < contentLength) {
									// if we are retrying and the length has increased, we have to allocate a new buffer
									byteBuffer = null;
								}
							}

							if(byteBuffer == null) {
								byteBuffer = ByteBuffer.allocateDirect(contentLength);
								//Out.debug("Allocated byteBuffer (length=" + byteBuffer.capacity() + ")");
							}
							else {
								byteBuffer.clear();
								//Out.debug("Cleared byteBuffer (length=" + byteBuffer.capacity() + ")");
							}
						}
						else {
							if(outputChannel == null) {
								outputChannel = FileChannel.open(outputPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
								Out.debug("FileChannel for output opened");
							}
							else {
								outputChannel.truncate(0);
								Out.debug("Truncated open file, set position to " + outputChannel.position());
							}
						}
					}

					Out.debug("Reading " + contentLength + " bytes from " + source);
					timeDownloadStart = System.currentTimeMillis();

					long writeoff = 0;	// counts the number of bytes read
					int readbytes = 0;	// the number of bytes in the last read
					
					// HttpsURLConnection is retarded and breaks (does not download more data) unless we do a blocking read, so now we use a normal byte array as a buffer like some primitive savage
					byte[] buffer = new byte[1500];
					
					do {
						readbytes = is.read(buffer);

						if(readbytes > 0) {
							//Out.debug("Read " + readbytes + " bytes of data");

							if(timeFirstByte == 0) {
								timeFirstByte = System.currentTimeMillis();
							}

							if(discardData) {
								//Out.debug("Skipped " + readbytes + " bytes");
							}
							else if(outputPath == null) {
								byteBuffer.put(buffer, 0, readbytes);
								//Out.debug("Added " + readbytes + " bytes to byteBuffer");
							}
							else {
								outputChannel.write(ByteBuffer.wrap(buffer, 0, readbytes));
								//Out.debug("Wrote " + readbytes + " bytes to outputChannel");
							}
							
							writeoff += readbytes;
							
							/*
							if(retries == 2 && writeoff > 50000) {
								Out.info("Pretented to fail");
								break;
							}
							*/

							if(downloadLimiter != null) {
								downloadLimiter.waitForQuota(Thread.currentThread(), (int) readbytes);
							}
						}
					} while(readbytes > 0);

					successful = writeoff == contentLength;
					timeDownloadFinish = System.currentTimeMillis();
					long dltime = getDownloadTimeMillis();
					Out.debug("Finished download for " + source + " in " + dltime + " ms" + (dltime > 0 ? ", speed=" + (writeoff / dltime) + "KB/s" : "") + ", writeoff=" + writeoff + ", successful=" + (successful ? "yes" : "no"));
					Stats.bytesRcvd(contentLength);
				}
				catch(Exception e) {
					if(e instanceof java.io.FileNotFoundException) {
						Out.warning("Server returned: 404 Not Found");
						break;
					}
					else if(e.getCause() instanceof java.io.FileNotFoundException) {
						Out.warning("Server returned: 404 Not Found");
						break;
					}

					Out.warning(e.toString());
					Out.warning("Retrying.. (" + retries + " tries left)");
					continue;
				}
				finally {
					try {
						is.close();
					} catch(Exception e) {}
				}
			}

			if(outputChannel != null) {
				try {
					outputChannel.close();

					if(!successful) {
						outputPath.toFile().delete();
					}
				} catch(Exception e) {}
			}

			if(!successful ) {
				Out.warning("Exhaused retries or aborted getting " + source);
			}
		}
	}
	
	public static void main(String[] args) {
		try {
			/*
			// skippy
			URL testurl = new URL("https://ehgt.org/b/2019-10/1.jpg");
			FileDownloader testdl = new FileDownloader(testurl, 30000, 30000, true);
			testdl.downloadFile();
			*/

			/*
			// savey
			URL testurl = new URL("https://ehgt.org/b/2019-10/1.jpg");
			File testfile = new File("testfile.jpg");
			FileDownloader testdl = new FileDownloader(testurl, 30000, 30000, testfile.toPath());
			testdl.downloadFile();
			*/
			
			/*
			// showy
			URL testurl = new URL("https://ehgt.org/g/opensearchdescription.xml");
			FileDownloader testdl = new FileDownloader(testurl, 30000, 30000);
			Out.info(testdl.getResponseAsString("UTF8"));
			*/
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}
