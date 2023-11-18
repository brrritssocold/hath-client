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

import java.nio.charset.Charset;
import java.nio.ByteBuffer;

public class HTTPResponseProcessorText extends HTTPResponseProcessor {
	private byte[] responseBytes;
	private int writeoff = 0;
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

		responseBytes = responseBody.getBytes(charset);
		contentType = mimeType + "; charset=" + charset.name();
	}

	public int getContentLength() {
		if(responseBytes != null) {
			return responseBytes.length;
		}
		else {
			return 0;
		}
	}

	public String getContentType() {
		return contentType;
	}

	public ByteBuffer getPreparedTCPBuffer() throws Exception {
		int bytecount = Math.min(getContentLength() - writeoff, Settings.TCP_PACKET_SIZE);
		ByteBuffer buffer = ByteBuffer.wrap(responseBytes, writeoff, bytecount);
		writeoff += bytecount;

		// this was a wrap, so we do not flip
		return buffer;
	}

}
