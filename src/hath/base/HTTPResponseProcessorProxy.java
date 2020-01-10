/*

Copyright 2008-2016 E-Hentai.org
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

package org.hath.base;

import java.lang.Thread;
import java.net.URL;
import java.nio.ByteBuffer;

public class HTTPResponseProcessorProxy extends HTTPResponseProcessor {
	private HTTPSession session;
	private ProxyFileDownloader proxyDownloader;
	private int readoff = 0;
	private ByteBuffer tcpBuffer;

	public HTTPResponseProcessorProxy(HTTPSession session, String fileid, URL source) {
		this.session = session;
		proxyDownloader = new ProxyFileDownloader(session.getHTTPServer().getHentaiAtHomeClient(), fileid, source);
	}

	public int initialize() {
		Out.info(session + ": Initializing proxy request...");
		tcpBuffer = ByteBuffer.allocateDirect(Settings.TCP_PACKET_SIZE);
		return proxyDownloader.initialize();
	}

	public String getContentType() {
		return proxyDownloader.getContentType();
	}

	public int getContentLength() {
		return proxyDownloader.getContentLength();
	}

	public ByteBuffer getPreparedTCPBuffer(int lingeringBytes) throws Exception {
		tcpBuffer.clear();
		
		if(lingeringBytes > 0) {
			tcpBuffer.limit(Settings.TCP_PACKET_SIZE - lingeringBytes);
		}

		int timeout = 0;
		int nextReadThrehold = Math.min(getContentLength(), readoff + tcpBuffer.limit());
		//Out.debug("Filling buffer with limit=" + tcpBuffer.limit() + " at readoff=" + readoff + ", trying to read " + (nextReadThrehold - readoff) + " bytes up to byte " + nextReadThrehold);

		while(nextReadThrehold > proxyDownloader.getCurrentWriteoff()) {
			try {
				Thread.currentThread().sleep(10);
			} catch(Exception e) {}

			if(++timeout > 30000) {
				// we have waited about five minutes, probably won't happen
				throw new Exception("Timeout while waiting for proxy request.");
			}
		}

		int readBytes = proxyDownloader.fillBuffer(tcpBuffer, readoff);
		readoff += readBytes;
		
		tcpBuffer.flip();
		return tcpBuffer;
	}

	public void requestCompleted() {
		proxyDownloader.proxyThreadCompleted();
	}
}
