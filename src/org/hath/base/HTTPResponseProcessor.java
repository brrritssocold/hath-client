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

import java.nio.ByteBuffer;

public abstract class HTTPResponseProcessor {
	private String header = "";

	public String getContentType() {
		return Settings.CONTENT_TYPE_DEFAULT;
	}

	public int getContentLength() {
		return 0;
	}
	
	public int initialize() {
		return 0;
	}
	
	public void cleanup() {}

	public ByteBuffer getPreparedTCPBuffer() throws Exception {
		return getPreparedTCPBuffer(0);
	}

	public abstract ByteBuffer getPreparedTCPBuffer(int lingeringBytes) throws Exception;

	public String getHeader() {
		return this.header;
	}

	public void addHeaderField(String name, String value) {
		// TODO: encode the value if needed.
		this.header += name + ": " + value + "\r\n";
	}
	
	public void requestCompleted() {
		// if the response processor needs to do some action after the request has completed, this can be overridden
	}
}
