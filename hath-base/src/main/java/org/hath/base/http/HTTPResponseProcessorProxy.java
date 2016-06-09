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

package org.hath.base.http;

import javax.servlet.http.HttpServletResponse;

import org.hath.base.HentaiAtHomeClient;
import org.hath.base.Out;
import org.hath.base.gallery.GalleryFileDownloader;
import org.hath.base.http.handlers.BaseHandler;

public class HTTPResponseProcessorProxy extends HTTPResponseProcessor {
	private HentaiAtHomeClient client;
	private GalleryFileDownloader gdf;
	private int readoff;
	
	public HTTPResponseProcessorProxy(HentaiAtHomeClient client, String fileid, String token, int gid, int page,
			String filename) {
		this.client = client;
		readoff = 0;
		gdf = new GalleryFileDownloader(client, fileid, token, gid, page, filename, false);
	}

	@Deprecated
	public HTTPResponseProcessorProxy(BaseHandler session, String fileid, String token, int gid, int page,
			String filename) {
		this.client = session.getHTTPServer().getHentaiAtHomeClient();
		readoff = 0;
		gdf = new GalleryFileDownloader(client, fileid, token, gid, page, filename, false);
	}

	@Override
	public void initialize(HttpServletResponse response) {
		Out.info(client + ": Initializing proxy request...");
		response.setStatus(gdf.initialize());
	}	

	@Override
	public String getContentType() {
		return gdf.getContentType();
	}

	@Override
	public int getContentLength() {
		return gdf.getContentLength();
	}

	@Override
	public byte[] getBytes() throws Exception {
		return getBytesRange(getContentLength());
	}

	@Override
	public byte[] getBytesRange(int len) throws Exception {
		// wait for data
		int endoff = readoff + len;
		
		//Out.debug("Reading data with readoff=" + readoff + " len=" + len + " writeoff=" + writeoff);
		
		int timeout = 0;
		
		while(endoff > gdf.getCurrentWriteoff()) {
			try {
				Thread.sleep(10);
			} catch(Exception e) {}
			
			if( ++timeout > 30000 ) {
				// we have waited about five minutes, probably won't happen
				Out.info("Timeout while waiting for proxy request.");
				throw new Exception("Timeout while waiting for proxy request.");
			}
		}
		
		byte[] range = gdf.getDownloadBufferRange(readoff, endoff);
		readoff += len;
		return range;
	}
}
