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

public class HTTPResponseProcessorProxy extends HTTPResponseProcessor {
	private HTTPSession session;
	private GalleryFileDownloader gdf;
	private int readoff;
	
	public HTTPResponseProcessorProxy(HTTPSession session, String fileid, String token, int gid, int page, String filename) {
		this.session = session;
		readoff = 0;
		gdf = new GalleryFileDownloader(session.getHTTPServer().getHentaiAtHomeClient(), fileid, token, gid, page, filename, false);
	}

	public int initialize() {
		Out.info(session + ": Initializing proxy request...");
		return gdf.initialize();
	}	

	public String getContentType() {
		return gdf.getContentType();
	}

	public int getContentLength() {
		return gdf.getContentLength();
	}

	public byte[] getBytes() throws Exception {
		return getBytesRange(getContentLength());
	}

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
