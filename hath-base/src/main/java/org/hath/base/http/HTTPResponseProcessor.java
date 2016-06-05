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

import java.io.IOException;
import java.util.LinkedList;

import javax.servlet.http.HttpServletResponse;

import org.hath.base.HentaiAtHomeClient;
import org.hath.base.Settings;

public abstract class HTTPResponseProcessor {
	private String header = "";
	private LinkedList<HeaderPair> headersToAdd = new LinkedList<>();

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

	public void updateResponse(HttpServletResponse response) throws IOException {
		response.setStatus(initialize());
		response.setContentLength(getContentLength());
		response.setContentType(getContentType());

		for (HeaderPair pair : headersToAdd) {
			response.addHeader(pair.name, pair.value);
		}

		try {
			response.getOutputStream().write(getBytes());
		} catch (Exception e) {
			e.printStackTrace();
			HentaiAtHomeClient.dieWithError("Lazy programmers don't use specific exceptions");
		}
	}

	/**
	 * Subclasses should update the response object directly
	 */
	@Deprecated
	public abstract byte[] getBytes() throws Exception;

	/**
	 * Subclasses should update the response object directly
	 */
	@Deprecated
	public abstract byte[] getBytesRange(int len) throws Exception;
	
	/**
	 * Subclasses should update the response object directly
	 */
	@Deprecated
	public String getHeader() {
		return this.header;
	}

	public void addHeaderField(String name, String value) {
		// TODO: encode the value if needed.
		this.header += name + ": " + value + "\r\n";
		headersToAdd.add(new HeaderPair(name, value));
	}

	protected class HeaderPair {
		public HeaderPair(String name, String value) {
			this.name = name;
			this.value = value;
		}

		public String name;
		public String value;
	}
}
