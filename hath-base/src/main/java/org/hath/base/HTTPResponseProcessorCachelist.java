/*

Copyright 2008-2014 E-Hentai.org
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

import java.util.LinkedList;

public class HTTPResponseProcessorCachelist extends HTTPResponseProcessor {
	private CacheHandler cacheHandler;
	
	private int cacheListWritten, segmentIndex, segmentCount;
	private StringBuilder fileidBuffer;

	public HTTPResponseProcessorCachelist(CacheHandler cacheHandler) {
		this.cacheHandler = cacheHandler;
	}
	
	public int initialize() {
		// note: this class is only safe to use during startup while the client is still single-threaded
		// any cache additions or deletions between the initial file length is calculated and this class is invoked will make things fail

		cacheListWritten = 0;
		segmentIndex = 0;
		segmentCount = cacheHandler.getSegmentCount();
		
		fileidBuffer = new StringBuilder(Settings.TCP_PACKET_SIZE_HIGH + Math.round(2 * cacheHandler.getStartupCachedFilesStrlen() / segmentCount));
		
		Out.info("Sending cache list, and waiting for the server to register the cached files.. (this could take a while)");
		
		return 200;
	}

	public int getContentLength() {
		return cacheHandler.getStartupCachedFilesStrlen();
	}
	
	public byte[] getBytes() {
		return getBytesRange(cacheHandler.getStartupCachedFilesStrlen());
	}
	
	public byte[] getBytesRange(int len) {
		//Out.debug("Before: cacheListWritten=" + cacheListWritten + ", fileidBuffer=" + fileidBuffer.length() +", len=" + len);

		while( fileidBuffer.length() < len ) {
			Out.info("Retrieving segment " + segmentIndex + " of " + segmentCount);
		
			if( segmentIndex >= segmentCount ) {
				HentaiAtHomeClient.dieWithError("Segment out of range");
			}
			
			LinkedList<String> fileList = cacheHandler.getCachedFilesSegment(Integer.toHexString(segmentCount | segmentIndex++).substring(1));

			if( fileList.size() < 1 ) {
				continue;
			}
			
			for( String fileid : fileList ) {
				fileidBuffer.append(fileid + "\n");
			}
		}
		
		byte[] returnBytes = fileidBuffer.substring(0, len).getBytes(java.nio.charset.Charset.forName("ISO-8859-1"));
		fileidBuffer.delete(0, len);

		if(returnBytes.length != len) {
			HentaiAtHomeClient.dieWithError("Length of cache list buffer (" + returnBytes.length + ") does not match requested length (" + len + ")! Bad program!");
		}
		
		cacheListWritten += returnBytes.length;
		
		//Out.debug("After: cacheListWritten=" + cacheListWritten + ", fileidBuffer=" + fileidBuffer.length() +", len=" + len);
		
		return returnBytes;
	}
	
}
