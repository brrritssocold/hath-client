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

import java.util.LinkedList;
import java.util.regex.Pattern;

import org.hath.base.Out;
import org.hath.base.http.handlers.BaseHandler;

/**
 * Verifies the Request and then chooses the appropriate way to handle the
 * request. Sets status Codes.
 *
 */
public class HTTPResponse {
	private static final Pattern absoluteUriPattern = Pattern.compile("^http://[^/]+/", Pattern.CASE_INSENSITIVE);

	private BaseHandler session;

	private boolean requestHeadOnly, validRequest;
	private boolean servercmd;
	private int responseStatusCode;

	public enum Sensing {
		FILE_REQUEST_TOO_SHORT, FILE_REQUEST_INVALID_KEY, FILE_REQUEST_FILE_NOT_FOUND, FILE_REQUEST_FILE_LOCAL, FILE_REQUEST_FILE_STATIC, FILE_REQUEST_VALID_FILE_TOKEN, FILE_REQUEST_INVALID_FILE_TOKEN, FILE_REQUEST_FILE_NOT_LOCAL_OR_STATIC, SERVER_CMD_INVALID_RPC_SERVER, SERVER_CMD_MALFORMED_COMMAND, SERVER_CMD_KEY_INVALID, SERVER_CMD_KEY_VALID, PROXY_REQUEST_INVALID_GID_OR_PAGE_INTEGERS, PROXY_REQUEST_CHECK_PASSKEY, PROXY_REQUEST_GRANTED, PROXY_REQUEST_DENIED, PROXY_REQUEST_PASSKEY_NOT_REQUIRED, PROXY_REQUEST_PASSKEY_AS_EXPECTED, PROXY_REQUEST_PASSKEY_INVALID, PROXY_REQUEST_INVALID_ARGUMENTS, PROXY_REQUEST_LOCAL_FILE, PROXY_REQUEST_PROXY_FILE, PROXY_REQUEST_INVALID_GID_OR_PAGE, TEST_REQUEST_VALID, TEST_REQUEST_INVALID_KEY, TEST_REQUEST_EXPIRED_KEY, TEST_REQUEST_INVALID_REQUEST, ROBOTS, FAVICON, INVALID_REQUEST_LEN2, HTTP_REQUEST_NULL, HTTP_REQUEST_INVALID_LENGTH, HTTP_REQUEST_TYPE_AND_FORM_INVALID, HTTP_REQUEST_INVALID_URL
	}

	public LinkedList<Sensing> sensingPointsHit = new LinkedList<>();

	public HTTPResponse(BaseHandler session) {
		this.session = session;

		validRequest = false;
		servercmd = false;
		requestHeadOnly = false;

		responseStatusCode = 500;	// if nothing alters this, there's a bug somewhere
	}

	private void hitSensingPoint(Sensing point) {
		sensingPointsHit.add(point);
	}

	public void parseRequest(String request, boolean localNetworkAccess) {
		if(request == null) {
			responseStatusCode = 400;
			hitSensingPoint(Sensing.HTTP_REQUEST_NULL);
			return;		
		}
	
		String[] requestParts = request.trim().split(" ", 3);

		if(requestParts.length != 3) {
			Out.warning(session + " Invalid HTTP request form.");
			hitSensingPoint(Sensing.HTTP_REQUEST_INVALID_LENGTH);
		} else if( !(requestParts[0].equalsIgnoreCase("GET") || requestParts[0].equalsIgnoreCase("HEAD")) || !requestParts[2].startsWith("HTTP/") ) {
			Out.warning(session + " HTTP request is not GET or HEAD.");
			responseStatusCode = 405;
			// TODO add header "Allow", "GET,HEAD"
			hitSensingPoint(Sensing.HTTP_REQUEST_TYPE_AND_FORM_INVALID);
			return;
		} else {
			validRequest = true;
			requestHeadOnly = requestParts[0].equalsIgnoreCase("HEAD");
			
			// The request URI may be an absolute path or an absolute URI for GET/HEAD requests (see section 5.1.2 of RFC2616)
			requestParts[1] = absoluteUriPattern.matcher(requestParts[1]).replaceFirst("/");
			
			String[] urlparts = requestParts[1].replace("%3d", "=").split("/");

			if( (urlparts.length < 2) || !urlparts[0].equals("")) {
				Out.warning(session + " The requested URL is invalid or not supported.");
				hitSensingPoint(Sensing.HTTP_REQUEST_INVALID_URL);
			} else {
				if (urlparts.length == 2) {
						Out.warning(session + " Invalid request type '" + urlparts[1] + "'.");
						hitSensingPoint(Sensing.INVALID_REQUEST_LEN2);
				}
				else {
					Out.warning(session + " Invalid request type '" + urlparts[1] + "'.");
				}
			}

			responseStatusCode = 404;
			return;
		}

		Out.warning(session + " Invalid HTTP request.");
		responseStatusCode = 400;
	}

	// accessors

	public int getResponseStatusCode() {
		return responseStatusCode;
	}

	public boolean isValidRequest() {
		return validRequest;
	}

	public boolean isRequestHeadOnly() {
		return requestHeadOnly;
	}

	public boolean isServercmd() {
		return servercmd;
	}
}
