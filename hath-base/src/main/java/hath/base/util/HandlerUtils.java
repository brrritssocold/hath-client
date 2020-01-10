/*

Copyright 2008-2016 E-Hentai.org
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

package hath.base.util;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;

public class HandlerUtils {
	private HandlerUtils() {
	}

	/**
	 * Returns a String showing if the request has been handled, or null if the request is null.
	 */
	public static String isHandledStatus(Request baseRequest) {
		return "handled: " + (baseRequest == null ? "null" : Boolean.toString(baseRequest.isHandled()));
	}

	/**
	 * Returns a String showing if the raw request, or null if the request is null.
	 */
	public static String requestStatus(HttpServletRequest request) {
		return "request: " + (request == null ? "null" : request.toString());
	}

	/**
	 * Returns a String showing the status code, or null if the response is null.
	 */
	public static String statusCodeStatus(HttpServletResponse response) {
		return "status code: " + (response == null ? "null" : Integer.toString(response.getStatus()));
	}

	/**
	 * Returns a String showing the status of the handler (request, isHandled and status code).
	 */
	public static String handlerStatus(Request baseRequest, HttpServletRequest request, HttpServletResponse response) {
		return requestStatus(request) + ", " + isHandledStatus(baseRequest) + ", " + statusCodeStatus(response);
	}
}
