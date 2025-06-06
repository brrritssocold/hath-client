/*

Copyright 2008-2024 E-Hentai.org
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
along with Hentai@Home.  If not, see <https://www.gnu.org/licenses/>.

*/

package hath.base;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;

// this class provides a buffered interface to read a file in chunks

public class HTTPResponseProcessorFile extends HTTPResponseProcessor {
	private HTTPSession session;
	private HVFile requestedHVFile;
	private FileChannel fileChannel;
	private ByteBuffer fileBuffer;
	private MessageDigest sha1Digest = null;
	private int readoff = 0;
	private boolean verifyFileIntegrity = false;

	public HTTPResponseProcessorFile(HTTPSession session, HVFile requestedHVFile, boolean verifyFileIntegrity) {
		this.session = session;
		this.requestedHVFile = requestedHVFile;
		this.verifyFileIntegrity = verifyFileIntegrity;
	}

	public int initialize() {
		int responseStatusCode = 0;

		if(verifyFileIntegrity) {
			//Out.debug("Reading file " + requestedHVFile.getFileid() + " with verifyFileIntegrity=" + verifyFileIntegrity);

			try {
				// the integrity check is done inline, which avoids most of the overhead, since we only need a small amount of extra CPU time to calculate the digest
				// this will not prevent a corrupt file from being sent this time around, as the digest cannot be calculated until after the file has finished sending, but it helps prevent a buildup of bitrot
				sha1Digest = MessageDigest.getInstance("SHA-1");
			} catch(Exception e) {}
		}

		try {
			fileChannel = FileChannel.open(requestedHVFile.getLocalFilePath(), StandardOpenOption.READ);
			fileBuffer = ByteBuffer.allocateDirect(Settings.isUseLessMemory() ? 8192 : 65536);
			fileChannel.read(fileBuffer);
			fileBuffer.flip();
			responseStatusCode = 200;
			Stats.fileSent();
		}
		catch(java.io.IOException e) {
			Out.warning("Failed reading content from " + requestedHVFile.getLocalFilePath());
			responseStatusCode = 500;
		}

		return responseStatusCode;
	}

	public void cleanup() {
		if(fileChannel != null) {
			try {
				fileChannel.close();
			} catch(Exception e) {}
		}

		// if the remote client closed the connection before the file was fully read, parts of the preimage have not been update()'d into sha1Digest, and the digest will therefore obviously be wrong. in this case, skip checking the digest
		// (if the size of the cached file did not match, we would not have attempted to read it in the first place)
		if( (sha1Digest != null) && (readoff == getContentLength()) ) {
			String sha1Hash = Tools.binaryToHex(sha1Digest.digest());

			if(requestedHVFile.getHash().equals(sha1Hash)) {
				Out.debug("Checked integrity of file " + requestedHVFile.getFileid() + ", found expected digest=" + sha1Hash);
			}
			else {
				Out.warning("Checked integrity of file " + requestedHVFile.getFileid() + ", found mismatching digest=" + sha1Hash + "; corrupt file will be deleted from the cache");
				session.getHTTPServer().getHentaiAtHomeClient().getCacheHandler().deleteFileFromCache(requestedHVFile);
			}
		}
	}

	public String getContentType() {
		return requestedHVFile.getMimeType();
	}

	public int getContentLength() {
		if(fileChannel != null) {
			return requestedHVFile.getSize();
		}
		else {
			return 0;
		}
	}

	public ByteBuffer getPreparedTCPBuffer() throws Exception {
		int readbytes = Math.min(getContentLength() - readoff, Settings.TCP_PACKET_SIZE);

		if(readbytes > fileBuffer.remaining()) {
			int fileBytes = 0;
			fileBuffer.compact();

			while(readbytes > fileBuffer.position()) {
				fileBytes += fileChannel.read(fileBuffer);
			}

			fileBuffer.flip();
			//Out.debug("Refilled buffer for " + requestedHVFile + " with " + fileBytes + " bytes, new remaining=" + fileBuffer.remaining());
		}

		//Out.debug("Reading from file " + requestedHVFile + ", readoff=" + readoff + ", readbytes=" + readbytes + ", remaining=" + fileBuffer.remaining());

		ByteBuffer tcpBuffer = fileBuffer.slice();
		tcpBuffer.limit(tcpBuffer.position() + readbytes);
		fileBuffer.position(fileBuffer.position() + readbytes);
		readoff += readbytes;

		if(sha1Digest != null) {
			// we use asReadOnlyBuffer to avoid it being consumed by the digest
			sha1Digest.update(tcpBuffer.asReadOnlyBuffer());
		}

		return tcpBuffer;
	}
}
