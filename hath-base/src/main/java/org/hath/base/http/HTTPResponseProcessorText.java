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

import java.nio.charset.Charset;
import java.util.Arrays;

import org.hath.base.Out;

public class HTTPResponseProcessorText extends HTTPResponseProcessor {
	private byte[] responseBytes;
	private int off;
	private String contentType;

	public HTTPResponseProcessorText(String responseBody) {	
		this(responseBody, "text/html");
	}

	public HTTPResponseProcessorText(String responseBody, String mimeType) {
		this(responseBody, mimeType, Charset.forName("ISO-8859-1"));
	}

	public HTTPResponseProcessorText(String responseBody, String mimeType, Charset charset) {
		int strlen = responseBody.length();
		
		if(strlen > 0) {
			Out.debug("Response Written:");

			if(strlen < 10000) {
				Out.debug(responseBody);
			}
			else {
				Out.debug("tl;dw");		
			}
		}

		this.responseBytes = responseBody.getBytes(charset);
		off = 0;
		contentType = mimeType + "; charset=" + charset.name();
	}
	
	@Override
	public int getContentLength() {
		if(responseBytes != null) {
			return responseBytes.length;
		}
		else {
			return 0;
		}
	}

	@Override
	public String getContentType() {
		return this.contentType;
	}
	
	@Override
	public byte[] getBytes() {
		return responseBytes;
	}
	
	@Override
	public byte[] getBytesRange(int len) {
		byte[] range = Arrays.copyOfRange(responseBytes, off, Math.min(responseBytes.length, off + len));
		off += len;
		return range;
	}
}
