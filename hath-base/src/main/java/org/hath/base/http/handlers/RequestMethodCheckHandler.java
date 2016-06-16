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

package org.hath.base.http.handlers;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.hath.base.Out;

/**
 * Checks if the request method is allowed
 */
public class RequestMethodCheckHandler extends AbstractHandler {
	private final Set<String> allowedMethods = new HashSet<String>();

	private void addAllowedMethod(HttpMethod method) {
		allowedMethods.add(method.toString());
	}

	public RequestMethodCheckHandler(HttpMethod... allowedMethods) {
		for (HttpMethod method : allowedMethods) {
			addAllowedMethod(method);
		}
	}

	private boolean isAllowedMethod(String method) {
		return allowedMethods.contains(method.toUpperCase());
	}

	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {

		if (!isAllowedMethod(request.getMethod())) {
			response.setStatus(HttpStatus.METHOD_NOT_ALLOWED_405);
			baseRequest.setHandled(true);
			Out.error("Invalid request method (" + request.getMethod() + ") from " + request.getRemoteAddr());

			for (String method : allowedMethods) {
				response.addHeader(HttpHeader.ALLOW.toString(), method);
			}
		}
	}
}
