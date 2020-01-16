/*

Copyright 2008-2019 E-Hentai.org
https://forums.e-hentai.org/
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

package hath.base;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import hath.base.http.HTTPBandwidthMonitor;

public class FileDownloader implements Runnable {
	private int timeout = 30000, maxDLTime = Integer.MAX_VALUE, retries = 3;
	private long timeDownloadStart = 0, timeFirstByte = 0, timeDownloadFinish = 0;
	private ByteBuffer byteBuffer = null;
	private FileChannel outputChannel = null;
	private HTTPBandwidthMonitor downloadLimiter = null;
	private Path outputPath = null;
	private URL source;
	private Thread myThread;
	private Object downloadLock = new Object();
	private boolean started = false, discardData = false;

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

		return timeDownloadFinish > 0;
	}

	public void startAsyncDownload() {
		// start a new thread to handle the download. this will return immediately
		if(myThread == null) {
			myThread = new Thread(this, FileDownloader.class.getSimpleName());
			myThread.start();
		}
	}

	public boolean waitAsyncDownload() {
		// synchronize on the download lock to wait for the download attempts to complete before returning
		synchronized(downloadLock) {}
		return timeDownloadFinish > 0;
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
			boolean success = false;

			if(started) {
				return;
			}

			started = true;

			while(!success && --retries >= 0) {
				InputStream is = null;
				ReadableByteChannel rbc = null;

				try {
					Out.info("Connecting to " + source.getHost() + "...");

					URLConnection connection = source.openConnection();
					connection.setConnectTimeout(5000);
					connection.setReadTimeout(timeout);
					connection.setRequestProperty("Connection", "Close");
					connection.setRequestProperty("User-Agent", "Hentai@Home " + Settings.CLIENT_VERSION);
					connection.connect();

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
					else if (contentLength > Settings.getInstance().getMaxAllowedFileSize()) {
						Out.warning(
								"Reported contentLength " + contentLength + " exceeds currently max allowed filesize "
										+ Settings.getInstance().getMaxAllowedFileSize());
						throw new java.net.SocketException("Reply exceeds expected length");
					}

					is = connection.getInputStream();

					if(!discardData) {
						rbc = Channels.newChannel(is);
						//Out.debug("ReadableByteChannel for input opened");

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
						else if(outputChannel == null) {
							outputChannel = FileChannel.open(outputPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
							//Out.debug("FileChannel for output opened");
						}
					}

					Out.info("Reading " + contentLength + " bytes from " + source);
					timeDownloadStart = System.currentTimeMillis();

					long writeoff = 0;	// counts the number of bytes read
					long readcount = 0;	// the number of bytes in the last read
					int available = 0;	// the number of bytes available to read
					int time = 0;		// counts the approximate time (in nanofortnights) since last byte was received

					while(writeoff < contentLength) {
						available = is.available();

						if(available > 0) {
							if(timeFirstByte == 0) {
								timeFirstByte = System.currentTimeMillis();
							}

							time = 0;

							if(discardData) {
								readcount = is.skip(available);
								//Out.debug("Skipped " + readcount + " bytes");
							}
							else if(outputPath == null) {
								readcount = rbc.read(byteBuffer);
								//Out.debug("Added " + readcount + " bytes to byteBuffer");
							}
							else {
								readcount = outputChannel.transferFrom(rbc, writeoff, (long) available);
								//Out.debug("Wrote " + readcount + " bytes to outputChannel");
							}

							if(readcount >= 0) {
								writeoff += readcount;
							}
							else {
								// readcount == -1 => EOF
								Out.warning("\nServer sent premature EOF, aborting.. (" + writeoff + " of " + contentLength + " bytes received)");
								throw new java.net.SocketException("Unexpected end of file from server");
							}
							
							if(downloadLimiter != null) {
								downloadLimiter.waitForQuota(Thread.currentThread(), (int) readcount);
							}
						}
						else {
							if(System.currentTimeMillis() - timeDownloadStart > maxDLTime) {
								Out.warning("\nDownload time limit has expired, aborting...");
								throw new java.net.SocketTimeoutException("Download timed out");
							}
							else if(time > timeout) {
								Out.warning("\nTimeout detected waiting for byte " + writeoff + ", aborting..");
								throw new java.net.SocketTimeoutException("Read timed out");
							}

							time += 5;
							Thread.currentThread().sleep(5);
						}
					}

					timeDownloadFinish = System.currentTimeMillis();
					long dltime = getDownloadTimeMillis();
					Out.debug("Finished in " + dltime + " ms" + (dltime > 0 ? ", speed=" + (writeoff / dltime) + "KB/s" : "") + ", writeoff=" + writeoff);
					Stats.bytesRcvd(contentLength);
					success = true;
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
					if(rbc != null) {
						try {
							rbc.close();
						} catch(Exception e) {}
					}
					
					try {
						is.close();
					} catch(Exception e) {}
				}
			}

			if(outputChannel != null) {
				try {
					outputChannel.close();

					if(!success) {
						outputPath.toFile().delete();
					}
				} catch(Exception e) {}
			}

			if(!success ) {
				Out.warning("Exhaused retries or aborted getting " + source);
			}
		}
	}
}
