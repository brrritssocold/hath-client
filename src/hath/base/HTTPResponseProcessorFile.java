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

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;

// this class provides provides a buffered interface to read a file in chunks

public class HTTPResponseProcessorFile extends HTTPResponseProcessor {
	private HVFile requestedHVFile;
	private FileChannel fileChannel;
	private ByteBuffer fileBuffer;
	private int readoff = 0;

	public HTTPResponseProcessorFile(HVFile requestedHVFile) {
		this.requestedHVFile = requestedHVFile;
	}

	public int initialize() {
		int responseStatusCode = 0;

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

		return tcpBuffer;
	}
}
