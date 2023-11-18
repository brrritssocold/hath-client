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
import java.util.Arrays;
import java.net.URL;
import java.net.URLConnection;
import java.io.File;
import java.io.RandomAccessFile;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.ByteBuffer;
import java.security.MessageDigest;

public class ProxyFileDownloader implements Runnable {
	private HentaiAtHomeClient client;
	private HVFile requestedHVFile;
	private String fileid;
	private File tempFile = null, returnFile = null;
	private RandomAccessFile fileHandle;
	private FileChannel fileChannel;
	private URL[] sources;
	private URLConnection connection;
	private Thread myThread;
	private MessageDigest sha1Digest;
	private int readoff, writeoff, contentLength;
	private boolean streamThreadSuccess = false, streamThreadComplete = false, proxyThreadComplete = false, fileFinalized = false;
	private Object downloadLock = new Object();

	public ProxyFileDownloader(HentaiAtHomeClient client, String fileid, URL[] sources) {
		this.client = client;
		this.fileid = fileid;
		this.sources = sources;

		this.requestedHVFile = HVFile.getHVFileFromFileid(fileid);
		writeoff = 0;
		readoff = 0;
		myThread = new Thread(this);
	}

	public int initialize() {
		// we'll need to run this in a private thread so we can push data to the originating client at the same time we download it (pass-through)
		// this will NOT work with HTTPS (see FileDownloader), but upstream can be kept as HTTP so This Is Fine™

		Out.debug("ProxyFileDownloader::initialize with fileid=" + fileid + " sources=" + Arrays.toString(sources)); 
		int retval = 500;
		
		for(URL source : sources) {
			try {
				Out.debug("ProxyFileDownloader: Requesting file download from " + source);
				
				connection = source.openConnection();
				connection.setConnectTimeout(5000);
				connection.setReadTimeout(30000);
				connection.setRequestProperty("Hath-Request", Settings.getClientID() + "-" + Tools.getSHA1String(Settings.getClientKey() + fileid));
				connection.setRequestProperty("User-Agent", "Hentai@Home " + Settings.CLIENT_VERSION);
				connection.connect();

				int tempLength = connection.getContentLength();
				retval = 0;

				if(tempLength < 0) {
					Out.warning("Request host did not send Content-Length, aborting transfer." + " (" + connection + ")");
					Out.warning("Note: A common reason for this is running firewalls with outgoing restrictions or programs like PeerGuardian/PeerBlock. Verify that the remote host is not blocked.");
					retval = 502;
				}
				else if(tempLength > Settings.getMaxAllowedFileSize()) {
					Out.warning("Reported contentLength " + contentLength + " exceeds currently max allowed filesize " + Settings.getMaxAllowedFileSize());
					retval = 502;
				}
				else if(tempLength != requestedHVFile.getSize()) {
					Out.warning("Reported contentLength " + contentLength + " does not match expected length of file " + fileid + " (" + connection + ")");
					retval = 502;
				}

				if(retval > 0) {
					// connection to upstream server could not be properly established; retry if possible
					continue;
				}

				contentLength = tempLength;

				// create the temporary file used to hold the proxied data
				tempFile = File.createTempFile("proxyfile_", "", Settings.getTempDir());
				fileHandle = new RandomAccessFile(tempFile, "rw");
				fileChannel = fileHandle.getChannel();

				// we need to calculate the SHA-1 hash at some point, so we might as well do it on the fly
				sha1Digest = MessageDigest.getInstance("SHA-1");

				// at this point, everything is ready to receive data from the server and pass it to the client. in order to do this, we'll fork off a new thread to handle the reading, while this thread returns.
				// control will thus pass to the HTTPSession where this HRP's read functions will be called, and data will be written to the connection this proxy request originated from.
				myThread.start();

				retval = 200;
				break;
			}
			catch(Exception e) {
				Out.warning(e.getMessage());

				try {
					if(fileHandle != null) {
						fileHandle.close();
					}
				} catch(Exception e2) {}
			}
		}

		return retval;
	}

	public void run() {
		synchronized(downloadLock) {
			int trycounter = 3;
			int bufferSize = 65536;
			int bufferThreshold = (int) Math.floor(bufferSize * 0.75);
			ByteBuffer byteBuffer = ByteBuffer.allocateDirect(Math.min(contentLength, bufferSize));

			do {
				InputStream is = null;
				ReadableByteChannel rbc = null;

				try {
					is = connection.getInputStream();
					rbc = Channels.newChannel(is);

					long downloadStart = System.currentTimeMillis();
					int readcount = 0;	// the number of bytes in the last read
					int writecount = 0;	// the number of bytes in the last write
					int time = 0;		// counts the approximate time (in nanofortnights) since last byte was received

					while(writeoff < contentLength) {
						if(is.available() > 0) {
							time = 0;
							readcount = rbc.read(byteBuffer);
							//Out.debug("Read " + readcount + " bytes from upstream server");

							if(readcount >= 0) {
								readoff += readcount;

								// we push the buffer to disk/digest if we either read all of the bytes, or we are above the flush threshold and we cannot fit the remainder in the buffer
								if( (readoff == contentLength) || ( (readoff > writeoff + bufferThreshold) && (writeoff < contentLength - bufferSize) ) ) {
									byteBuffer.flip();
									// we have to make a "metacopy" of this buffer to avoid it being consumed by the digest
									sha1Digest.update(byteBuffer.asReadOnlyBuffer());
									// FileChannel.write(ByteBuffer, long) is guaranteed to consume the entire buffer
									writecount = fileChannel.write(byteBuffer, writeoff);
									writeoff += writecount;
									Stats.bytesRcvd(writecount);
									//Out.debug("Wrote " + writecount + " bytes to " + tempFile);
									byteBuffer.clear();
								}
							}
							else {
								// readcount == -1 => EOF
								Out.warning("\nServer sent premature EOF, aborting.. (" + writeoff + " of " + contentLength + " bytes received)");
								throw new java.net.SocketException("Unexpected end of file from server");
							}
						}
						else {
							if(System.currentTimeMillis() - downloadStart > 300000) {
								Out.warning("\nDownload time limit has expired, aborting...");
								throw new java.net.SocketTimeoutException("Download timed out");
							}
							else if(time > 30000) {
								Out.warning("\nTimeout detected waiting for byte " + writeoff + ", aborting..");
								throw new java.net.SocketTimeoutException("Read timed out");
							}

							time += 5;
							Thread.currentThread().sleep(5);
						}
					}

					Stats.fileRcvd();
					streamThreadSuccess = true;
				}
				catch(Exception e) {
					writeoff = 0;
					readoff = 0;
					byteBuffer.clear();
					sha1Digest.reset();
					Out.debug("Retrying.. (" + trycounter + " tries left)");
				}
				finally {
					try { rbc.close(); } catch(Exception e) {}
					try { is.close(); } catch(Exception e) {}
				}
			} while(!streamThreadSuccess && --trycounter > 0);

			streamThreadComplete = true;
			checkFinalizeDownloadedFile();
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

	public int fillBuffer(ByteBuffer buffer, int offset) throws java.io.IOException {
		int readBytes = 0;

		while(buffer.hasRemaining() && writeoff > offset + readBytes) {
			// this method will never be called unless sufficient bytes are available, so we always want to fill the buffer before we return.
			// we do not buffer reads when doing proxied file downloads. as the data was *just* written, it is almost guaranteed to be in the OS disk buffer.
			readBytes += fileChannel.read(buffer, offset + readBytes);
		}

		// flipping the buffer before use is the responsibility of the caller
		return readBytes;
	}

	public void proxyThreadCompleted() {
		Stats.fileSent();
		proxyThreadComplete = true;
		checkFinalizeDownloadedFile();
	}
	
	private synchronized void checkFinalizeDownloadedFile() {
		if(!streamThreadComplete || !proxyThreadComplete) {
			// we have to wait for both the upstream and downstream transfers to complete before we can close this file
			return;
		}

		if(fileFinalized) {
			Out.warning("ProxyFileDownloader: Attempted to finalize file that was already finalized");
			return;
		}

		fileFinalized = true;

		if(fileChannel != null) {
			try {
				fileChannel.close();
			} catch(Exception e) {}
		}

		if(fileHandle != null) {
			try {
				fileHandle.close();
			} catch(Exception e) {}
		}

		if(tempFile.length() != getContentLength()) {
			Out.debug("Requested file " + fileid + " is incomplete, and will not be stored. (bytes=" + tempFile.length() + ")");
		}
		else {
			String sha1Hash = Tools.binaryToHex(sha1Digest.digest());

			if( !requestedHVFile.getHash().equals(sha1Hash) ) {
				Out.debug("Requested file " + fileid + " is corrupt, and will not be stored. (digest=" + sha1Hash + ")");
			}
			else if( !Settings.isStaticRange(fileid) ) {
				Out.debug("The file " + fileid + " is not in a static range, and will not be stored.");
			}
			else {
				if(client.getCacheHandler().importFile(tempFile, requestedHVFile)) {
					Out.debug("Requested file " + fileid + " was successfully stored in cache.");
				}
				else {
					Out.debug("Requested file " + fileid + " exists or cannot be cached.");
				}

				Out.debug("Proxy file download request complete for " + fileid);
			}
		}

		if(tempFile.exists()) {
			tempFile.delete();
		}
	}
}
